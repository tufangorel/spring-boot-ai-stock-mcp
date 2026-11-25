package org.example.stock.agent;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

/**
 * One local Bucket4j bucket per {@code X-User-Id}. The existing {@link Clock} is the time source, so a test moves
 * that clock and the minute window moves with it.
 */
@Component
class AskUserLimiter {

	static final String USER_HEADER = "X-User-Id";

	static final String MISSING_USER = "X-User-Id is required.";

	static final String TOO_MANY = "Too many questions. Try again in a minute.";

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final Clock clock;

	private final int permits;

	AskUserLimiter(Clock clock, StockAgentProperties properties) {
		this.clock = clock;
		this.permits = properties.askPermitsPerMinute();
	}

	boolean tryConsume(String userId) {
		return bucket(userId).tryConsume(1);
	}

	private Bucket bucket(String userId) {
		return buckets.computeIfAbsent(userId, id -> Bucket.builder()
				.withCustomTimePrecision(timeMeter(clock))
				.addLimit(limit -> limit.capacity(permits).refillIntervally(permits, Duration.ofMinutes(1)))
				.build());
	}

	static TimeMeter timeMeter(Clock clock) {
		return new TimeMeter() {
			@Override
			public long currentTimeNanos() {
				return clock.millis() * 1_000_000L;
			}

			@Override
			public boolean isWallClockBased() {
				return true;
			}
		};
	}

}
