package dev.joe.aimemoryservice.domain;

import java.time.OffsetDateTime;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;

/**
 * Persistence-facing memory projection.
 *
 * <p>The embedding is intentionally excluded so it cannot accidentally leak
 * into transport responses.</p>
 */
public record Memory(
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
