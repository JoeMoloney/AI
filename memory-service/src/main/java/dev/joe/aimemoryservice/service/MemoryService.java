package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;

/** Creates and retrieves memories independent of their transport adapter. */
public interface MemoryService {
    /**
     * Validates, embeds, duplicate-checks, and stores a new active memory.
     *
     * @param request memory attributes to store
     * @return the stored memory without its embedding
     */
    MemoryResponse createMemory(StoreMemoryRequest request);

    /**
     * Retrieves a memory in any lifecycle state.
     *
     * @param id memory identifier
     * @return the requested memory
     */
    MemoryResponse getMemory(long id);
}
