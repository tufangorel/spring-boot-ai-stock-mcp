package org.example.stock.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

/**
 * A chat model that replays a script of replies. Like the real models, it runs the tools a reply asks for and sends
 * the results back before taking the next reply.
 */
class ScriptedChatModel implements ChatModel {

	private final Deque<AssistantMessage> replies = new ArrayDeque<>();

	private final List<Prompt> prompts = new ArrayList<>();

	private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

	private RuntimeException failure;

	private int calls;

	ScriptedChatModel script(AssistantMessage... script) {
		replies.clear();
		replies.addAll(List.of(script));
		prompts.clear();
		failure = null;
		return this;
	}

	ScriptedChatModel failWith(RuntimeException failure) {
		script();
		this.failure = failure;
		return this;
	}

	AssistantMessage callTool(String name, String arguments) {
		return AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("call-" + ++calls, "function", name, arguments)))
				.build();
	}

	static AssistantMessage answer(String text) {
		return new AssistantMessage(text);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt current = prompt;
		while (true) {
			prompts.add(current);
			if (failure != null) {
				throw failure;
			}
			AssistantMessage reply = replies.poll();
			if (reply == null) {
				throw new IllegalStateException("The script has no more replies");
			}
			ChatResponse response = new ChatResponse(List.of(new Generation(reply)));
			if (!reply.hasToolCalls()) {
				return response;
			}
			ToolExecutionResult result = toolCallingManager.executeToolCalls(current, response);
			current = new Prompt(result.conversationHistory(), current.getOptions());
		}
	}

	/** Tool calling options, so that ChatClient adds the request's tool callbacks to the prompt. */
	@Override
	public ChatOptions getOptions() {
		return ToolCallingChatOptions.builder().build();
	}

	List<Prompt> prompts() {
		return prompts;
	}

}
