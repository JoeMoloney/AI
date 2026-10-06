package dev.joe.aimemoryservice.dto;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryType;

/**
 * Search projection for an active memory.
 *
 * @param id memory identifier
 * @param projectId owning project, if any
 * @param sourceId provenance source, if any
 * @param scope visibility scope
 * @param memoryType semantic category
 * @param title short title
 * @param content durable knowledge
 * @param confidence confidence level
 * @param evidence supporting evidence, if any
 * @param similarity cosine similarity to the query; not the final RRF score
 */
public record MemorySearchResult(
    long id,
    Long projectId,
    Long sourceId,
    MemoryScope scope,
    MemoryType memoryType,
    String title,
    String content,
    Confidence confidence,
    String evidence,
    double similarity
) {}
