package org.example.stock.mcp.provider;

import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.example.stock.mcp.MutableClock;
import org.example.stock.mcp.provider.ProviderActivity.Snapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderActivityTests {

	private static final Instant START = Instant.parse("2026-11-06T12:55:00Z");

	private final MutableClock clock = new MutableClock(START);

	private final ProviderActivity activity = new ProviderActivity(clock, ProviderActivity.TRADING_VIEW,
			ProviderActivity.YAHOO);

	private final ClientHttpRequestInterceptor tradingView = activity.recording(ProviderActivity.TRADING_VIEW);

	private final ClientHttpRequestInterceptor yahoo = activity.recording(ProviderActivity.YAHOO, HttpStatus.NOT_FOUND);

	@Test
	void reportsEveryProviderBeforeItsFirstRequest() {
		assertThat(activity.snapshots()).containsExactly(
				Map.entry(ProviderActivity.TRADING_VIEW, new Snapshot(null, null, null, 0)),
				Map.entry(ProviderActivity.YAHOO, new Snapshot(null, null, null, 0)));
		assertThat(state(ProviderActivity.TRADING_VIEW).state()).isEqualTo("not called yet");
	}

	@Test
	void recordsASuccessfulRequestAndPassesTheResponseOn() throws IOException {
		MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);

		assertThat(tradingView.intercept(new MockClientHttpRequest(), new byte[0], (request, body) -> response))
				.isSameAs(response);

		assertThat(state(ProviderActivity.TRADING_VIEW)).isEqualTo(new Snapshot(START, null, null, 0));
		assertThat(state(ProviderActivity.TRADING_VIEW).state()).isEqualTo("ok");
		assertThat(state(ProviderActivity.YAHOO).state()).isEqualTo("not called yet");
	}

	@Test
	void recordsAnErrorStatusAsAFailureAndPassesTheResponseOn() throws IOException {
		MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE);

		assertThat(tradingView.intercept(new MockClientHttpRequest(), new byte[0], (request, body) -> response))
				.isSameAs(response);

		assertThat(state(ProviderActivity.TRADING_VIEW)).isEqualTo(new Snapshot(null, START, "HTTP 503", 1));
		assertThat(state(ProviderActivity.TRADING_VIEW).state()).isEqualTo("failing");
	}

	@Test
	void recordsARequestWithoutAResponseAndRethrowsTheSameException() {
		IOException timeout = new HttpConnectTimeoutException("HTTP connect timed out");
		IllegalStateException failure = new IllegalStateException("broken");

		assertThatThrownBy(() -> tradingView.intercept(new MockClientHttpRequest(), new byte[0], (request, body) -> {
			throw timeout;
		})).isSameAs(timeout);
		assertThatThrownBy(() -> yahoo.intercept(new MockClientHttpRequest(), new byte[0], (request, body) -> {
			throw failure;
		})).isSameAs(failure);

		assertThat(state(ProviderActivity.TRADING_VIEW))
				.isEqualTo(new Snapshot(null, START, "HttpConnectTimeoutException", 1));
		assertThat(state(ProviderActivity.YAHOO)).isEqualTo(new Snapshot(null, START, "IllegalStateException", 1));
	}

	@Test
	void countsFailuresInARowUntilARequestSucceeds() throws IOException {
		call(tradingView, HttpStatus.OK);
		clock.advance(Duration.ofMinutes(1));
		call(tradingView, HttpStatus.TOO_MANY_REQUESTS);
		clock.advance(Duration.ofMinutes(1));
		call(tradingView, HttpStatus.BAD_GATEWAY);

		assertThat(state(ProviderActivity.TRADING_VIEW))
				.isEqualTo(new Snapshot(START, START.plusSeconds(120), "HTTP 502", 2));
		assertThat(state(ProviderActivity.TRADING_VIEW).state()).isEqualTo("failing");

		clock.advance(Duration.ofMinutes(1));
		call(tradingView, HttpStatus.OK);

		assertThat(state(ProviderActivity.TRADING_VIEW))
				.isEqualTo(new Snapshot(START.plusSeconds(180), START.plusSeconds(120), "HTTP 502", 0));
		assertThat(state(ProviderActivity.TRADING_VIEW).state()).isEqualTo("ok");
	}

	@Test
	void takesAStatusTheProviderAnswersWithForASuccess() throws IOException {
		call(yahoo, HttpStatus.NOT_FOUND);
		call(tradingView, HttpStatus.NOT_FOUND);

		assertThat(state(ProviderActivity.YAHOO)).isEqualTo(new Snapshot(START, null, null, 0));
		assertThat(state(ProviderActivity.TRADING_VIEW)).isEqualTo(new Snapshot(null, START, "HTTP 404", 1));
	}

	@Test
	void addsAProviderThatWasNotAnnounced() throws IOException {
		call(activity.recording("other"), HttpStatus.OK);
		call(activity.recording("another"), HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(activity.snapshots().keySet()).containsExactly(ProviderActivity.TRADING_VIEW,
				ProviderActivity.YAHOO, "other", "another");
		assertThat(state("other").state()).isEqualTo("ok");
		assertThat(state("another").state()).isEqualTo("failing");
	}

	private Snapshot state(String provider) {
		return activity.snapshots().get(provider);
	}

	private static ClientHttpResponse call(ClientHttpRequestInterceptor interceptor, HttpStatus status)
			throws IOException {
		return interceptor.intercept(new MockClientHttpRequest(), new byte[0],
				(request, body) -> new MockClientHttpResponse(new byte[0], status));
	}

}
