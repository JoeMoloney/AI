package dev.joe.aimemoryservice.service;

import java.util.List;

import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;

public interface MemorySearchService {
    List<MemorySearchResult> searchMemories(SearchMemoryRequest request);
}
