package org.example.stock.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole agent with a scripted model and fake tools in place of the LLM and the MCP server. */
@SpringBootTest(properties = { "spring.ai.openai.api-key=test-key", "spring.ai.mcp.client.enabled=false",
		"stocks.max-question-characters=40" })
@AutoConfigureMockMvc
class StockAgentIntegrationTests {

	static final String README_REQUEST = """
			{ "question": "Aselsan hissesi şu an kaç TL?" }
			""";

	static final String README_RESPONSE = """
			{
			  "answer": "ASELSAN (ASELS) 15:40 itibarıyla 330,75 TL, günlük değişim %0,53. Veri TradingView'den ve yaklaşık 15 dakika gecikmeli.",
			  "quotes": [
			    {
			      "symbol": "ASELS",
			      "name": "Aselsan Elektronik Sanayi ve Ticaret A.S. Class B",
			      "price": 330.75,
			      "currency": "TRY",
			      "change": 1.75,
			      "changePercent": 0.53,
			      "asOf": "2026-11-06T15:40:11+03:00",
			      "delayMinutes": 15,
			      "source": "TradingView",
			      "marketOpen": true
			    }
			  ],
			  "histories": [],
			  "toolCalls": [
			    { "tool": "findSymbol", "arguments": { "query": "Aselsan" }, "error": null },
			    { "tool": "getQuote", "arguments": { "symbols": ["ASELS"] }, "error": null }
			  ],
			  "warnings": [],
			  "disclaimer": "Prices are delayed and come from unofficial sources. This is not investment advice."
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ScriptedChatModel model;

	@Autowired
	private FakeTools tools;

	@BeforeEach
	void reset() {
		tools.reset();
	}

	@Test
	void answersTheReadmeSample() throws Exception {
		tools.returning("findSymbol", Samples.SYMBOLS_JSON).returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("findSymbol", "{\"query\":\"Aselsan\"}"),
				model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer(Samples.ANSWER));

		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content(README_REQUEST))
				.andExpect(status().isOk())
				.andExpect(content().json(README_RESPONSE, true));

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.startsWith("Current time in Istanbul: Friday 2026-11-06 15:55");
	}

	@Test
	void explainsATooLongQuestion() throws Exception {
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"" + "x".repeat(41) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason("The question has 41 characters; the limit is 40."));
	}

	@Test
	void explainsAModelFailure() throws Exception {
		model.failWith(new IllegalStateException("401 Unauthorized"));

		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content(README_REQUEST))
				.andExpect(status().isBadGateway())
				.andExpect(status().reason(containsString("The model call failed: 401 Unauthorized")));
	}

	@Test
	void publishesTheApiDescription() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Spring Boot AI Stock Agent"))
				.andExpect(jsonPath("$.paths['/api/stocks/ask'].post.summary").value("Ask a question about a stock"));
	}

	@TestConfiguration
	static class Fakes {

		@Bean
		@Primary
		ScriptedChatModel scriptedChatModel() {
			return new ScriptedChatModel();
		}

		@Bean
		@Primary
		FakeTools fakeTools() {
			return new FakeTools();
		}

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-06T12:55:00Z"), ZoneOffset.UTC);
		}

	}

}
