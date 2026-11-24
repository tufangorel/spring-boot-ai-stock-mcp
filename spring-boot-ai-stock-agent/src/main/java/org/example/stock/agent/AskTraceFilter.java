package org.example.stock.agent;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mints the ask trace id and span id before anything else handles {@code POST /api/stocks/ask}, including a rejection.
 * The ids are on the response even when no model or tool log line is written.
 */
@Component
class AskTraceFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !request.getRequestURI().endsWith("/api/stocks/ask");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		AskTraceIds.Ids ids = AskTraceIds.open();
		response.setHeader(AskTraceIds.TRACE_HEADER, ids.traceId());
		response.setHeader(AskTraceIds.SPAN_HEADER, ids.spanId());
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			AskTraceIds.close();
		}
	}

}
