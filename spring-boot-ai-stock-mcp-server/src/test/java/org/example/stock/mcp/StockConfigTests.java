package org.example.stock.mcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.example.stock.mcp.provider.ProviderActivity;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The provider clients as the server builds them: every request is measured by the {@code http.client.requests}
 * timer, recorded for the health endpoint, and given up on after the read timeout.
 */
class StockConfigTests {

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

	private final ObservationRegistry observations = ObservationRegistry.create();

	private final ProviderActivity activity = new ProviderActivity(Clock.systemUTC(), ProviderActivity.TRADING_VIEW,
			ProviderActivity.YAHOO);

	StockConfigTests() {
		observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
	}

	@Test
	void measuresAndRecordsEveryProviderRequest() {
		RestClient.Builder builder = StockConfig.restClientBuilder(StockProperties.defaults(),
				"https://scanner.example", observations, activity.recording(ProviderActivity.TRADING_VIEW));
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andExpect(header(HttpHeaders.USER_AGENT, StockProperties.defaults().userAgent()))
				.andRespond(withSuccess(Recorded.read("tradingview-search.json"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://scanner.example/turkey/scan"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		TradingViewClient client = new TradingViewClient(builder.build());

		assertThat(client.search("turk hava", 5)).hasSize(1);
		assertThatExceptionOfType(HttpServerErrorException.class).isThrownBy(() -> client.search("turk hava", 5));

		assertThat(providerRequests()).containsExactlyInAnyOrder(
				"client.name=scanner.example error=none exception=none method=POST outcome=SUCCESS status=200 "
						+ "uri=/turkey/scan count=1",
				"client.name=scanner.example error=ServiceUnavailable exception=ServiceUnavailable method=POST "
						+ "outcome=SERVER_ERROR status=503 uri=/turkey/scan count=1");
		assertThat(activity.snapshots().get(ProviderActivity.TRADING_VIEW).state()).isEqualTo("failing");
		assertThat(activity.snapshots().get(ProviderActivity.TRADING_VIEW).lastError()).isEqualTo("HTTP 503");
		assertThat(activity.snapshots().get(ProviderActivity.TRADING_VIEW).lastSuccess()).isNotNull();
	}

	@Test
	void givesUpOnAProviderThatDoesNotAnswer() throws IOException {
		StockProperties defaults = StockProperties.defaults();
		StockProperties properties = new StockProperties(defaults.cacheTtl(), defaults.connectTimeout(),
				Duration.ofMillis(300), defaults.tradingViewUrl(), defaults.yahooUrl(), defaults.userAgent(),
				defaults.yahooDelayMinutes(), defaults.maxSymbols(), defaults.maxMatches(), defaults.maxCandles(),
				defaults.recordToolPayloads(), defaults.maxPayloadCharacters());
		// Connections are accepted by the operating system, but nothing ever reads the request or answers it.
		try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			YahooClient client = new YahooClient(StockConfig.restClientBuilder(properties,
					"http://127.0.0.1:" + silent.getLocalPort(), observations,
					activity.recording(ProviderActivity.YAHOO, HttpStatus.NOT_FOUND)).build(), 15);
			long started = System.nanoTime();

			assertThatExceptionOfType(ResourceAccessException.class).isThrownBy(() -> client.quote("ASELS", true));

			assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(250),
					Duration.ofSeconds(3));
		}
		// Spring reports a request without a response as status CLIENT_ERROR, which is not an HTTP 4xx.
		assertThat(providerRequests()).containsExactly(
				"client.name=127.0.0.1 error=ResourceAccessException exception=ResourceAccessException method=GET "
						+ "outcome=UNKNOWN status=CLIENT_ERROR "
						+ "uri=/v8/finance/chart/{symbol}?range={range}&interval={interval} count=1");
		assertThat(activity.snapshots().get(ProviderActivity.YAHOO).state()).isEqualTo("failing");
		assertThat(activity.snapshots().get(ProviderActivity.YAHOO).lastError()).isEqualTo("HttpTimeoutException");
	}

	/** One line per combination of tags of the timer Spring records for the requests of a {@code RestClient}. */
	private List<String> providerRequests() {
		return meters.find("http.client.requests")
				.timers()
				.stream()
				.map(timer -> timer.getId()
						.getTags()
						.stream()
						.map(tag -> tag.getKey() + "=" + tag.getValue())
						.collect(Collectors.joining(" ")) + " count=" + timer.count())
				.toList();
	}

}
