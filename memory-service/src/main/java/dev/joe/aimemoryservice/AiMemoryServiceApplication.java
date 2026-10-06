package dev.joe.aimemoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entry point for the AI memory service.
 *
 * <p>{@link ConfigurationPropertiesScan} discovers the validated Ollama and
 * memory settings used by the service.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiMemoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiMemoryServiceApplication.class, args);
	}

}
