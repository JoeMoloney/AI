package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.client.OllamaClient;
import dev.joe.aimemoryservice.service.serviceimpl.EmbeddingServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

    @Mock
    private OllamaClient ollamaClient;

    @InjectMocks
    private EmbeddingServiceImpl embeddingService;

    @Test
    void prefixesDocumentText() {
        float[] expected = {0.1f, 0.2f};

        when(ollamaClient.embed("search_document: Durable memory"))
                .thenReturn(expected);

        float[] result =
                embeddingService.embedDocument("  Durable memory  ");

        assertArrayEquals(expected, result);
        verify(ollamaClient)
                .embed("search_document: Durable memory");
    }

    @Test
    void prefixesQueryText() {
        float[] expected = {0.3f, 0.4f};

        when(ollamaClient.embed("search_query: Find previous failures"))
                .thenReturn(expected);

        float[] result =
                embeddingService.embedQuery("Find previous failures");

        assertArrayEquals(expected, result);
        verify(ollamaClient)
                .embed("search_query: Find previous failures");
    }

    @Test
    void rejectsBlankText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> embeddingService.embedDocument("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> embeddingService.embedQuery(null)
        );

        verifyNoInteractions(ollamaClient);
    }
}
