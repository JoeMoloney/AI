package dev.joe.aimemoryservice.domain;

import java.time.OffsetDateTime;

/** Project namespace used to partition non-global memories. */
public record Project(
    long id,
    String name,
    String description,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
