package dev.joe.aimemoryservice.dto;

public record DuplicateMemoryCandidate(
    long id,
    String title,
    double similarity
) {
}
