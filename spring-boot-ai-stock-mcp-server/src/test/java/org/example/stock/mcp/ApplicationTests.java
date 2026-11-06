package org.example.stock.mcp;

import org.example.stock.mcp.provider.TradingViewClient;
import org.example.stock.mcp.provider.YahooClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class ApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private StockProperties properties;

	@Test
	void wiresTheProvidersWithTheConfiguredLimits() {
		assertThat(context.getBean(TradingViewClient.class)).isNotNull();
		assertThat(context.getBean(YahooClient.class)).isNotNull();
		assertThat(properties).isEqualTo(StockProperties.defaults());
	}

}
