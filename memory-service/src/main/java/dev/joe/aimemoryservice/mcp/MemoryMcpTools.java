package dev.joe.aimemoryservice.mcp;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.service.MemoryLifecycleService;
import dev.joe.aimemoryservice.service.MemorySearchService;
import dev.joe.aimemoryservice.service.MemoryService;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Small MCP tool surface for AI clients.
 *
 * <p>Methods only translate tool arguments into existing DTOs and delegate to
 * the same services used by REST; business rules must not be duplicated here.</p>
 */
@Component
public class MemoryMcpTools {
    private final MemoryService memoryService;
    private final MemorySearchService searchService;
    private final MemoryLifecycleService lifecycleService;

    public MemoryMcpTools(
        MemoryService memoryService,
        MemorySearchService searchService,
        MemoryLifecycleService lifecycleService
    ) {
        this.memoryService = memoryService;
        this.searchService = searchService;
        this.lifecycleService = lifecycleService;
    }

    @Tool(
        name = "memory_search",
        description = "Search active memories using combined semantic and full-text relevance"
    )
    public List<MemorySearchResult> search(
        @ToolParam(description = "Natural-language search query") String query,
        @ToolParam(description = "Project ID; omit for global-only search", required = false)
        @Nullable Long projectId,
        @ToolParam(description = "Include global memories; defaults to true", required = false)
        @Nullable Boolean includeGlobal,
        @ToolParam(description = "Maximum results from 1 to 20; defaults to 5", required = false)
        @Nullable Integer limit
    ) {
        return searchService.searchMemories(
            new SearchMemoryRequest(query, projectId, includeGlobal, limit)
        );
    }

    @Tool(name = "memory_store", description = "Store a durable memory after duplicate detection")
    public MemoryResponse store(
        @ToolParam(description = "Project ID; required for PROJECT or SESSION scope", required = false)
        @Nullable Long projectId,
        @ToolParam(description = "Optional source ID", required = false)
        @Nullable Long sourceId,
        @ToolParam(description = "Memory scope: GLOBAL, PROJECT, or SESSION") MemoryScope scope,
        @ToolParam(description = "Memory type") MemoryType memoryType,
        @ToolParam(description = "Short descriptive title") String title,
        @ToolParam(description = "Knowledge to remember") String content,
        @ToolParam(description = "Confidence level; defaults to MEDIUM", required = false)
        @Nullable Confidence confidence,
        @ToolParam(description = "Optional supporting evidence", required = false)
        @Nullable String evidence
    ) {
        return memoryService.createMemory(new StoreMemoryRequest(
            projectId, sourceId, scope, memoryType, title, content, confidence, evidence
        ));
    }

    @Tool(name = "memory_get", description = "Retrieve one memory by ID, including lifecycle status")
    public MemoryResponse get(@ToolParam(description = "Memory ID") long id) {
        return memoryService.getMemory(id);
    }

    @Tool(name = "memory_update", description = "Partially update an active memory")
    public MemoryResponse update(
        @ToolParam(description = "Memory ID") long id,
        @ToolParam(description = "Replacement memory type", required = false)
        @Nullable MemoryType memoryType,
        @ToolParam(description = "Replacement title", required = false)
        @Nullable String title,
        @ToolParam(description = "Replacement content", required = false)
        @Nullable String content,
        @ToolParam(description = "Replacement confidence", required = false)
        @Nullable Confidence confidence,
        @ToolParam(description = "Replacement evidence; blank clears it", required = false)
        @Nullable String evidence
    ) {
        return lifecycleService.updateMemory(
            id,
            new UpdateMemoryRequest(memoryType, title, content, confidence, evidence)
        );
    }

    @Tool(
        name = "memory_supersede",
        description = "Mark an active memory as superseded by another active memory"
    )
    public MemoryResponse supersede(
        @ToolParam(description = "Memory ID to supersede") long oldMemoryId,
        @ToolParam(description = "Active replacement memory ID") long replacementMemoryId
    ) {
        return lifecycleService.supersedeMemory(oldMemoryId, replacementMemoryId);
    }
}
