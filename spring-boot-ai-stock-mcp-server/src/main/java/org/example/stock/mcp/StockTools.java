package org.example.stock.mcp;

import java.util.List;

import org.example.stock.mcp.StockData.History;
import org.example.stock.mcp.StockData.Quotes;
import org.example.stock.mcp.StockData.SymbolMatches;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The MCP tools. They describe what the caller wants, not which provider answers it; {@link QuoteService} picks the
 * provider. An exception thrown here reaches the MCP client as a tool error carrying its message.
 */
@Component
public class StockTools {

	private final QuoteService quoteService;

	public StockTools(QuoteService quoteService) {
		this.quoteService = quoteService;
	}

	@McpTool(name = "findSymbol", title = "Find a Borsa Istanbul ticker",
			description = """
					Finds Borsa Istanbul tickers for a company name or part of one, for example "aselsan" or \
					"turk hava". Returns at most 5 matches, largest companies first. Use it whenever the ticker \
					is not certain.""",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = true))
	public SymbolMatches findSymbol(
			@McpToolParam(description = "Company name or ticker to search for", required = true) String query) {
		return quoteService.findSymbol(query);
	}

	@McpTool(name = "getQuote", title = "Get delayed stock quotes",
			description = """
					Returns the latest delayed quote for 1 to 10 Borsa Istanbul tickers such as ASELS or THYAO. \
					Each quote has asOf (Istanbul time the price is from), delayMinutes, source, and marketOpen. \
					When marketOpen is false the price is the last close. Unknown tickers are listed in notFound.""",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = true))
	public Quotes getQuote(@McpToolParam(description = "Tickers, for example [\"ASELS\", \"THYAO\"]",
			required = true) List<String> symbols) {
		return quoteService.quotes(symbols);
	}

	@McpTool(name = "getHistory", title = "Get price history",
			description = """
					Returns delayed OHLC candles for one Borsa Istanbul ticker, oldest first, at most 120. \
					range is one of 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y. interval is optional and must fit the range: \
					1d takes 1m, 5m, 15m, 30m, 60m; 5d takes 5m, 15m, 30m, 60m, 1d; 1mo takes 30m, 60m, 1d, 1wk; \
					3mo and 6mo take 1d, 1wk; 1y takes 1d, 1wk, 1mo; 5y takes 1wk, 1mo.""",
			annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
					openWorldHint = true))
	public History getHistory(@McpToolParam(description = "Ticker, for example ASELS", required = true) String symbol,
			@McpToolParam(description = "One of 1d, 5d, 1mo, 3mo, 6mo, 1y, 5y", required = true) String range,
			@McpToolParam(description = "Candle size; leave empty for the default of the range",
					required = false) String interval) {
		return quoteService.history(symbol, range, interval);
	}

}
