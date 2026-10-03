package dev.joe.aimemoryservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.joe.aimemoryservice.client.OllamaClient;
import dev.joe.aimemoryservice.config.OllamaProperties;
import dev.joe.aimemoryservice.exceptions.OllamaClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaClientTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requestsAndReturnsEmbedding() throws Exception {
        startServer(200, embeddingResponse(768));

        float[] embedding = createClient().embed("search_query: memory");

        assertEquals(768, embedding.length);
        assertTrue(requestBody.get().contains(
                "\"model\":\"nomic-embed-text\""
        ));
        assertTrue(requestBody.get().contains(
                "\"input\":\"search_query: memory\""
        ));
    }

    @Test
    void rejectsUnexpectedEmbeddingDimensions() throws Exception {
        startServer(200, embeddingResponse(3));

        OllamaClientException exception = assertThrows(
                OllamaClientException.class,
                () -> createClient().embed("search_query: memory")
        );

        assertTrue(exception.getMessage().contains(
                "Expected an embedding with 768 dimensions"
        ));
    }

    @Test
    void translatesHttpErrors() throws Exception {
        startServer(500, """
                {"error":"embedding failed"}
                """);

        OllamaClientException exception = assertThrows(
                OllamaClientException.class,
                () -> createClient().embed("search_query: memory")
        );

        assertTrue(exception.getMessage().contains("HTTP status 500"));
    }

    private OllamaClient createClient() {
        OllamaProperties properties = new OllamaProperties(
                URI.create(
                        "http://127.0.0.1:" + server.getAddress().getPort()
                ),
                "nomic-embed-text",
                Duration.ofSeconds(5)
        );

        return new OllamaClient(RestClient.builder(), properties);
    }

    private void startServer(int status, String responseBody)
            throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/api/embed", exchange -> {
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));

            sendResponse(exchange, status, responseBody);
        });

        server.start();
    }

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String embeddingResponse(int dimensions) {
        String values = IntStream.range(0, dimensions)
                .mapToObj(index -> "0.1")
                .collect(Collectors.joining(","));

        return "{\"embeddings\":[[" + values + "]]}";
    }
}