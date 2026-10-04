package dev.joe.aimemoryservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SearchMemoryRequest(
    @NotBlank @Size(max = 2000) String query,
    @Positive Long projectId,
    Boolean includeGlobal,
    @Min(1) @Max(20) Integer limit
) {}