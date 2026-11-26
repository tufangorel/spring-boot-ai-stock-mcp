package org.example.stock.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.context.TestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import({ AskTraceFilter.class, AskUserRateLimitFilter.class, AskUserLimiter.class, AskUserRateLimitTests.ClockConfig.class,
		TestAskTracing.class })
class AskUserRateLimitTests {

	private static final AskUserLimiterTests.MutableClock CLOCK = new AskUserLimiterTests.MutableClock(
			Instant.parse("2026-11-23T09:00:00Z"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StockAgent agent;

	@Test
	void missingUserIs400WithTraceHeadersAndDoesNotCallTheAgent() throws Exception {
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(AskUserLimiter.MISSING_USER))
				.andExpect(header().exists(AskTraceIds.TRACE_HEADER))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER));
		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, " ").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(AskUserLimiter.MISSING_USER));

		verifyNoInteractions(agent);
	}

	@Test
	void eleventhCallIs429WithTraceHeadersUntilTheMinuteElapses() throws Exception {
		given(agent.ask(anyString())).willReturn(new StockAnswer("330,75 TL", List.of(), List.of(), List.of(),
				List.of(), StockAnswer.DISCLAIMER));

		for (int call = 0; call < 10; call++) {
			mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "ada")
							.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"ASELS?\"}"))
					.andExpect(status().isOk());
		}
		MvcResult rejected = mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "ada")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(status().reason(AskUserLimiter.TOO_MANY))
				.andReturn();
		assertThat(rejected.getResponse().getHeader(AskTraceIds.TRACE_HEADER)).matches("[0-9a-f]{32}");
		assertThat(rejected.getResponse().getHeader(AskTraceIds.SPAN_HEADER)).matches("[0-9a-f]{16}");
		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "bea")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isOk());

		CLOCK.plus(Duration.ofMinutes(1));

		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "ada")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isOk());
		verify(agent, times(12)).ask("ASELS?");
	}

	@Test
	void aBlankQuestionStillTakesAToken() throws Exception {
		for (int call = 0; call < 10; call++) {
			mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "blank")
							.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
					.andExpect(status().isBadRequest())
					.andExpect(header().exists(AskTraceIds.TRACE_HEADER));
		}
		mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "blank")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(status().reason(AskUserLimiter.TOO_MANY))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER));
	}

	@Test
	void otherRequestsAreNotLimited() throws Exception {
		mockMvc.perform(get("/api/stocks/ask")).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(post("/api/stocks/other").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());

		verifyNoInteractions(agent);
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		Clock clock() {
			return CLOCK;
		}

		@Bean
		StockAgentProperties stockAgentProperties() {
			return new StockAgentProperties(500, 10);
		}

	}

}
