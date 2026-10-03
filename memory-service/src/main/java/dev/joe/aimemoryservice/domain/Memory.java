package dev.joe.aimemoryservice.domain;

import java.lang.management.MemoryType;
import java.time.OffsetDateTime;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;

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