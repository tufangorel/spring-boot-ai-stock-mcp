package org.example.stock.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts a question only when it is written in English or Turkish and contains an English or Turkish word. A capital
 * ticker, a number, or a capitalized name does not count as that word. The model and the MCP server are not called
 * when this rejects the question.
 */
final class QuestionLanguage {

	static final String REJECTION = "The question must be in English or Turkish.";

	private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
			| Pattern.UNICODE_CHARACTER_CLASS;

	/** A letter that is neither English nor Turkish. */
	private static final Pattern FOREIGN_LETTER = Pattern.compile("[\\p{L}&&[^A-Za-zÇĞİÖŞÜçğıöşüı]]");

	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{Nd}]+");

	/**
	 * A whole token. {@code hisse} and {@code fiyat} include a suffix, such as {@code hissesi}.
	 */
	private static final Pattern WORD = Pattern.compile("hisse\\p{L}*|fiyat\\p{L}*|borsa|bist|tl|lot|hacim|sembol|"
			+ "ticker|stock|share|price|quote|what|is|the|of|current|şu|an|kaç|nedir|bugün|hava|nasıl|son|gün|ve",
			CASE_INSENSITIVE);

	private QuestionLanguage() {
	}

	static void reject(String question) {
		if (FOREIGN_LETTER.matcher(question).find() || !allowed(question)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTION);
		}
	}

	private static boolean allowed(String question) {
		boolean word = false;
		Matcher tokens = TOKEN.matcher(question);
		while (tokens.find()) {
			String token = tokens.group();
			if (WORD.matcher(token).matches()) {
				word = true;
			}
			else if (!digit(token) && !name(token) && !ticker(token)) {
				return false;
			}
		}
		return word;
	}

	private static boolean digit(String token) {
		for (int i = 0; i < token.length();) {
			int letter = token.codePointAt(i);
			if (Character.isDigit(letter)) {
				return true;
			}
			i += Character.charCount(letter);
		}
		return false;
	}

	private static boolean ticker(String token) {
		for (int i = 0; i < token.length();) {
			int letter = token.codePointAt(i);
			if (!Character.isUpperCase(letter)) {
				return false;
			}
			i += Character.charCount(letter);
		}
		return true;
	}

	private static boolean name(String token) {
		int first = token.codePointAt(0);
		if (!Character.isUpperCase(first)) {
			return false;
		}
		boolean lower = false;
		for (int i = Character.charCount(first); i < token.length();) {
			int letter = token.codePointAt(i);
			if (Character.isLowerCase(letter)) {
				lower = true;
			}
			i += Character.charCount(letter);
		}
		return lower;
	}

}
