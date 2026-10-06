package dev.joe.aimemoryservice.dto;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request to create and embed a durable memory.
 *
 * @param projectId required for project/session scope; absent for global scope
 * @param sourceId optional provenance source
 * @param scope visibility scope
 * @param memoryType semantic category
 * @param title short searchable title
 * @param content durable knowledge
 * @param confidence confidence level; null defaults to {@code MEDIUM}
 * @param evidence optional supporting evidence
 */
public record StoreMemoryRequest(
    @Positive Long projectId,
    @Positive Long sourceId,
    @NotNull MemoryScope scope,
    @NotNull MemoryType memoryType,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 6000) String content,
    Confidence confidence,
    @Size(max = 2000) String evidence
) {}
