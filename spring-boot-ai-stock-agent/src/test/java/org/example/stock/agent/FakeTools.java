package org.example.stock.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stands in for the stock MCP server. Results are wrapped the way Spring AI's MCP callback returns them, as
 * {@code [{"text":"..."}]}, which the server's MCP test pins down.
 */
class FakeTools implements ToolCallbackProvider {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Map<String, Function<String, String>> tools = new LinkedHashMap<>();

	private final List<String> inputs = new ArrayList<>();

	FakeTools returning(String name, String json) {
		tools.put(name, input -> mcpText(json));
		return this;
	}

	FakeTools failing(String name, RuntimeException failure) {
		tools.put(name, input -> {
			throw failure;
		});
		return this;
	}

	FakeTools reset() {
		tools.clear();
		inputs.clear();
		return this;
	}

	List<String> inputs() {
		return inputs;
	}

	static String mcpText(String json) {
		return JSON.writeValueAsString(List.of(Map.of("text", json)));
	}

	static ToolDefinition definition(String name) {
		return ToolDefinition.builder().name(name).description(name).inputSchema("{\"type\":\"object\"}").build();
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return tools.entrySet().stream().map(tool -> (ToolCallback) new ToolCallback() {

			@Override
			public ToolDefinition getToolDefinition() {
				return definition(tool.getKey());
			}

			@Override
			public String call(String toolInput) {
				inputs.add(toolInput);
				return tool.getValue().apply(toolInput);
			}

		}).toArray(ToolCallback[]::new);
	}

}
