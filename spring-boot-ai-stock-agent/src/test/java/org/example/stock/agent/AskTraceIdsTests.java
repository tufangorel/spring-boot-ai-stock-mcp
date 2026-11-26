package org.example.stock.agent;

import java.lang.reflect.Constructor;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AskTraceIdsTests {

	@Test
	void appendsTheCurrentSpanAndSkipsAMissingOne() {
		Span span = mock(Span.class);
		io.micrometer.tracing.TraceContext context = mock(io.micrometer.tracing.TraceContext.class);
		when(span.isNoop()).thenReturn(false);
		when(span.context()).thenReturn(context);
		when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
		when(context.spanId()).thenReturn("00f067aa0ba902b7");
		Tracer tracer = mock(Tracer.class);
		when(tracer.currentSpan()).thenReturn(span);

		StringBuilder line = new StringBuilder("Agent model call");
		AskTraceIds.append(line, tracer);
		assertThat(line).hasToString(
				"Agent model call traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=00f067aa0ba902b7");

		when(tracer.currentSpan()).thenReturn(Span.NOOP);
		StringBuilder quiet = new StringBuilder("Agent model call");
		AskTraceIds.append(quiet, tracer);
		assertThat(quiet).hasToString("Agent model call");
		assertThat(AskTraceIds.present(null)).isFalse();

		when(context.traceId()).thenReturn("   ");
		assertThat(AskTraceIds.present(span)).isFalse();
	}

	@Test
	void isNotConstructedByCallers() throws Exception {
		Constructor<AskTraceIds> constructor = AskTraceIds.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		assertThat(constructor.newInstance()).isNotNull();
	}

}
