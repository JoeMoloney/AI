package dev.joe.aimemoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiMemoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiMemoryServiceApplication.class, args);
	}

}
