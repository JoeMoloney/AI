package local.opencode.x360mcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Reads Xenia's read-only, chunked function trace output. */
public final class XeniaTraceImporter {
    private static final int HEADER_SIZE = 48;
    private static final int CALLER_HISTORY_COUNT = 4;
    private static final long MAX_CHUNK_BYTES = 512L * 1024 * 1024;

    public record TraceFunction(
            String startAddress,
            String endAddress,
            long type,
            String threadUseMask,
            String callCount,
            List<String> callerHistory,
            long instructionCount,
            long coveredInstructionCount,
            String instructionExecutionCount,
            boolean hasCoverage,
            boolean inGeneratedIndex) {}

    public record ChunkIdentity(String path, long sizeBytes, String sha256, int records) {}

    public record ImportResult(
            String requestedBasePath,
            List<ChunkIdentity> chunks,
            List<TraceFunction> functions,
            int totalTraceFunctionCount,
            int coverageFunctionCount,
            long coveredInstructionCount,
            String totalCalls,
            String totalInstructionExecutions,
            List<String> warnings) {}

    public ImportResult read(Path requestedBasePath, FunctionIndex index, int limit) throws IOException {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        Path base = requestedBasePath.toAbsolutePath().normalize();
        List<ChunkIdentity> chunks = new ArrayList<>();
        List<TraceFunction> functions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long coveredInstructions = 0;
        java.math.BigInteger totalCalls = java.math.BigInteger.ZERO;
        java.math.BigInteger totalInstructionExecutions = java.math.BigInteger.ZERO;
        int coverageFunctionCount = 0;
        boolean foundChunk = false;

        for (int chunkNumber = 0; ; chunkNumber++) {
            Path chunkPath = chunkPath(base, chunkNumber);
            if (!Files.exists(chunkPath)) {
                if (!foundChunk) {
                    throw new IOException("No Xenia trace chunks found. Expected first chunk at " + chunkPath);
                }
                break;
            }
            foundChunk = true;
            long size = Files.size(chunkPath);
            if (!Files.isRegularFile(chunkPath) || size == 0 || size > MAX_CHUNK_BYTES) {
                throw new IOException("Invalid Xenia trace chunk size/type at " + chunkPath + ": " + size);
            }
            byte[] bytes = Files.readAllBytes(chunkPath);
            MessageDigest digest = sha256Digest();
            digest.update(bytes);
            ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int chunkRecords = 0;
            while (data.remaining() >= 4) {
                int recordStart = data.position();
                long dataSize = Integer.toUnsignedLong(data.getInt());
                if (dataSize == 0) {
                    if (!allZero(bytes, recordStart, bytes.length)) {
                        warnings.add("Non-zero bytes follow the zero terminator in " + chunkPath
                                + " at offset " + recordStart);
                    }
                    break;
                }
                if (dataSize < HEADER_SIZE || dataSize > Integer.MAX_VALUE
                        || dataSize - 4 > data.remaining()) {
                    throw new IOException("Malformed/truncated trace record in " + chunkPath
                            + " at offset " + recordStart + ": data_size=" + dataSize
                            + ", remaining=" + (data.remaining() + 4));
                }
                int recordEnd = recordStart + (int) dataSize;
                int start = data.getInt();
                int end = data.getInt();
                long type = Integer.toUnsignedLong(data.getInt());
                long threadMask = data.getLong();
                long callCount = data.getLong();
                int[] callers = new int[CALLER_HISTORY_COUNT];
                for (int i = 0; i < callers.length; i++) {
                    callers[i] = data.getInt();
                }
                long instructionCount = Integer.toUnsignedLong((end - start) / 4 + 1);
                if (Integer.compareUnsigned(end, start) < 0 || ((end - start) & 3) != 0) {
                    throw new IOException("Invalid guest function extent in " + chunkPath
                            + " at offset " + recordStart + ": start=0x"
                            + Integer.toHexString(start) + ", end=0x" + Integer.toHexString(end));
                }
                long expectedCoverageBytes = instructionCount * Long.BYTES;
                long payloadBytes = dataSize - HEADER_SIZE;
                boolean hasCoverage = payloadBytes == expectedCoverageBytes;
                if (payloadBytes != 0 && !hasCoverage) {
                    throw new IOException("Unexpected trace payload size in " + chunkPath
                            + " at offset " + recordStart + ": " + payloadBytes
                            + " bytes for " + instructionCount + " guest instructions");
                }
                long coveredInFunction = 0;
                java.math.BigInteger instructionExecutions = java.math.BigInteger.ZERO;
                if (hasCoverage) {
                    for (long i = 0; i < instructionCount; i++) {
                        long count = data.getLong();
                        if (count != 0) {
                            coveredInFunction++;
                        }
                        instructionExecutions = instructionExecutions.add(unsignedLong(count));
                    }
                    coveredInstructions += coveredInFunction;
                    totalInstructionExecutions = totalInstructionExecutions.add(instructionExecutions);
                    coverageFunctionCount++;
                }
                data.position(recordEnd);
                chunkRecords++;
                totalCalls = totalCalls.add(unsignedLong(callCount));
                List<String> callerHistory = new ArrayList<>();
                for (int caller : callers) {
                    if (caller != 0) {
                        callerHistory.add(address(caller));
                    }
                }
                functions.add(new TraceFunction(
                        address(start), address(end), type, Long.toUnsignedString(threadMask),
                        Long.toUnsignedString(callCount), List.copyOf(callerHistory),
                        instructionCount, coveredInFunction, instructionExecutions.toString(),
                        hasCoverage, index != null && index.containsAddress(start)));
            }
            if (data.remaining() != 0 && !allZero(bytes, data.position(), bytes.length)) {
                warnings.add("Ignoring " + data.remaining() + " trailing non-zero bytes in " + chunkPath);
            }
            chunks.add(new ChunkIdentity(chunkPath.toString(), size,
                    HexFormat.of().formatHex(digest.digest()), chunkRecords));
        }

        List<TraceFunction> top = functions.stream()
                .sorted(Comparator.comparing((TraceFunction f) -> new java.math.BigInteger(f.callCount())).reversed()
                        .thenComparing(TraceFunction::startAddress))
                .limit(limit)
                .toList();
        if (coverageFunctionCount == 0) {
            warnings.add("No instruction-coverage payloads found; this trace may have been recorded without trace_function_coverage.");
        }
        return new ImportResult(base.toString(), List.copyOf(chunks), top, functions.size(), coverageFunctionCount,
                coveredInstructions, totalCalls.toString(), totalInstructionExecutions.toString(),
                List.copyOf(warnings));
    }

    private static Path chunkPath(Path base, int number) {
        String name = base.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String chunkName = (dot > 0 ? name.substring(0, dot) : name) + "." + number;
        return base.resolveSibling(chunkName);
    }

    private static boolean allZero(byte[] bytes, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static String address(int value) {
        return String.format("0x%08X", value);
    }

    private static java.math.BigInteger unsignedLong(long value) {
        return new java.math.BigInteger(Long.toUnsignedString(value));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
