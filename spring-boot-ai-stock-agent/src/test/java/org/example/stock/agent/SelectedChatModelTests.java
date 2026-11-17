package org.example.stock.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectedChatModelTests {

	@Test
	void usesTheOpenAiModelUnlessTheProviderIsAnthropic() {
		assertThat(new SelectedChatModel("openai", "gpt-4o-mini", "claude-haiku-4-5").label())
				.isEqualTo("openai/gpt-4o-mini");
		assertThat(new SelectedChatModel("anthropic", "gpt-4o-mini", "claude-haiku-4-5").label())
				.isEqualTo("anthropic/claude-haiku-4-5");
	}

}
