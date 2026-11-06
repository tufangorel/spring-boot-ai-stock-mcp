package org.example.stock.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootAiStockMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootAiStockMcpServerApplication.class, args);
	}

}
