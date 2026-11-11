package org.example.stock.mcp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param cacheTtl how long quotes, symbol searches, and histories are reused; the data is delayed anyway
 * @param yahooDelayMinutes Yahoo does not report the delay for Borsa Istanbul, so it is configured
 * @param recordToolPayloads whether the arguments and result of each tool call go into its span and log line
 * @param maxPayloadCharacters recorded arguments and results longer than this are cut
 */
@ConfigurationProperties("stocks")
public record StockProperties(
		@DefaultValue("60s") Duration cacheTtl,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout,
		@DefaultValue("https://scanner.tradingview.com") String tradingViewUrl,
		@DefaultValue("https://query1.finance.yahoo.com") String yahooUrl,
		@DefaultValue("Mozilla/5.0 (compatible; spring-boot-ai-stock-mcp-server)") String userAgent,
		@DefaultValue("15") int yahooDelayMinutes,
		@DefaultValue("10") int maxSymbols,
		@DefaultValue("5") int maxMatches,
		@DefaultValue("120") int maxCandles,
		@DefaultValue("true") boolean recordToolPayloads,
		@DefaultValue("4000") int maxPayloadCharacters) {

	public static StockProperties defaults() {
		return new StockProperties(Duration.ofSeconds(60), Duration.ofSeconds(3), Duration.ofSeconds(5),
				"https://scanner.tradingview.com", "https://query1.finance.yahoo.com",
				"Mozilla/5.0 (compatible; spring-boot-ai-stock-mcp-server)", 15, 10, 5, 120, true, 4000);
	}

}
