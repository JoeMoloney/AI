package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.Project;
import dev.joe.aimemoryservice.domain.ScoredMemory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.MemorySearchResult;
import dev.joe.aimemoryservice.dto.SearchMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.exceptions.ResourceNotFoundException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.repository.ProjectRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.serviceimpl.MemorySearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemorySearchServiceImplTest {

    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.parse("2026-10-04T10:00:00Z");

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private MemorySearchServiceImpl memorySearchService;

    @Test
    void searchesProjectAndGlobalMemoriesUsingDefaults() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "  Why did long sessions become slow?  ",
                1L,
                null,
                null
        );

        Project project = new Project(
                1L,
                "local-ai-stack",
                null,
                TIMESTAMP,
                TIMESTAMP
        );

        float[] embedding = new float[768];

        when(projectRepository.findById(1L))
                .thenReturn(Optional.of(project));
        when(embeddingService.embedQuery(
                "Why did long sessions become slow?"
        )).thenReturn(embedding);
        when(memoryRepository.hybridSearch(
                "Why did long sessions become slow?",
                embedding,
                1L,
                true,
                5
        )).thenReturn(List.of(scoredMemory()));

        List<MemorySearchResult> results =
                memorySearchService.searchMemories(request);

        assertEquals(1, results.size());
        assertEquals(1L, results.getFirst().id());
        assertEquals(
                "Large context caused model slowdown",
                results.getFirst().title()
        );
        assertEquals(
                0.7594,
                results.getFirst().similarity(),
                0.0001
        );
    }

    @Test
    void searchesGlobalMemoriesWithoutProject() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "Reusable troubleshooting lesson",
                null,
                true,
                2
        );

        float[] embedding = new float[768];

        when(embeddingService.embedQuery(
                "Reusable troubleshooting lesson"
        )).thenReturn(embedding);
        when(memoryRepository.hybridSearch(
                "Reusable troubleshooting lesson",
                embedding,
                null,
                true,
                2
        )).thenReturn(List.of());

        List<MemorySearchResult> results =
                memorySearchService.searchMemories(request);

        assertEquals(List.of(), results);
        verifyNoInteractions(projectRepository);
    }

    @Test
    void rejectsSearchWithoutAnyScope() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "Search query",
                null,
                false,
                5
        );

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> memorySearchService.searchMemories(request)
        );

        verifyNoInteractions(
                projectRepository,
                embeddingService,
                memoryRepository
        );
    }

    @Test
    void rejectsMissingProject() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "Search query",
                99L,
                true,
                5
        );

        when(projectRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> memorySearchService.searchMemories(request)
        );

        verifyNoInteractions(
                embeddingService,
                memoryRepository
        );
    }

        @Test
    void rejectsBlankQuery() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "   ",
                1L,
                true,
                5);

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> memorySearchService.searchMemories(request));

        verifyNoInteractions(
                projectRepository,
                embeddingService,
                memoryRepository);
    }

    @Test
    void rejectsOutOfRangeLimit() {
        SearchMemoryRequest request = new SearchMemoryRequest(
                "Search query",
                1L,
                true,
                21);

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> memorySearchService.searchMemories(request));

        verifyNoInteractions(
                projectRepository,
                embeddingService,
                memoryRepository);
    }

    private static ScoredMemory scoredMemory() {
        Memory memory = new Memory(
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
                TIMESTAMP,
                TIMESTAMP
        );

        return new ScoredMemory(memory, 0.7594);
    }
}
