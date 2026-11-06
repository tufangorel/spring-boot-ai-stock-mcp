package org.example.stock.mcp.provider;

import tools.jackson.databind.JsonNode;

final class Json {

	private Json() {
	}

	static Double number(JsonNode node) {
		return node == null || !node.isNumber() ? null : node.asDouble();
	}

	static Long whole(JsonNode node) {
		return node == null || !node.isNumber() ? null : node.asLong();
	}

	static String text(JsonNode node) {
		return node == null || !node.isString() || node.asString().isBlank() ? null : node.asString();
	}

	static Double round(Double value) {
		return value == null ? null : Math.round(value * 100) / 100.0;
	}

}
