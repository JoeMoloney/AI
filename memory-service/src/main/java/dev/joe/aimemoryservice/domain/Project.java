package dev.joe.aimemoryservice.domain;

import java.time.OffsetDateTime;

public record Project(
    long id,
    String name,
    String description,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}