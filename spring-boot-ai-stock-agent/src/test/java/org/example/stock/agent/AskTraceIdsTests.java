package org.example.stock.agent;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskTraceIdsTests {

	@AfterEach
	void close() {
		AskTraceIds.close();
	}

	@Test
	void mintsLowercaseHexAndClearsIt() {
		AskTraceIds.Ids first = AskTraceIds.open();
		AskTraceIds.Ids second = AskTraceIds.open();

		assertThat(first.traceId()).hasSize(32).matches("[0-9a-f]+");
		assertThat(first.spanId()).hasSize(16).matches("[0-9a-f]+");
		assertThat(second.traceId()).isNotEqualTo(first.traceId());
		assertThat(AskTraceIds.current()).isEqualTo(second);

		StringBuilder line = new StringBuilder("Agent model call");
		AskTraceIds.append(line);
		assertThat(line).hasToString("Agent model call traceId=" + second.traceId() + " spanId=" + second.spanId());

		AskTraceIds.close();
		StringBuilder quiet = new StringBuilder("Agent model call");
		AskTraceIds.append(quiet);
		assertThat(quiet).hasToString("Agent model call");
		assertThat(AskTraceIds.current()).isNull();
	}

	@Test
	void drawsAgainWhenTheBytesAreAllZero() {
		Random zerosThenOnes = new Random() {
			private int calls;

			@Override
			public void nextBytes(byte[] bytes) {
				Arrays.fill(bytes, (byte) (calls++ == 0 ? 0 : 1));
			}
		};

		assertThat(AskTraceIds.allZero(new byte[] { 0, 0 })).isTrue();
		assertThat(AskTraceIds.allZero(new byte[] { 0, 1 })).isFalse();
		assertThat(AskTraceIds.hex(2, zerosThenOnes)).isEqualTo("0101");
	}

	@Test
	void isNotConstructedByCallers() throws Exception {
		Constructor<AskTraceIds> constructor = AskTraceIds.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		assertThat(constructor.newInstance()).isNotNull();
	}

}
