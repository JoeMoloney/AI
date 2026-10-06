package dev.joe.aimemoryservice.service.serviceimpl;

import org.springframework.stereotype.Service;

import dev.joe.aimemoryservice.client.OllamaClient;
import dev.joe.aimemoryservice.service.EmbeddingService;

/** Applies nomic document/query prefixes before delegating to Ollama. */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String DOCUMENT_PREFIX = "search_document: ";
    private static final String QUERY_PREFIX = "search_query: ";

    private final OllamaClient ollamaClient;

    public EmbeddingServiceImpl(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public float[] embedDocument(String text) {
        return embed(DOCUMENT_PREFIX, text);
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(QUERY_PREFIX, text);
    }

    private float[] embed(String prefix, String text) {
        if(text == null || text.isBlank())
            throw new IllegalArgumentException("Text to embed must not be blank");

        return ollamaClient.embed(prefix + text.strip());
    }
    
}
