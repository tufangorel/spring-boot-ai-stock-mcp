package org.example.stock.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@Tag(name = "Stocks", description = "Answer questions about Borsa Istanbul stocks from delayed market data")
public class StockController {

	private final StockAgent agent;

	public StockController(StockAgent agent) {
		this.agent = agent;
	}

	@Operation(summary = "Ask a question about a stock",
			description = "Returns the answer with the quotes, price histories, and tool calls it is based on, and warnings when the answer mentions a number that no tool returned. Returns 400 for a blank or too long question and 502 when the MCP server or the model fails.")
	@PostMapping("/ask")
	public StockAnswer ask(@Valid @RequestBody AskRequest request) {
		return agent.ask(request.question());
	}

}
