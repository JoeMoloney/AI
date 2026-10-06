package dev.joe.aimemoryservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.service.MemoryLifecycleService;
import dev.joe.aimemoryservice.service.MemorySearchService;
import dev.joe.aimemoryservice.service.MemoryService;
import jakarta.validation.Valid;

/** REST adapter for creation, retrieval, search, updates, and lifecycle transitions. */
@RestController 
@RequestMapping("/api/memories")
public class MemoryController {
    private final MemoryService memoryService;
    private final MemorySearchService memorySearchService;
    private final MemoryLifecycleService memoryLifecycleService;

    public MemoryController(MemoryService memoryService, MemorySearchService memorySearchService, MemoryLifecycleService memoryLifecycleService) {
        this.memoryService = memoryService;
        this.memorySearchService = memorySearchService;
        this.memoryLifecycleService = memoryLifecycleService;
    }

    @PostMapping
    public ResponseEntity<MemoryResponse> createMemory(@Valid @RequestBody StoreMemoryRequest request) {
        MemoryResponse response = memoryService.createMemory(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping("/{id}")
    public MemoryResponse getMemory(@PathVariable long id) {
        return memoryService.getMemory(id);
    }

    @PostMapping("/search")
    public List<MemorySearchResult> searchMemories(@Valid @RequestBody SearchMemoryRequest request) {
        return memorySearchService.searchMemories(request);
    }

    @PatchMapping("/{id}")
    public MemoryResponse updateMemory(@PathVariable long id, @Valid @RequestBody UpdateMemoryRequest request) {
        return memoryLifecycleService.updateMemory(id, request);
    }

    @PostMapping("/{id}/invalidate")
    public MemoryResponse invalidateMemory(@PathVariable long id) {
        return memoryLifecycleService.invalidateMemory(id);
    }

    @PostMapping("/{id}/archive")
    public MemoryResponse archiveMemory(@PathVariable long id) {
        return memoryLifecycleService.archiveMemory(id);
    }

    @PostMapping("/{id}/supersede/{replacementId}")
    public MemoryResponse supersedeMemory(@PathVariable long id, @PathVariable long replacementId) {
        return memoryLifecycleService.supersedeMemory(id, replacementId);
    }
}
