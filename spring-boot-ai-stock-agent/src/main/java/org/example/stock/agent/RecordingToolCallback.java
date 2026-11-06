package org.example.stock.agent;

import java.util.List;
import java.util.function.Supplier;

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

	private final ToolCallback delegate;

	private final List<ToolCallRecord> records;

	private final JsonMapper jsonMapper;

	RecordingToolCallback(ToolCallback delegate, List<ToolCallRecord> records, JsonMapper jsonMapper) {
		this.delegate = delegate;
		this.records = records;
		this.jsonMapper = jsonMapper;
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
		String result;
		try {
			result = call.get();
		}
		catch (RuntimeException ex) {
			records.add(new ToolCallRecord(toolName(), arguments, null, message(ex)));
			throw ex;
		}
		records.add(new ToolCallRecord(toolName(), arguments, ToolResults.unwrap(result, jsonMapper), null));
		return result;
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
