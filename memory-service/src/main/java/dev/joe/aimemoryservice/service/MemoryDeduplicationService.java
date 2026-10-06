package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.domain.enums.MemoryScope;

/** Detects likely duplicate knowledge before a new memory is inserted. */
public interface MemoryDeduplicationService {
    /**
     * Rejects an embedding when sufficiently similar active memories exist in
     * the same project and exact scope.
     *
     * @param embedding candidate document embedding
     * @param projectId project identifier, or null for global scope
     * @param scope candidate scope
     */
    void rejectLikelyDuplicates(float[] embedding, Long projectId, MemoryScope scope);
}
