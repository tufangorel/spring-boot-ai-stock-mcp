package org.example.stock.agent;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stops a question from overriding the system prompt. Override phrases and the old question tags are rejected
 * before the model is called. An allowed question is encoded so a closing tag cannot appear as raw text.
 */
final class QuestionGuard {

	static final String REJECTION = "The question was rejected because it tries to change the agent's instructions.";

	private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final List<Pattern> OVERRIDES = List.of(
			Pattern.compile("</?question>", CASE_INSENSITIVE),
			Pattern.compile("ignore previous instructions", CASE_INSENSITIVE),
			Pattern.compile("ignore the rules", CASE_INSENSITIVE),
			Pattern.compile("ignore all instructions", CASE_INSENSITIVE),
			Pattern.compile("disregard the rules", CASE_INSENSITIVE),
			Pattern.compile("disregard the instructions", CASE_INSENSITIVE),
			Pattern.compile("you are now", CASE_INSENSITIVE),
			Pattern.compile("new instructions", CASE_INSENSITIVE),
			Pattern.compile("system prompt", CASE_INSENSITIVE),
			Pattern.compile("önceki talimatları", CASE_INSENSITIVE),
			Pattern.compile("kuralları yok say", CASE_INSENSITIVE),
			Pattern.compile("sistem talimat", CASE_INSENSITIVE));

	private QuestionGuard() {
	}

	static void reject(String question) {
		for (Pattern override : OVERRIDES) {
			if (override.matcher(question).find()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTION);
			}
		}
	}

	/** A JSON string with {@code <} escaped, so the question cannot close a tag in the user message. */
	static String encode(JsonMapper jsonMapper, String question) {
		return jsonMapper.writeValueAsString(question).replace("<", "\\u003c");
	}

}
