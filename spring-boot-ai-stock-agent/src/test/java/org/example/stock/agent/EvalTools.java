package org.example.stock.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stands in for the stock MCP server during the tool-choice eval. The names, descriptions and parameter text come
 * from {@code eval/tool-definitions.json}, which a server test keeps equal to the {@code @McpTool} annotations.
 * Results are fixed sample JSON, so the eval checks which tool the model calls.
 */
final class EvalTools implements ToolCallbackProvider {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Intervals for each range, the default first. The same table as {@code QuoteService}. */
	private static final Map<String, List<String>> INTERVALS = new LinkedHashMap<>();

	private static final Pattern THY = Pattern.compile("(^|[^a-z])thy([^a-z]|$)");

	static {
		INTERVALS.put("1d", List.of("5m", "1m", "15m", "30m", "60m"));
		INTERVALS.put("5d", List.of("30m", "5m", "15m", "60m", "1d"));
		INTERVALS.put("1mo", List.of("1d", "30m", "60m", "1wk"));
		INTERVALS.put("3mo", List.of("1d", "1wk"));
		INTERVALS.put("6mo", List.of("1d", "1wk"));
		INTERVALS.put("1y", List.of("1wk", "1d", "1mo"));
		INTERVALS.put("5y", List.of("1mo", "1wk"));
	}

	private final List<ToolDefinition> definitions;

	EvalTools() {
		this.definitions = definitions(load());
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return definitions.stream().map(definition -> (ToolCallback) new ToolCallback() {

			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				JsonNode arguments = ToolResults.unwrap(toolInput, JSON);
				return switch (definition.name()) {
					case "findSymbol" -> FakeTools.mcpText(findSymbol(arguments).toString());
					case "getQuote" -> FakeTools.mcpText(quote(arguments).toString());
					case "getHistory" -> FakeTools.mcpText(history(definition, arguments).toString());
					default -> throw new ToolExecutionException(definition,
							new IllegalArgumentException("Unknown tool " + definition.name()));
				};
			}

		}).toArray(ToolCallback[]::new);
	}

	private static JsonNode load() {
		try (InputStream in = EvalTools.class.getResourceAsStream("/eval/tool-definitions.json")) {
			if (in == null) {
				throw new IllegalStateException("eval/tool-definitions.json is missing");
			}
			return JSON.readTree(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<ToolDefinition> definitions(JsonNode fixture) {
		return fixture.path("tools").valueStream().map(tool -> {
			ObjectNode schema = JSON.createObjectNode();
			schema.put("type", "object");
			ObjectNode properties = schema.putObject("properties");
			ArrayNode required = schema.putArray("required");
			for (JsonNode parameter : tool.path("parameters")) {
				ObjectNode property = properties.putObject(parameter.path("name").asString());
				property.put("type", parameter.path("type").asString());
				property.put("description", parameter.path("description").asString());
				if ("array".equals(parameter.path("type").asString())) {
					property.putObject("items").put("type", "string");
				}
				if (parameter.path("required").asBoolean()) {
					required.add(parameter.path("name").asString());
				}
			}
			return ToolDefinition.builder()
					.name(tool.path("name").asString())
					.description(tool.path("description").asString())
					.inputSchema(schema.toString())
					.build();
		}).toList();
	}

	private static ObjectNode findSymbol(JsonNode arguments) {
		String query = arguments.path("query").asString("");
		String lower = query.toLowerCase(Locale.ROOT);
		ObjectNode result = JSON.createObjectNode();
		result.put("query", query);
		ArrayNode matches = result.putArray("matches");
		if (lower.contains("asels")) {
			matches.add(match("ASELS", "Aselsan Elektronik Sanayi ve Ticaret A.S. Class B"));
		}
		if (lower.contains("thyao") || lower.contains("turk hava") || lower.contains("türk hava")
				|| lower.contains("turkish") || THY.matcher(lower).find()) {
			matches.add(match("THYAO", "Turk Hava Yollari A.O."));
		}
		return result;
	}

	private static ObjectNode match(String symbol, String name) {
		ObjectNode match = JSON.createObjectNode();
		match.put("symbol", symbol);
		match.put("name", name);
		match.put("exchange", "BIST");
		return match;
	}

	private static ObjectNode quote(JsonNode arguments) {
		ObjectNode result = JSON.createObjectNode();
		ArrayNode quotes = result.putArray("quotes");
		ArrayNode notFound = result.putArray("notFound");
		for (String raw : texts(arguments.path("symbols"))) {
			String ticker = tickerOrNull(raw);
			if ("ASELS".equals(ticker)) {
				quotes.add(quote("ASELS", "Aselsan Elektronik Sanayi ve Ticaret A.S. Class B", 330.75, 1.75, 0.53));
			}
			else if ("THYAO".equals(ticker)) {
				quotes.add(quote("THYAO", "Turk Hava Yollari A.O.", 285.5, -1.25, -0.44));
			}
			else {
				notFound.add(ticker != null ? ticker : raw);
			}
		}
		return result;
	}

	private static ObjectNode quote(String symbol, String name, double price, double change, double changePercent) {
		ObjectNode quote = JSON.createObjectNode();
		quote.put("symbol", symbol);
		quote.put("name", name);
		quote.put("price", price);
		quote.put("currency", "TRY");
		quote.put("change", change);
		quote.put("changePercent", changePercent);
		quote.put("asOf", "2026-11-06T15:40:11+03:00");
		quote.put("delayMinutes", 15);
		quote.put("source", "TradingView");
		quote.put("marketOpen", true);
		return quote;
	}

	private static ObjectNode history(ToolDefinition definition, JsonNode arguments) {
		String symbol = arguments.path("symbol").asString("");
		String ticker = tickerOrNull(symbol);
		if (ticker == null) {
			throw toolError(definition, "\"" + symbol + "\" is not a Borsa Istanbul ticker such as ASELS. "
					+ "Use findSymbol to look up a company by name.");
		}
		String range = arguments.path("range").asString("");
		List<String> allowed = INTERVALS.get(range);
		if (allowed == null) {
			throw toolError(definition, "Range \"" + range + "\" is not supported. Use one of "
					+ String.join(", ", INTERVALS.keySet()) + ".");
		}
		String interval = arguments.path("interval").asString("").trim();
		String chosen = interval.isEmpty() ? allowed.getFirst() : interval;
		if (!allowed.contains(chosen)) {
			throw toolError(definition, "Interval \"" + chosen + "\" does not fit range " + range + ". Use one of "
					+ String.join(", ", allowed) + ".");
		}
		if (!ticker.equals("ASELS") && !ticker.equals("THYAO")) {
			throw toolError(definition, "No Borsa Istanbul stock has the ticker " + ticker
					+ ". Use findSymbol to look it up by company name.");
		}
		ObjectNode history = JSON.createObjectNode();
		history.put("symbol", ticker);
		history.put("currency", "TRY");
		history.put("range", range);
		history.put("interval", chosen);
		ArrayNode candles = history.putArray("candles");
		ObjectNode candle = candles.addObject();
		candle.put("time", "2026-11-06T09:30:00+03:00");
		candle.put("open", 329.5);
		candle.put("high", 335.75);
		candle.put("low", 325.25);
		candle.put("close", 330.75);
		candle.put("volume", 30329395);
		history.put("omittedCandles", 0);
		history.put("asOf", "2026-11-06T15:25:56+03:00");
		history.put("delayMinutes", 15);
		history.put("source", "Yahoo Finance");
		return history;
	}

	private static ToolExecutionException toolError(ToolDefinition definition, String message) {
		return new ToolExecutionException(definition, new IllegalArgumentException(message));
	}

	/** A Borsa Istanbul ticker, or null when the text is a company name or otherwise not a ticker. */
	private static String tickerOrNull(String symbol) {
		String ticker = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
		if (ticker.startsWith("BIST:")) {
			ticker = ticker.substring(5);
		}
		if (ticker.endsWith(".IS")) {
			ticker = ticker.substring(0, ticker.length() - 3);
		}
		return ticker.matches("[A-Z0-9]{2,12}") ? ticker : null;
	}

	private static List<String> texts(JsonNode node) {
		if (node.isArray()) {
			return node.valueStream().map(JsonNode::asString).toList();
		}
		if (node.isString() && !node.asString().isBlank()) {
			return List.of(node.asString());
		}
		return List.of();
	}

}
