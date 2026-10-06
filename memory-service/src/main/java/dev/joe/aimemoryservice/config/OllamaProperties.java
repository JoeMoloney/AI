package dev.joe.aimemoryservice.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Ollama connection settings.
 *
 * @param baseUrl Ollama server base URL
 * @param embeddingModel model passed to {@code /api/embed}
 * @param timeout connection and read timeout
 */
@Validated 
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(@NotNull URI baseUrl, @NotBlank String embeddingModel, @NotNull Duration timeout) {

}
