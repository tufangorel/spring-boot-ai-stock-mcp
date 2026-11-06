package org.example.stock.agent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

/**
 * Turns what a tool callback returns into the tool's own JSON. Spring AI's MCP callback returns the MCP text content
 * as {@code [{"text":"{...}"}]}; other clients may include {@code "type":"text"} or return a result object with
 * {@code structuredContent}; a local callback returns the JSON directly.
 */
final class ToolResults {

	private ToolResults() {
	}

	static JsonNode unwrap(String raw, JsonMapper jsonMapper) {
		return unwrap(parse(raw, jsonMapper), jsonMapper);
	}

	private static JsonNode unwrap(JsonNode node, JsonMapper jsonMapper) {
		if (node.isObject() && node.has("structuredContent")) {
			return node.get("structuredContent");
		}
		if (node.isObject() && node.has("content")) {
			return unwrap(node.get("content"), jsonMapper);
		}
		if (node.isArray() && !node.isEmpty() && node.get(0).path("text").isString()) {
			StringBuilder text = new StringBuilder();
			for (JsonNode item : node) {
				text.append(item.path("text").asString());
			}
			return parse(text.toString(), jsonMapper);
		}
		return node;
	}

	private static JsonNode parse(String text, JsonMapper jsonMapper) {
		if (text == null) {
			return StringNode.valueOf("");
		}
		try {
			JsonNode node = jsonMapper.readTree(text);
			return node.isMissingNode() ? StringNode.valueOf(text) : node;
		}
		catch (JacksonException ex) {
			return StringNode.valueOf(text);
		}
	}

}
