package org.example.stock.mcp;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

/** Borsa Istanbul continuous trading hours. Public holidays and half days are not modeled. */
@Component
public class MarketClock {

	public static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

	private static final LocalTime OPEN = LocalTime.of(10, 0);

	private static final LocalTime CLOSE = LocalTime.of(18, 0);

	private final Clock clock;

	public MarketClock(Clock clock) {
		this.clock = clock;
	}

	public boolean isOpen() {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(ISTANBUL));
		LocalTime time = now.toLocalTime();
		return now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY
				&& !time.isBefore(OPEN) && time.isBefore(CLOSE);
	}

	public Instant now() {
		return clock.instant();
	}

	public static OffsetDateTime istanbulTime(long epochSeconds) {
		return Instant.ofEpochSecond(epochSeconds).atZone(ISTANBUL).toOffsetDateTime();
	}

}
