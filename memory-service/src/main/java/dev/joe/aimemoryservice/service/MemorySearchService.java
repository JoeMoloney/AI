package dev.joe.aimemoryservice.service;

import java.util.List;

import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;

/** Provides project-aware hybrid retrieval of active memories. */
public interface MemorySearchService {
    /**
     * Searches vector and full-text candidates and returns their fused order.
     *
     * @param request query, visibility, and result-limit options
     * @return active memories ordered by hybrid relevance
     */
    List<MemorySearchResult> searchMemories(SearchMemoryRequest request);
}
