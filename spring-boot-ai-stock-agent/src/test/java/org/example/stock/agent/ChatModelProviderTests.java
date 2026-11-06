package org.example.stock.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** The real configuration: the chosen model provider, and the MCP client, which connects on the first question. */
class ChatModelProviderTests {

	@Nested
	@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
	class DefaultProvider {

		@Autowired
		private ApplicationContext context;

		@Test
		void usesOpenAiAndTheStockMcpServer() {
			ChatModel chatModel = context.getBean(ChatModel.class);

			assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
			assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("gpt-4o-mini");
			assertThat(context.getBeansOfType(AnthropicChatModel.class)).isEmpty();
			assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();
			assertThat(context.getBean(ToolCallbackProvider.class)).isInstanceOf(SyncMcpToolCallbackProvider.class);
		}

	}

	@Nested
	@SpringBootTest(properties = {
			"AI_PROVIDER=anthropic",
			"ANTHROPIC_MODEL=claude-sonnet-4-5",
			"spring.ai.anthropic.api-key=test-key" })
	class AnthropicProvider {

		@Autowired
		private ChatModel chatModel;

		@Test
		void usesTheConfiguredAnthropicModel() {
			assertThat(chatModel).isInstanceOf(AnthropicChatModel.class);
			assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("claude-sonnet-4-5");
			assertThat(chatModel.getDefaultOptions().getTemperature()).isEqualTo(0.2);
			assertThat(chatModel.getDefaultOptions().getMaxTokens()).isEqualTo(2048);
		}

	}

}
