package dev.joe.aimemoryservice.service.serviceimpl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.joe.aimemoryservice.domain.Project;
import dev.joe.aimemoryservice.dto.CreateProjectRequest;
import dev.joe.aimemoryservice.dto.ProjectResponse;
import dev.joe.aimemoryservice.exceptions.ProjectAlreadyExistsException;
import dev.joe.aimemoryservice.repository.ProjectRepository;
import dev.joe.aimemoryservice.service.ProjectService;

@Service 
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional 
    public ProjectResponse createProject(CreateProjectRequest request) {
        String name = request.name().trim();
        String description = normaliseDescription(request.description());

        if(projectRepository.existsByName(name)) {
            throw new ProjectAlreadyExistsException(name);
        }

        try {
            Project project = projectRepository.create(name, description);
            return toResponse(project);
        } catch (DuplicateKeyException exception) {
            throw new ProjectAlreadyExistsException(name);
        }
    }

    @Override
    @Transactional(readOnly = true) 
    public List<ProjectResponse> getProjects() {
        return projectRepository.findAll()
            .stream()
            .map(ProjectServiceImpl::toResponse)
            .toList();
    }

    private static String normaliseDescription(String description) {
        if(description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
            project.id(),
            project.name(),
            project.description(),
            project.createdAt(),
            project.updatedAt()
        );
    }
    
}
