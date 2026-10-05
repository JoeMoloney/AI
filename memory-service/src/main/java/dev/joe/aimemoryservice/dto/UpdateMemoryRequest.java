package dev.joe.aimemoryservice.dto;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import jakarta.validation.constraints.Size;

public record UpdateMemoryRequest(
    MemoryType memoryType,
    @Size(max = 200)
    String title,
    @Size(max = 6000)
    String content,
    Confidence confidence,
    @Size(max = 2000)
    String evidence
) {}