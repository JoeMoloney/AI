package dev.joe.aimemoryservice.dto;

import java.time.OffsetDateTime;

public record ProjectResponse(
    long id, 
    String name, 
    String description,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    
}
