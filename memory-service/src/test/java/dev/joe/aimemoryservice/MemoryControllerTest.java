package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.controller.MemoryController;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.exceptions.GlobalExceptionHandler;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.OllamaClientException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void reportsInvalidMemoryRequest() throws Exception {
        when(memoryService.createMemory(any(StoreMemoryRequest.class)))
                .thenThrow(new InvalidMemoryRequestException(
                        "Global memories must not have a project ID"
                ));

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
                        "Unable to connect to Ollama"
                ));

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
        OffsetDateTime timestamp =
                OffsetDateTime.parse("2026-10-04T10:00:00Z");

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
                timestamp
        );
    }
}