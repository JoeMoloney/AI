package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.domain.enums.MemoryScope;

public interface MemoryDeduplicationService {
    void rejectLikelyDuplicates(float[] embedding, Long projectId, MemoryScope scope);
}
