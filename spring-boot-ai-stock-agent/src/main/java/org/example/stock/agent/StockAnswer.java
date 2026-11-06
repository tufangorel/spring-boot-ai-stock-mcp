package org.example.stock.agent;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/**
 * The answer and the data behind it. Everything except {@code answer} comes from the recorded tool results, not from
 * the model.
 */
public record StockAnswer(
		@Schema(description = "The model's answer in the language of the question") String answer,
		@Schema(description = "Every quote the tools returned, one per ticker") List<Quote> quotes,
		@Schema(description = "A summary of every price history the tools returned") List<HistorySummary> histories,
		@Schema(description = "The tool calls made, in order") List<ToolCall> toolCalls,
		@Schema(description = "Problems found in the answer or the tool results") List<String> warnings,
		String disclaimer) {

	public static final String DISCLAIMER = "Prices are delayed and come from unofficial sources. "
			+ "This is not investment advice.";

	/**
	 * @param asOf the Istanbul time the price is from, as the server reported it
	 */
	public record Quote(String symbol, String name, Double price, String currency, Double change, Double changePercent,
			String asOf, Integer delayMinutes, String source, Boolean marketOpen) {
	}

	/**
	 * @param from the time of the first candle
	 * @param to the time of the last candle
	 */
	public record HistorySummary(String symbol, String currency, String range, String interval, int candles,
			String from, String to, Double firstClose, Double lastClose, Double low, Double high, Integer delayMinutes,
			String source) {
	}

	public record ToolCall(String tool, JsonNode arguments, String error) {
	}

}
