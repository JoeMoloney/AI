package dev.joe.aimemoryservice.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated 
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(@NotNull URI baseUrl, @NotBlank String embeddingModel, @NotNull Duration timeout) {

}
