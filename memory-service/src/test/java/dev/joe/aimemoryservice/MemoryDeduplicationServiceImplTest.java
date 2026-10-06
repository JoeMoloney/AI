package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.config.MemoryProperties;
import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.ScoredMemory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;
import dev.joe.aimemoryservice.exceptions.DuplicateMemoryException;
import dev.joe.aimemoryservice.repository.MemoryRepository;
import dev.joe.aimemoryservice.service.serviceimpl.MemoryDeduplicationServiceImpl;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryDeduplicationServiceImplTest {
    private final MemoryRepository repository = mock(MemoryRepository.class);
    private final float[] embedding = new float[768];

    @Test
    void rejectsLikelyDuplicate() {
        var service = service(true);
        var memory = new Memory(
            7L, 1L, null, MemoryScope.PROJECT, MemoryType.FACT,
            "Existing lesson", "Existing content", Confidence.HIGH,
            MemoryStatus.ACTIVE, null, null,
            OffsetDateTime.parse("2026-10-06T10:00:00Z"),
            OffsetDateTime.parse("2026-10-06T10:00:00Z")
        );
        when(repository.findDuplicateCandidates(
            embedding, 1L, MemoryScope.PROJECT, 0.95, 3
        )).thenReturn(List.of(new ScoredMemory(memory, 0.98)));

        DuplicateMemoryException exception = assertThrows(
            DuplicateMemoryException.class,
            () -> service.rejectLikelyDuplicates(embedding, 1L, MemoryScope.PROJECT)
        );

        assertEquals(7L, exception.candidates().getFirst().id());
        assertEquals(0.98, exception.candidates().getFirst().similarity());
    }

    @Test
    void allowsUniqueMemory() {
        var service = service(true);
        when(repository.findDuplicateCandidates(
            embedding, 1L, MemoryScope.PROJECT, 0.95, 3
        )).thenReturn(List.of());

        assertDoesNotThrow(
            () -> service.rejectLikelyDuplicates(embedding, 1L, MemoryScope.PROJECT)
        );
    }

    @Test
    void skipsCheckWhenDisabled() {
        var service = service(false);

        assertDoesNotThrow(
            () -> service.rejectLikelyDuplicates(embedding, 1L, MemoryScope.PROJECT)
        );
        verifyNoInteractions(repository);
    }

    private MemoryDeduplicationServiceImpl service(boolean enabled) {
        return new MemoryDeduplicationServiceImpl(
            repository,
            new MemoryProperties(new MemoryProperties.Deduplication(enabled, 0.95, 3))
        );
    }
}
