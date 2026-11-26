package org.example.stock.agent;

import java.net.URI;
import java.net.http.HttpRequest;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpTracePropagationTests {

	private final Tracer tracer = mock(Tracer.class);

	private final McpTracePropagation propagation = new McpTracePropagation(tracer);

	@Test
	void addsTheCurrentSpanAsTraceparentOnTheMcpPost() {
		Span span = span("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", true);
		when(tracer.currentSpan()).thenReturn(span);

		McpTransportContext context = propagation.context();
		HttpRequest request = request("POST", context);

		assertThat(request.headers().firstValue(McpTracePropagation.TRACEPARENT))
				.contains("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
		assertThat(request.headers().firstValue(AskTraceIds.TRACE_HEADER)).isEmpty();
	}

	@Test
	void leavesTheRequestAloneWithoutASpanOrOnAnotherMethod() {
		when(tracer.currentSpan()).thenReturn(Span.NOOP);
		assertThat(propagation.context()).isSameAs(McpTransportContext.EMPTY);

		Span unsampled = span("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false);
		HttpRequest get = request("GET", McpTransportContext.create(java.util.Map.of(McpTracePropagation.TRACEPARENT,
				McpTracePropagation.header(unsampled))));
		assertThat(get.headers().firstValue(McpTracePropagation.TRACEPARENT)).isEmpty();

		HttpRequest post = request("POST", null);
		assertThat(post.headers().firstValue(McpTracePropagation.TRACEPARENT)).isEmpty();
		assertThat(McpTracePropagation.header(unsampled)).endsWith("-00");

		HttpRequest ignored = request("POST",
				McpTransportContext.create(java.util.Map.of(McpTracePropagation.TRACEPARENT, 16)));
		assertThat(ignored.headers().firstValue(McpTracePropagation.TRACEPARENT)).isEmpty();
	}

	private static HttpRequest request(String method, McpTransportContext context) {
		URI uri = URI.create("http://localhost:8081/mcp");
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
		new McpTracePropagation(mock(Tracer.class)).customize(builder, method, uri, "{}", context);
		return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
	}

	private static Span span(String traceId, String spanId, boolean sampled) {
		TraceContext context = mock(TraceContext.class);
		when(context.traceId()).thenReturn(traceId);
		when(context.spanId()).thenReturn(spanId);
		when(context.sampled()).thenReturn(sampled);
		Span span = mock(Span.class);
		when(span.isNoop()).thenReturn(false);
		when(span.context()).thenReturn(context);
		return span;
	}

}
