package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.mcp.MemoryMcpTools;
import dev.joe.aimemoryservice.service.MemoryLifecycleService;
import dev.joe.aimemoryservice.service.MemorySearchService;
import dev.joe.aimemoryservice.service.MemoryService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryMcpToolsTest {
    private final MemoryService memoryService = mock(MemoryService.class);
    private final MemorySearchService searchService = mock(MemorySearchService.class);
    private final MemoryLifecycleService lifecycleService = mock(MemoryLifecycleService.class);
    private final MemoryMcpTools tools = new MemoryMcpTools(
        memoryService, searchService, lifecycleService
    );

    @Test
    void delegatesAllToolsToApplicationServices() {
        tools.search("known issue", 1L, true, 8);
        tools.store(
            1L, null, MemoryScope.PROJECT, MemoryType.FAILURE,
            "Known issue", "Detailed lesson", Confidence.HIGH, "Observed locally"
        );
        tools.get(7L);
        tools.update(7L, null, null, "Updated lesson", Confidence.CONFIRMED, null);
        tools.supersede(7L, 8L);

        verify(searchService).searchMemories(
            new SearchMemoryRequest("known issue", 1L, true, 8)
        );
        verify(memoryService).createMemory(new StoreMemoryRequest(
            1L, null, MemoryScope.PROJECT, MemoryType.FAILURE,
            "Known issue", "Detailed lesson", Confidence.HIGH, "Observed locally"
        ));
        verify(memoryService).getMemory(7L);
        verify(lifecycleService).updateMemory(
            7L,
            new UpdateMemoryRequest(null, null, "Updated lesson", Confidence.CONFIRMED, null)
        );
        verify(lifecycleService).supersedeMemory(7L, 8L);
    }
}
