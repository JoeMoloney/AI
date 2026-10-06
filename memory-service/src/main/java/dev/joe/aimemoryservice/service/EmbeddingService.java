package dev.joe.aimemoryservice.service;

/**
 * Generates embeddings with the model-specific semantics required for storage
 * and retrieval.
 *
 * <p>Document and query inputs use different prefixes so callers must choose
 * the method that matches their operation.</p>
 */
public interface EmbeddingService {
    /**
     * Embeds text that will be stored and searched later.
     *
     * @param text canonical memory text
     * @return a 768-dimensional embedding
     */
    float[] embedDocument(String text);

    /**
     * Embeds a user's retrieval query.
     *
     * @param text natural-language query
     * @return a 768-dimensional embedding
     */
    float[] embedQuery(String text);
}
