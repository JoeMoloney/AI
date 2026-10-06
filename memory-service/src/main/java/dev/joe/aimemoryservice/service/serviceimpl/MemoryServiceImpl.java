package dev.joe.aimemoryservice.service.serviceimpl;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.repository.ProjectRepository;
import dev.joe.aimemoryservice.repository.SourceRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.MemoryEmbeddingTextBuilder;
import dev.joe.aimemoryservice.service.MemoryDeduplicationService;
import dev.joe.aimemoryservice.service.MemoryService;
import org.springframework.stereotype.Service;

/** Implements validated memory creation, duplicate checking, and retrieval. */
@Service
public class MemoryServiceImpl implements MemoryService {

    private final MemoryRepository memoryRepository;
    private final ProjectRepository projectRepository;
    private final SourceRepository sourceRepository;
    private final EmbeddingService embeddingService;
    private final MemoryDeduplicationService deduplicationService;

    public MemoryServiceImpl(
            MemoryRepository memoryRepository,
            ProjectRepository projectRepository,
            SourceRepository sourceRepository,
            EmbeddingService embeddingService,
            MemoryDeduplicationService deduplicationService
    ) {
        this.memoryRepository = memoryRepository;
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.embeddingService = embeddingService;
        this.deduplicationService = deduplicationService;
    }

    @Override
    public MemoryResponse createMemory(StoreMemoryRequest request) {
        validateRequiredValues(request);
        validateScope(request.scope(), request.projectId());
        validateProject(request.projectId());
        validateSource(request.sourceId(), request.projectId());

        String title = request.title().trim();
        String content = request.content().trim();
        String evidence = normalizeOptionalText(request.evidence());

        Confidence confidence = request.confidence() == null
                ? Confidence.MEDIUM
                : request.confidence();

        String embeddingText = MemoryEmbeddingTextBuilder.build(
                title,
                content,
                evidence
        );

        float[] embedding =
                embeddingService.embedDocument(embeddingText);

        deduplicationService.rejectLikelyDuplicates(
                embedding,
                request.projectId(),
                request.scope()
        );

        Memory memory = memoryRepository.insert(
                request.projectId(),
                request.sourceId(),
                request.scope(),
                request.memoryType(),
                title,
                content,
                confidence,
                MemoryStatus.ACTIVE,
                evidence,
                embedding
        );

        return toResponse(memory);
    }

    @Override 
    public MemoryResponse getMemory(long id) {
        Memory memory = memoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Memory", id));
        return toResponse(memory);
    }

    private void validateRequiredValues(StoreMemoryRequest request) {
        if (request == null)
            throw new InvalidMemoryRequestException("Memory request must not be null");

        if (request.scope() == null)
            throw new InvalidMemoryRequestException("Memory scope must be provided");

        if (request.memoryType() == null)
            throw new InvalidMemoryRequestException("Memory type must be provided");

        if (request.title() == null || request.title().isBlank())
            throw new InvalidMemoryRequestException("Memory title must not be blank");

        if (request.content() == null || request.content().isBlank())
            throw new InvalidMemoryRequestException("Memory content must not be blank");
    }

    private void validateScope(
            MemoryScope scope,
            Long projectId
    ) {
        if (scope == MemoryScope.GLOBAL && projectId != null)
            throw new InvalidMemoryRequestException("Global memories must not have a project ID");

        if ((scope == MemoryScope.PROJECT || scope == MemoryScope.SESSION) && projectId == null)
            throw new InvalidMemoryRequestException(scope + " memories require a project ID");
    }

    private void validateProject(Long projectId) {
        if (projectId == null)
            return;

        if (projectRepository.findById(projectId).isEmpty())
            throw new ResourceNotFoundException("Project", projectId);
    }

    private void validateSource(Long sourceId, Long projectId) {
        if (sourceId == null)
            return;

        if (!sourceRepository.existsById(sourceId))
            throw new ResourceNotFoundException("Source", sourceId);

        if (!sourceRepository.isCompatibleWithProject(sourceId, projectId))
            throw new InvalidMemoryRequestException("Source " + sourceId + " is not compatible with the memory project");
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank())
            return null;

        return value.trim();
    }

    private static MemoryResponse toResponse(Memory memory) {
        return new MemoryResponse(
                memory.id(),
                memory.projectId(),
                memory.sourceId(),
                memory.scope(),
                memory.memoryType(),
                memory.title(),
                memory.content(),
                memory.confidence(),
                memory.status(),
                memory.evidence(),
                memory.supersededBy(),
                memory.createdAt(),
                memory.updatedAt()
        );
    }
}
