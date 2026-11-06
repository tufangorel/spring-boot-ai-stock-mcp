package org.example.stock.mcp.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.stock.mcp.MarketClock;
import org.example.stock.mcp.StockData.Quote;
import org.example.stock.mcp.StockData.SymbolMatch;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Reads the TradingView screener endpoint for the Turkish market. It is unofficial and undocumented, so every field
 * is read defensively.
 */
public class TradingViewClient {

	public static final String SOURCE = "TradingView";

	static final List<String> QUOTE_COLUMNS = List.of("name", "description", "close", "change_abs", "change", "open",
			"high", "low", "volume", "currency", "update_time", "update_mode");

	private static final Pattern DELAY_SECONDS = Pattern.compile("delayed_streaming_(\\d+)");

	private final RestClient restClient;

	public TradingViewClient(RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * @return quotes for the tickers TradingView knows; unknown tickers are simply absent
	 */
	public List<Quote> quotes(List<String> tickers, boolean marketOpen) {
		JsonNode response = scan(Map.of(
				"symbols", Map.of("tickers", tickers.stream().map(ticker -> "BIST:" + ticker).toList(),
						"query", Map.of("types", List.of())),
				"columns", QUOTE_COLUMNS));
		List<Quote> quotes = new ArrayList<>();
		for (JsonNode row : response.path("data")) {
			JsonNode d = row.path("d");
			Double price = Json.number(d.get(2));
			if (price == null) {
				continue;
			}
			Long updated = Json.whole(d.get(10));
			quotes.add(new Quote(Json.text(d.get(0)), Json.text(d.get(1)), price, Json.text(d.get(9)),
					Json.round(Json.number(d.get(3))), Json.round(Json.number(d.get(4))), Json.number(d.get(5)),
					Json.number(d.get(6)), Json.number(d.get(7)), Json.whole(d.get(8)),
					updated == null ? null : MarketClock.istanbulTime(updated), delayMinutes(Json.text(d.get(11))),
					SOURCE, marketOpen));
		}
		return quotes;
	}

	public List<SymbolMatch> search(String query, int limit) {
		JsonNode response = scan(Map.of(
				"filter", List.of(Map.of("left", "name,description", "operation", "match", "right", query)),
				"columns", List.of("name", "description", "exchange"),
				"sort", Map.of("sortBy", "market_cap_basic", "sortOrder", "desc"),
				"range", List.of(0, limit)));
		List<SymbolMatch> matches = new ArrayList<>();
		for (JsonNode row : response.path("data")) {
			JsonNode d = row.path("d");
			String symbol = Json.text(d.get(0));
			if (symbol != null) {
				matches.add(new SymbolMatch(symbol, Json.text(d.get(1)), Json.text(d.get(2))));
			}
		}
		return matches;
	}

	/**
	 * @return the delay in minutes, 0 for real-time data, or {@code null} when TradingView does not say
	 */
	static Integer delayMinutes(String updateMode) {
		if ("streaming".equals(updateMode)) {
			return 0;
		}
		Matcher matcher = DELAY_SECONDS.matcher(updateMode == null ? "" : updateMode);
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) / 60 : null;
	}

	private JsonNode scan(Map<String, Object> body) {
		return restClient.post().uri("/turkey/scan").body(body).retrieve().body(JsonNode.class);
	}

}
