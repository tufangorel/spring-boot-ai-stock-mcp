package org.example.stock.agent;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole agent with a scripted model and fake tools in place of the LLM and the MCP server. */
@SpringBootTest(properties = { "spring.ai.openai.api-key=test-key", "spring.ai.mcp.client.enabled=false",
		"stocks.max-question-characters=40" })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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

		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "reader").contentType(MediaType.APPLICATION_JSON).content(README_REQUEST))
				.andExpect(status().isOk())
				.andExpect(content().json(README_RESPONSE, true));

		assertThat(model.prompts().getFirst().getUserMessage().getText())
				.startsWith("Current time in Istanbul: Friday 2026-11-06 15:55");
	}

	@Test
	void writesTheAskIdsOnTheModelAndToolLines(CapturedOutput output) throws Exception {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer(Samples.ANSWER));

		MvcResult result = mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "reader").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS fiyati\"}"))
				.andExpect(status().isOk())
				.andReturn();

		String traceId = result.getResponse().getHeader(AskTraceIds.TRACE_HEADER);
		String spanId = result.getResponse().getHeader(AskTraceIds.SPAN_HEADER);
		assertThat(output).contains("Agent model call")
				.contains("Agent MCP tool call getQuote")
				.contains("traceId=" + traceId)
				.doesNotContain("spanId=" + spanId);
	}

	@Test
	void returnsIdsForARejectedQuestionWithoutALogLine(CapturedOutput output) throws Exception {
		model.script();

		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "reader").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Bugün hava nasıl?\"}"))
				.andExpect(status().isOk())
				.andExpect(header().exists(AskTraceIds.TRACE_HEADER))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER));

		assertThat(output).doesNotContain("Agent model call").doesNotContain("Agent MCP tool call");
		assertThat(model.prompts()).isEmpty();
	}

	@Test
	void explainsATooLongQuestion() throws Exception {
		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "reader").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"" + "x".repeat(41) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason("The question has 41 characters; the limit is 40."));
	}

	@Test
	void explainsAModelFailure() throws Exception {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.failWith(new IllegalStateException("401 Unauthorized"));

		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "reader").contentType(MediaType.APPLICATION_JSON).content(README_REQUEST))
				.andExpect(status().isBadGateway())
				.andExpect(status().reason(containsString("The model call failed: 401 Unauthorized")));
	}

	@Test
	void publishesTheApiDescription() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(AskTraceIds.TRACE_HEADER))
				.andExpect(header().doesNotExist(AskTraceIds.SPAN_HEADER))
				.andExpect(jsonPath("$.info.title").value("Spring Boot AI Stock Agent"))
				.andExpect(jsonPath("$.paths['/api/stocks/ask'].post.summary").value("Ask a question about a stock"));
	}

	@TestConfiguration
	static class Fakes {

		@Bean
		ScriptedChatModel scriptedChatModel() {
			return new ScriptedChatModel();
		}

		@Bean
		@Primary
		ModelCallCostLogger loggingModel(ScriptedChatModel scripted, SelectedChatModel selected,
				ObservationRegistry observations, Tracer tracer) {
			return new ModelCallCostLogger(scripted, selected, observations, tracer);
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
