package dev.joe.aimemoryservice.domain;

public record ScoredMemory(
    Memory memory,
    double similarity
) {}