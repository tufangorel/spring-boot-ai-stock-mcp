package org.example.stock.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the documentation samples in step with the code. */
class ReadmeTests {

	@Test
	void readmeShowsTheTestedSample() throws IOException {
		assertThat(read("../README.md"))
				.contains("```json\n" + StockAgentIntegrationTests.README_REQUEST + "```\n")
				.contains("```json\n" + StockAgentIntegrationTests.README_RESPONSE + "```\n")
				.contains("```json\n" + read("../.cursor/mcp.json") + "```\n");
	}

	private static String read(String file) throws IOException {
		return Files.readString(Path.of(file)).replace("\r\n", "\n");
	}

}
