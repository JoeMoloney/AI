package dev.joe.aimemoryservice.dto;

import java.time.OffsetDateTime;

/**
 * External project representation.
 *
 * @param id project identifier
 * @param name unique project name
 * @param description optional description
 * @param createdAt creation time
 * @param updatedAt last modification time
 */
public record ProjectResponse(
    long id, 
    String name, 
    String description,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    
}
