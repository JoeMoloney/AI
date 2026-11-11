package local.opencode.x360mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XeniaTraceImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void readsChunkedHeaderCallersAndInstructionCoverage() throws Exception {
        Path generated = Files.createDirectories(tempDir.resolve("generated"));
        Files.writeString(generated.resolve("func.cpp"), "DEFINE_REX_FUNC(sub_82100000) { return 0; }");
        FunctionIndex index = FunctionIndex.build(generated);
        Path base = tempDir.resolve("capture.trace");
        Path chunk = tempDir.resolve("capture.0");
        ByteBuffer record = ByteBuffer.allocate(48 + 3 * Long.BYTES + 32).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(48 + 3 * Long.BYTES);
        record.putInt(0x82100000);
        record.putInt(0x82100008);
        record.putInt(2);
        record.putLong(5);
        record.putLong(0xFFFF_FFFF_FFFF_FFFEL);
        record.putInt(0x82100010);
        record.putInt(0);
        record.putInt(0x82000000);
        record.putInt(0);
        record.putLong(3);
        record.putLong(0);
        record.putLong(7);
        Files.write(chunk, record.array());

        XeniaTraceImporter.ImportResult result = new XeniaTraceImporter().read(base, index, 10);
        assertEquals(1, result.totalTraceFunctionCount());
        assertEquals(1, result.coverageFunctionCount());
        assertEquals(2, result.coveredInstructionCount());
        assertEquals("18446744073709551614", result.totalCalls());
        assertEquals("10", result.totalInstructionExecutions());
        assertTrue(result.functions().get(0).inGeneratedIndex());
        assertEquals("0x82100000", result.functions().get(0).startAddress());
        assertEquals("0x82100010", result.functions().get(0).callerHistory().get(0));
        assertEquals(1, result.chunks().get(0).records());
        assertEquals(64, result.chunks().get(0).sha256().length());
    }

    @Test
    void parsesRecordsWithoutCoverageAndWarns() throws Exception {
        Path base = tempDir.resolve("plain.trace");
        ByteBuffer record = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(48).putInt(0x82100000).putInt(0x82100000).putInt(0)
                .putLong(1).putLong(0).putInt(0).putInt(0).putInt(0).putInt(0);
        Files.write(tempDir.resolve("plain.0"), record.array());

        XeniaTraceImporter.ImportResult result = new XeniaTraceImporter().read(base, null, 10);
        assertEquals(1, result.totalTraceFunctionCount());
        assertFalse(result.functions().get(0).hasCoverage());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("without trace_function_coverage")));
    }

    @Test
    void rejectsTruncatedRecordsAndMissingChunks() throws Exception {
        Path base = tempDir.resolve("broken.trace");
        Files.write(tempDir.resolve("broken.0"), ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(48).putInt(0x82100000).array());
        assertThrows(IOException.class, () -> new XeniaTraceImporter().read(base, null, 10));
        assertThrows(IOException.class, () -> new XeniaTraceImporter().read(tempDir.resolve("missing.trace"), null, 10));
    }
}
