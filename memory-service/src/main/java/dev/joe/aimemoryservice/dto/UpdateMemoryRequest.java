package dev.joe.aimemoryservice.dto;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import jakarta.validation.constraints.Size;

/**
 * Partial update for an active memory.
 *
 * <p>Null components are unchanged. Blank evidence clears existing evidence;
 * blank title or content is invalid.</p>
 *
 * @param memoryType replacement category
 * @param title replacement title
 * @param content replacement content
 * @param confidence replacement confidence
 * @param evidence replacement evidence, or blank to clear
 */
public record UpdateMemoryRequest(
    MemoryType memoryType,
    @Size(max = 200)
    String title,
    @Size(max = 6000)
    String content,
    Confidence confidence,
    @Size(max = 2000)
    String evidence
) {}
