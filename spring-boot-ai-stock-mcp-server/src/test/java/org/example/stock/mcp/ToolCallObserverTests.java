package org.example.stock.mcp;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.example.stock.mcp.StockData.SymbolMatch;
import org.example.stock.mcp.StockData.SymbolMatches;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ToolCallObserverTests {

	static final Map<String, Object> ARGUMENTS = Map.of("query", "aselsan");

	static final SymbolMatches MATCHES = new SymbolMatches("aselsan",
			List.of(new SymbolMatch("ASELS", "Aselsan", "BIST")));

	static final String MATCHES_JSON = "{\"query\":\"aselsan\",\"matches\":[{\"symbol\":\"ASELS\",\"name\":\"Aselsan\","
			+ "\"exchange\":\"BIST\"}]}";

	private final TestObservationRegistry registry = TestObservationRegistry.create();

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void recordsTheArgumentsAndTheResultOfASuccessfulCall(CapturedOutput output) {
		ToolCallObserver observer = new ToolCallObserver(registry, jsonMapper, true, 4000);

		SymbolMatches result = observer.observe("findSymbol", ARGUMENTS, () -> MATCHES);

		assertThat(result).isSameAs(MATCHES);
		TestObservationRegistryAssert.assertThat(registry)
				.hasObservationWithNameEqualTo("mcp.tool.call")
				.that()
				.hasBeenStopped()
				.hasContextualNameEqualTo("tools/call findSymbol")
				.hasLowCardinalityKeyValue("gen_ai.tool.name", "findSymbol")
				.hasLowCardinalityKeyValue("outcome", "success")
				.hasHighCardinalityKeyValue("gen_ai.tool.call.arguments", "{\"query\":\"aselsan\"}")
				.hasHighCardinalityKeyValue("gen_ai.tool.call.result", MATCHES_JSON)
				.doesNotHaveError();
		assertThat(output).containsPattern("MCP tool call findSymbol outcome=success durationMs=\\d+ ")
				.contains("arguments={\"query\":\"aselsan\"} result=" + MATCHES_JSON);
	}

	@Test
	void recordsAFailedCallAndRethrowsTheSameException(CapturedOutput output) {
		ToolCallObserver observer = new ToolCallObserver(registry, jsonMapper, true, 4000);
		IllegalArgumentException failure = new IllegalArgumentException("Range \"2d\" is not supported.");

		assertThatThrownBy(() -> observer.observe("getHistory", ARGUMENTS, () -> {
			throw failure;
		})).isSameAs(failure);

		TestObservationRegistryAssert.assertThat(registry)
				.hasObservationWithNameEqualTo("mcp.tool.call")
				.that()
				.hasBeenStopped()
				.hasLowCardinalityKeyValue("gen_ai.tool.name", "getHistory")
				.hasLowCardinalityKeyValue("outcome", "error")
				.hasHighCardinalityKeyValue("gen_ai.tool.call.arguments", "{\"query\":\"aselsan\"}")
				.doesNotHaveHighCardinalityKeyValueWithKey("gen_ai.tool.call.result")
				.hasError(failure);
		assertThat(output).containsPattern("MCP tool call getHistory outcome=error durationMs=\\d+ ")
				.contains("error=IllegalArgumentException: \"Range \\\"2d\\\" is not supported.\"");
	}

	@Test
	void cutsArgumentsAndResultsThatAreTooLong(CapturedOutput output) {
		ToolCallObserver observer = new ToolCallObserver(registry, jsonMapper, true, 10);

		observer.observe("findSymbol", ARGUMENTS, () -> MATCHES);

		TestObservationRegistryAssert.assertThat(registry)
				.hasObservationWithNameEqualTo("mcp.tool.call")
				.that()
				.hasHighCardinalityKeyValue("gen_ai.tool.call.arguments", "{\"query\":\"... (9 more characters)")
				.hasHighCardinalityKeyValue("gen_ai.tool.call.result",
						"{\"query\":\"... (" + (MATCHES_JSON.length() - 10) + " more characters)");
		assertThat(output).contains("arguments={\"query\":\"... (9 more characters) result={\"query\":\"... (");
	}

	@Test
	void leavesArgumentsAndResultsOutWhenTheyAreTurnedOff(CapturedOutput output) {
		ToolCallObserver observer = new ToolCallObserver(registry, jsonMapper, false, 4000);

		observer.observe("findSymbol", ARGUMENTS, () -> MATCHES);

		TestObservationRegistryAssert.assertThat(registry)
				.hasObservationWithNameEqualTo("mcp.tool.call")
				.that()
				.hasLowCardinalityKeyValue("gen_ai.tool.name", "findSymbol")
				.hasLowCardinalityKeyValue("outcome", "success")
				.doesNotHaveHighCardinalityKeyValueWithKey("gen_ai.tool.call.arguments")
				.doesNotHaveHighCardinalityKeyValueWithKey("gen_ai.tool.call.result");
		assertThat(output).containsPattern("MCP tool call findSymbol outcome=success durationMs=\\d+\\R")
				.doesNotContain("aselsan");
	}

}
