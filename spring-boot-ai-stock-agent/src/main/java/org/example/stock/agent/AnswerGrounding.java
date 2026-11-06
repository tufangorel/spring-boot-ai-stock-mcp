package org.example.stock.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Checks the model's answer against the tool results. A number written with a decimal or thousands separator, in
 * Turkish ({@code 1.234,56}) or English ({@code 1,234.56}) style, must match a number in a tool result or in the
 * question, after rounding to the digits shown. Plain integers are not checked, because times, dates, and the delay
 * are written that way.
 */
final class AnswerGrounding {

	private static final Pattern NUMBER = Pattern.compile("(?<![\\d.,])\\d[\\d.,]*\\d");

	private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:X\\d{3})+");

	private AnswerGrounding() {
	}

	static List<String> warnings(String question, String answer, List<ToolCallRecord> calls) {
		List<String> warnings = new ArrayList<>();
		if (calls.isEmpty()) {
			warnings.add("No tool was called, so the answer is not based on market data.");
		}
		for (ToolCallRecord call : calls) {
			if (call.error() != null) {
				warnings.add("The " + call.tool() + " tool failed: " + call.error());
			}
		}
		List<BigDecimal> known = new ArrayList<>();
		for (ToolCallRecord call : calls) {
			collect(call.result(), known);
		}
		Matcher questionNumbers = NUMBER.matcher(question == null ? "" : question);
		while (questionNumbers.find()) {
			known.addAll(readings(questionNumbers.group()));
		}
		Set<String> ungrounded = new LinkedHashSet<>();
		Matcher answerNumbers = NUMBER.matcher(answer == null ? "" : answer);
		while (answerNumbers.find()) {
			List<BigDecimal> readings = readings(answerNumbers.group());
			if (!readings.isEmpty() && readings.stream().noneMatch(reading -> matchesAny(reading, known))) {
				ungrounded.add(answerNumbers.group());
			}
		}
		for (String number : ungrounded) {
			warnings.add("The answer mentions " + number + ", which is not in any tool result.");
		}
		return warnings;
	}

	/**
	 * @return the values the text can stand for, in either style; empty when it is not a number in either style,
	 * like a date such as {@code 06.11.2026}
	 */
	static List<BigDecimal> readings(String text) {
		List<BigDecimal> readings = new ArrayList<>();
		read(text, '.', ',', readings);
		read(text, ',', '.', readings);
		return readings;
	}

	private static void read(String text, char decimal, char thousands, List<BigDecimal> readings) {
		int point = text.indexOf(decimal);
		if (point != text.lastIndexOf(decimal)) {
			return;
		}
		String whole = point < 0 ? text : text.substring(0, point);
		String fraction = point < 0 ? "" : text.substring(point + 1);
		if (fraction.indexOf(thousands) >= 0) {
			return;
		}
		if (whole.indexOf(thousands) >= 0) {
			if (!THOUSANDS.matcher(whole.replace(thousands, 'X')).matches()) {
				return;
			}
			whole = whole.replace(String.valueOf(thousands), "");
		}
		else if (point < 0) {
			return;
		}
		readings.add(new BigDecimal(fraction.isEmpty() ? whole : whole + "." + fraction));
	}

	private static boolean matchesAny(BigDecimal reading, List<BigDecimal> known) {
		BigDecimal shown = reading.abs();
		for (BigDecimal value : known) {
			if (value.abs().setScale(shown.scale(), RoundingMode.HALF_UP).compareTo(shown) == 0) {
				return true;
			}
		}
		return false;
	}

	private static void collect(JsonNode node, List<BigDecimal> numbers) {
		if (node == null) {
			return;
		}
		if (node.isNumber()) {
			numbers.add(node.decimalValue());
		}
		for (JsonNode child : node) {
			collect(child, numbers);
		}
	}

}
