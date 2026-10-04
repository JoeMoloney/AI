package dev.joe.aimemoryservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.service.MemorySearchService;
import dev.joe.aimemoryservice.service.MemoryService;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/memories")
public class MemoryController {
    private final MemoryService memoryService;
    private final MemorySearchService memorySearchService;

    public MemoryController(MemoryService memoryService, MemorySearchService memorySearchService) {
        this.memoryService = memoryService;
        this.memorySearchService = memorySearchService;
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
}
