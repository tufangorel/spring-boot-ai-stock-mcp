package org.example.stock.agent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ModelCallCostLoggerTests {

	private static final Prompt PROMPT = new Prompt("secret-question");

	private static final ChatOptions OPTIONS = ChatOptions.builder().model("gpt-6.1-sol").temperature(0.2).build();

	@Test
	void logsOnePricedLinePerCallForTheDefaultOpenAiModel(CapturedOutput output) {
		ChatResponse response = response(tool("findSymbol"), usage(840, 40));
		ModelCallCostLogger logger = logger("openai/gpt-6.1-sol", new FixedModel(response, null, OPTIONS));

		assertThat(logger.call(PROMPT)).isSameAs(response);
		assertThat(logger.call(PROMPT)).isSameAs(response);

		assertThat(output).contains(
				"Agent model call model=openai/gpt-6.1-sol tools=findSymbol inputTokens=840 outputTokens=40 estimatedCostUsd=0.002080");
		assertThat(output).doesNotContain("secret-question");
		assertThat(count(output, "Agent model call")).isEqualTo(2);
	}

	@Test
	void logsTheAnthropicListPrice(CapturedOutput output) {
		ModelCallCostLogger logger = logger("anthropic/claude-fable-5-2",
				new FixedModel(response(new AssistantMessage("done"), usage(900, 60)), null, OPTIONS));

		logger.call(PROMPT);

		assertThat(output).contains(
				"Agent model call model=anthropic/claude-fable-5-2 tools= inputTokens=900 outputTokens=60 estimatedCostUsd=0.009600");
	}

	@Test
	void pricesAModelIdThatHasNoProviderPrefix(CapturedOutput output) {
		ModelCallCostLogger logger = logger("gpt-6.1-sol",
				new FixedModel(response(new AssistantMessage("done"), usage(0, 0)), null, OPTIONS));

		logger.call(PROMPT);

		assertThat(output).contains(
				"Agent model call model=gpt-6.1-sol tools= inputTokens=0 outputTokens=0 estimatedCostUsd=0.000000");
	}

	@Test
	void leavesTheCostUnsetForAnUnknownModel(CapturedOutput output) {
		AssistantMessage tools = AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "findSymbol", "{}"),
						new AssistantMessage.ToolCall("2", "function", "getQuote", "{}")))
				.build();
		ModelCallCostLogger logger = logger("openai/gpt-4o-mini",
				new FixedModel(response(tools, usage(10, 2)), null, OPTIONS));

		logger.call(PROMPT);

		assertThat(output).contains(
				"Agent model call model=openai/gpt-4o-mini tools=findSymbol,getQuote inputTokens=10 outputTokens=2");
		assertThat(output).doesNotContain("estimatedCostUsd");
	}

	@Test
	void leavesTokensUnsetWhenTheResponseReportsNone(CapturedOutput output) {
		ChatResponse empty = new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
		ChatResponse explicitNull = new ChatResponse(List.of(new Generation(new AssistantMessage("done"))),
				ChatResponseMetadata.builder().usage(null).build());
		ChatResponse nullCounts = new ChatResponse(List.of(new Generation(new AssistantMessage("done"))),
				ChatResponseMetadata.builder().usage(usage(null, null)).build());
		FixedModel model = new FixedModel(empty, null, OPTIONS);
		ModelCallCostLogger logger = logger("openai/gpt-6.1-sol", model);

		logger.call(PROMPT);
		model.response = explicitNull;
		logger.call(PROMPT);
		model.response = nullCounts;
		logger.call(PROMPT);

		assertThat(count(output, "inputTokens= outputTokens=")).isEqualTo(3);
		assertThat(output).doesNotContain("estimatedCostUsd");
	}

	@Test
	void doesNotLogWhenTheCallFailsOrReturnsNothing(CapturedOutput output) {
		ModelCallCostLogger failing = logger("openai/gpt-6.1-sol",
				new FixedModel(null, new IllegalStateException("401 Unauthorized"), OPTIONS));
		ModelCallCostLogger empty = logger("openai/gpt-6.1-sol", new FixedModel(null, null, OPTIONS));

		assertThatThrownBy(() -> failing.call(PROMPT)).isInstanceOf(IllegalStateException.class)
				.hasMessage("401 Unauthorized");
		assertThat(empty.call(PROMPT)).isNull();

		assertThat(output).doesNotContain("Agent model call");
	}

	@Test
	void delegatesOptionsAndStreamingWithoutACostLine(CapturedOutput output) {
		ChatResponse response = response(new AssistantMessage("done"), usage(1, 1));
		FixedModel model = new FixedModel(response, null, OPTIONS);
		ModelCallCostLogger logger = logger("openai/gpt-6.1-sol", model);

		assertThat(logger.getOptions()).isSameAs(OPTIONS);
		assertThat(logger.getDefaultOptions()).isSameAs(OPTIONS);
		assertThat(logger.delegate()).isSameAs(model);
		assertThat(logger.stream(PROMPT).blockFirst()).isSameAs(response);

		assertThat(output).doesNotContain("Agent model call");
	}

	private static ModelCallCostLogger logger(String label, ChatModel delegate) {
		return new ModelCallCostLogger(delegate, new SelectedChatModel(label));
	}

	private static ChatResponse response(AssistantMessage message, Usage usage) {
		return new ChatResponse(List.of(new Generation(message)), ChatResponseMetadata.builder().usage(usage).build());
	}

	private static AssistantMessage tool(String name) {
		return AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", name, "{}")))
				.build();
	}

	private static Usage usage(Integer input, Integer output) {
		return new Usage() {
			@Override
			public Integer getPromptTokens() {
				return input;
			}

			@Override
			public Integer getCompletionTokens() {
				return output;
			}

			@Override
			public Object getNativeUsage() {
				return null;
			}
		};
	}

	private static int count(CharSequence output, String text) {
		String textOutput = output.toString();
		int count = 0;
		int from = 0;
		while ((from = textOutput.indexOf(text, from)) >= 0) {
			count++;
			from += text.length();
		}
		return count;
	}

	private static final class FixedModel implements ChatModel {

		private ChatResponse response;

		private final RuntimeException failure;

		private final ChatOptions options;

		private FixedModel(ChatResponse response, RuntimeException failure, ChatOptions options) {
			this.response = response;
			this.failure = failure;
			this.options = options;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			if (failure != null) {
				throw failure;
			}
			return response;
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			return Flux.just(response);
		}

		@Override
		public ChatOptions getOptions() {
			return options;
		}

	}

}
