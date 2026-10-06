package dev.joe.aimemoryservice.service.serviceimpl;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.ScoredMemory;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.repository.ProjectRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.MemorySearchService;

@Service 
public class MemorySearchServiceImpl implements MemorySearchService {
    private static final int DEFAULT_LIMIT = 5;

    private final MemoryRepository memoryRepository;
    private final ProjectRepository projectRepository;
    private final EmbeddingService embeddingService;

    public MemorySearchServiceImpl(
        MemoryRepository memoryRepository,
        ProjectRepository projectRepository,
        EmbeddingService embeddingService
    ) {
        this.memoryRepository = memoryRepository;
        this.projectRepository = projectRepository;
        this.embeddingService = embeddingService;
    }

    @Override 
    public List<MemorySearchResult> searchMemories(SearchMemoryRequest request) {
        validateRequest(request);

        String query = request.query().trim();
        boolean includeGlobal = request.includeGlobal() == null || request.includeGlobal();
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        validateSearchScope(request.projectId(), includeGlobal);

        validateProject(request.projectId());

        float[] queryEmbedding = embeddingService.embedQuery(query);

        return memoryRepository.hybridSearch(query, queryEmbedding, request.projectId(), includeGlobal, limit)
            .stream()
            .map(MemorySearchServiceImpl::toResult)
            .toList();
    }

    private static void validateRequest(SearchMemoryRequest request) {
        if(request == null)
            throw new InvalidMemoryRequestException("Search request must not be null");
        if(request.query() == null || request.query().isBlank())
            throw new InvalidMemoryRequestException("Search query must not be blank");
        if(request.limit() != null && (request.limit() < 1 || request.limit() > 20))
            throw new InvalidMemoryRequestException("Search limit must be between 1 and 20");
    }

    private static void validateSearchScope(Long projectId, boolean includeGlobal) {
        if(projectId == null && !includeGlobal)
            throw new InvalidMemoryRequestException("Search must include a project or global memories");
    }

    private void validateProject(Long projectId) {
        if(projectId == null)
            return;
        if(projectRepository.findById(projectId).isEmpty())
            throw new ResourceNotFoundException("Project", projectId);
    }

    private static MemorySearchResult toResult(ScoredMemory scoredMemory) {
        Memory memory = scoredMemory.memory();

        return new MemorySearchResult(
            memory.id(),
            memory.projectId(),
            memory.sourceId(),
            memory.scope(),
            memory.memoryType(),
            memory.title(),
            memory.content(),
            memory.confidence(),
            memory.evidence(),
            scoredMemory.similarity()
        );
    }
}
