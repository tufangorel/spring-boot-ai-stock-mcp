package org.example.stock.agent;

import java.util.regex.Pattern;

/**
 * The agent's role is questions about Borsa Istanbul stocks. A question with no stock cue is outside that role, so
 * the chat model is not called.
 */
final class StockCue {

	private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
			| Pattern.UNICODE_CHARACTER_CLASS;

	/** Whole words, plus Turkish suffixes on {@code hisse} and {@code fiyat}, such as {@code hissesi}. */
	private static final Pattern WORDS = Pattern.compile(
			"\\b(?:hisse\\p{L}*|fiyat\\p{L}*|borsa|bist|tl|lot|hacim|sembol|ticker|stock|share|price|quote)\\b",
			CASE_INSENSITIVE);

	/** A ticker written in capitals, such as {@code ASELS}. */
	private static final Pattern TICKER = Pattern.compile("(?<![\\p{L}])\\p{Lu}{2,6}(?![\\p{L}])");

	private StockCue() {
	}

	static boolean inRole(String question) {
		return WORDS.matcher(question).find() || TICKER.matcher(question).find();
	}

}
