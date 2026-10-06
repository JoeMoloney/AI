package dev.joe.aimemoryservice.dto;

/**
 * Existing memory included in a duplicate conflict.
 *
 * @param id existing memory identifier
 * @param title existing memory title
 * @param similarity cosine similarity to the proposed memory
 */
public record DuplicateMemoryCandidate(
    long id,
    String title,
    double similarity
) {
}
