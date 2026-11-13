package org.example.stock.mcp;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quote;
import org.example.stock.mcp.StockData.Quotes;
import org.example.stock.mcp.StockData.SymbolMatches;
import org.example.stock.mcp.provider.ProviderRetry;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Chooses the provider for each request so that the tools, and the model behind them, never have to. Quotes and
 * symbol search come from TradingView, with Yahoo as the quote fallback; history comes from Yahoo. Search, history
 * and the Yahoo quote fallback are tried once more after a quick temporary failure; TradingView quotes are not,
 * so that the Yahoo fallback is not delayed.
 */
@Service
public class QuoteService {

	private static final Log logger = LogFactory.getLog(QuoteService.class);

	/** The intervals Yahoo accepts for each range, the default first, restricted to keep results small. */
	static final Map<String, List<String>> INTERVALS = new LinkedHashMap<>();

	static {
		INTERVALS.put("1d", List.of("5m", "1m", "15m", "30m", "60m"));
		INTERVALS.put("5d", List.of("30m", "5m", "15m", "60m", "1d"));
		INTERVALS.put("1mo", List.of("1d", "30m", "60m", "1wk"));
		INTERVALS.put("3mo", List.of("1d", "1wk"));
		INTERVALS.put("6mo", List.of("1d", "1wk"));
		INTERVALS.put("1y", List.of("1wk", "1d", "1mo"));
		INTERVALS.put("5y", List.of("1mo", "1wk"));
	}

	private final TradingViewClient tradingView;

	private final YahooClient yahoo;

	private final ProviderRetry providerRetry;

	private final MarketClock marketClock;

	private final StockProperties properties;

	private final TtlCache<String, Quote> quotes;

	private final TtlCache<String, SymbolMatches> searches;

	private final TtlCache<String, History> histories;

	public QuoteService(TradingViewClient tradingView, YahooClient yahoo, ProviderRetry providerRetry,
			MarketClock marketClock, StockProperties properties, Clock clock) {
		this.tradingView = tradingView;
		this.yahoo = yahoo;
		this.providerRetry = providerRetry;
		this.marketClock = marketClock;
		this.properties = properties;
		this.quotes = new TtlCache<>(clock, properties.cacheTtl());
		this.searches = new TtlCache<>(clock, properties.cacheTtl());
		this.histories = new TtlCache<>(clock, properties.cacheTtl());
	}

	public SymbolMatches findSymbol(String query) {
		String text = query == null ? "" : query.trim();
		if (text.isEmpty() || text.length() > 100) {
			throw new IllegalArgumentException("Give a company name or ticker of 1 to 100 characters.");
		}
		String key = text.toLowerCase(Locale.ROOT);
		Optional<SymbolMatches> cached = searches.get(key);
		if (cached.isPresent()) {
			return cached.get();
		}
		SymbolMatches matches;
		try {
			matches = new SymbolMatches(text, providerRetry.execute("TradingView search",
					() -> tradingView.search(text, properties.maxMatches())));
		}
		catch (RestClientException ex) {
			throw unavailable("Symbol search is unavailable", ex);
		}
		searches.put(key, matches);
		return matches;
	}

	public Quotes quotes(List<String> symbols) {
		Set<String> tickers = new LinkedHashSet<>();
		for (String symbol : symbols == null ? List.<String>of() : symbols) {
			tickers.add(Symbols.normalize(symbol));
		}
		if (tickers.isEmpty() || tickers.size() > properties.maxSymbols()) {
			throw new IllegalArgumentException("Ask for 1 to " + properties.maxSymbols() + " tickers at a time.");
		}
		Map<String, Quote> found = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (String ticker : tickers) {
			quotes.get(ticker).ifPresentOrElse(quote -> found.put(ticker, quote), () -> missing.add(ticker));
		}
		if (!missing.isEmpty()) {
			fetch(missing).forEach((ticker, quote) -> {
				quotes.put(ticker, quote);
				found.put(ticker, quote);
			});
		}
		List<Quote> ordered = new ArrayList<>();
		List<String> notFound = new ArrayList<>();
		for (String ticker : tickers) {
			if (found.containsKey(ticker)) {
				ordered.add(found.get(ticker));
			}
			else {
				notFound.add(ticker);
			}
		}
		return new Quotes(ordered, notFound);
	}

	public History history(String symbol, String range, String interval) {
		String ticker = Symbols.normalize(symbol);
		List<String> allowed = INTERVALS.get(range);
		if (allowed == null) {
			throw new IllegalArgumentException(
					"Range \"" + range + "\" is not supported. Use one of " + String.join(", ", INTERVALS.keySet()) + ".");
		}
		String chosen = interval == null || interval.isBlank() ? allowed.getFirst() : interval.trim();
		if (!allowed.contains(chosen)) {
			throw new IllegalArgumentException("Interval \"" + chosen + "\" does not fit range " + range
					+ ". Use one of " + String.join(", ", allowed) + ".");
		}
		String key = ticker + "|" + range + "|" + chosen;
		Optional<History> cached = histories.get(key);
		if (cached.isPresent()) {
			return cached.get();
		}
		History history;
		try {
			history = providerRetry.execute("Yahoo history",
					() -> yahoo.history(ticker, range, chosen, properties.maxCandles()))
					.orElseThrow(() -> new IllegalArgumentException("No Borsa Istanbul stock has the ticker " + ticker
							+ ". Use findSymbol to look it up by company name."));
		}
		catch (RestClientException ex) {
			throw unavailable("Price history is unavailable", ex);
		}
		histories.put(key, history);
		return history;
	}

	private Map<String, Quote> fetch(List<String> tickers) {
		boolean marketOpen = marketClock.isOpen();
		Map<String, Quote> fetched = new LinkedHashMap<>();
		RestClientException tradingViewFailure = null;
		try {
			for (Quote quote : tradingView.quotes(tickers, marketOpen)) {
				fetched.put(quote.symbol(), quote);
			}
		}
		catch (RestClientException ex) {
			logger.warn("TradingView quote request failed, falling back to Yahoo: " + ex.getMessage());
			tradingViewFailure = ex;
		}
		for (String ticker : tickers) {
			if (fetched.containsKey(ticker)) {
				continue;
			}
			try {
				providerRetry.execute("Yahoo quote", () -> yahoo.quote(ticker, marketOpen))
						.ifPresent(quote -> fetched.put(ticker, quote));
			}
			catch (RestClientException ex) {
				if (tradingViewFailure != null) {
					throw unavailable("Quotes are unavailable", ex);
				}
				logger.warn("Yahoo quote request for " + ticker + " failed: " + ex.getMessage());
			}
		}
		return fetched;
	}

	private static IllegalStateException unavailable(String what, RestClientException ex) {
		return new IllegalStateException(what + " because the data provider did not respond. "
				+ "Try again in a minute.", ex);
	}

}
