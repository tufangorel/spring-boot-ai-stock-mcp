package org.example.stock.mcp;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.stock.mcp.provider.ProviderActivity;
import org.example.stock.mcp.provider.ProviderActivity.Snapshot;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows how the last request to each data provider went, as the health component {@code dataProviders}, which the
 * health group {@code providers} publishes at {@code /actuator/health/providers}. It sends no requests, and it is
 * always {@code UP}: a provider that fails is not a fault of this server, which then falls back or reports a tool
 * error, so it must not take the server out of service. The state of each provider is in the details.
 */
@Component
public class DataProvidersHealthIndicator implements HealthIndicator {

	private final ProviderActivity activity;

	public DataProvidersHealthIndicator(ProviderActivity activity) {
		this.activity = activity;
	}

	@Override
	public Health health() {
		Health.Builder health = Health.up();
		activity.snapshots().forEach((provider, snapshot) -> health.withDetail(provider, details(snapshot)));
		return health.build();
	}

	private static Map<String, Object> details(Snapshot snapshot) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("state", snapshot.state());
		if (snapshot.lastSuccess() != null) {
			details.put("lastSuccess", istanbulTime(snapshot.lastSuccess()));
		}
		if (snapshot.lastFailure() != null) {
			details.put("lastFailure", istanbulTime(snapshot.lastFailure()));
			details.put("lastError", snapshot.lastError());
		}
		details.put("consecutiveFailures", snapshot.consecutiveFailures());
		return details;
	}

	private static String istanbulTime(Instant instant) {
		return DateTimeFormatter.ISO_OFFSET_DATE_TIME
				.format(instant.truncatedTo(ChronoUnit.SECONDS).atZone(MarketClock.ISTANBUL).toOffsetDateTime());
	}

}
