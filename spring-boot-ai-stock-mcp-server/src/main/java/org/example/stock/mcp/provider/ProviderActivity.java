package org.example.stock.mcp.provider;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Remembers how the last request to each data provider went, so that the health endpoint can show it without sending
 * requests of its own. Only requests the tools really make are recorded, so the information is as old as the last
 * one. It is kept in memory and starts empty.
 */
public class ProviderActivity {

	public static final String TRADING_VIEW = "tradingView";

	public static final String YAHOO = "yahoo";

	/**
	 * @param lastError why the last failed request failed: the HTTP status, such as {@code HTTP 503}, or the name of
	 * the exception when there was no response
	 * @param consecutiveFailures the failed requests since the last successful one
	 */
	public record Snapshot(Instant lastSuccess, Instant lastFailure, String lastError, int consecutiveFailures) {

		static final Snapshot NOT_CALLED = new Snapshot(null, null, null, 0);

		public String state() {
			if (lastSuccess == null && lastFailure == null) {
				return "not called yet";
			}
			return consecutiveFailures == 0 ? "ok" : "failing";
		}

	}

	private final Clock clock;

	private final Map<String, Snapshot> providers = new LinkedHashMap<>();

	/**
	 * @param providers the providers to report, in this order, also before their first request
	 */
	public ProviderActivity(Clock clock, String... providers) {
		this.clock = clock;
		for (String provider : providers) {
			this.providers.put(provider, Snapshot.NOT_CALLED);
		}
	}

	/**
	 * @param alsoSuccessful statuses outside 2xx that are a normal answer of this provider
	 * @return an interceptor that records the outcome of every request and changes nothing about it
	 */
	public ClientHttpRequestInterceptor recording(String provider, HttpStatus... alsoSuccessful) {
		List<Integer> accepted = Arrays.stream(alsoSuccessful).map(HttpStatus::value).toList();
		return (request, body, execution) -> {
			ClientHttpResponse response;
			try {
				response = execution.execute(request, body);
			}
			catch (IOException | RuntimeException ex) {
				failed(provider, ex.getClass().getSimpleName());
				throw ex;
			}
			int status = response.getStatusCode().value();
			if (response.getStatusCode().is2xxSuccessful() || accepted.contains(status)) {
				succeeded(provider);
			}
			else {
				failed(provider, "HTTP " + status);
			}
			return response;
		};
	}

	/**
	 * @return the state of each provider, in the order they were given
	 */
	public synchronized Map<String, Snapshot> snapshots() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(providers));
	}

	private synchronized void succeeded(String provider) {
		Snapshot before = providers.getOrDefault(provider, Snapshot.NOT_CALLED);
		providers.put(provider, new Snapshot(clock.instant(), before.lastFailure(), before.lastError(), 0));
	}

	private synchronized void failed(String provider, String error) {
		Snapshot before = providers.getOrDefault(provider, Snapshot.NOT_CALLED);
		providers.put(provider,
				new Snapshot(before.lastSuccess(), clock.instant(), error, before.consecutiveFailures() + 1));
	}

}
