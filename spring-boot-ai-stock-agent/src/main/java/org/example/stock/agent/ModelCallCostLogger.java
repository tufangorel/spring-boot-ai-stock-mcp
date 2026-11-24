package org.example.stock.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Writes one INFO line for each call to the chat model, with the configured model, the tools that response requested,
 * the token counts, and an estimated cost when the model id has a known list price.
 */
class ModelCallCostLogger implements ChatModel {

	private static final Log logger = LogFactory.getLog(ModelCallCostLogger.class);

	private static final BigDecimal MILLION = new BigDecimal("1000000");

	private static final Map<String, Rate> RATES = Map.of(
			"gpt-6.1-sol", new Rate(new BigDecimal("2"), new BigDecimal("10")),
			"claude-fable-5-2", new Rate(new BigDecimal("8"), new BigDecimal("40")));

	private final ChatModel delegate;

	private final SelectedChatModel selectedChatModel;

	ModelCallCostLogger(ChatModel delegate, SelectedChatModel selectedChatModel) {
		this.delegate = delegate;
		this.selectedChatModel = selectedChatModel;
	}

	ChatModel delegate() {
		return delegate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ChatResponse response = delegate.call(prompt);
		if (response != null) {
			logger.info(line(response));
		}
		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return delegate.stream(prompt);
	}

	@Override
	public ChatOptions getOptions() {
		return delegate.getOptions();
	}

	@Override
	@Deprecated
	public ChatOptions getDefaultOptions() {
		return delegate.getDefaultOptions();
	}

	private String line(ChatResponse response) {
		Usage usage = response.getMetadata().getUsage();
		Integer input = tokens(usage, true);
		Integer output = tokens(usage, false);
		StringBuilder line = new StringBuilder("Agent model call model=").append(selectedChatModel.label())
				.append(" tools=").append(tools(response))
				.append(" inputTokens=").append(input == null ? "" : input)
				.append(" outputTokens=").append(output == null ? "" : output);
		if (input != null && output != null) {
			BigDecimal cost = cost(input, output);
			if (cost != null) {
				line.append(" estimatedCostUsd=").append(cost.toPlainString());
			}
		}
		AskTraceIds.append(line);
		return line.toString();
	}

	/** Prompt and completion tokens. {@link EmptyUsage} means the response did not report any. */
	private static Integer tokens(Usage usage, boolean input) {
		if (usage == null || usage instanceof EmptyUsage) {
			return null;
		}
		return input ? usage.getPromptTokens() : usage.getCompletionTokens();
	}

	private static String tools(ChatResponse response) {
		List<String> names = new ArrayList<>();
		for (Generation generation : response.getResults()) {
			AssistantMessage output = generation.getOutput();
			if (output != null && output.hasToolCalls()) {
				for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
					names.add(toolCall.name());
				}
			}
		}
		return String.join(",", names);
	}

	/** List price for the configured model id, or null when that id has no rate. */
	private BigDecimal cost(int input, int output) {
		Rate rate = RATES.get(modelId(selectedChatModel.label()));
		if (rate == null) {
			return null;
		}
		return rate.inputPerMillion().multiply(BigDecimal.valueOf(input))
				.add(rate.outputPerMillion().multiply(BigDecimal.valueOf(output)))
				.divide(MILLION, 6, RoundingMode.HALF_UP);
	}

	/** The model id in {@code openai/gpt-6.1-sol}, or the whole label when it has no slash. */
	private static String modelId(String label) {
		int slash = label.lastIndexOf('/');
		return slash < 0 ? label : label.substring(slash + 1);
	}

	private record Rate(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
	}

}
