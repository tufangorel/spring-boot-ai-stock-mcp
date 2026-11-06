package org.example.stock.mcp.provider;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.stock.mcp.MarketClock;
import org.example.stock.mcp.StockData.Candle;
import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quote;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Reads Yahoo Finance's chart endpoint, which is unofficial. Borsa Istanbul tickers carry the {@code .IS} suffix. */
public class YahooClient {

	public static final String SOURCE = "Yahoo Finance";

	private final RestClient restClient;

	private final int delayMinutes;

	public YahooClient(RestClient restClient, int delayMinutes) {
		this.restClient = restClient;
		this.delayMinutes = delayMinutes;
	}

	public Optional<Quote> quote(String ticker, boolean marketOpen) {
		return chart(ticker, "1d", "1d").map(result -> {
			JsonNode meta = result.path("meta");
			Double price = Json.number(meta.get("regularMarketPrice"));
			Double previousClose = Json.number(meta.has("chartPreviousClose") ? meta.get("chartPreviousClose")
					: meta.get("previousClose"));
			Double change = price == null || previousClose == null ? null : price - previousClose;
			Double changePercent = change == null || previousClose == 0 ? null : change / previousClose * 100;
			return new Quote(ticker, name(meta), price, Json.text(meta.get("currency")), Json.round(change),
					Json.round(changePercent), first(result.path("indicators").path("quote").path(0).path("open")),
					Json.number(meta.get("regularMarketDayHigh")), Json.number(meta.get("regularMarketDayLow")),
					Json.whole(meta.get("regularMarketVolume")), time(meta.get("regularMarketTime")), delayMinutes,
					SOURCE, marketOpen);
		}).filter(quote -> quote.price() != null);
	}

	public Optional<History> history(String ticker, String range, String interval, int maxCandles) {
		return chart(ticker, range, interval).map(result -> {
			JsonNode meta = result.path("meta");
			JsonNode timestamps = result.path("timestamp");
			JsonNode quote = result.path("indicators").path("quote").path(0);
			List<Candle> candles = new ArrayList<>();
			for (int i = 0; i < timestamps.size(); i++) {
				Double close = Json.number(quote.path("close").get(i));
				if (close != null) {
					candles.add(new Candle(MarketClock.istanbulTime(timestamps.get(i).asLong()),
							Json.number(quote.path("open").get(i)), Json.number(quote.path("high").get(i)),
							Json.number(quote.path("low").get(i)), close, Json.whole(quote.path("volume").get(i))));
				}
			}
			int omitted = Math.max(0, candles.size() - maxCandles);
			return new History(ticker, Json.text(meta.get("currency")), range, interval,
					List.copyOf(candles.subList(omitted, candles.size())), omitted, time(meta.get("regularMarketTime")),
					delayMinutes, SOURCE);
		});
	}

	private Optional<JsonNode> chart(String ticker, String range, String interval) {
		JsonNode response;
		try {
			response = restClient.get()
					.uri("/v8/finance/chart/{symbol}?range={range}&interval={interval}", ticker + ".IS", range, interval)
					.retrieve()
					.body(JsonNode.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
		JsonNode result = response == null ? null : response.path("chart").path("result").path(0);
		return result == null || !result.isObject() ? Optional.empty() : Optional.of(result);
	}

	private static String name(JsonNode meta) {
		String longName = Json.text(meta.get("longName"));
		return longName != null ? longName : Json.text(meta.get("shortName"));
	}

	private static Double first(JsonNode values) {
		for (JsonNode value : values) {
			if (value.isNumber()) {
				return value.asDouble();
			}
		}
		return null;
	}

	private static OffsetDateTime time(JsonNode epochSeconds) {
		Long seconds = Json.whole(epochSeconds);
		return seconds == null ? null : MarketClock.istanbulTime(seconds);
	}

}
