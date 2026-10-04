package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;

public interface MemoryService {
    MemoryResponse createMemory(StoreMemoryRequest request);
    MemoryResponse getMemory(long id);
}
