package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.client.OllamaClient;
import dev.joe.aimemoryservice.config.OllamaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(
        named = "RUN_OLLAMA_TESTS",
        matches = "true"
)
/**
 * Integration tests for {@link OllamaClient}.
 *
 * <p>These tests require a running Ollama instance in the background (default:
 * {@code http://localhost:11434}), otherwise they will fail to connect.
 * They are skipped unless the {@code RUN_OLLAMA_TESTS=true} environment
 * variable is set.
 */
class OllamaClientIntegrationTest {

    @Test
    void generatesEmbeddingUsingRealOllama() {
        String baseUrl = System.getenv().getOrDefault(
                "OLLAMA_BASE_URL",
                "http://localhost:11434"
        );

        String model = System.getenv().getOrDefault(
                "OLLAMA_EMBEDDING_MODEL",
                "nomic-embed-text"
        );

        OllamaProperties properties = new OllamaProperties(
                URI.create(baseUrl),
                model,
                Duration.ofSeconds(60)
        );

        OllamaClient client = new OllamaClient(
                RestClient.builder(),
                properties
        );

        float[] embedding = client.embed(
                "search_query: What did we learn about memory?"
        );

        assertEquals(768, embedding.length);
    }
}