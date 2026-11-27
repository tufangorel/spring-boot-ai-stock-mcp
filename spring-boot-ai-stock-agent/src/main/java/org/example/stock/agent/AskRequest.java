package org.example.stock.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AskRequest(@Schema(description = "A question about Borsa Istanbul stocks, in English or Turkish",
		example = "What is the current price of Aselsan?") @NotBlank String question) {
}
