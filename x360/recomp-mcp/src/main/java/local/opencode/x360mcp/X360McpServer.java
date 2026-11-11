package local.opencode.x360mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only MCP tools for navigating Xbox 360 recompiler artifacts. */
public final class X360McpServer {
    private static final Pattern XENIA_TRACE_FLAGS = Pattern.compile(
            "DEFINE_bool\\((trace_functions|trace_function_coverage|trace_function_data|trace_function_references)");

    private X360McpServer() {}

    public static void main(String[] args) throws IOException {
        Path config = configuredRegistryPath();
        TargetRegistry registry = TargetRegistry.load(config);
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpServer.sync(transport)
                .serverInfo("x360-recomp-analysis", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .toolCall(
                        McpSchema.Tool.builder("list_targets", schema(Map.of(), List.of()))
                                .description("List configured Xbox 360 original-XEX, Xenia, and native recompilation workspaces. Start here; never assume which game's artifacts are configured.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            List<Map<String, Object>> targets = new ArrayList<>();
                            for (Target target : registry.list()) {
                                targets.add(Map.of(
                                        "id", target.id(),
                                        "name", target.name(),
                                        "originalXex", target.originalXex().toString(),
                                        "recompRoot", target.recompRoot().toString(),
                                        "generatedSourceRoot", target.generatedSourceRoot().toString(),
                                        "nativeExecutable", target.nativeExecutable().toString(),
                                        "xeniaRoot", target.xeniaRoot().toString()));
                            }
                            return Map.of("registry", registry.configPath().toString(), "targets", targets);
                        }))
                .toolCall(
                        McpSchema.Tool.builder("inspect_target", schema(
                                        Map.of("target", stringSchema("Configured target ID from list_targets")),
                                        List.of("target")))
                                .description("Inspect one configured title's XEX identity, generated function index, native executable, and locally available Xenia tracing options. This is read-only and does not launch/build anything.")
                                .build(),
                        (exchange, request) -> call(() -> inspectTarget(
                                registry.require(stringArg(request.arguments(), "target")))))
                .toolCall(
                        McpSchema.Tool.builder("search_functions", schema(
                                        Map.of(
                                                "target", stringSchema("Configured target ID"),
                                                "query", stringSchema("Substring of guest address or generated function symbol, e.g. 82139 or sub_82139370"),
                                                "limit", integerSchema(1, 100, "Maximum matches to return; default 20")),
                                        List.of("target", "query")))
                                .description("Search the generated C++ function index by guest PPC address or symbol. Returns guest-address identity, generated source location, and source-file hash. Does not infer function meaning.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            FunctionIndex index = index(registry.require(stringArg(request.arguments(), "target")));
                            int limit = optionalInt(request.arguments(), "limit", 20);
                            FunctionIndex.SearchResult result = index.search(
                                    stringArg(request.arguments(), "query"), limit);
                            return Map.of(
                                    "sourceRoot", index.sourceRoot().toString(),
                                    "totalMatches", result.totalMatches(),
                                    "returned", result.matches().size(),
                                    "matches", result.matches());
                        }))
                .toolCall(
                        McpSchema.Tool.builder("lookup_function", schema(
                                        Map.of(
                                                "target", stringSchema("Configured target ID"),
                                                "address", stringSchema("32-bit Xbox 360 guest PPC address, e.g. 0x82139370"),
                                                "maxChars", integerSchema(1000, 24000, "Maximum source excerpt size; default 12000")),
                                        List.of("target", "address")))
                                .description("Look up a guest PPC address in generated static-recompilation C++. Returns the generated function source and line, a bounded excerpt, and source hash. Address is the join key for later Ghidra, Xenia trace, and profile evidence.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            FunctionIndex index = index(registry.require(stringArg(request.arguments(), "target")));
                            FunctionIndex.LookupResult result = index.lookup(
                                    stringArg(request.arguments(), "address"),
                                    optionalInt(request.arguments(), "maxChars", 12000));
                            if (result.function() == null) {
                                return Map.of(
                                        "found", false,
                                        "address", stringArg(request.arguments(), "address"),
                                        "sourceRoot", index.sourceRoot().toString(),
                                        "indexedFunctionCount", index.size());
                            }
                            return Map.of(
                                    "found", true,
                                    "sourceRoot", index.sourceRoot().toString(),
                                    "function", result.function(),
                                    "excerpt", result.excerpt(),
                                    "truncated", result.truncated());
                        }))
                .toolCall(
                        McpSchema.Tool.builder("import_xenia_trace", schema(
                                        Map.of(
                                                "target", stringSchema("Configured target ID whose generated functions will be used for address matching"),
                                                "traceBasePath", stringSchema("Base path passed to Xenia's trace_function_data_path, e.g. /tmp/functions.trace; chunks are read as functions.0, functions.1, ..."),
                                                "limit", integerSchema(1, 1000, "Maximum highest-call-count trace records to return; default 100")),
                                        List.of("target", "traceBasePath")))
                                .description("Read-only import of Xenia's chunked function trace. Parses little-endian function headers, call counts, caller history, and optional instruction coverage; hashes each chunk and matches guest starts against generated C++. Does not modify or copy trace files. Requires the base path used by Xenia, whose output chunks use numbered extensions.")
                                .build(),
                        (exchange, request) -> call(() -> {
                            Target target = registry.require(stringArg(request.arguments(), "target"));
                            FunctionIndex index = index(target);
                            XeniaTraceImporter.ImportResult imported = new XeniaTraceImporter().read(
                                    Path.of(stringArg(request.arguments(), "traceBasePath")),
                                    index,
                                    optionalInt(request.arguments(), "limit", 100));
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("target", Map.of("id", target.id(), "name", target.name()));
                            result.put("configuredOriginalXex", fileIdentity(
                                    target.originalXex(), "XEX", new ArrayList<>()));
                            result.put("xeniaTraceFormatEvidence", Map.of(
                                    "headerSource", target.xeniaRoot().resolve("src/xenia/cpu/function_trace_data.h").toString(),
                                    "writerSource", target.xeniaRoot().resolve("src/xenia/base/mapped_memory_win.cc").toString(),
                                    "headerBytes", 48,
                                    "callerHistoryEntries", 4,
                                    "chunkNaming", "<base with extension replaced>.<zero-based chunk number>"));
                            result.put("generatedSourceIndexSha256", index.fingerprint());
                            result.put("import", imported);
                            result.put("warning", "Trace capture identity (XEX/build/configuration) is not embedded by this format; verify it independently before comparing results.");
                            return result;
                        }))
                .build();
    }

    private static Path configuredRegistryPath() {
        String configured = System.getenv("X360_ANALYSIS_CONFIG");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".config", "opencode", "x360-targets.properties")
                .toAbsolutePath().normalize();
    }

    private static Map<String, Object> inspectTarget(Target target) throws Exception {
        List<String> warnings = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", target.id());
        result.put("name", target.name());
        result.put("originalXex", fileIdentity(target.originalXex(), "XEX", warnings));
        result.put("nativeExecutable", fileIdentity(target.nativeExecutable(), "native executable", warnings));
        result.put("recompRoot", directoryIdentity(target.recompRoot(), warnings));
        result.put("generatedSourceRoot", directoryIdentity(target.generatedSourceRoot(), warnings));
        result.put("xeniaRoot", directoryIdentity(target.xeniaRoot(), warnings));

        if (Files.isDirectory(target.generatedSourceRoot())) {
            FunctionIndex index = index(target);
            result.put("indexedGeneratedFunctionCount", index.size());
            result.put("generatedSourceIndexSha256", index.fingerprint());
        } else {
            result.put("indexedGeneratedFunctionCount", 0);
        }

        Path flags = target.xeniaRoot().resolve("src/xenia/cpu/cpu_flags.cc");
        if (Files.isRegularFile(flags)) {
            String source = Files.readString(flags);
            Path processor = target.xeniaRoot().resolve("src/xenia/cpu/processor.cc");
            String processorSource = Files.isRegularFile(processor) ? Files.readString(processor) : "";
            var matcher = XENIA_TRACE_FLAGS.matcher(source);
            List<String> supported = new ArrayList<>();
            while (matcher.find()) {
                if (!supported.contains(matcher.group(1))) {
                    supported.add(matcher.group(1));
                }
            }
            result.put("xeniaTraceOptionsSource", flags.toString());
            result.put("xeniaTraceOptions", supported);
            result.put("xeniaTraceOutputPathOption", processorSource.contains("trace_function_data_path"));
            result.put("xeniaInstructionBreakpointAvailable", source.contains("break_on_instruction"));
        } else {
            result.put("xeniaTraceOptions", List.of());
            warnings.add("Xenia source flags were not found; no claim is made about installed-build capabilities.");
        }
        result.put("warnings", warnings);
        result.put("readOnly", true);
        return result;
    }

    private static Map<String, Object> fileIdentity(Path path, String role, List<String> warnings) throws Exception {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("role", role);
        identity.put("path", path.toString());
        identity.put("exists", Files.isRegularFile(path));
        if (Files.isRegularFile(path)) {
            identity.put("sizeBytes", Files.size(path));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            identity.put("sha256", HexFormat.of().formatHex(digest.digest()));
        } else {
            warnings.add(role + " is missing at configured path " + path);
        }
        return identity;
    }

    private static Map<String, Object> directoryIdentity(Path path, List<String> warnings) {
        boolean exists = Files.isDirectory(path);
        if (!exists) {
            warnings.add("Directory is missing at configured path " + path);
        }
        return Map.of("path", path.toString(), "exists", exists);
    }

    private static FunctionIndex index(Target target) throws IOException {
        Path path = target.generatedSourceRoot().toAbsolutePath().normalize();
        // Rebuild on each request: generated sources may change while this stdio
        // server stays alive, and stale cross-build function mappings are unsafe.
        return FunctionIndex.build(path);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integerSchema(int minimum, int maximum, String description) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum, "description", description);
    }

    private static String stringArg(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
        return string;
    }

    private static int optionalInt(Map<String, Object> arguments, String name, int fallback) {
        Object value = arguments.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return number.intValue();
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

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }
}
