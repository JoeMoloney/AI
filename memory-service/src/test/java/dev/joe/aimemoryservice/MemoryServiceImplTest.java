package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.Project;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.StoreMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.repository.ProjectRepository;
import dev.joe.aimemoryservice.repository.SourceRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.serviceimpl.MemoryServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceImplTest {

    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.parse("2026-10-04T10:00:00Z");

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private MemoryServiceImpl memoryService;

    @Test
    void createsProjectMemory() {
        StoreMemoryRequest request = new StoreMemoryRequest(
                1L,
                null,
                MemoryScope.PROJECT,
                MemoryType.FAILURE,
                "  Large context slowdown  ",
                "  Large contexts increased CPU usage.  ",
                null,
                "  Smaller contexts remained responsive.  "
        );

        Project project = new Project(
                1L,
                "local-ai-stack",
                "Local AI infrastructure",
                TIMESTAMP,
                TIMESTAMP
        );

        float[] embedding = new float[768];

        Memory storedMemory = new Memory(
                1L,
                1L,
                null,
                MemoryScope.PROJECT,
                MemoryType.FAILURE,
                "Large context slowdown",
                "Large contexts increased CPU usage.",
                Confidence.MEDIUM,
                MemoryStatus.ACTIVE,
                "Smaller contexts remained responsive.",
                null,
                TIMESTAMP,
                TIMESTAMP
        );

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));

        when(embeddingService.embedDocument("""
                Title: Large context slowdown

                Content:
                Large contexts increased CPU usage.

                Evidence:
                Smaller contexts remained responsive.
                """.strip()))
                .thenReturn(embedding);

        when(memoryRepository.insert(
                1L,
                null,
                MemoryScope.PROJECT,
                MemoryType.FAILURE,
                "Large context slowdown",
                "Large contexts increased CPU usage.",
                Confidence.MEDIUM,
                MemoryStatus.ACTIVE,
                "Smaller contexts remained responsive.",
                embedding
        )).thenReturn(storedMemory);

        MemoryResponse response =
                memoryService.createMemory(request);

        assertEquals(1L, response.id());
        assertEquals(Confidence.MEDIUM, response.confidence());
        assertEquals(MemoryStatus.ACTIVE, response.status());
        assertEquals(
                "Large context slowdown",
                response.title()
        );

        verifyNoInteractions(sourceRepository);
    }

    @Test
    void rejectsGlobalMemoryWithProject() {
        StoreMemoryRequest request = request(
                1L,
                null,
                MemoryScope.GLOBAL
        );

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> memoryService.createMemory(request)
        );

        verifyNoInteractions(
                projectRepository,
                sourceRepository,
                embeddingService,
                memoryRepository
        );
    }

    @Test
    void rejectsMissingProject() {
        StoreMemoryRequest request = request(
                99L,
                null,
                MemoryScope.PROJECT
        );

        when(projectRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> memoryService.createMemory(request)
        );

        verifyNoInteractions(
                sourceRepository,
                embeddingService,
                memoryRepository
        );
    }

    @Test
    void rejectsIncompatibleSource() {
        StoreMemoryRequest request = request(
                1L,
                7L,
                MemoryScope.PROJECT
        );

        Project project = new Project(
                1L,
                "local-ai-stack",
                null,
                TIMESTAMP,
                TIMESTAMP
        );

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));
        when(sourceRepository.existsById(7L))
                .thenReturn(true);
        when(sourceRepository.isCompatibleWithProject(7L, 1L))
                .thenReturn(false);

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> memoryService.createMemory(request)
        );

        verifyNoInteractions(
                embeddingService,
                memoryRepository
        );
    }

    private static StoreMemoryRequest request(
            Long projectId,
            Long sourceId,
            MemoryScope scope
    ) {
        return new StoreMemoryRequest(
                projectId,
                sourceId,
                scope,
                MemoryType.FACT,
                "Memory title",
                "Memory content",
                Confidence.HIGH,
                null
        );
    }
}