package org.example.stock.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.stock.agent.StockAnswer.ToolCall;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asks the real chat models which MCP tool to call. The name ends in {@code Tests} so Surefire's default
 * include finds it. The {@code eval} tag keeps it out of {@code mvn verify}; run with {@code -Peval} when
 * {@code OPENAI_API_KEY} or {@code ANTHROPIC_API_KEY} is set. A missing key skips that provider.
 */
@Tag("eval")
class ToolChoiceEvalTests {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:55:00Z"), ZoneOffset.UTC);

	private static final Pattern MICROSOFT = Pattern.compile("microsoft|msft", Pattern.CASE_INSENSITIVE);

	@Nested
	@Tag("eval")
	@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
	class OpenAi {

		private final StockAgent agent = agent(OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder()
						.apiKey(System.getenv("OPENAI_API_KEY"))
						.model(model("OPENAI_MODEL", "gpt-4o-mini"))
						.temperature(0.2)
						.timeout(Duration.ofSeconds(60))
						.build())
				.build());

		@Test
		void looksUpACompanyNameBeforeQuotingIt() {
			companyName("OpenAI", agent);
		}

		@Test
		void quotesAKnownTicker() {
			knownTicker("OpenAI", agent);
		}

		@Test
		void quotesTwoTickers() {
			twoTickers("OpenAI", agent);
		}

		@Test
		void requestsAMonthOfHistory() {
			monthOfHistory("OpenAI", agent);
		}

		@Test
		void requestsFiveMinuteCandles() {
			fiveMinuteCandles("OpenAI", agent);
		}

		@Test
		void quotesATickerAskedForInEnglish() {
			englishPrice("OpenAI", agent);
		}

		@Test
		void doesNotSubstituteABistTickerForMicrosoft() {
			microsoft("OpenAI", agent);
		}

	}

	@Nested
	@Tag("eval")
	@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
	class Anthropic {

		private final StockAgent agent = agent(AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
						.apiKey(System.getenv("ANTHROPIC_API_KEY"))
						.model(model("ANTHROPIC_MODEL", "claude-haiku-4-5"))
						.temperature(0.2)
						.maxTokens(2048)
						.timeout(Duration.ofSeconds(60))
						.build())
				.build());

		@Test
		void looksUpACompanyNameBeforeQuotingIt() {
			companyName("Anthropic", agent);
		}

		@Test
		void quotesAKnownTicker() {
			knownTicker("Anthropic", agent);
		}

		@Test
		void quotesTwoTickers() {
			twoTickers("Anthropic", agent);
		}

		@Test
		void requestsAMonthOfHistory() {
			monthOfHistory("Anthropic", agent);
		}

		@Test
		void requestsFiveMinuteCandles() {
			fiveMinuteCandles("Anthropic", agent);
		}

		@Test
		void quotesATickerAskedForInEnglish() {
			englishPrice("Anthropic", agent);
		}

		@Test
		void doesNotSubstituteABistTickerForMicrosoft() {
			microsoft("Anthropic", agent);
		}

	}

	private static void companyName(String provider, StockAgent agent) {
		String question = "Aselsan hissesi şu an kaç TL?";
		StockAnswer answer = agent.ask(question);

		assertThat(answer.toolCalls()).as(detail(provider, question, answer)).isNotEmpty();
		assertThat(answer.toolCalls().getFirst().tool()).as(detail(provider, question, answer)).isEqualTo("findSymbol");
		Set<String> quoted = quotedSymbols(answer);
		if (!quoted.isEmpty()) {
			assertThat(quoted).as(detail(provider, question, answer)).containsExactly("ASELS");
		}
	}

	private static void knownTicker(String provider, StockAgent agent) {
		String question = "ASELS fiyatı ne?";
		StockAnswer answer = agent.ask(question);

		assertThat(quotedSymbols(answer)).as(detail(provider, question, answer)).containsExactly("ASELS");
	}

	private static void twoTickers(String provider, StockAgent agent) {
		String question = "ASELS ve THYAO fiyatı";
		StockAnswer answer = agent.ask(question);

		assertThat(quotedSymbols(answer)).as(detail(provider, question, answer))
				.containsExactlyInAnyOrder("ASELS", "THYAO");
	}

	private static void monthOfHistory(String provider, StockAgent agent) {
		String question = "THYAO son 1 ay";
		StockAnswer answer = agent.ask(question);

		assertThat(histories(answer)).as(detail(provider, question, answer)).anySatisfy(call -> {
			assertThat(ticker(text(call, "symbol"))).isEqualTo("THYAO");
			assertThat(text(call, "range")).isEqualTo("1mo");
		});
	}

	private static void fiveMinuteCandles(String provider, StockAgent agent) {
		String question = "ASELS bugün 5 dakikalık";
		StockAnswer answer = agent.ask(question);

		assertThat(histories(answer)).as(detail(provider, question, answer)).anySatisfy(call -> {
			assertThat(ticker(text(call, "symbol"))).isEqualTo("ASELS");
			assertThat(text(call, "range")).isEqualTo("1d");
			assertThat(text(call, "interval")).isEqualTo("5m");
		});
	}

	private static void englishPrice(String provider, StockAgent agent) {
		String question = "What is the latest price of THYAO?";
		StockAnswer answer = agent.ask(question);

		assertThat(quotedSymbols(answer)).as(detail(provider, question, answer)).containsExactly("THYAO");
	}

	private static void microsoft(String provider, StockAgent agent) {
		String question = "Microsoft fiyatı ne kadar?";
		StockAnswer answer = agent.ask(question);

		assertThat(answer.toolCalls()).as(detail(provider, question, answer)).isNotEmpty();
		assertThat(answer.toolCalls()).as(detail(provider, question, answer)).allSatisfy(call -> {
			assertThat(call.tool()).isIn("findSymbol", "getQuote");
			assertThat(refersToMicrosoft(call)).isTrue();
			assertThat(tickers(call)).doesNotContain("ASELS", "THYAO");
		});
	}

	private static StockAgent agent(ChatModel model) {
		return new StockAgent(ChatClient.builder(model), new EvalTools(), JsonMapper.builder().build(), CLOCK,
				new StockAgentProperties(500));
	}

	private static String model(String variable, String defaultModel) {
		String configured = System.getenv(variable);
		return configured == null || configured.isBlank() ? defaultModel : configured;
	}

	private static Set<String> quotedSymbols(StockAnswer answer) {
		Set<String> symbols = new LinkedHashSet<>();
		for (ToolCall call : answer.toolCalls()) {
			if ("getQuote".equals(call.tool())) {
				symbols.addAll(tickers(call));
			}
		}
		return symbols;
	}

	private static List<ToolCall> histories(StockAnswer answer) {
		return answer.toolCalls().stream().filter(call -> "getHistory".equals(call.tool())).toList();
	}

	private static List<String> tickers(ToolCall call) {
		List<String> tickers = new ArrayList<>();
		for (String raw : texts(call.arguments().path("symbols"))) {
			tickers.add(ticker(raw));
		}
		String symbol = text(call, "symbol");
		if (!symbol.isEmpty()) {
			tickers.add(ticker(symbol));
		}
		return tickers;
	}

	private static boolean refersToMicrosoft(ToolCall call) {
		if (MICROSOFT.matcher(text(call, "query")).find()) {
			return true;
		}
		return texts(call.arguments().path("symbols")).stream().anyMatch(symbol -> MICROSOFT.matcher(symbol).find());
	}

	private static List<String> texts(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			return node.valueStream().map(JsonNode::asString).toList();
		}
		if (node.isString() && !node.asString().isBlank()) {
			return List.of(node.asString());
		}
		return List.of();
	}

	private static String text(ToolCall call, String field) {
		JsonNode value = call.arguments().path(field);
		return value.isString() ? value.asString().trim() : "";
	}

	private static String ticker(String raw) {
		String ticker = raw.trim().toUpperCase(Locale.ROOT);
		if (ticker.startsWith("BIST:")) {
			ticker = ticker.substring(5);
		}
		if (ticker.endsWith(".IS")) {
			ticker = ticker.substring(0, ticker.length() - 3);
		}
		return ticker;
	}

	private static String detail(String provider, String question, StockAnswer answer) {
		return provider + "\nquestion: " + question + "\ncalls: " + answer.toolCalls() + "\nanswer: " + answer.answer();
	}

}
