package org.example.stock.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RecordingToolCallbackTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final List<ToolCallRecord> records = new ArrayList<>();

	@Test
	void recordsTheArgumentsAndTheUnwrappedResult() {
		ToolCallback delegate = mock(ToolCallback.class);
		given(delegate.getToolDefinition()).willReturn(FakeTools.definition("getQuote"));
		given(delegate.call("{\"symbols\":[\"ASELS\"]}")).willReturn(FakeTools.mcpText(Samples.QUOTES_JSON));
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper);

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
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper);

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
		RecordingToolCallback callback = new RecordingToolCallback(delegate, records, jsonMapper);

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

		new RecordingToolCallback(delegate, records, jsonMapper).call("{}");

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

}
