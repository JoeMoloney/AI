package dev.joe.aimemoryservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "memory")
public record MemoryProperties(
    @NotNull @Valid Deduplication deduplication
) {
    public record Deduplication(
        boolean enabled,
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @Min(1) @Max(20) int candidateLimit
    ) {
    }
}
