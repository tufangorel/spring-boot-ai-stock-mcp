package org.example.stock.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A small in-memory cache. Expired entries are replaced on the next write for the same key. */
final class TtlCache<K, V> {

	private record Entry<V>(V value, Instant expires) {
	}

	private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

	private final Clock clock;

	private final Duration ttl;

	TtlCache(Clock clock, Duration ttl) {
		this.clock = clock;
		this.ttl = ttl;
	}

	Optional<V> get(K key) {
		Entry<V> entry = entries.get(key);
		return entry != null && clock.instant().isBefore(entry.expires()) ? Optional.of(entry.value())
				: Optional.empty();
	}

	void put(K key, V value) {
		entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
	}

}
