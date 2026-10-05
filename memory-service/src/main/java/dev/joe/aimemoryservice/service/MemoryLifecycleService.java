package dev.joe.aimemoryservice.service;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;

public interface MemoryLifecycleService {
    MemoryResponse updateMemory(long id, UpdateMemoryRequest request);
    MemoryResponse invalidateMemory(long id);
    MemoryResponse archiveMemory(long Id);
    MemoryResponse supersedeMemory(long oldMemoryId, long replacementMemoryId);
}
