package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.CreateProjectRequest;
import dev.joe.aimemoryservice.dto.ProjectResponse;

import java.util.List;

/** Manages projects used to partition non-global memories. */
public interface ProjectService {
    /**
     * Creates a uniquely named project.
     *
     * @param request project name and optional description
     * @return created project
     */
    ProjectResponse createProject(CreateProjectRequest request);

    /** @return all projects ordered by repository policy */
    List<ProjectResponse> getProjects();
}
