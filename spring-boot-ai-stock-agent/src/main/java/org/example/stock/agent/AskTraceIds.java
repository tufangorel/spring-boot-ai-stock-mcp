package org.example.stock.agent;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Random;

/**
 * One trace id and one span id for the ask that is running on this thread. The values are minted here until the agent
 * exports OpenTelemetry spans. A caller-supplied id is never read.
 */
final class AskTraceIds {

	static final String TRACE_HEADER = "X-Trace-Id";

	static final String SPAN_HEADER = "X-Span-Id";

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final ThreadLocal<Ids> CURRENT = new ThreadLocal<>();

	private AskTraceIds() {
	}

	record Ids(String traceId, String spanId) {
	}

	static Ids open() {
		Ids ids = new Ids(hex(16, RANDOM), hex(8, RANDOM));
		CURRENT.set(ids);
		return ids;
	}

	static void close() {
		CURRENT.remove();
	}

	static Ids current() {
		return CURRENT.get();
	}

	static void append(StringBuilder line) {
		Ids ids = CURRENT.get();
		if (ids == null) {
			return;
		}
		line.append(" traceId=").append(ids.traceId()).append(" spanId=").append(ids.spanId());
	}

	static String hex(int size, Random random) {
		byte[] bytes = new byte[size];
		do {
			random.nextBytes(bytes);
		}
		while (allZero(bytes));
		return HexFormat.of().formatHex(bytes);
	}

	static boolean allZero(byte[] bytes) {
		for (byte value : bytes) {
			if (value != 0) {
				return false;
			}
		}
		return true;
	}

}
