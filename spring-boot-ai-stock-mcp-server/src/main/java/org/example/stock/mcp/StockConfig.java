package org.example.stock.mcp;

import java.net.http.HttpClient;
import java.time.Clock;

import io.micrometer.observation.ObservationRegistry;
import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
	TradingViewClient tradingViewClient(StockProperties properties, ObservationRegistry observationRegistry) {
		return new TradingViewClient(restClient(properties, properties.tradingViewUrl(), observationRegistry));
	}

	@Bean
	YahooClient yahooClient(StockProperties properties, ObservationRegistry observationRegistry) {
		return new YahooClient(restClient(properties, properties.yahooUrl(), observationRegistry),
				properties.yahooDelayMinutes());
	}

	/** The registry makes every provider request a span under the tool call that caused it. */
	static RestClient restClient(StockProperties properties, String baseUrl, ObservationRegistry observationRegistry) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
				.observationRegistry(observationRegistry)
				.build();
	}

}
