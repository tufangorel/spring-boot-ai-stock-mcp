package org.example.stock.agent;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.stereotype.Component;

/**
 * Puts the current span on the MCP HTTP request as a W3C {@code traceparent} header. The value is captured on the
 * calling thread and read from the MCP transport context, because the HTTP call itself may run on another thread.
 * {@code X-Trace-Id} is not sent.
 */
@Component
class McpTracePropagation implements McpSyncHttpClientRequestCustomizer, McpClientCustomizer<McpClient.SyncSpec> {

	static final String TRACEPARENT = "traceparent";

	private final Tracer tracer;

	McpTracePropagation(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void customize(String name, McpClient.SyncSpec spec) {
		spec.transportContextProvider(this::context);
	}

	@Override
	public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body,
			McpTransportContext context) {
		if (!"POST".equals(method) || context == null) {
			return;
		}
		Object value = context.get(TRACEPARENT);
		if (value instanceof String header) {
			builder.header(TRACEPARENT, header);
		}
	}

	McpTransportContext context() {
		Span span = tracer.currentSpan();
		if (!AskTraceIds.present(span)) {
			return McpTransportContext.EMPTY;
		}
		return McpTransportContext.create(Map.of(TRACEPARENT, header(span)));
	}

	static String header(Span span) {
		String flags = Boolean.FALSE.equals(span.context().sampled()) ? "00" : "01";
		return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-" + flags;
	}

}
