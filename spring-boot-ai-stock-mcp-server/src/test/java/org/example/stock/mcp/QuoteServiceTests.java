package org.example.stock.mcp;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quote;
import org.example.stock.mcp.StockData.Quotes;
import org.example.stock.mcp.StockData.SymbolMatch;
import org.example.stock.mcp.StockData.SymbolMatches;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class QuoteServiceTests {

	/** Friday 2026-11-06 14:00 in Istanbul, while the market is open. */
	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T11:00:00Z"));

	private final TradingViewClient tradingView = mock(TradingViewClient.class);

	private final YahooClient yahoo = mock(YahooClient.class);

	private final QuoteService service = new QuoteService(tradingView, yahoo, new MarketClock(clock),
			StockProperties.defaults(), clock);

	@Test
	void takesQuotesFromTradingViewInTheRequestedOrder() {
		given(tradingView.quotes(List.of("THYAO", "ASELS"), true))
				.willReturn(List.of(quote("ASELS", "TradingView"), quote("THYAO", "TradingView")));

		Quotes quotes = service.quotes(List.of("thyao", "BIST:ASELS", "ASELS.IS"));

		assertThat(quotes.quotes()).extracting(Quote::symbol).containsExactly("THYAO", "ASELS");
		assertThat(quotes.notFound()).isEmpty();
		verifyNoInteractions(yahoo);
	}

	@Test
	void asksYahooForTickersTradingViewDoesNotKnow() {
		given(tradingView.quotes(List.of("ASELS", "NEWCO", "NOPE1"), true)).willReturn(List.of(quote("ASELS", "TradingView")));
		given(yahoo.quote("NEWCO", true)).willReturn(Optional.of(quote("NEWCO", "Yahoo Finance")));
		given(yahoo.quote("NOPE1", true)).willReturn(Optional.empty());

		Quotes quotes = service.quotes(List.of("ASELS", "NEWCO", "NOPE1"));

		assertThat(quotes.quotes()).extracting(Quote::source).containsExactly("TradingView", "Yahoo Finance");
		assertThat(quotes.notFound()).containsExactly("NOPE1");
		verify(yahoo, never()).quote("ASELS", true);
	}

	@Test
	void fallsBackToYahooWhenTradingViewFails() {
		given(tradingView.quotes(anyList(), anyBoolean())).willThrow(new ResourceAccessException("timed out"));
		given(yahoo.quote("ASELS", true)).willReturn(Optional.of(quote("ASELS", "Yahoo Finance")));

		assertThat(service.quotes(List.of("ASELS")).quotes()).extracting(Quote::source)
				.containsExactly("Yahoo Finance");
	}

	@Test
	void reportsATickerAsNotFoundWhenOnlyYahooFails() {
		given(tradingView.quotes(anyList(), anyBoolean())).willReturn(List.of());
		given(yahoo.quote("NOPE1", true)).willThrow(new ResourceAccessException("timed out"));

		assertThat(service.quotes(List.of("NOPE1")).notFound()).containsExactly("NOPE1");
	}

	@Test
	void failsWhenBothProvidersFail() {
		given(tradingView.quotes(anyList(), anyBoolean())).willThrow(new ResourceAccessException("timed out"));
		given(yahoo.quote("ASELS", true)).willThrow(new ResourceAccessException("timed out"));

		assertThatIllegalStateException().isThrownBy(() -> service.quotes(List.of("ASELS")))
				.withMessage("Quotes are unavailable because the data provider did not respond. Try again in a minute.");
	}

	@Test
	void tellsTheProvidersWhetherTheMarketIsOpen() {
		clock.advance(Duration.ofHours(5));
		given(tradingView.quotes(List.of("ASELS"), false)).willReturn(List.of(quote("ASELS", "TradingView")));

		assertThat(service.quotes(List.of("ASELS")).quotes()).hasSize(1);
		verify(tradingView).quotes(List.of("ASELS"), false);
	}

	@Test
	void reusesQuotesUntilTheyExpire() {
		given(tradingView.quotes(anyList(), anyBoolean())).willReturn(List.of(quote("ASELS", "TradingView")));

		service.quotes(List.of("ASELS"));
		clock.advance(Duration.ofSeconds(59));
		service.quotes(List.of("asels"));
		verify(tradingView, times(1)).quotes(anyList(), anyBoolean());

		clock.advance(Duration.ofSeconds(1));
		service.quotes(List.of("ASELS"));
		verify(tradingView, times(2)).quotes(anyList(), anyBoolean());
	}

	@Test
	void fetchesOnlyTheTickersThatAreNotCached() {
		given(tradingView.quotes(List.of("ASELS"), true)).willReturn(List.of(quote("ASELS", "TradingView")));
		given(tradingView.quotes(List.of("THYAO"), true)).willReturn(List.of(quote("THYAO", "TradingView")));

		service.quotes(List.of("ASELS"));
		Quotes quotes = service.quotes(List.of("ASELS", "THYAO"));

		assertThat(quotes.quotes()).extracting(Quote::symbol).containsExactly("ASELS", "THYAO");
		verify(tradingView).quotes(List.of("THYAO"), true);
	}

	@Test
	void rejectsTooFewOrTooManyTickers() {
		List<String> eleven = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			eleven.add("AB" + i);
		}

		assertThatIllegalArgumentException().isThrownBy(() -> service.quotes(List.of()))
				.withMessage("Ask for 1 to 10 tickers at a time.");
		assertThatIllegalArgumentException().isThrownBy(() -> service.quotes(null));
		assertThatIllegalArgumentException().isThrownBy(() -> service.quotes(eleven));
		assertThatIllegalArgumentException().isThrownBy(() -> service.quotes(List.of("not a ticker")))
				.withMessageContaining("findSymbol");
		verifyNoInteractions(tradingView, yahoo);
	}

	@Test
	void findsSymbolsAndCachesTheSearch() {
		given(tradingView.search("Aselsan", 5)).willReturn(List.of(new SymbolMatch("ASELS", "Aselsan", "BIST")));

		SymbolMatches matches = service.findSymbol(" Aselsan ");
		service.findSymbol("aselsan");

		assertThat(matches).isEqualTo(new SymbolMatches("Aselsan", List.of(new SymbolMatch("ASELS", "Aselsan", "BIST"))));
		verify(tradingView, times(1)).search(anyString(), anyInt());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void rejectsAnEmptySearch(String query) {
		assertThatIllegalArgumentException().isThrownBy(() -> service.findSymbol(query))
				.withMessage("Give a company name or ticker of 1 to 100 characters.");
	}

	@Test
	void rejectsALongSearch() {
		assertThatIllegalArgumentException().isThrownBy(() -> service.findSymbol("a".repeat(101)));
	}

	@Test
	void reportsSearchFailures() {
		given(tradingView.search("aselsan", 5)).willThrow(new ResourceAccessException("timed out"));

		assertThatIllegalStateException().isThrownBy(() -> service.findSymbol("aselsan"))
				.withMessageStartingWith("Symbol search is unavailable");
	}

	@ParameterizedTest(name = "{0} defaults to {1}")
	@CsvSource({ "1d, 5m", "5d, 30m", "1mo, 1d", "3mo, 1d", "6mo, 1d", "1y, 1wk", "5y, 1mo" })
	void choosesTheDefaultIntervalOfTheRange(String range, String interval) {
		History history = history(range, interval);
		given(yahoo.history("ASELS", range, interval, 120)).willReturn(Optional.of(history));

		assertThat(service.history("asels", range, null)).isSameAs(history);
		assertThat(service.history("ASELS", range, " ")).isSameAs(history);
		verify(yahoo, times(1)).history("ASELS", range, interval, 120);
	}

	@Test
	void acceptsAnIntervalThatFitsTheRange() {
		History history = history("1y", "1d");
		given(yahoo.history("ASELS", "1y", "1d", 120)).willReturn(Optional.of(history));

		assertThat(service.history("ASELS", "1y", " 1d ")).isSameAs(history);
	}

	@Test
	void rejectsUnsupportedRangesAndIntervals() {
		assertThatIllegalArgumentException().isThrownBy(() -> service.history("ASELS", "2d", null))
				.withMessage("Range \"2d\" is not supported. Use one of 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y.");
		assertThatIllegalArgumentException().isThrownBy(() -> service.history("ASELS", "5y", "1m"))
				.withMessage("Interval \"1m\" does not fit range 5y. Use one of 1mo, 1wk.");
		verifyNoInteractions(yahoo);
	}

	@Test
	void reportsAnUnknownTickerAndProviderFailuresForHistory() {
		given(yahoo.history("NOPE1", "1d", "5m", 120)).willReturn(Optional.empty());
		given(yahoo.history("ASELS", "1d", "5m", 120)).willThrow(new ResourceAccessException("timed out"));

		assertThatIllegalArgumentException().isThrownBy(() -> service.history("NOPE1", "1d", null))
				.withMessageContaining("No Borsa Istanbul stock has the ticker NOPE1");
		assertThatIllegalStateException().isThrownBy(() -> service.history("ASELS", "1d", null))
				.withMessageStartingWith("Price history is unavailable");
	}

	private static Quote quote(String symbol, String source) {
		return new Quote(symbol, symbol + " A.S.", 100.0, "TRY", 1.0, 1.01, 99.0, 101.0, 98.0, 1000L,
				OffsetDateTime.parse("2026-11-06T13:45:00+03:00"), 15, source, true);
	}

	private static History history(String range, String interval) {
		return new History("ASELS", "TRY", range, interval, List.of(), 0, null, 15, "Yahoo Finance");
	}

}
