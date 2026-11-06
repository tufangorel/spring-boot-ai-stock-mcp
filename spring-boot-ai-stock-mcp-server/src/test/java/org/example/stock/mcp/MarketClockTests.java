package org.example.stock.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketClockTests {

	@ParameterizedTest(name = "{0} is open: {1}")
	@CsvSource({
			"2026-11-06T06:59:59Z, false",
			"2026-11-06T07:00:00Z, true",
			"2026-11-06T12:00:00Z, true",
			"2026-11-06T14:59:59Z, true",
			"2026-11-06T15:00:00Z, false",
			"2026-11-07T12:00:00Z, false",
			"2026-11-08T12:00:00Z, false" })
	void isOpenOnWeekdaysFromTenToSixIstanbulTime(String instant, boolean open) {
		MarketClock clock = new MarketClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));

		assertThat(clock.isOpen()).isEqualTo(open);
		assertThat(clock.now()).isEqualTo(Instant.parse(instant));
	}

	@Test
	void showsEpochSecondsInIstanbulTime() {
		assertThat(MarketClock.istanbulTime(1793968811))
				.isEqualTo(OffsetDateTime.parse("2026-11-06T15:40:11+03:00"));
	}

}
