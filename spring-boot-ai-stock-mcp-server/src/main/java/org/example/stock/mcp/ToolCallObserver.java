package org.example.stock.mcp;

import java.util.Map;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records every tool call as one observation, which becomes a span named {@code tools/call <tool>}, a
 * {@code mcp.tool.call} timer, and one log line. The MCP SDK has no hook for this, so {@link StockTools} wraps each
 * tool with {@link #observe}.
 * <p>
 * The attribute names follow the OpenTelemetry conventions for generative AI tool calls. Arguments and results are
 * recorded as JSON and cut to a maximum length; errors are always recorded with their message.
 */
public class ToolCallObserver {

	static final String OBSERVATION = "mcp.tool.call";

	static final String TOOL = "gen_ai.tool.name";

	static final String OUTCOME = "outcome";

	static final String ARGUMENTS = "gen_ai.tool.call.arguments";

	static final String RESULT = "gen_ai.tool.call.result";

	private static final Log logger = LogFactory.getLog(ToolCallObserver.class);

	private final ObservationRegistry registry;

	private final JsonMapper jsonMapper;

	private final boolean recordPayloads;

	private final int maxPayloadCharacters;

	public ToolCallObserver(ObservationRegistry registry, JsonMapper jsonMapper, boolean recordPayloads,
			int maxPayloadCharacters) {
		this.registry = registry;
		this.jsonMapper = jsonMapper;
		this.recordPayloads = recordPayloads;
		this.maxPayloadCharacters = maxPayloadCharacters;
	}

	/**
	 * Runs the tool and records the call. An exception from the tool is recorded and rethrown unchanged.
	 */
	public <T> T observe(String tool, Map<String, Object> arguments, Supplier<T> call) {
		Observation observation = Observation.createNotStarted(OBSERVATION, registry)
				.contextualName("tools/call " + tool)
				.lowCardinalityKeyValue(TOOL, tool);
		String argumentsText = "";
		if (recordPayloads) {
			String argumentsJson = json(arguments);
			observation.highCardinalityKeyValue(ARGUMENTS, argumentsJson);
			argumentsText = " arguments=" + argumentsJson;
		}
		long started = System.nanoTime();
		observation.start();
		// The log lines are written inside the scope so that they carry the trace ID of this call.
		try (Observation.Scope scope = observation.openScope()) {
			T result;
			try {
				result = call.get();
			}
			catch (RuntimeException ex) {
				observation.lowCardinalityKeyValue(OUTCOME, "error").error(ex);
				logger.info("MCP tool call " + tool + " outcome=error durationMs=" + millisSince(started)
						+ argumentsText + " error=" + ex.getClass().getSimpleName() + ": " + json(ex.getMessage()));
				throw ex;
			}
			observation.lowCardinalityKeyValue(OUTCOME, "success");
			String resultText = "";
			if (recordPayloads) {
				String resultJson = json(result);
				observation.highCardinalityKeyValue(RESULT, resultJson);
				resultText = " result=" + resultJson;
			}
			logger.info("MCP tool call " + tool + " outcome=success durationMs=" + millisSince(started)
					+ argumentsText + resultText);
			return result;
		}
		finally {
			observation.stop();
		}
	}

	private String json(Object value) {
		String json = jsonMapper.writeValueAsString(value);
		if (json.length() <= maxPayloadCharacters) {
			return json;
		}
		return json.substring(0, maxPayloadCharacters) + "... (" + (json.length() - maxPayloadCharacters)
				+ " more characters)";
	}

	private static long millisSince(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000;
	}

}
