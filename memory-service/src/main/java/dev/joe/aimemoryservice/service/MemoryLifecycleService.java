package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;

/** Applies updates and one-way lifecycle transitions to active memories. */
public interface MemoryLifecycleService {
    /**
     * Partially updates an active memory, regenerating its embedding only when
     * title, content, or evidence changes.
     *
     * @param id memory identifier
     * @param request fields to change; null fields remain unchanged
     * @return updated memory
     */
    MemoryResponse updateMemory(long id, UpdateMemoryRequest request);

    /**
     * Marks active knowledge as incorrect.
     *
     * @param id memory identifier
     * @return invalidated memory
     */
    MemoryResponse invalidateMemory(long id);

    /**
     * Marks an active memory as no longer relevant.
     *
     * @param id memory identifier
     * @return archived memory
     */
    MemoryResponse archiveMemory(long id);

    /**
     * Atomically replaces one active memory with another compatible memory.
     *
     * @param oldMemoryId memory becoming superseded
     * @param replacementMemoryId active replacement in the same project/scope
     * @return the superseded memory
     */
    MemoryResponse supersedeMemory(long oldMemoryId, long replacementMemoryId);
}
