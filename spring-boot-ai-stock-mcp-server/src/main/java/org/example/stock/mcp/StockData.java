package org.example.stock.mcp;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What the tools return. Every price carries its source, the time it is from, and its delay, so that a client never
 * has to present delayed data as live.
 */
public final class StockData {

	private StockData() {
	}

	public record SymbolMatch(String symbol, String name, String exchange) {
	}

	public record SymbolMatches(String query, List<SymbolMatch> matches) {
	}

	/**
	 * @param asOf when the price was current on the exchange, in Istanbul time
	 * @param marketOpen whether Borsa Istanbul was in its trading hours when the quote was fetched; when it is not,
	 * {@code price} is the last close
	 */
	public record Quote(String symbol, String name, Double price, String currency, Double change, Double changePercent,
			Double open, Double dayHigh, Double dayLow, Long volume, OffsetDateTime asOf, Integer delayMinutes,
			String source, boolean marketOpen) {
	}

	/**
	 * @param notFound requested tickers that no provider knows
	 */
	public record Quotes(List<Quote> quotes, List<String> notFound) {
	}

	public record Candle(OffsetDateTime time, Double open, Double high, Double low, Double close, Long volume) {
	}

	/**
	 * @param omittedCandles older candles left out to keep the result small
	 */
	public record History(String symbol, String currency, String range, String interval, List<Candle> candles,
			int omittedCandles, OffsetDateTime asOf, Integer delayMinutes, String source) {
	}

}
