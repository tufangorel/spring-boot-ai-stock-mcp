package org.example.stock.mcp.provider;

import java.time.OffsetDateTime;
import java.util.List;

import org.example.stock.mcp.Recorded;
import org.example.stock.mcp.StockData.Quote;
import org.example.stock.mcp.StockData.SymbolMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TradingViewClientTests {

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://scanner.example");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final TradingViewClient client = new TradingViewClient(builder.build());

	@Test
	void mapsTheScannerColumnsToQuotes() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"symbols":{"tickers":["BIST:ASELS","BIST:THYAO","BIST:NOPE1"],"query":{"types":[]}},
						 "columns":["name","description","close","change_abs","change","open","high","low","volume",
						            "currency","update_time","update_mode"]}""", true))
				.andRespond(withSuccess(Recorded.read("tradingview-quotes.json"), MediaType.APPLICATION_JSON));

		List<Quote> quotes = client.quotes(List.of("ASELS", "THYAO", "NOPE1"), true);

		assertThat(quotes).containsExactly(
				new Quote("ASELS", "Aselsan Elektronik Sanayi ve Ticaret A.S. Class B", 330.75, "TRY", 1.75, 0.53,
						329.5, 335.75, 325.25, 30437685L, OffsetDateTime.parse("2026-11-06T15:40:11+03:00"), 15,
						"TradingView", true),
				new Quote("THYAO", "Turk Hava Yollari A.O.", 298.25, "TRY", 1.25, 0.42, 290.0, 299.25, 288.0,
						30104760L, OffsetDateTime.parse("2026-11-06T15:39:49+03:00"), 15, "TradingView", true));
		server.verify();
	}

	@Test
	void skipsRowsWithoutAPriceAndToleratesMissingColumns() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andRespond(withSuccess("""
						{"totalCount":2,"data":[
						  {"s":"BIST:OLD","d":["OLD","Delisted",null]},
						  {"s":"BIST:NEW","d":["NEW","",12.5,null,null,null,null,null,"n/a"]}]}""",
						MediaType.APPLICATION_JSON));

		List<Quote> quotes = client.quotes(List.of("OLD", "NEW"), false);

		assertThat(quotes).containsExactly(new Quote("NEW", null, 12.5, null, null, null, null, null, null, null,
				null, null, "TradingView", false));
	}

	@Test
	void returnsNothingForAnEmptyResponse() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andRespond(withSuccess("{\"totalCount\":0,\"data\":[]}", MediaType.APPLICATION_JSON));

		assertThat(client.quotes(List.of("NOPE1"), true)).isEmpty();
	}

	@Test
	void searchesNameAndDescriptionLargestCompaniesFirst() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andExpect(content().json("""
						{"filter":[{"left":"name,description","operation":"match","right":"turk hava"}],
						 "columns":["name","description","exchange"],
						 "sort":{"sortBy":"market_cap_basic","sortOrder":"desc"},
						 "range":[0,5]}""", true))
				.andRespond(withSuccess(Recorded.read("tradingview-search.json"), MediaType.APPLICATION_JSON));

		assertThat(client.search("turk hava", 5))
				.containsExactly(new SymbolMatch("THYAO", "Turk Hava Yollari A.O.", "BIST"));
	}

	@Test
	void skipsSearchRowsWithoutATicker() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andRespond(withSuccess("{\"data\":[{\"s\":\"?\",\"d\":[null,\"x\",\"BIST\"]}]}",
						MediaType.APPLICATION_JSON));

		assertThat(client.search("x", 5)).isEmpty();
	}

	@Test
	void letsProviderFailuresThrough() {
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatExceptionOfType(HttpServerErrorException.class)
				.isThrownBy(() -> client.quotes(List.of("ASELS"), true));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
			"delayed_streaming_900, 15",
			"delayed_streaming_1200, 20",
			"streaming, 0",
			"endofday, null",
			"null, null" })
	void readsTheDelayFromTheUpdateMode(String updateMode, Integer minutes) {
		assertThat(TradingViewClient.delayMinutes(updateMode)).isEqualTo(minutes);
	}

}
