package org.example.stock.mcp.provider;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ProviderRetryTests {

	private final ProviderRetry retry = new ProviderRetry(new RetryTemplate(RetryPolicy.builder()
			.maxRetries(1)
			.delay(Duration.ZERO)
			.predicate(ProviderRetry::isRetryable)
			.build()));

	@ParameterizedTest
	@ValueSource(ints = { 502, 503, 504 })
	void retriesAGatewayErrorOnce(int status) {
		AtomicInteger attempts = new AtomicInteger();
		HttpServerErrorException failure = HttpServerErrorException.create(HttpStatus.valueOf(status), "fail", null,
				null, null);

		String result = retry.execute("TradingView search", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw failure;
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void retriesAConnectionFailureOnce() {
		AtomicInteger attempts = new AtomicInteger();

		String result = retry.execute("Yahoo history", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw new ResourceAccessException("refused", new ConnectException("Connection refused"));
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void doesNotRetryAReadTimeout() {
		AtomicInteger attempts = new AtomicInteger();
		ResourceAccessException timeout = new ResourceAccessException("timed out",
				new HttpTimeoutException("request timed out"));

		assertThatThrownBy(() -> retry.execute("Yahoo quote", () -> {
			attempts.incrementAndGet();
			throw timeout;
		})).isSameAs(timeout);
		assertThat(attempts).hasValue(1);
	}

	@Test
	void doesNotRetryClientErrors() {
		AtomicInteger attempts = new AtomicInteger();
		HttpClientErrorException tooMany = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down",
				null, null, null);
		HttpClientErrorException notFound = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "missing", null, null,
				null);

		assertThatThrownBy(() -> retry.execute("Yahoo quote", () -> {
			attempts.incrementAndGet();
			throw tooMany;
		})).isSameAs(tooMany);
		assertThatThrownBy(() -> retry.execute("Yahoo quote", () -> {
			attempts.incrementAndGet();
			throw notFound;
		})).isSameAs(notFound);
		assertThat(attempts).hasValue(2);
	}

	@Test
	void rethrowsTheLastRestClientExceptionAfterTwoFailures() {
		AtomicInteger attempts = new AtomicInteger();
		HttpServerErrorException first = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "1", null,
				null, null);
		HttpServerErrorException second = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "2", null, null,
				null);

		assertThatThrownBy(() -> retry.execute("TradingView search", () -> {
			throw attempts.incrementAndGet() == 1 ? first : second;
		})).isSameAs(second).isInstanceOf(RestClientException.class).isNotInstanceOf(org.springframework.core.retry.RetryException.class);
		assertThat(attempts).hasValue(2);
	}

	@Test
	void classifiesTimeoutsAndRetryableFailures() {
		assertThat(ProviderRetry.isRetryable(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", null, null,
				null))).isTrue();
		assertThat(ProviderRetry.isRetryable(new ResourceAccessException("refused", new ConnectException()))).isTrue();
		assertThat(ProviderRetry.isRetryable(new ResourceAccessException("timed out",
				new HttpTimeoutException("timed out")))).isFalse();
		assertThat(ProviderRetry.isRetryable(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", null,
				null, null))).isFalse();
		assertThat(ProviderRetry.isRetryable(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "x", null,
				null, null))).isFalse();
		assertThat(ProviderRetry.isRetryable(new IllegalStateException("not a provider failure"))).isFalse();
		assertThat(ProviderRetry.isTimeout(new ResourceAccessException("x", new HttpTimeoutException("x")))).isTrue();
		assertThat(ProviderRetry.isTimeout(new ResourceAccessException("x", new HttpConnectTimeoutException("x"))))
				.isTrue();
		assertThat(ProviderRetry.isTimeout(new ResourceAccessException("x", new SocketTimeoutException("x")))).isTrue();
		assertThat(ProviderRetry.isTimeout(new ResourceAccessException("x", new IOException(new TimeoutException("x")))))
				.isTrue();
		assertThat(ProviderRetry.isTimeout(new ResourceAccessException("x", new IOException("x")))).isFalse();
	}

	@Test
	void logsTheCallNameAndHttpStatusBeforeTheSecondAttempt(CapturedOutput output) {
		ProviderRetry production = new ProviderRetry();
		AtomicInteger attempts = new AtomicInteger();

		String result = production.execute("TradingView search", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", null, null, null);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(attempts).hasValue(2);
		assertThat(output).contains("TradingView search failed (HTTP 503); trying once more");
	}

	@Test
	void logsTheRootCauseNameForAConnectionFailure(CapturedOutput output) {
		ProviderRetry production = new ProviderRetry();
		AtomicInteger attempts = new AtomicInteger();

		String result = production.execute("Yahoo history", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw new ResourceAccessException("refused", new ConnectException("Connection refused"));
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(output).contains("Yahoo history failed (ConnectException); trying once more");
	}

	@Test
	void logsAGenericNameWhenTheCallNameIsMissing(CapturedOutput output) {
		ProviderRetry production = new ProviderRetry();
		AtomicInteger attempts = new AtomicInteger();

		assertThat(production.execute(null, () -> {
			if (attempts.incrementAndGet() == 1) {
				throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "blip", null, null, null);
			}
			return "ok";
		})).isEqualTo("ok");
		assertThat(output).contains("Provider call failed (HTTP 502); trying once more");
	}

	@Test
	void describesExceptionsForTheLogLine() {
		assertThat(ProviderRetry.reason(null)).isEqualTo("unknown");
		assertThat(ProviderRetry.reason(HttpServerErrorException.create(HttpStatus.GATEWAY_TIMEOUT, "x", null, null,
				null))).isEqualTo("HTTP 504");
		assertThat(ProviderRetry.reason(new ResourceAccessException("refused", new ConnectException("Connection refused"))))
				.isEqualTo("ConnectException");
		Throwable loop = new Throwable("loop") {
			@Override
			public synchronized Throwable getCause() {
				return this;
			}
		};
		assertThat(ProviderRetry.reason(loop)).isEqualTo(loop.getClass().getSimpleName());
	}

}
