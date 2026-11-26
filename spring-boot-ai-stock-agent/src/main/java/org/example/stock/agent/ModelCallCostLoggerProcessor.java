package org.example.stock.agent;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
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

	private final ObjectProvider<ObservationRegistry> observations;

	private final ObjectProvider<Tracer> tracer;

	/**
	 * The registry and tracer are resolved when a chat model is wrapped, not in this constructor. This processor is
	 * created before other beans, and asking for them here would build the tracer before its handlers and exporters
	 * exist.
	 */
	ModelCallCostLoggerProcessor(ObjectProvider<SelectedChatModel> selectedChatModel,
			ObjectProvider<ObservationRegistry> observations, ObjectProvider<Tracer> tracer) {
		this.selectedChatModel = selectedChatModel;
		this.observations = observations;
		this.tracer = tracer;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof OpenAiChatModel || bean instanceof AnthropicChatModel) {
			return new ModelCallCostLogger((ChatModel) bean, selectedChatModel.getObject(), observations.getObject(),
					tracer.getObject());
		}
		return bean;
	}

}
