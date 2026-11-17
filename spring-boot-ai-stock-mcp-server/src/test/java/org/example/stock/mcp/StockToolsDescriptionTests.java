package org.example.stock.mcp;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool-choice eval shows the model the text in the agent's fixture. This fails the normal build when a tool
 * description changes and the fixture is left behind.
 */
class StockToolsDescriptionTests {

	private static final Path FIXTURE = Path.of("..", "spring-boot-ai-stock-agent", "src", "test", "resources", "eval",
			"tool-definitions.json");

	@Test
	void fixtureMatchesThePublishedToolText() throws IOException {
		JsonNode tools = JsonMapper.builder().build().readTree(Files.readString(FIXTURE)).path("tools");
		List<Method> published = List.of(StockTools.class.getDeclaredMethods()).stream()
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.toList();

		assertThat(published).map(method -> method.getAnnotation(McpTool.class).name())
				.containsExactlyInAnyOrderElementsOf(tools.valueStream().map(tool -> tool.path("name").asString()).toList());

		for (Method method : published) {
			McpTool annotation = method.getAnnotation(McpTool.class);
			JsonNode tool = tools.valueStream()
					.filter(candidate -> annotation.name().equals(candidate.path("name").asString()))
					.findFirst()
					.orElseThrow();
			assertThat(tool.path("description").asString()).as(annotation.name()).isEqualTo(annotation.description());
			Parameter[] parameters = method.getParameters();
			JsonNode fixtureParameters = tool.path("parameters");
			assertThat(fixtureParameters).as(annotation.name()).hasSize(parameters.length);
			for (int i = 0; i < parameters.length; i++) {
				McpToolParam parameter = parameters[i].getAnnotation(McpToolParam.class);
				JsonNode fixtureParameter = fixtureParameters.get(i);
				assertThat(fixtureParameter.path("name").asString()).isEqualTo(parameters[i].getName());
				assertThat(fixtureParameter.path("description").asString()).isEqualTo(parameter.description());
				assertThat(fixtureParameter.path("required").asBoolean()).isEqualTo(parameter.required());
			}
		}
	}

}
