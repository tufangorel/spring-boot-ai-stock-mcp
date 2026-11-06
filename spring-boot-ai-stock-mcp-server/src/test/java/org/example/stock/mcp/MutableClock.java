package org.example.stock.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class MutableClock extends Clock {

	private Instant now;

	MutableClock(Instant now) {
		this.now = now;
	}

	void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}

}
