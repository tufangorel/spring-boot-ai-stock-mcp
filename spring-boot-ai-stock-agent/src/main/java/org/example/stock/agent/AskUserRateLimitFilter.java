package org.example.stock.agent;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after {@link AskTraceFilter} and before {@link StockAgent}. A missing user id is HTTP 400 and does not take a
 * token. The 11th ask in the minute is HTTP 429. Neither call reaches the agent.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AskUserRateLimitFilter extends OncePerRequestFilter {

	private final AskUserLimiter limiter;

	AskUserRateLimitFilter(AskUserLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !request.getRequestURI().endsWith("/api/stocks/ask");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String userId = request.getHeader(AskUserLimiter.USER_HEADER);
		if (userId == null || userId.isBlank()) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, AskUserLimiter.MISSING_USER);
			return;
		}
		if (!limiter.tryConsume(userId)) {
			response.sendError(429, AskUserLimiter.TOO_MANY);
			return;
		}
		filterChain.doFilter(request, response);
	}

}
