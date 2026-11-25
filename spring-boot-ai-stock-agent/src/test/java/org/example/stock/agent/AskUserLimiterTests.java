package org.example.stock.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskUserLimiterTests {

	@Test
	void givesEachUserTenCallsUntilTheMinuteElapses() {
		MutableClock clock = new MutableClock(Instant.parse("2026-11-23T09:00:00Z"));
		AskUserLimiter limiter = new AskUserLimiter(clock, new StockAgentProperties(500, 10));

		for (int call = 0; call < 10; call++) {
			assertThat(limiter.tryConsume("ada")).isTrue();
		}
		assertThat(limiter.tryConsume("ada")).isFalse();
		assertThat(limiter.tryConsume("bea")).isTrue();

		clock.plus(Duration.ofMinutes(1));

		assertThat(limiter.tryConsume("ada")).isTrue();
	}

	@Test
	void readsTheClockAsWallTime() {
		MutableClock clock = new MutableClock(Instant.parse("2026-11-23T09:00:00Z"));
		TimeMeter meter = AskUserLimiter.timeMeter(clock);

		assertThat(meter.isWallClockBased()).isTrue();
		assertThat(meter.currentTimeNanos()).isEqualTo(clock.millis() * 1_000_000L);
	}

	static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void plus(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

	}

}
