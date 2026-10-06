package dev.joe.aimemoryservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Hybrid memory-search request.
 *
 * @param query natural-language query
 * @param projectId project to search, or null for global-only search
 * @param includeGlobal whether global memories are visible; null defaults true
 * @param limit maximum results from 1 to 20; null defaults to 5
 */
public record SearchMemoryRequest(
    @NotBlank @Size(max = 2000) String query,
    @Positive Long projectId,
    Boolean includeGlobal,
    @Min(1) @Max(20) Integer limit
) {}
