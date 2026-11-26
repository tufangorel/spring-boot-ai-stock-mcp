package org.example.stock.agent;

import java.io.IOException;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the MVC ask span onto the response after the server observation has started, including a rejection. A value
 * sent by the caller as {@code X-Trace-Id} or {@code X-Span-Id} is ignored.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class AskTraceFilter extends OncePerRequestFilter {

	private final Tracer tracer;

	AskTraceFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !request.getRequestURI().endsWith("/api/stocks/ask");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		AskTraceIds.write(response, tracer.currentSpan());
		filterChain.doFilter(request, response);
	}

}
