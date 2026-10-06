package dev.joe.aimemoryservice.service.serviceimpl;

import dev.joe.aimemoryservice.config.MemoryProperties;
import dev.joe.aimemoryservice.dto.DuplicateMemoryCandidate;
import dev.joe.aimemoryservice.exceptions.DuplicateMemoryException;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.service.MemoryDeduplicationService;
import org.springframework.stereotype.Service;

@Service
public class MemoryDeduplicationServiceImpl implements MemoryDeduplicationService {
    private final MemoryRepository memoryRepository;
    private final MemoryProperties properties;

    public MemoryDeduplicationServiceImpl(
        MemoryRepository memoryRepository,
        MemoryProperties properties
    ) {
        this.memoryRepository = memoryRepository;
        this.properties = properties;
    }

    @Override
    public void rejectLikelyDuplicates(float[] embedding, Long projectId, MemoryScope scope) {
        MemoryProperties.Deduplication settings = properties.deduplication();
        if (!settings.enabled()) {
            return;
        }

        var candidates = memoryRepository.findDuplicateCandidates(
            embedding,
            projectId,
            scope,
            settings.similarityThreshold(),
            settings.candidateLimit()
        ).stream().map(scored -> new DuplicateMemoryCandidate(
            scored.memory().id(),
            scored.memory().title(),
            scored.similarity()
        )).toList();

        if (!candidates.isEmpty()) {
            throw new DuplicateMemoryException(candidates);
        }
    }
}
