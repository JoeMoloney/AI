package dev.joe.aimemoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a project namespace.
 *
 * @param name unique human-readable project name
 * @param description optional project description
 */
public record CreateProjectRequest(
    @NotBlank 
    @Size(max = 100)
    String name,
    @Size(max = 1000)
    String description
) {

}
