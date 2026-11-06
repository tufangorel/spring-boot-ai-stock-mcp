package org.example.stock.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultsTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final JsonNode quotes = jsonMapper.readTree(Samples.QUOTES_JSON);

	@Test
	void unwrapsSpringAiMcpText() {
		assertThat(ToolResults.unwrap(FakeTools.mcpText(Samples.QUOTES_JSON), jsonMapper)).isEqualTo(quotes);
	}

	@Test
	void unwrapsTypedTextContentSplitIntoParts() {
		String raw = "[{\"type\":\"text\",\"text\":\"{\\\"quotes\\\":\"},{\"type\":\"text\",\"text\":\"[]}\"}]";

		assertThat(ToolResults.unwrap(raw, jsonMapper)).isEqualTo(jsonMapper.readTree("{\"quotes\":[]}"));
	}

	@Test
	void prefersStructuredContent() {
		String raw = "{\"content\":[{\"type\":\"text\",\"text\":\"ignored\"}],\"structuredContent\":" + Samples.QUOTES_JSON
				+ "}";

		assertThat(ToolResults.unwrap(raw, jsonMapper)).isEqualTo(quotes);
	}

	@Test
	void unwrapsAResultObjectWithContent() {
		String raw = "{\"content\":" + FakeTools.mcpText(Samples.QUOTES_JSON) + ",\"isError\":false}";

		assertThat(ToolResults.unwrap(raw, jsonMapper)).isEqualTo(quotes);
	}

	@Test
	void keepsPlainJsonAndText() {
		assertThat(ToolResults.unwrap(Samples.QUOTES_JSON, jsonMapper)).isEqualTo(quotes);
		assertThat(ToolResults.unwrap("[1,2]", jsonMapper)).isEqualTo(jsonMapper.readTree("[1,2]"));
		assertThat(ToolResults.unwrap("[]", jsonMapper)).isEqualTo(jsonMapper.readTree("[]"));
		assertThat(ToolResults.unwrap("[{\"text\":\"Market is closed\"}]", jsonMapper))
				.isEqualTo(StringNode.valueOf("Market is closed"));
		assertThat(ToolResults.unwrap("not json", jsonMapper)).isEqualTo(StringNode.valueOf("not json"));
		assertThat(ToolResults.unwrap("", jsonMapper)).isEqualTo(StringNode.valueOf(""));
		assertThat(ToolResults.unwrap(null, jsonMapper)).isEqualTo(StringNode.valueOf(""));
	}

}
