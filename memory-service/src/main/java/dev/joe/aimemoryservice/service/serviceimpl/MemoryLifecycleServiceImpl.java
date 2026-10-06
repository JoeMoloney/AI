package dev.joe.aimemoryservice.service.serviceimpl;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.MemoryEmbeddingTextBuilder;
import dev.joe.aimemoryservice.service.MemoryLifecycleService;

/** Enforces active-only updates and one-way memory lifecycle transitions. */
@Service 
public class MemoryLifecycleServiceImpl implements MemoryLifecycleService {

    private final MemoryRepository memoryRepository;
    private final EmbeddingService embeddingService;

    public MemoryLifecycleServiceImpl(MemoryRepository memoryRepository, EmbeddingService embeddingService) {
        this.memoryRepository = memoryRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public MemoryResponse updateMemory(long id, UpdateMemoryRequest request) {
        validateUpdateRequest(request);

        Memory existing = requireMemory(id);
        requireActive(existing);

        MemoryType memoryType = request.memoryType() == null
                ? existing.memoryType()
                : request.memoryType();

        String title = mergeRequiredText(
                request.title(),
                existing.title(),
                "Memory title"
        );

        String content = mergeRequiredText(
                request.content(),
                existing.content(),
                "Memory content"
        );

        Confidence confidence = request.confidence() == null
                ? existing.confidence()
                : request.confidence();

        String evidence = request.evidence() == null
                ? existing.evidence()
                : normalizeOptionalText(request.evidence());

        boolean embeddingChanged =
                !Objects.equals(title, existing.title())
                || !Objects.equals(content, existing.content())
                || !Objects.equals(evidence, existing.evidence());

        float[] replacementEmbedding = null;

        if (embeddingChanged) {
            String embeddingText =
                    MemoryEmbeddingTextBuilder.build(
                            title,
                            content,
                            evidence
                    );

            replacementEmbedding = embeddingService.embedDocument(embeddingText);
        }

        return memoryRepository.updateActive(
                        id,
                        memoryType,
                        title,
                        content,
                        confidence,
                        evidence,
                        replacementEmbedding
                )
                .map(MemoryLifecycleServiceImpl::toResponse)
                .orElseThrow(() ->
                        new InvalidMemoryRequestException("Memory " + id + " is no longer active")
                );
    }

    @Override
    public MemoryResponse invalidateMemory(long id) {
        return transitionMemory(id, MemoryStatus.INVALIDATED);
    }

    @Override
    public MemoryResponse archiveMemory(long id) {
        return transitionMemory(id, MemoryStatus.ARCHIVED);
    }

    @Override
    @Transactional
    public MemoryResponse supersedeMemory(long oldMemoryId, long replacementMemoryId) {
        if (oldMemoryId == replacementMemoryId) {
            throw new InvalidMemoryRequestException(
                    "A memory cannot supersede itself"
            );
        }

        Memory oldMemory = requireMemory(oldMemoryId);
        Memory replacement = requireMemory(replacementMemoryId);

        requireActive(oldMemory);
        requireActive(replacement);

        if (!Objects.equals(oldMemory.projectId(), replacement.projectId()))
            throw new InvalidMemoryRequestException("Superseding memories must belong to the same project");

        if (oldMemory.scope() != replacement.scope())
            throw new InvalidMemoryRequestException("Superseding memories must have the same scope");

        List<Memory> updated = memoryRepository.supersede(oldMemoryId, replacementMemoryId);

        if (updated.size() != 2)
            throw new InvalidMemoryRequestException("Both memories must exist to complete supersession");

        return updated.stream()
                .filter(memory -> memory.id() == oldMemoryId)
                .findFirst()
                .map(MemoryLifecycleServiceImpl::toResponse)
                .orElseThrow(() ->
                        new InvalidMemoryRequestException("Supersession did not update memory " + oldMemoryId)
                );
    }

    private MemoryResponse transitionMemory(long id, MemoryStatus status) {
        Memory existing = requireMemory(id);
        requireActive(existing);

        return memoryRepository.transitionActiveStatus(id, status, null)
            .map(MemoryLifecycleServiceImpl::toResponse)
            .orElseThrow(() ->
                    new InvalidMemoryRequestException("Memory " + id + " is no longer active")
            );
    }

    private Memory requireMemory(long id) {
        return memoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Memory", id)
                );
    }

    private static void requireActive(Memory memory) {
        if (memory.status() != MemoryStatus.ACTIVE)
            throw new InvalidMemoryRequestException("Memory " + memory.id() + " is not active");
    }

    private static void validateUpdateRequest(
            UpdateMemoryRequest request) {
        if (request == null)
            throw new InvalidMemoryRequestException("Update request must not be null");

        if (request.memoryType() == null
                && request.title() == null
                && request.content() == null
                && request.confidence() == null
                && request.evidence() == null) {
            throw new InvalidMemoryRequestException("At least one update field must be provided");
        }
    }

    private static String mergeRequiredText(String requested, String existing, String fieldName) {
        if (requested == null)
            return existing;

        if (requested.isBlank())
            throw new InvalidMemoryRequestException(fieldName + " must not be blank");

        return requested.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value.isBlank())
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
