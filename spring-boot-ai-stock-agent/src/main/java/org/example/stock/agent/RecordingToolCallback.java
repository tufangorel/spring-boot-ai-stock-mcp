package org.example.stock.agent;

import java.util.List;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wraps a tool callback for one request and records each call, so the response is built from what the tools
 * returned rather than from what the model says they returned.
 */
class RecordingToolCallback implements ToolCallback {

	/** Arguments and results longer than this are cut, matching the MCP server's tool log. */
	static final int MAX_PAYLOAD_CHARACTERS = 4000;

	private static final Log logger = LogFactory.getLog(RecordingToolCallback.class);

	private final ToolCallback delegate;

	private final List<ToolCallRecord> records;

	private final JsonMapper jsonMapper;

	private final String model;

	private final ObservationRegistry observations;

	private final Tracer tracer;

	RecordingToolCallback(ToolCallback delegate, List<ToolCallRecord> records, JsonMapper jsonMapper, String model) {
		this(delegate, records, jsonMapper, model, ObservationRegistry.NOOP, Tracer.NOOP);
	}

	RecordingToolCallback(ToolCallback delegate, List<ToolCallRecord> records, JsonMapper jsonMapper, String model,
			ObservationRegistry observations, Tracer tracer) {
		this.delegate = delegate;
		this.records = records;
		this.jsonMapper = jsonMapper;
		this.model = model;
		this.observations = observations;
		this.tracer = tracer;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return record(toolInput, () -> delegate.call(toolInput));
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		return record(toolInput, () -> delegate.call(toolInput, toolContext));
	}

	/** The name the MCP server gave the tool; the client may have added a prefix to the definition name. */
	String toolName() {
		return delegate instanceof SyncMcpToolCallback mcp ? mcp.getOriginalToolName()
				: delegate.getToolDefinition().name();
	}

	private String record(String toolInput, Supplier<String> call) {
		JsonNode arguments = ToolResults.unwrap(toolInput, jsonMapper);
		Observation observation = Observation.createNotStarted("agent.tool.call", observations)
				.contextualName("agent tool " + toolName())
				.highCardinalityKeyValue("tool", toolName());
		observation.start();
		long started = System.nanoTime();
		try (Observation.Scope scope = observation.openScope()) {
			String result;
			try {
				result = call.get();
			}
			catch (RuntimeException ex) {
				observation.highCardinalityKeyValue("outcome", "error").error(ex);
				records.add(new ToolCallRecord(toolName(), arguments, null, message(ex)));
				logger.info(line("error", started, arguments, null, ex));
				throw ex;
			}
			observation.highCardinalityKeyValue("outcome", "success");
			JsonNode unwrapped = ToolResults.unwrap(result, jsonMapper);
			records.add(new ToolCallRecord(toolName(), arguments, unwrapped, null));
			logger.info(line("success", started, arguments, unwrapped, null));
			return result;
		}
		finally {
			observation.stop();
		}
	}

	private String line(String outcome, long started, JsonNode arguments, JsonNode result, RuntimeException error) {
		StringBuilder line = new StringBuilder("Agent MCP tool call ").append(toolName())
				.append(" model=").append(model)
				.append(" outcome=").append(outcome)
				.append(" durationMs=").append((System.nanoTime() - started) / 1_000_000)
				.append(" arguments=").append(cut(json(arguments)));
		if (error == null) {
			line.append(" result=").append(cut(json(result)));
		}
		else {
			line.append(" error=").append(error.getClass().getSimpleName()).append(": ").append(cut(json(message(error))));
		}
		AskTraceIds.append(line, tracer);
		return line.toString();
	}

	private String json(JsonNode node) {
		return node == null || node.isMissingNode() ? "null" : node.toString();
	}

	private String json(String text) {
		return jsonMapper.writeValueAsString(text);
	}

	private static String cut(String json) {
		if (json.length() <= MAX_PAYLOAD_CHARACTERS) {
			return json;
		}
		return json.substring(0, MAX_PAYLOAD_CHARACTERS) + "... (" + (json.length() - MAX_PAYLOAD_CHARACTERS)
				+ " more characters)";
	}

	/**
	 * The tool's own error text. Spring AI reports an MCP tool error as
	 * {@code Error calling tool: [TextContent[annotations=null, text=...]]}.
	 */
	static String message(Throwable ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}
		int text = message.indexOf("text=");
		if (text < 0) {
			return message;
		}
		String rest = message.substring(text + "text=".length());
		int lineEnd = rest.indexOf('\n');
		return (lineEnd >= 0 ? rest.substring(0, lineEnd) : rest.replaceFirst("(, meta=[^\\]]*)?]]$", "")).strip();
	}

}
