package dev.joe.aimemoryservice.dto;

import java.time.OffsetDateTime;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;

/**
 * Complete external representation of a memory, excluding its embedding.
 *
 * @param id memory identifier
 * @param projectId owning project, if any
 * @param sourceId provenance source, if any
 * @param scope visibility scope
 * @param memoryType semantic category
 * @param title short title
 * @param content durable knowledge
 * @param confidence confidence level
 * @param status lifecycle status
 * @param evidence supporting evidence, if any
 * @param supersededBy replacement ID for superseded memories
 * @param createdAt creation time
 * @param updatedAt last modification time
 */
public record MemoryResponse(
    long id,
    Long projectId,
    Long sourceId,
    MemoryScope scope,
    MemoryType memoryType,
    String title,
    String content,
    Confidence confidence,
    MemoryStatus status,
    String evidence,
    Long supersededBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
