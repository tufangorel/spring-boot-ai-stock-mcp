package org.example.stock.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The chat model this agent process is configured to call. The label is written on every tool-call audit line.
 */
@Component
public class SelectedChatModel {

	private final String label;

	@Autowired
	public SelectedChatModel(@Value("${spring.ai.model.chat}") String provider,
			@Value("${spring.ai.openai.chat.options.model}") String openaiModel,
			@Value("${spring.ai.anthropic.chat.options.model}") String anthropicModel) {
		this("anthropic".equals(provider) ? "anthropic/" + anthropicModel : provider + "/" + openaiModel);
	}

	SelectedChatModel(String label) {
		this.label = label;
	}

	/** Provider and model name, for example {@code openai/gpt-4o-mini}. */
	public String label() {
		return label;
	}

}
