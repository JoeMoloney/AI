package dev.joe.aimemoryservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Memory behavior settings.
 *
 * @param deduplication duplicate-detection policy
 */
@Validated
@ConfigurationProperties(prefix = "memory")
public record MemoryProperties(
    @NotNull @Valid Deduplication deduplication
) {
    /**
     * Duplicate-detection settings applied before memory insertion.
     *
     * @param enabled whether creation performs a similarity check
     * @param similarityThreshold inclusive cosine-similarity threshold
     * @param candidateLimit maximum candidates returned in a conflict
     */
    public record Deduplication(
        boolean enabled,
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @Min(1) @Max(20) int candidateLimit
    ) {
    }
}
