package org.example.stock.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stocks")
public record StockAgentProperties(@DefaultValue("500") int maxQuestionCharacters) {
}
