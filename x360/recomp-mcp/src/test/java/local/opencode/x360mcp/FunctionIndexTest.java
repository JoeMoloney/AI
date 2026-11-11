package local.opencode.x360mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void indexesGeneratedFunctionAddressesAndReturnsBoundedSource() throws Exception {
        Path generated = Files.createDirectories(tempDir.resolve("generated"));
        Files.writeString(generated.resolve("shard.cpp"), """
                // generated fixture
                DEFINE_REX_FUNC(sub_82139370) {
                  sub_82ABCDEF();
                  return 7;
                }
                DEFINE_REX_FUNC(sub_82ABCDEF) {
                  return 9;
                }
                """);

        FunctionIndex index = FunctionIndex.build(generated);
        assertEquals(2, index.size());
        assertEquals(2, index.search("82", 10).totalMatches());
        assertEquals("sub_82139370", index.lookup("82139370", 3000).function().symbol());
        assertTrue(index.lookup("0x82139370", 3000).excerpt().contains("return 7"));
        assertFalse(index.lookup("0x81111111", 3000).function() != null);
        assertEquals(List.of("0x82ABCDEF"), index.lookup("82139370", 3000).function().directCallees());
        assertEquals(List.of("0x82139370"), index.lookup("82ABCDEF", 3000).function().directCallers());
    }

    @Test
    void rejectsDuplicateGuestFunctionAddressesInsteadOfChoosingArbitrarily() throws Exception {
        Path generated = Files.createDirectories(tempDir.resolve("duplicates"));
        Files.writeString(generated.resolve("a.cpp"), "DEFINE_REX_FUNC(sub_82139370) { return 1; }");
        Files.writeString(generated.resolve("b.cpp"), "DEFINE_REX_FUNC(sub_82139370) { return 2; }");

        IOException error = assertThrows(IOException.class, () -> FunctionIndex.build(generated));
        assertTrue(error.getMessage().contains("Duplicate generated function address"));
    }

    @Test
    void validatesSearchLimitAndAddressSyntax() throws Exception {
        Path generated = Files.createDirectories(tempDir.resolve("validation"));
        Files.writeString(generated.resolve("empty.cpp"), "// no generated functions\n");
        FunctionIndex index = FunctionIndex.build(generated);

        assertThrows(IllegalArgumentException.class, () -> index.search("x", 101));
        assertThrows(IllegalArgumentException.class, () -> index.lookup("not-an-address", 3000));
        assertThrows(IllegalArgumentException.class, () -> index.lookup("82139370", 999));
    }

    @Test
    void rebuildReflectsGeneratedSourceChanges() throws Exception {
        Path generated = Files.createDirectories(tempDir.resolve("refresh"));
        Path source = generated.resolve("shard.cpp");
        Files.writeString(source, "DEFINE_REX_FUNC(sub_82139370) { return 1; }");
        FunctionIndex first = FunctionIndex.build(generated);
        Files.writeString(source, "DEFINE_REX_FUNC(sub_82ABCDEF) { return 2; }");
        FunctionIndex refreshed = FunctionIndex.build(generated);

        assertFalse(refreshed.lookup("82139370", 3000).function() != null);
        assertEquals("sub_82ABCDEF", refreshed.lookup("82ABCDEF", 3000).function().symbol());
        assertFalse(first.fingerprint().equals(refreshed.fingerprint()));
    }
}
