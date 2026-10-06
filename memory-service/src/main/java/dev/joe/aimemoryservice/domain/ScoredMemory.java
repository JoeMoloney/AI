package dev.joe.aimemoryservice.domain;

/** A memory paired with cosine similarity to a query or candidate. */
public record ScoredMemory(
    Memory memory,
    double similarity
) {}
