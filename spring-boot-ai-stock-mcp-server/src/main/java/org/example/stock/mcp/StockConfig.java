package org.example.stock.mcp;

import java.net.http.HttpClient;
import java.time.Clock;

import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class StockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	TradingViewClient tradingViewClient(StockProperties properties) {
		return new TradingViewClient(restClient(properties, properties.tradingViewUrl()));
	}

	@Bean
	YahooClient yahooClient(StockProperties properties) {
		return new YahooClient(restClient(properties, properties.yahooUrl()), properties.yahooDelayMinutes());
	}

	static RestClient restClient(StockProperties properties, String baseUrl) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
				.build();
	}

}
