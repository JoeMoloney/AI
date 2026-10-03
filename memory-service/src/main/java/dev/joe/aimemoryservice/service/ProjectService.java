package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.CreateProjectRequest;
import dev.joe.aimemoryservice.dto.ProjectResponse;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(CreateProjectRequest request);

    List<ProjectResponse> getProjects();
}
