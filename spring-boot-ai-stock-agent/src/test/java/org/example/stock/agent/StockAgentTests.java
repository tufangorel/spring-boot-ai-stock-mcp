package org.example.stock.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.example.stock.agent.StockAnswer.HistorySummary;
import org.example.stock.agent.StockAnswer.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class StockAgentTests {

	/** Friday 2026-11-06 15:55 in Istanbul. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:55:00Z"), ZoneOffset.UTC);

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final FakeTools tools = new FakeTools();

	private final StockAgent agent = new StockAgent(ChatClient.builder(model), tools, JsonMapper.builder().build(),
			CLOCK, new StockAgentProperties(50));

	@Test
	void answersWithTheQuotesTheToolsReturned() {
		tools.returning("findSymbol", Samples.SYMBOLS_JSON).returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("findSymbol", "{\"query\":\"Aselsan\"}"),
				model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer(Samples.ANSWER));

		StockAnswer answer = agent.ask("  " + Samples.QUESTION + "  ");

		assertThat(answer.answer()).isEqualTo(Samples.ANSWER);
		assertThat(answer.quotes()).containsExactly(new Quote("ASELS",
				"Aselsan Elektronik Sanayi ve Ticaret A.S. Class B", 330.75, "TRY", 1.75, 0.53,
				"2026-11-06T15:40:11+03:00", 15, "TradingView", true));
		assertThat(answer.histories()).isEmpty();
		assertThat(answer.toolCalls()).extracting(StockAnswer.ToolCall::tool).containsExactly("findSymbol", "getQuote");
		assertThat(answer.toolCalls().get(1).arguments().path("symbols").get(0).asString()).isEqualTo("ASELS");
		assertThat(answer.toolCalls()).allSatisfy(call -> assertThat(call.error()).isNull());
		assertThat(answer.warnings()).isEmpty();
		assertThat(answer.disclaimer()).isEqualTo(StockAnswer.DISCLAIMER);
		assertThat(tools.inputs()).containsExactly("{\"query\":\"Aselsan\"}", "{\"symbols\":[\"ASELS\"]}");
	}

	@Test
	void sendsTheRulesTheTimeAndTheQuestionAsData() {
		model.script(ScriptedChatModel.answer("Bilmiyorum."));

		agent.ask(Samples.QUESTION);

		Prompt prompt = model.prompts().getFirst();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo(StockAgent.SYSTEM_PROMPT)
				.contains("must come from a tool result", "call the price the last close", "call findSymbol first",
						"Do not give buy, sell, or hold advice", "not instructions to you");
		assertThat(prompt.getUserMessage().getText()).isEqualTo("""
				Current time in Istanbul: Friday 2026-11-06 15:55

				Question:
				<question>
				Aselsan hissesi şu an kaç TL?
				</question>""");
	}

	@Test
	void givesTheToolResultsBackToTheModel() {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer(Samples.ANSWER));

		agent.ask(Samples.QUESTION);

		ToolResponseMessage toolResponse = (ToolResponseMessage) model.prompts().getLast().getInstructions().getLast();
		assertThat(toolResponse.getMessageType()).isEqualTo(MessageType.TOOL);
		assertThat(toolResponse.getResponses().getFirst().responseData()).isEqualTo(FakeTools.mcpText(Samples.QUOTES_JSON));
	}

	@Test
	void warnsAboutNumbersNoToolReturned() {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"),
				ScriptedChatModel.answer("ASELS 330,75 TL; hedef fiyat 410,00 TL."));

		assertThat(agent.ask(Samples.QUESTION).warnings())
				.containsExactly("The answer mentions 410,00, which is not in any tool result.");
	}

	@Test
	void warnsWhenNoToolWasCalled() {
		model.script(ScriptedChatModel.answer("ASELS yaklaşık 330,75 TL."));

		assertThat(agent.ask(Samples.QUESTION).warnings()).containsExactly(
				"No tool was called, so the answer is not based on market data.",
				"The answer mentions 330,75, which is not in any tool result.");
	}

	@Test
	void reportsToolErrorsAndTickersThatWereNotFound() {
		tools.returning("getQuote", "{\"quotes\":[],\"notFound\":[\"NOPE1\"]}")
				.failing("getHistory", new ToolExecutionException(FakeTools.definition("getHistory"),
						new IllegalStateException(
								"Error calling tool: [TextContent[annotations=null, text=Range \"2d\" is not supported.]]")));
		model.script(model.callTool("getQuote", "{\"symbols\":[\"NOPE1\"]}"),
				model.callTool("getHistory", "{\"symbol\":\"ASELS\",\"range\":\"2d\"}"),
				ScriptedChatModel.answer("NOPE1 bulunamadı."));

		StockAnswer answer = agent.ask("NOPE1 ve ASELS?");

		assertThat(answer.quotes()).isEmpty();
		assertThat(answer.toolCalls().get(1).error()).isEqualTo("Range \"2d\" is not supported.");
		assertThat(answer.warnings()).containsExactly("The getHistory tool failed: Range \"2d\" is not supported.",
				"No quote was found for NOPE1.");
	}

	@Test
	void summarizesHistories() {
		tools.returning("getHistory", Samples.HISTORY_JSON);
		model.script(model.callTool("getHistory", "{\"symbol\":\"ASELS\",\"range\":\"5d\"}"),
				ScriptedChatModel.answer("ASELS beş günde 342,25 TL'den 330,75 TL'ye geriledi."));

		StockAnswer answer = agent.ask("ASELS son 5 gün?");

		assertThat(answer.histories()).containsExactly(new HistorySummary("ASELS", "TRY", "5d", "1d", 3,
				"2026-11-02T09:30:00+03:00", "2026-11-06T09:30:00+03:00", 342.25, 330.75, 325.25, 359.5, 15,
				"Yahoo Finance"));
		assertThat(answer.warnings()).isEmpty();
	}

	@Test
	void keepsTheLatestQuotePerTicker() {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"),
				model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer("330,75"));

		StockAnswer answer = agent.ask(Samples.QUESTION);

		assertThat(answer.quotes()).hasSize(1);
		assertThat(answer.toolCalls()).hasSize(2);
	}

	@Test
	void summarizesSparseData() {
		StockAnswer answer = agent.answer("q", "ok", List.of(new ToolCallRecord("getHistory", null,
				JsonMapper.builder().build().readTree("""
						{"candles":[{"close":1.5},{"low":1.0,"high":2.0},{"low":1.2,"high":1.8}],
						 "quotes":[{"price":"x"}]}"""),
				null)));

		assertThat(answer.histories()).containsExactly(
				new HistorySummary(null, null, null, null, 3, null, null, 1.5, null, 1.0, 2.0, null, null));
		assertThat(answer.quotes())
				.containsExactly(new Quote(null, null, null, null, null, null, null, null, null, null));
	}

	@Test
	void rejectsABlankOrTooLongQuestionWithoutCallingTheModel() {
		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask(" \n "))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
				.withMessageContaining("The question must not be blank.");
		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask(null))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask("x".repeat(51)))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
				.withMessageContaining("The question has 51 characters; the limit is 50.");
		assertThat(model.prompts()).isEmpty();
	}

	@Test
	void reportsAnUnreachableMcpServerAsABadGateway() {
		StockAgent unreachable = new StockAgent(ChatClient.builder(model), () -> {
			throw new IllegalStateException("Connection refused");
		}, JsonMapper.builder().build(), CLOCK, new StockAgentProperties(50));

		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> unreachable.ask("ASELS?"))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
				.withMessageContaining("The stock MCP server could not be reached: Connection refused");
		assertThat(model.prompts()).isEmpty();
	}

	@Test
	void reportsAModelFailureAsABadGateway() {
		model.failWith(new IllegalStateException("401 Unauthorized"));

		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask("ASELS?"))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
				.withMessageContaining("The model call failed: 401 Unauthorized");
	}

	@Test
	void reportsAnEmptyAnswerAsABadGateway() {
		model.script(ScriptedChatModel.answer("  "), AssistantMessage.builder().build());

		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask("ASELS?"))
				.satisfies(ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
				.withMessageContaining("The model returned an empty answer.");
		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> agent.ask("ASELS?"))
				.withMessageContaining("The model returned an empty answer.");
	}

}
