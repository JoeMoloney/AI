package dev.joe.aimemoryservice.dto;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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