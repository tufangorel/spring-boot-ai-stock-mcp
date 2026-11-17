package org.example.stock.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class RecordingToolCallbackTests {

	private static final String MODEL = "openai/gpt-4o-mini";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final List<ToolCallRecord> records = new ArrayList<>();

	@Test
	void recordsTheArgumentsAndTheUnwrappedResult() {
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getQuote"));
		given(delegate.call("{\"symbols\":[\"ASELS\"]}")).willReturn(FakeTools.mcpText(Samples.QUOTES_JSON));
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper, MODEL);

		String result = callback.call("{\"symbols\":[\"ASELS\"]}");

		assertThat(result).isEqualTo(FakeTools.mcpText(Samples.QUOTES_JSON));
		assertThat(records).singleElement().satisfies(record -> {
			assertThat(record.tool()).isEqualTo("getQuote");
			assertThat(record.arguments()).isEqualTo(jsonMapper.readTree("{\"symbols\":[\"ASELS\"]}"));
			assertThat(record.result()).isEqualTo(jsonMapper.readTree(Samples.QUOTES_JSON));
			assertThat(record.error()).isNull();
		});
	}

	@Test
	void recordsAFailureAndRethrowsIt() {
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getQuote"));
		given(delegate.call("{}")).willThrow(new IllegalStateException("Connection refused"));
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper, MODEL);

		assertThatIllegalStateException().isThrownBy(() -> callback.call("{}")).withMessage("Connection refused");
		assertThat(records).singleElement().satisfies(record -> {
			assertThat(record.result()).isNull();
			assertThat(record.error()).isEqualTo("Connection refused");
		});
	}

	@Test
	void passesTheToolContextAndMetadataThrough() {
		ToolCallback delegate = mock(ToolCallback.class);
		ToolContext context = new ToolContext(Map.of("k", "v"));
		ToolMetadata metadata = ToolMetadata.builder().returnDirect(false).build();
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("findSymbol"));
		given(delegate.getToolMetadata()).willReturn(metadata);
		given(delegate.call("{\"query\":\"aselsan\"}", context)).willReturn("{\"matches\":[]}");
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper, MODEL);

		assertThat(callback.call("{\"query\":\"aselsan\"}", context)).isEqualTo("{\"matches\":[]}");
		assertThat(callback.getToolMetadata()).isSameAs(metadata);
		assertThat(callback.getToolDefinition().name()).isEqualTo("findSymbol");
		assertThat(records).singleElement().satisfies(record -> assertThat(record.result().path("matches").isArray()).isTrue());
	}

	@Test
	void namesMcpToolsByTheirServerNameWithoutTheClientPrefix() {
		SyncMcpToolCallback delegate = mock(SyncMcpToolCallback.class);
		given(delegate.getOriginalToolName()).willReturn("getQuote");
		given(delegate.call("{}")).willReturn("[]");

		new RecordingToolCallback(delegate, records, jsonMapper, MODEL).call("{}");

		assertThat(records).singleElement().satisfies(record -> assertThat(record.tool()).isEqualTo("getQuote"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Error calling tool: [TextContent[annotations=null, text=Range \"2d\" is not supported.]]|Range \"2d\" is not supported.",
			"Error calling tool: [TextContent[annotations=null, text=Bad range, meta=null]]|Bad range",
			"Connection refused|Connection refused" })
	void extractsTheToolsOwnErrorText(String message, String expected) {
		assertThat(RecordingToolCallback.message(new IllegalStateException(message))).isEqualTo(expected);
	}

	@Test
	void keepsTheFirstLineOfARepeatedErrorText() {
		String message = "Error calling tool: [TextContent[annotations=null, text=Bad range.\r\nBad range.]]";

		assertThat(RecordingToolCallback.message(new IllegalStateException(message))).isEqualTo("Bad range.");
	}

	@Test
	void namesTheExceptionWhenItHasNoMessage() {
		assertThat(RecordingToolCallback.message(new IllegalStateException())).isEqualTo("IllegalStateException");
		assertThat(RecordingToolCallback.message(new IllegalStateException(" "))).isEqualTo("IllegalStateException");
	}

	@Test
	void logsTheModelOutcomeAndPayloads(CapturedOutput output) {
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getQuote"));
		given(delegate.call("{\"symbols\":[\"ASELS\"]}")).willReturn(FakeTools.mcpText(Samples.QUOTES_JSON));

		new RecordingToolCallback(delegate, records, jsonMapper, MODEL).call("{\"symbols\":[\"ASELS\"]}");

		assertThat(output).contains("Agent MCP tool call getQuote model=" + MODEL + " outcome=success")
				.containsPattern("durationMs=\\d+")
				.contains("arguments={\"symbols\":[\"ASELS\"]}")
				.contains("result=" + jsonMapper.readTree(Samples.QUOTES_JSON));
	}

	@Test
	void logsAFailedCallWithTheModel(CapturedOutput output) {
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getHistory"));
		given(delegate.call("{}")).willThrow(new IllegalArgumentException("Range \"2d\" is not supported."));

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
				() -> new RecordingToolCallback(delegate, records, jsonMapper, "anthropic/claude-haiku-4-5").call("{}"));

		assertThat(output)
				.contains("Agent MCP tool call getHistory model=anthropic/claude-haiku-4-5 outcome=error")
				.containsPattern("durationMs=\\d+")
				.contains("error=IllegalArgumentException: \"Range \\\"2d\\\" is not supported.\"");
	}

	@Test
	void cutsPayloadsLongerThanFourThousandCharacters(CapturedOutput output) {
		String payload = "a".repeat(RecordingToolCallback.MAX_PAYLOAD_CHARACTERS);
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getQuote"));
		given(delegate.call("\"x\"")).willReturn(jsonMapper.writeValueAsString(payload));

		new RecordingToolCallback(delegate, records, jsonMapper, MODEL).call("\"x\"");

		int extra = jsonMapper.writeValueAsString(payload).length() - RecordingToolCallback.MAX_PAYLOAD_CHARACTERS;
		assertThat(output).contains("result=" + jsonMapper.writeValueAsString(payload).substring(0, RecordingToolCallback.MAX_PAYLOAD_CHARACTERS))
				.contains("... (" + extra + " more characters)");
	}

}
