package org.example.stock.mcp.provider;

import java.time.OffsetDateTime;

import org.example.stock.mcp.Recorded;
import org.example.stock.mcp.StockData.Candle;
import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooClientTests {

	private static final String QUOTE_URL = "https://yahoo.example/v8/finance/chart/ASELS.IS?range=1d&interval=1d";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://yahoo.example");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final YahooClient client = new YahooClient(builder.build(), 15);

	@Test
	void mapsTheChartMetaToAQuote() {
		server.expect(requestTo(QUOTE_URL))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(Recorded.read("yahoo-quote.json"), MediaType.APPLICATION_JSON));

		assertThat(client.quote("ASELS", true)).contains(new Quote("ASELS",
				"ASELSAN Elektronik Sanayi ve Ticaret Anonim Sirketi", 331.0, "TRY", 2.0, 0.61, 329.5, 335.75, 325.25,
				30446698L, OffsetDateTime.parse("2026-11-06T15:31:47+03:00"), 15, "Yahoo Finance", true));
		server.verify();
	}

	@Test
	void usesTheShortNameAndPreviousCloseWhenTheOthersAreMissing() {
		server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess("""
				{"chart":{"result":[{"meta":{"shortName":"ASELSAN","regularMarketPrice":110.0,"previousClose":100.0},
				  "indicators":{"quote":[{"open":[null]}]}}]}}""", MediaType.APPLICATION_JSON));

		Quote quote = client.quote("ASELS", false).orElseThrow();

		assertThat(quote.name()).isEqualTo("ASELSAN");
		assertThat(quote.change()).isEqualTo(10.0);
		assertThat(quote.changePercent()).isEqualTo(10.0);
		assertThat(quote.open()).isNull();
		assertThat(quote.asOf()).isNull();
		assertThat(quote.marketOpen()).isFalse();
	}

	@Test
	void leavesTheChangeOutWithoutAPreviousCloseOrWhenItIsZero() {
		server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess("""
				{"chart":{"result":[{"meta":{"regularMarketPrice":110.0}}]}}""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess("""
				{"chart":{"result":[{"meta":{"regularMarketPrice":110.0,"chartPreviousClose":0}}]}}""",
				MediaType.APPLICATION_JSON));

		Quote withoutClose = client.quote("ASELS", true).orElseThrow();
		Quote zeroClose = client.quote("ASELS", true).orElseThrow();

		assertThat(withoutClose.name()).isNull();
		assertThat(withoutClose.change()).isNull();
		assertThat(withoutClose.changePercent()).isNull();
		assertThat(zeroClose.change()).isEqualTo(110.0);
		assertThat(zeroClose.changePercent()).isNull();
	}

	@Test
	void findsNoQuoteWithoutAPrice() {
		server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess("""
				{"chart":{"result":[{"meta":{"currency":"TRY"}}]}}""", MediaType.APPLICATION_JSON));

		assertThat(client.quote("ASELS", true)).isEmpty();
	}

	@Test
	void findsNothingForAnUnknownTicker() {
		server.expect(requestTo("https://yahoo.example/v8/finance/chart/NOPE1.IS?range=1d&interval=1d"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
					.body(Recorded.read("yahoo-not-found.json")));

		assertThat(client.quote("NOPE1", true)).isEmpty();
	}

	@Test
	void findsNothingInAResponseWithoutAResult() {
		server.expect(requestTo(QUOTE_URL))
				.andRespond(withSuccess("{\"chart\":{\"result\":null}}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(QUOTE_URL)).andRespond(withSuccess());

		assertThat(client.quote("ASELS", true)).isEmpty();
		assertThat(client.quote("ASELS", true)).isEmpty();
	}

	@Test
	void letsProviderFailuresThrough() {
		server.expect(requestTo(QUOTE_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertThatExceptionOfType(HttpServerErrorException.class).isThrownBy(() -> client.quote("ASELS", true));
	}

	@Test
	void mapsTheCandlesInIstanbulTime() {
		server.expect(requestTo("https://yahoo.example/v8/finance/chart/ASELS.IS?range=1d&interval=60m"))
				.andRespond(withSuccess(Recorded.read("yahoo-history.json"), MediaType.APPLICATION_JSON));

		History history = client.history("ASELS", "1d", "60m", 120).orElseThrow();

		assertThat(history.symbol()).isEqualTo("ASELS");
		assertThat(history.currency()).isEqualTo("TRY");
		assertThat(history.range()).isEqualTo("1d");
		assertThat(history.interval()).isEqualTo("60m");
		assertThat(history.candles()).hasSize(8);
		assertThat(history.candles().getFirst()).isEqualTo(new Candle(
				OffsetDateTime.parse("2026-11-06T09:30:00+03:00"), 329.5, 334.75, 329.5, 332.0, 6565999L));
		assertThat(history.omittedCandles()).isZero();
		assertThat(history.asOf()).isEqualTo(OffsetDateTime.parse("2026-11-06T15:31:47+03:00"));
		assertThat(history.delayMinutes()).isEqualTo(15);
		assertThat(history.source()).isEqualTo("Yahoo Finance");
	}

	@Test
	void skipsGapsAndKeepsOnlyTheNewestCandles() {
		server.expect(requestTo("https://yahoo.example/v8/finance/chart/ASELS.IS?range=5d&interval=1d"))
				.andRespond(withSuccess("""
						{"chart":{"result":[{"meta":{"currency":"TRY"},
						  "timestamp":[1793600000,1793700000,1793800000,1793900000],
						  "indicators":{"quote":[{"open":[1.0,2.0,3.0,4.0],"high":[1.5,null,3.5,4.5],
						    "low":[0.5,null,2.5,3.5],"close":[1.25,null,3.25,4.25],"volume":[10,null,30,40]}]}}]}}""",
						MediaType.APPLICATION_JSON));

		History history = client.history("ASELS", "5d", "1d", 2).orElseThrow();

		assertThat(history.candles()).extracting(Candle::close).containsExactly(3.25, 4.25);
		assertThat(history.omittedCandles()).isEqualTo(1);
		assertThat(history.asOf()).isNull();
	}

	@Test
	void findsNoHistoryForAnUnknownTicker() {
		server.expect(requestTo("https://yahoo.example/v8/finance/chart/NOPE1.IS?range=5d&interval=1d"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(client.history("NOPE1", "5d", "1d", 120)).isEmpty();
	}

}
