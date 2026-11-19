package org.example.stock.agent;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Wraps the provider chat model so each of its calls is logged. A test double is left as it is.
 */
@Component
class ModelCallCostLoggerProcessor implements BeanPostProcessor {

	private final ObjectProvider<SelectedChatModel> selectedChatModel;

	ModelCallCostLoggerProcessor(ObjectProvider<SelectedChatModel> selectedChatModel) {
		this.selectedChatModel = selectedChatModel;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof OpenAiChatModel || bean instanceof AnthropicChatModel) {
			return new ModelCallCostLogger((ChatModel) bean, selectedChatModel.getObject());
		}
		return bean;
	}

}
