package dev.joe.aimemoryservice.client;

import java.net.http.HttpClient;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import dev.joe.aimemoryservice.config.OllamaProperties;
import dev.joe.aimemoryservice.exceptions.OllamaClientException;

@Component 
public class OllamaClient {
    private static final int EXPECTED_EMBEDDING_DIMENSIONS = 768;

    private final RestClient restClient;
    private final String embeddingModel;

    public OllamaClient(RestClient.Builder restClientBuilder, OllamaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .build();

        this.embeddingModel = properties.embeddingModel();
    }

    public float[] embed(String text) {
        if(text == null || text.isBlank())
            throw new IllegalArgumentException("Embedding text must not be blank");

        EmbedRequest request = new EmbedRequest(embeddingModel, text);

        try {
            EmbedResponse response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbedResponse.class);

            return extractEmbedding(response);
        } catch (RestClientResponseException exception) {
            throw new OllamaClientException("Ollama returned HTTP status "+ exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            throw new OllamaClientException("Unable to connect to Ollama", exception);
        } catch (RestClientException exception) {
            throw new OllamaClientException("Invalid response received from Ollama", exception);
        }
    }

    private float[] extractEmbedding(EmbedResponse response) {
        if(response == null || response.embeddings() == null || response.embeddings().size() != 1 || response.embeddings().getFirst() == null)
                throw new OllamaClientException("Ollama returned an invalid embedding response");

         float[] embedding = response.embeddings().getFirst();

         if(embedding.length != EXPECTED_EMBEDDING_DIMENSIONS)
            throw new OllamaClientException("Expected an embedding with "+EXPECTED_EMBEDDING_DIMENSIONS + " dimensions, but received: "+embedding.length);

         return embedding;
    }

    private record EmbedRequest(String model, String input) {}
    private record EmbedResponse(List<float[]> embeddings) {}
}
