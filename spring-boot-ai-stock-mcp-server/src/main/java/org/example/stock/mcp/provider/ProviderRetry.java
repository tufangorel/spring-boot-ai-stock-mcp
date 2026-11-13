package org.example.stock.mcp.provider;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Retries a provider call once when the failure is quick and likely temporary. Timeouts, client errors and every other
 * failure are left alone: a timeout has already spent seconds, and a 4xx answer will not change.
 * <p>
 * {@link RetryTemplate#invoke} rethrows the original exception, so callers still see the provider's
 * {@code RestClientException}.
 */
public class ProviderRetry {

	private static final Log logger = LogFactory.getLog(ProviderRetry.class);

	private final RetryTemplate retryTemplate;

	private final ThreadLocal<String> currentCall = new ThreadLocal<>();

	public ProviderRetry() {
		this.retryTemplate = newRetryTemplate();
	}

	/** For tests that need a different delay or no delay. */
	public ProviderRetry(RetryTemplate retryTemplate) {
		this.retryTemplate = retryTemplate;
	}

	/**
	 * @param what a short name for the log line when the call is tried again, such as {@code TradingView search}
	 */
	public <T> T execute(String what, Supplier<T> call) {
		currentCall.set(what);
		try {
			return retryTemplate.invoke(call);
		}
		finally {
			currentCall.remove();
		}
	}

	public static boolean isRetryable(Throwable throwable) {
		if (throwable instanceof HttpServerErrorException server) {
			int status = server.getStatusCode().value();
			return status == 502 || status == 503 || status == 504;
		}
		if (throwable instanceof ResourceAccessException) {
			return !isTimeout(throwable);
		}
		return false;
	}

	public static boolean isTimeout(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof HttpTimeoutException || current instanceof HttpConnectTimeoutException
					|| current instanceof java.net.SocketTimeoutException || current instanceof TimeoutException) {
				return true;
			}
		}
		return false;
	}

	private RetryTemplate newRetryTemplate() {
		RetryTemplate template = new RetryTemplate(RetryPolicy.builder()
				.maxRetries(1)
				.delay(Duration.ofMillis(300))
				.jitter(Duration.ofMillis(100))
				.predicate(ProviderRetry::isRetryable)
				.build());
		template.setRetryListener(new RetryListener() {
			@Override
			public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
				String what = currentCall.get();
				logger.warn((what != null ? what : "Provider call") + " failed (" + reason(retryState.getLastException())
						+ "); trying once more");
			}
		});
		return template;
	}

	/** Visible for tests. */
	static String reason(Throwable ex) {
		if (ex instanceof HttpServerErrorException server) {
			return "HTTP " + server.getStatusCode().value();
		}
		if (ex == null) {
			return "unknown";
		}
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root.getClass().getSimpleName();
	}

}
