package org.example.stock.agent;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI stockOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Spring Boot AI Stock Agent")
				.description("Ask a question about Borsa Istanbul stocks in plain language and get an answer with the delayed quotes and price histories it is based on. Market data comes from the stock MCP server. This is not investment advice.")
				.version("0.0.1"));
	}

}
