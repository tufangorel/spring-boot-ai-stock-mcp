package org.example.stock.mcp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.example.stock.mcp.StockData.Candle;
import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quote;
import org.example.stock.mcp.StockData.SymbolMatch;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/** Starts the server and talks to it the way the agent and Cursor do: as an MCP client over Streamable HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.ai.mcp.client.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class StockToolsMcpTests {

	static final Quote ASELS = new Quote("ASELS", "Aselsan Elektronik Sanayi ve Ticaret A.S. Class B", 330.75, "TRY",
			1.75, 0.53, 329.5, 335.75, 325.25, 30437685L, OffsetDateTime.parse("2026-11-06T15:40:11+03:00"), 15,
			"TradingView", true);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@LocalServerPort
	private int port;

	@MockitoBean
	private TradingViewClient tradingView;

	@MockitoBean
	private YahooClient yahoo;

	@Autowired
	private MeterRegistry meterRegistry;

	private McpSyncClient client;

	@BeforeEach
	void connect() {
		client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).build())
				.requestTimeout(Duration.ofSeconds(10))
				.build();
		client.initialize();
	}

	@AfterEach
	void close() {
		client.closeGracefully();
	}

	@Test
	void listsTheThreeReadOnlyTools() {
		List<Tool> tools = client.listTools().tools();

		assertThat(tools).extracting(Tool::name).containsExactlyInAnyOrder("findSymbol", "getQuote", "getHistory");
		assertThat(tools).allSatisfy(tool -> {
			assertThat(tool.annotations().readOnlyHint()).isTrue();
			assertThat(tool.annotations().destructiveHint()).isFalse();
		});
		assertThat(tools).filteredOn(tool -> tool.name().equals("getHistory"))
				.singleElement()
				.satisfies(tool -> assertThat(tool.inputSchema().get("required")).isEqualTo(List.of("symbol", "range")));
		assertThat(client.getCurrentInitializationResult().serverInfo().name())
				.isEqualTo("spring-boot-ai-stock-mcp-server");
	}

	@Test
	void returnsQuotesAsJsonText() {
		given(tradingView.quotes(eq(List.of("ASELS", "NOPE1")), anyBoolean())).willReturn(List.of(ASELS));
		given(yahoo.quote("NOPE1", false)).willReturn(Optional.empty());
		given(yahoo.quote("NOPE1", true)).willReturn(Optional.empty());

		CallToolResult result = client.callTool(new CallToolRequest("getQuote",
				Map.of("symbols", List.of("asels", "NOPE1"))));

		assertThat(result.isError()).isFalse();
		assertThat(text(result)).contains("\"symbol\":\"ASELS\"", "\"price\":330.75",
				"\"asOf\":\"2026-11-06T15:40:11+03:00\"", "\"delayMinutes\":15", "\"source\":\"TradingView\"",
				"\"notFound\":[\"NOPE1\"]");
	}

	@Test
	void findsSymbolsAndHistory() {
		given(tradingView.search("aselsan", 5)).willReturn(List.of(new SymbolMatch("ASELS", "Aselsan", "BIST")));
		given(yahoo.history("ASELS", "5d", "1d", 120)).willReturn(Optional.of(new History("ASELS", "TRY", "5d", "1d",
				List.of(new Candle(OffsetDateTime.parse("2026-11-06T09:30:00+03:00"), 329.5, 335.75, 325.25, 330.75,
						30329395L)),
				0, OffsetDateTime.parse("2026-11-06T15:25:56+03:00"), 15, "Yahoo Finance")));

		CallToolResult symbols = client.callTool(new CallToolRequest("findSymbol", Map.of("query", "aselsan")));
		CallToolResult history = client.callTool(new CallToolRequest("getHistory",
				Map.of("symbol", "ASELS", "range", "5d", "interval", "1d")));

		assertThat(text(symbols)).isEqualTo(
				"{\"query\":\"aselsan\",\"matches\":[{\"symbol\":\"ASELS\",\"name\":\"Aselsan\",\"exchange\":\"BIST\"}]}");
		assertThat(text(history)).contains("\"candles\":[{\"time\":\"2026-11-06T09:30:00+03:00\"",
				"\"source\":\"Yahoo Finance\"");
	}

	@Test
	void returnsInvalidInputAsAToolErrorTheModelCanActOn() {
		CallToolResult result = client.callTool(new CallToolRequest("getHistory",
				Map.of("symbol", "ASELS", "range", "2d")));

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).contains("Range \"2d\" is not supported. Use one of 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y.");
	}

	@Test
	void givesSpringAiToolCallbacksTheToolJsonInsideTheMcpContent() {
		given(tradingView.quotes(eq(List.of("ASELS")), anyBoolean())).willReturn(List.of(ASELS));
		ToolCallback getQuote = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client))
				.stream()
				.filter(callback -> callback.getToolDefinition().name().endsWith("getQuote"))
				.findFirst()
				.orElseThrow();

		String output = getQuote.call("{\"symbols\":[\"ASELS\"]}");

		assertThat(output).startsWith("[{\"text\":\"{\\\"quotes\\\":[{\\\"symbol\\\":\\\"ASELS\\\"")
				.contains("\\\"price\\\":330.75")
				.endsWith("\\\"notFound\\\":[]}\"}]");
	}

	@Test
	void givesSpringAiToolCallbacksToolErrorsAsExceptions() {
		ToolCallback getHistory = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client))
				.stream()
				.filter(callback -> callback.getToolDefinition().name().endsWith("getHistory"))
				.findFirst()
				.orElseThrow();

		assertThatThrownBy(() -> getHistory.call("{\"symbol\":\"ASELS\",\"range\":\"2d\"}"))
				.isInstanceOf(ToolExecutionException.class)
				.hasMessageStartingWith("Error calling tool: [TextContent[annotations=null, text=Range \"2d\" is not supported");
	}

	@Test
	void recordsATimerAndALogLineWithArgumentsAndResultForEachToolCall(CapturedOutput output) {
		given(tradingView.search("aselsan", 5)).willReturn(List.of(new SymbolMatch("ASELS", "Aselsan", "BIST")));
		long before = toolCalls("findSymbol", "success");

		client.callTool(new CallToolRequest("findSymbol", Map.of("query", "aselsan")));

		assertThat(toolCalls("findSymbol", "success")).isEqualTo(before + 1);
		assertThat(output).contains("arguments={\"query\":\"aselsan\"} result={\"query\":\"aselsan\",\"matches\":"
				+ "[{\"symbol\":\"ASELS\",\"name\":\"Aselsan\",\"exchange\":\"BIST\"}]}");
		// The trace and span IDs in front of the logger name tie the line to its trace.
		assertThat(output).containsPattern("\\[[0-9a-f]{32}-[0-9a-f]{16}] \\S*ToolCallObserver\\s*: "
				+ "MCP tool call findSymbol outcome=success durationMs=\\d+ ");
	}

	@Test
	void recordsAToolErrorWithItsMessage(CapturedOutput output) {
		long before = toolCalls("getHistory", "error");

		client.callTool(new CallToolRequest("getHistory", Map.of("symbol", "ASELS", "range", "2d")));

		assertThat(toolCalls("getHistory", "error")).isEqualTo(before + 1);
		assertThat(output).containsPattern("MCP tool call getHistory outcome=error durationMs=\\d+ ")
				.contains("arguments={\"symbol\":\"ASELS\",\"range\":\"2d\",\"interval\":null} "
						+ "error=IllegalArgumentException: \"Range \\\"2d\\\" is not supported.");
	}

	@Test
	void reportsItsHealthWithoutDetails() {
		assertThat(get("/actuator/health"))
				.isEqualTo(json("{\"status\":\"UP\",\"groups\":[\"liveness\",\"providers\",\"readiness\"]}"));
		assertThat(get("/actuator/health/liveness")).isEqualTo(json("{\"status\":\"UP\"}"));
		assertThat(get("/actuator/health/readiness")).isEqualTo(json("{\"status\":\"UP\"}"));
	}

	/** The provider clients are replaced in this test, so no request has reached a provider. */
	@Test
	void showsTheStateOfEachProviderInItsOwnHealthGroup() {
		assertThat(get("/actuator/health/providers")).isEqualTo(json("""
				{"status":"UP","components":{"dataProviders":{"status":"UP","details":{
				  "tradingView":{"state":"not called yet","consecutiveFailures":0},
				  "yahoo":{"state":"not called yet","consecutiveFailures":0}}}}}"""));
	}

	private JsonNode get(String path) {
		return json(RestClient.create().get().uri("http://127.0.0.1:" + port + path).retrieve().body(String.class));
	}

	private static JsonNode json(String text) {
		return JSON.readTree(text);
	}

	/** The server is shared by the tests in this class, so calls are counted from where a test starts. */
	private long toolCalls(String tool, String outcome) {
		Timer timer = meterRegistry.find("mcp.tool.call").tags("gen_ai.tool.name", tool, "outcome", outcome).timer();
		return timer == null ? 0 : timer.count();
	}

	private static String text(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}

}
