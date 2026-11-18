package org.example.stock.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class SpringBootAiStockAgentApplicationTests {

	@Test
	void mainStartsTheApplication() {
		String[] args = { "--server.port=0" };
		try (var spring = mockStatic(SpringApplication.class)) {
			SpringBootAiStockAgentApplication.main(args);
			spring.verify(() -> SpringApplication.run(SpringBootAiStockAgentApplication.class, args));
		}
	}

}
