package org.example.stock.agent;

import tools.jackson.databind.JsonNode;

/**
 * One tool call made while answering a question.
 *
 * @param result the tool's payload, or {@code null} when the call failed
 * @param error why the call failed, or {@code null}
 */
public record ToolCallRecord(String tool, JsonNode arguments, JsonNode result, String error) {
}
