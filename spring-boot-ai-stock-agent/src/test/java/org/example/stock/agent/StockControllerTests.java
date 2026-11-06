package org.example.stock.agent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
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
				.andExpect(jsonPath("$.answer").value("330,75 TL"))
				.andExpect(jsonPath("$.disclaimer").value(StockAnswer.DISCLAIMER));
	}

	@Test
	void rejectsABlankQuestion() throws Exception {
		mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
				.andExpect(status().isBadRequest());
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
				.andExpect(status().isBadGateway());
	}

}
