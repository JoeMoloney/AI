package dev.joe.aimemoryservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.service.MemoryService;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/memories")
public class MemoryController {
    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public ResponseEntity<MemoryResponse> createMemory(@Valid @RequestBody StoreMemoryRequest request) {
        MemoryResponse response = memoryService.createMemory(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
}
