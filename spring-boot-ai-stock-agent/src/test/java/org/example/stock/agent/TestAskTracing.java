package org.example.stock.agent;

import java.io.IOException;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A real tracer for MVC slice tests, which do not start the server observation. The filter opens one span around the
 * ask so the response headers have ids.
 */
@TestConfiguration
class TestAskTracing {

	@Bean
	ObservationRegistry observationRegistry() {
		return ObservationRegistry.create();
	}

	@Bean
	Tracer tracer() {
		SdkTracerProvider provider = SdkTracerProvider.builder().build();
		OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
		return new OtelTracer(sdk.getTracer("test"), new OtelCurrentTraceContext(), event -> {
		});
	}

	@Bean
	FilterRegistrationBean<OncePerRequestFilter> openAskSpan(Tracer tracer) {
		OncePerRequestFilter filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				Span span = tracer.nextSpan().name("test-ask").start();
				try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
					filterChain.doFilter(request, response);
				}
				finally {
					span.end();
				}
			}
		};
		FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
