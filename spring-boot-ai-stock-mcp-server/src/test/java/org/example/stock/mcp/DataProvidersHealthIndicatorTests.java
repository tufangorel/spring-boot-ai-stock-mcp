package org.example.stock.mcp;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.example.stock.mcp.provider.ProviderActivity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DataProvidersHealthIndicatorTests {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T12:55:00.750Z"));

	private final ProviderActivity activity = new ProviderActivity(clock, ProviderActivity.TRADING_VIEW,
			ProviderActivity.YAHOO);

	private final DataProvidersHealthIndicator indicator = new DataProvidersHealthIndicator(activity);

	@Test
	void showsProvidersThatWereNotCalledYet() {
		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsExactly(
				Map.entry("tradingView", Map.of("state", "not called yet", "consecutiveFailures", 0)),
				Map.entry("yahoo", Map.of("state", "not called yet", "consecutiveFailures", 0)));
	}

	@Test
	void staysUpAndShowsTheLastRequestsInIstanbulTimeWhenAProviderFails() throws IOException {
		call(activity.recording(ProviderActivity.TRADING_VIEW), HttpStatus.OK);
		clock.advance(Duration.ofSeconds(5));
		call(activity.recording(ProviderActivity.YAHOO), HttpStatus.SERVICE_UNAVAILABLE);
		call(activity.recording(ProviderActivity.YAHOO), HttpStatus.SERVICE_UNAVAILABLE);

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsExactly(
				Map.entry("tradingView",
						Map.of("state", "ok", "lastSuccess", "2026-11-06T15:55:00+03:00", "consecutiveFailures", 0)),
				Map.entry("yahoo", Map.of("state", "failing", "lastFailure", "2026-11-06T15:55:05+03:00",
						"lastError", "HTTP 503", "consecutiveFailures", 2)));
	}

	@Test
	void keepsTheLastFailureOfAProviderThatAnswersAgain() throws IOException {
		call(activity.recording(ProviderActivity.YAHOO), HttpStatus.BAD_GATEWAY);
		clock.advance(Duration.ofMinutes(2));
		call(activity.recording(ProviderActivity.YAHOO), HttpStatus.OK);

		assertThat(indicator.health().getDetails().get("yahoo")).isEqualTo(Map.of("state", "ok", "lastSuccess",
				"2026-11-06T15:57:00+03:00", "lastFailure", "2026-11-06T15:55:00+03:00", "lastError", "HTTP 502",
				"consecutiveFailures", 0));
	}

	private static void call(ClientHttpRequestInterceptor interceptor, HttpStatus status) throws IOException {
		interceptor.intercept(new MockClientHttpRequest(), new byte[0],
				(request, body) -> new MockClientHttpResponse(new byte[0], status));
	}

}
