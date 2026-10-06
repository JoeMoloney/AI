package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.controller.MemoryController;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.exceptions.GlobalExceptionHandler;
import dev.joe.aimemoryservice.exceptions.DuplicateMemoryException;
import dev.joe.aimemoryservice.dto.DuplicateMemoryCandidate;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.OllamaClientException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.service.MemoryLifecycleService;
import dev.joe.aimemoryservice.service.MemorySearchService;
import dev.joe.aimemoryservice.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryController.class)
@Import(GlobalExceptionHandler.class)
class MemoryControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private MemoryService memoryService;
        @MockitoBean
        private MemorySearchService memorySearchService;
        @MockitoBean
        private MemoryLifecycleService memoryLifecycleService;

        @Test
        void createsMemory() throws Exception {
                when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                                .thenReturn(memoryResponse());

                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.scope").value("PROJECT"))
                                .andExpect(jsonPath("$.memoryType").value("FAILURE"))
                                .andExpect(jsonPath("$.confidence").value("HIGH"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void rejectsBlankTitle() throws Exception {
                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "projectId": 1,
                                                  "scope": "PROJECT",
                                                  "memoryType": "FAILURE",
                                                  "title": "   ",
                                                  "content": "Memory content"
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title")
                                                .value("Request validation failed"));
        }

        @Test
        void reportsMissingProject() throws Exception {
                when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                                .thenThrow(new ResourceNotFoundException("Project", 99L));

                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.title")
                                                .value("Resource not found"))
                                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void reportsLikelyDuplicate() throws Exception {
                when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                                .thenThrow(new DuplicateMemoryException(List.of(
                                                new DuplicateMemoryCandidate(7L, "Existing lesson", 0.98)
                                )));

                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.title").value("Likely duplicate memory"))
                                .andExpect(jsonPath("$.candidates[0].id").value(7))
                                .andExpect(jsonPath("$.candidates[0].similarity").value(0.98));
        }

        @Test
        void reportsInvalidMemoryRequest() throws Exception {
                when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                                .thenThrow(new InvalidMemoryRequestException(
                                                "Global memories must not have a project ID"));

                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title")
                                                .value("Invalid memory request"))
                                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void reportsOllamaFailure() throws Exception {
                when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                                .thenThrow(new OllamaClientException(
                                                "Unable to connect to Ollama"));

                mockMvc.perform(post("/api/memories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()))
                                .andExpect(status().isBadGateway())
                                .andExpect(jsonPath("$.title")
                                                .value("Embedding service failure"))
                                .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        void returnsMemoryById() throws Exception {
                when(memoryService.getMemory(1L))
                                .thenReturn(memoryResponse());

                mockMvc.perform(get("/api/memories/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.title")
                                                .value("Large context caused model slowdown"))
                                .andExpect(jsonPath("$.scope").value("PROJECT"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void reportsMissingMemory() throws Exception {
                when(memoryService.getMemory(99L))
                                .thenThrow(new ResourceNotFoundException(
                                                "Memory",
                                                99L));

                mockMvc.perform(get("/api/memories/99"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.title")
                                                .value("Resource not found"))
                                .andExpect(jsonPath("$.detail")
                                                .value("Memory with ID 99 was not found"))
                                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void searchesMemories() throws Exception {
                MemorySearchResult result = new MemorySearchResult(
                                1L,
                                1L,
                                null,
                                MemoryScope.PROJECT,
                                MemoryType.FAILURE,
                                "Large context caused model slowdown",
                                "Large contexts increased CPU usage.",
                                Confidence.HIGH,
                                "Smaller contexts remained responsive.",
                                0.7594);

                when(memorySearchService.searchMemories(
                                any(SearchMemoryRequest.class))).thenReturn(List.of(result));

                mockMvc.perform(post("/api/memories/search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "Why did long sessions become slow?",
                                                  "projectId": 1,
                                                  "includeGlobal": true,
                                                  "limit": 5
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(1))
                                .andExpect(jsonPath("$[0].scope").value("PROJECT"))
                                .andExpect(jsonPath("$[0].memoryType").value("FAILURE"))
                                .andExpect(jsonPath("$[0].title")
                                                .value("Large context caused model slowdown"))
                                .andExpect(jsonPath("$[0].similarity").value(0.7594));
        }

        @Test
        void rejectsBlankSearchQuery() throws Exception {
                mockMvc.perform(post("/api/memories/search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "   ",
                                                  "projectId": 1
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title")
                                                .value("Request validation failed"))
                                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void reportsMissingSearchProject() throws Exception {
                when(memorySearchService.searchMemories(
                                any(SearchMemoryRequest.class))).thenThrow(new ResourceNotFoundException(
                                                "Project",
                                                99L));

                mockMvc.perform(post("/api/memories/search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "Search query",
                                                  "projectId": 99,
                                                  "includeGlobal": true
                                                }
                                                """))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.title")
                                                .value("Resource not found"))
                                .andExpect(jsonPath("$.detail")
                                                .value("Project with ID 99 was not found"))
                                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void updatesMemory() throws Exception {
                when(memoryLifecycleService.updateMemory(
                                eq(1L),
                                any(UpdateMemoryRequest.class))).thenReturn(lifecycleResponse(
                                                MemoryStatus.ACTIVE,
                                                null));

                mockMvc.perform(patch("/api/memories/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "content": "Updated memory content",
                                                  "confidence": "CONFIRMED"
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void invalidatesMemory() throws Exception {
                when(memoryLifecycleService.invalidateMemory(1L))
                                .thenReturn(lifecycleResponse(
                                                MemoryStatus.INVALIDATED,
                                                null));

                mockMvc.perform(post("/api/memories/1/invalidate"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.status")
                                                .value("INVALIDATED"));
        }

        @Test
        void archivesMemory() throws Exception {
                when(memoryLifecycleService.archiveMemory(1L))
                                .thenReturn(lifecycleResponse(
                                                MemoryStatus.ARCHIVED,
                                                null));

                mockMvc.perform(post("/api/memories/1/archive"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.status")
                                                .value("ARCHIVED"));
        }

        @Test
        void supersedesMemory() throws Exception {
                when(memoryLifecycleService.supersedeMemory(1L, 2L))
                                .thenReturn(lifecycleResponse(
                                                MemoryStatus.SUPERSEDED,
                                                2L));

                mockMvc.perform(post(
                                "/api/memories/1/supersede/2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.status")
                                                .value("SUPERSEDED"))
                                .andExpect(jsonPath("$.supersededBy")
                                                .value(2));
        }

        private static MemoryResponse lifecycleResponse(
                        MemoryStatus status,
                        Long supersededBy) {
                OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-05T10:00:00Z");

                return new MemoryResponse(
                                1L,
                                1L,
                                null,
                                MemoryScope.PROJECT,
                                MemoryType.FACT,
                                "Memory title",
                                "Updated memory content",
                                Confidence.CONFIRMED,
                                status,
                                null,
                                supersededBy,
                                timestamp,
                                timestamp);
        }

        private static String validRequest() {
                return """
                                {
                                  "projectId": 1,
                                  "scope": "PROJECT",
                                  "memoryType": "FAILURE",
                                  "title": "Large context caused model slowdown",
                                  "content": "Large contexts increased CPU usage.",
                                  "confidence": "HIGH",
                                  "evidence": "Smaller contexts remained responsive."
                                }
                                """;
        }

        private static MemoryResponse memoryResponse() {
                OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-04T10:00:00Z");

                return new MemoryResponse(
                                1L,
                                1L,
                                null,
                                MemoryScope.PROJECT,
                                MemoryType.FAILURE,
                                "Large context caused model slowdown",
                                "Large contexts increased CPU usage.",
                                Confidence.HIGH,
                                MemoryStatus.ACTIVE,
                                "Smaller contexts remained responsive.",
                                null,
                                timestamp,
                                timestamp);
        }
}
