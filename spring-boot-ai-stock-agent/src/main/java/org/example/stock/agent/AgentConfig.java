package org.example.stock.agent;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
