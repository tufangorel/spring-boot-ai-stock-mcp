package org.example.stock.mcp;

import java.net.http.HttpClient;
import java.time.Clock;

import io.micrometer.observation.ObservationRegistry;
import org.example.stock.mcp.provider.ProviderActivity;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class StockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ToolCallObserver toolCallObserver(ObservationRegistry observationRegistry, JsonMapper jsonMapper,
			StockProperties properties) {
		return new ToolCallObserver(observationRegistry, jsonMapper, properties.recordToolPayloads(),
				properties.maxPayloadCharacters());
	}

	@Bean
	ProviderActivity providerActivity(Clock clock) {
		return new ProviderActivity(clock, ProviderActivity.TRADING_VIEW, ProviderActivity.YAHOO);
	}

	@Bean
	TradingViewClient tradingViewClient(StockProperties properties, ObservationRegistry observationRegistry,
			ProviderActivity providerActivity) {
		return new TradingViewClient(restClientBuilder(properties, properties.tradingViewUrl(), observationRegistry,
				providerActivity.recording(ProviderActivity.TRADING_VIEW)).build());
	}

	/** Yahoo answers 404 for a ticker it does not know, which says nothing against Yahoo. */
	@Bean
	YahooClient yahooClient(StockProperties properties, ObservationRegistry observationRegistry,
			ProviderActivity providerActivity) {
		return new YahooClient(restClientBuilder(properties, properties.yahooUrl(), observationRegistry,
				providerActivity.recording(ProviderActivity.YAHOO, HttpStatus.NOT_FOUND)).build(),
				properties.yahooDelayMinutes());
	}

	/**
	 * The registry makes every provider request a span under the tool call that caused it, and a measurement of the
	 * {@code http.client.requests} timer. The interceptor sees the outcome of every request.
	 */
	static RestClient.Builder restClientBuilder(StockProperties properties, String baseUrl,
			ObservationRegistry observationRegistry, ClientHttpRequestInterceptor interceptor) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
				.observationRegistry(observationRegistry)
				.requestInterceptor(interceptor);
	}

}
