package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.dto.MemoryResponse;
import dev.joe.aimemoryservice.dto.UpdateMemoryRequest;
import dev.joe.aimemoryservice.exceptions.InvalidMemoryRequestException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.service.EmbeddingService;
import dev.joe.aimemoryservice.service.serviceimpl.MemoryLifecycleServiceImpl;
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
class MemoryLifecycleServiceImplTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-10-05T10:00:00Z");

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private MemoryLifecycleServiceImpl lifecycleService;

    @Test
    void reembedsWhenContentChanges() {
        Memory existing = memory(
                Confidence.HIGH,
                "Original content");

        UpdateMemoryRequest request = new UpdateMemoryRequest(
                null,
                null,
                "Updated content",
                null,
                null);

        float[] embedding = new float[768];

        Memory updated = memory(
                Confidence.HIGH,
                "Updated content");

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(embeddingService.embedDocument("""
                Title: Memory title

                Content:
                Updated content

                Evidence:
                Supporting evidence
                """.strip()))
                .thenReturn(embedding);

        when(memoryRepository.updateActive(
                1L,
                MemoryType.FACT,
                "Memory title",
                "Updated content",
                Confidence.HIGH,
                "Supporting evidence",
                embedding)).thenReturn(Optional.of(updated));

        MemoryResponse response = lifecycleService.updateMemory(1L, request);

        assertEquals("Updated content", response.content());

        verify(embeddingService).embedDocument("""
                Title: Memory title

                Content:
                Updated content

                Evidence:
                Supporting evidence
                """.strip());
    }

    @Test
    void doesNotReembedWhenOnlyConfidenceChanges() {
        Memory existing = memory(
                Confidence.HIGH,
                "Original content");

        UpdateMemoryRequest request = new UpdateMemoryRequest(
                null,
                null,
                null,
                Confidence.CONFIRMED,
                null);

        Memory updated = new Memory(
                existing.id(),
                existing.projectId(),
                existing.sourceId(),
                existing.scope(),
                existing.memoryType(),
                existing.title(),
                existing.content(),
                Confidence.CONFIRMED,
                existing.status(),
                existing.evidence(),
                existing.supersededBy(),
                existing.createdAt(),
                existing.updatedAt());

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(memoryRepository.updateActive(
                1L,
                MemoryType.FACT,
                "Memory title",
                "Original content",
                Confidence.CONFIRMED,
                "Supporting evidence",
                null)).thenReturn(Optional.of(updated));

        MemoryResponse response = lifecycleService.updateMemory(1L, request);

        assertEquals(
                Confidence.CONFIRMED,
                response.confidence());

        verifyNoInteractions(embeddingService);
    }

    @Test
    void invalidatesActiveMemory() {
        Memory existing = lifecycleMemory(
                1L,
                MemoryStatus.ACTIVE,
                null);

        Memory invalidated = lifecycleMemory(
                1L,
                MemoryStatus.INVALIDATED,
                null);

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(existing));
        when(memoryRepository.transitionActiveStatus(
                1L,
                MemoryStatus.INVALIDATED,
                null)).thenReturn(Optional.of(invalidated));

        MemoryResponse response = lifecycleService.invalidateMemory(1L);

        assertEquals(
                MemoryStatus.INVALIDATED,
                response.status());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void archivesActiveMemory() {
        Memory existing = lifecycleMemory(
                1L,
                MemoryStatus.ACTIVE,
                null);

        Memory archived = lifecycleMemory(
                1L,
                MemoryStatus.ARCHIVED,
                null);

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(existing));
        when(memoryRepository.transitionActiveStatus(
                1L,
                MemoryStatus.ARCHIVED,
                null)).thenReturn(Optional.of(archived));

        MemoryResponse response = lifecycleService.archiveMemory(1L);

        assertEquals(MemoryStatus.ARCHIVED, response.status());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void supersedesMemoryAtomically() {
        Memory oldMemory = lifecycleMemory(
                1L,
                MemoryStatus.ACTIVE,
                null);

        Memory replacement = lifecycleMemory(
                2L,
                MemoryStatus.ACTIVE,
                null);

        Memory superseded = lifecycleMemory(
                1L,
                MemoryStatus.SUPERSEDED,
                2L);

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(oldMemory));
        when(memoryRepository.findById(2L))
                .thenReturn(Optional.of(replacement));
        when(memoryRepository.supersede(1L, 2L))
                .thenReturn(List.of(replacement, superseded));

        MemoryResponse response = lifecycleService.supersedeMemory(1L, 2L);

        assertEquals(MemoryStatus.SUPERSEDED, response.status());
        assertEquals(2L, response.supersededBy());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void rejectsSelfSupersession() {
        assertThrows(
                InvalidMemoryRequestException.class,
                () -> lifecycleService.supersedeMemory(1L, 1L));

        verifyNoInteractions(
                memoryRepository,
                embeddingService);
    }

    @Test
    void rejectsTransitionOfInactiveMemory() {
        Memory archived = lifecycleMemory(
                1L,
                MemoryStatus.ARCHIVED,
                null);

        when(memoryRepository.findById(1L))
                .thenReturn(Optional.of(archived));

        assertThrows(
                InvalidMemoryRequestException.class,
                () -> lifecycleService.invalidateMemory(1L));

        verifyNoInteractions(embeddingService);
    }

    private static Memory lifecycleMemory(
            long id,
            MemoryStatus status,
            Long supersededBy) {
        return new Memory(
                id,
                1L,
                null,
                MemoryScope.PROJECT,
                MemoryType.FACT,
                "Memory " + id,
                "Memory content",
                Confidence.HIGH,
                status,
                null,
                supersededBy,
                TIMESTAMP,
                TIMESTAMP);
    }

    private static Memory memory(
            Confidence confidence,
            String content) {
        return new Memory(
                1L,
                1L,
                null,
                MemoryScope.PROJECT,
                MemoryType.FACT,
                "Memory title",
                content,
                confidence,
                MemoryStatus.ACTIVE,
                "Supporting evidence",
                null,
                TIMESTAMP,
                TIMESTAMP);
    }
}