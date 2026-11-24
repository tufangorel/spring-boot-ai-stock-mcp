package org.example.stock.agent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(AskTraceFilter.class)
class StockControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StockAgent agent;

	@Test
	void returnsTheAnswer() throws Exception {
		given(agent.ask("ASELS?")).willReturn(new StockAnswer("330,75 TL", List.of(), List.of(), List.of(), List.of(),
				StockAnswer.DISCLAIMER));

		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isOk())
				.andExpect(header().exists(AskTraceIds.TRACE_HEADER))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER))
				.andExpect(header().doesNotExist("X-Request-Id"))
				.andExpect(jsonPath("$.answer").value("330,75 TL"))
				.andExpect(jsonPath("$.disclaimer").value(StockAnswer.DISCLAIMER));
	}

	@Test
	void mintsANewTraceIdForEachCallAndIgnoresTheClientsIds() throws Exception {
		given(agent.ask("ASELS?")).willReturn(new StockAnswer("330,75 TL", List.of(), List.of(), List.of(), List.of(),
				StockAnswer.DISCLAIMER));

		MvcResult first = mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.header(AskTraceIds.TRACE_HEADER, "client-trace")
						.header(AskTraceIds.SPAN_HEADER, "client-span")
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isOk())
				.andReturn();
		MvcResult second = mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isOk())
				.andReturn();

		String firstTrace = first.getResponse().getHeader(AskTraceIds.TRACE_HEADER);
		String secondTrace = second.getResponse().getHeader(AskTraceIds.TRACE_HEADER);
		assertThat(firstTrace).matches("[0-9a-f]{32}").isNotEqualTo("client-trace");
		assertThat(first.getResponse().getHeader(AskTraceIds.SPAN_HEADER)).matches("[0-9a-f]{16}")
				.isNotEqualTo("client-span");
		assertThat(secondTrace).matches("[0-9a-f]{32}").isNotEqualTo(firstTrace);
		assertThat(first.getResponse().getHeader("X-Request-Id")).isNull();
	}

	@Test
	void rejectsABlankQuestion() throws Exception {
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(header().exists(AskTraceIds.TRACE_HEADER))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER));
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(agent);
	}

	@Test
	void passesTheAgentsStatusThrough() throws Exception {
		given(agent.ask("long")).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "too long"));
		given(agent.ask("down")).willThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "unreachable"));

		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"long\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"down\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(header().exists(AskTraceIds.TRACE_HEADER))
				.andExpect(header().exists(AskTraceIds.SPAN_HEADER));
	}

}
