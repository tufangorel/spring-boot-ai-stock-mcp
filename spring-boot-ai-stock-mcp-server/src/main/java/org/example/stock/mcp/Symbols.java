package org.example.stock.mcp;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes the ways a Borsa Istanbul ticker can be written: {@code asels}, {@code ASELS.IS}, {@code BIST:ASELS}. */
public final class Symbols {

	private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{2,12}");

	private Symbols() {
	}

	/**
	 * @throws IllegalArgumentException with a message the model can act on when the text is not a ticker
	 */
	public static String normalize(String symbol) {
		String ticker = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
		if (ticker.startsWith("BIST:")) {
			ticker = ticker.substring(5);
		}
		if (ticker.endsWith(".IS")) {
			ticker = ticker.substring(0, ticker.length() - 3);
		}
		if (!TICKER.matcher(ticker).matches()) {
			throw new IllegalArgumentException("\"" + symbol + "\" is not a Borsa Istanbul ticker such as ASELS. "
					+ "Use findSymbol to look up a company by name.");
		}
		return ticker;
	}

}
