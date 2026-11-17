package org.example.stock.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EvalToolsTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ToolCallback[] tools = new EvalTools().getToolCallbacks();

	@Test
	void publishesTheThreeToolDescriptions() {
		assertThat(tools).extracting(tool -> tool.getToolDefinition().name())
				.containsExactly("findSymbol", "getQuote", "getHistory");
		assertThat(tools[0].getToolDefinition().description()).contains("aselsan", "ticker");
		assertThat(tools[0].getToolDefinition().inputSchema()).contains("\"query\"");
		assertThat(tools[1].getToolDefinition().inputSchema()).contains("\"symbols\"");
		assertThat(tools[2].getToolDefinition().inputSchema()).contains("\"symbol\"", "\"range\"", "\"interval\"");
	}

	@Test
	void findsAselsanAndTurkishAirlinesAndNobodyElse() {
		assertThat(json(tools[0].call("{\"query\":\"Aselsan\"}")).path("matches").get(0).path("symbol").asString())
				.isEqualTo("ASELS");
		assertThat(json(tools[0].call("{\"query\":\"turk hava\"}")).path("matches").get(0).path("symbol").asString())
				.isEqualTo("THYAO");
		assertThat(json(tools[0].call("{\"query\":\"Microsoft\"}")).path("matches")).isEmpty();
	}

	@Test
	void quotesKnownTickersAndListsTheRestAsNotFound() {
		JsonNode quotes = json(tools[1].call("{\"symbols\":[\"asels\",\"THYAO.IS\",\"Microsoft\"]}"));

		assertThat(quotes.path("quotes")).extracting(quote -> quote.path("symbol").asString())
				.containsExactly("ASELS", "THYAO");
		assertThat(quotes.path("notFound")).extracting(JsonNode::asString).containsExactly("MICROSOFT");
	}

	@Test
	void returnsOneCandleForASupportedRange() {
		JsonNode history = json(tools[2].call("{\"symbol\":\"ASELS\",\"range\":\"1d\",\"interval\":\"5m\"}"));

		assertThat(history.path("symbol").asString()).isEqualTo("ASELS");
		assertThat(history.path("range").asString()).isEqualTo("1d");
		assertThat(history.path("interval").asString()).isEqualTo("5m");
		assertThat(history.path("candles")).hasSize(1);
	}

	@Test
	void rejectsAnUnknownRange() {
		assertThatExceptionOfType(ToolExecutionException.class)
				.isThrownBy(() -> tools[2].call("{\"symbol\":\"ASELS\",\"range\":\"2d\"}"))
				.withMessageContaining("Range \"2d\" is not supported");
	}

	private static JsonNode json(String mcpText) {
		return ToolResults.unwrap(mcpText, JSON);
	}

}
