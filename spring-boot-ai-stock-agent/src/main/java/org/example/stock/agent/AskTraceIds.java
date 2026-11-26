package org.example.stock.agent;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the current OpenTelemetry span. The response headers are the MVC ask span. A log line written inside a child
 * span uses that child span. A caller-supplied id is never read.
 */
final class AskTraceIds {

	static final String TRACE_HEADER = "X-Trace-Id";

	static final String SPAN_HEADER = "X-Span-Id";

	private AskTraceIds() {
	}

	static void write(HttpServletResponse response, Span span) {
		if (!present(span)) {
			return;
		}
		response.setHeader(TRACE_HEADER, span.context().traceId());
		response.setHeader(SPAN_HEADER, span.context().spanId());
	}

	static void append(StringBuilder line, Tracer tracer) {
		Span span = tracer.currentSpan();
		if (!present(span)) {
			return;
		}
		line.append(" traceId=").append(span.context().traceId()).append(" spanId=").append(span.context().spanId());
	}

	static boolean present(Span span) {
		return span != null && !span.isNoop() && !span.context().traceId().isBlank();
	}

}
