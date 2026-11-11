package local.opencode.pcsx2pine;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public final class PineMcpServer {
    private static final AtomicLong CALL_IDS = new AtomicLong();
    private static final Map<String, Object> EMPTY_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false);

    private PineMcpServer() {}

    public static void main(String[] args) throws Exception {
        PineClient pine = new PineClient();
        int port = Integer.parseInt(System.getenv().getOrDefault("MCP_PORT", "18083"));
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
        McpServer.sync(transport)
                .serverInfo("pcsx2-pine", "0.2.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        McpSchema.Tool.builder("status", EMPTY_SCHEMA)
                                .description("Read PCSX2's PINE emulator status (read-only).")
                                .build(),
                        (exchange, request) -> call("status", () -> {
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
                        (exchange, request) -> call("game_info", () -> {
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
                        (exchange, request) -> call("read_memory", () -> {
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
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), "/mcp");
        server.setHandler(context);
        server.start();
        System.err.println("pcsx2-pine MCP listening on http://0.0.0.0:" + port + "/mcp; PINE socket " + pine.socketPath());
        server.join();
    }

    private static McpSchema.CallToolResult call(String tool, IoSupplier<Map<String, Object>> operation) {
        long id = CALL_IDS.incrementAndGet();
        long started = System.nanoTime();
        System.err.println("MCP tool call started tool=" + tool + " id=" + id);
        try {
            String json = McpJsonDefaults.getMapper().writeValueAsString(operation.get());
            System.err.println("MCP tool call completed tool=" + tool + " id=" + id + " durationMs=" + ((System.nanoTime() - started) / 1_000_000));
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .build();
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            System.err.println("MCP tool call failed tool=" + tool + " id=" + id + " durationMs=" + ((System.nanoTime() - started) / 1_000_000)
                    + " error=" + message);
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
