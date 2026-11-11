package local.opencode.pcsx2pine;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PineMcpServer {
    private static final Map<String, Object> EMPTY_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false);

    private PineMcpServer() {}

    public static void main(String[] args) {
        PineClient pine = new PineClient();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpServer.sync(transport)
                .serverInfo("pcsx2-pine", "0.2.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        McpSchema.Tool.builder("status", EMPTY_SCHEMA)
                                .description("Read PCSX2's PINE emulator status (read-only).")
                                .build(),
                        (exchange, request) -> call(() -> {
                            PineClient.EmulatorStatus status = pine.getStatus();
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("socket", pine.socketPath().toString());
                            result.put("value", status.value());
                            result.put("state", status.state());
                            return result;
                        }))
                .toolCall(
                        McpSchema.Tool.builder("game_info", EMPTY_SCHEMA)
                                .description("Read the running game's title, serial, CRC, version, and PCSX2 build version through PINE.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            PineClient.GameInfo info = pine.getGameInfo();
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("socket", pine.socketPath().toString());
                            result.put("title", info.title());
                            result.put("id", info.id());
                            result.put("crc", info.crc());
                            result.put("gameVersion", info.gameVersion());
                            result.put("pcsx2Version", info.pcsx2Version());
                            return result;
                        }))
                .toolCall(
                        McpSchema.Tool.builder("read_memory", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "address", Map.of("type", "integer", "minimum", 0, "maximum", 4294967295L,
                                                        "description", "PS2 guest address, decimal or hexadecimal integer"),
                                                "length", Map.of("type", "integer", "minimum", 1, "maximum", 256,
                                                        "description", "Number of bytes to read (1–256)")),
                                        "required", List.of("address", "length"),
                                        "additionalProperties", false))
                                .description("Read up to 256 bytes from a 32-bit PS2 guest address through PINE. This tool cannot write memory.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            Object addressValue = request.arguments().get("address");
                            Object lengthValue = request.arguments().get("length");
                            if (!(addressValue instanceof Number addressNumber) || !(lengthValue instanceof Number lengthNumber)) {
                                throw new IllegalArgumentException("address and length must be integers");
                            }
                            long address = exactLong(addressNumber, "address");
                            int length = Math.toIntExact(exactLong(lengthNumber, "length"));
                            byte[] bytes = pine.readMemory(address, length);
                            return Map.of(
                                    "address", String.format("0x%08x", address),
                                    "length", length,
                                    "hex", toHex(bytes));
                        }))
                .build();
    }

    private static McpSchema.CallToolResult call(IoSupplier<Map<String, Object>> operation) {
        try {
            String json = McpJsonDefaults.getMapper().writeValueAsString(operation.get());
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .build();
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(message)))
                    .isError(true)
                    .build();
        }
    }

    private static long exactLong(Number number, String name) {
        long value = number.longValue();
        if (number.doubleValue() != (double) value) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(String.format("%02x", Byte.toUnsignedInt(value)));
        }
        return result.toString();
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
