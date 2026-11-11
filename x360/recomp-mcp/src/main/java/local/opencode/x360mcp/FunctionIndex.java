package local.opencode.x360mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Builds a read-only address-to-generated-source index for a recompiled game. */
public final class FunctionIndex {
    private static final Pattern FUNCTION = Pattern.compile(
            "DEFINE_REX_FUNC\\s*\\(\\s*(sub_([0-9a-fA-F]{8}))\\s*\\)");
    private static final Pattern CALL = Pattern.compile("\\bsub_([0-9a-fA-F]{8})\\s*\\(");
    private static final Pattern ADDRESS = Pattern.compile("^(?:0x)?([0-9a-fA-F]{1,8})$");

    public record Function(String address, String symbol, String source, int line,
                           String sourceSha256, List<String> directCallees,
                           List<String> directCallers) {}

    public record SearchResult(List<Function> matches, int totalMatches) {}

    public record LookupResult(Function function, int totalMatches, String excerpt, boolean truncated) {}

    private record Definition(int start, int end, String symbol, String address) {}

    private final Path sourceRoot;
    private final Map<String, Function> functions;
    private final String fingerprint;

    private FunctionIndex(Path sourceRoot, Map<String, Function> functions, String fingerprint) {
        this.sourceRoot = sourceRoot;
        this.functions = Map.copyOf(functions);
        this.fingerprint = fingerprint;
    }

    public static FunctionIndex build(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Generated source directory does not exist: " + sourceRoot);
        }

        Map<String, Function> functions = new TreeMap<>();
        Map<String, List<String>> calleesByAddress = new TreeMap<>();
        MessageDigest indexDigest = sha256Digest();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".cpp"))
                    .sorted()
                    .toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                Path relativePath = sourceRoot.relativize(path);
                String sourceHash = sha256(source.getBytes(StandardCharsets.UTF_8));
                indexDigest.update(relativePath.toString().getBytes(StandardCharsets.UTF_8));
                indexDigest.update((byte) 0);
                indexDigest.update(sourceHash.getBytes(StandardCharsets.US_ASCII));
                Matcher matcher = FUNCTION.matcher(source);
                int previousMatch = 0;
                int line = 1;
                List<Definition> entries = new ArrayList<>();
                while (matcher.find()) {
                    entries.add(new Definition(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)));
                }
                for (int definitionIndex = 0; definitionIndex < entries.size(); definitionIndex++) {
                    Definition definition = entries.get(definitionIndex);
                    for (int i = previousMatch; i < definition.start(); i++) {
                        if (source.charAt(i) == '\n') {
                            line++;
                        }
                    }
                    String address = definition.address().toUpperCase(Locale.ROOT);
                    previousMatch = definition.end();
                    int sourceEnd = definitionIndex + 1 < entries.size()
                            ? entries.get(definitionIndex + 1).start() : source.length();
                    Matcher calls = CALL.matcher(source.substring(definition.start(), sourceEnd));
                    List<String> directCallees = new ArrayList<>();
                    while (calls.find()) {
                        String callee = "0x" + calls.group(1).toUpperCase(Locale.ROOT);
                        if (!directCallees.contains(callee)) {
                            directCallees.add(callee);
                        }
                    }
                    Function function = new Function(
                            "0x" + address,
                            definition.symbol(),
                            sourceRoot.relativize(path).toString(),
                            line,
                            sourceHash,
                            List.copyOf(directCallees),
                            List.of());
                    Function previous = functions.putIfAbsent(address, function);
                    if (previous != null) {
                        throw new IOException("Duplicate generated function address 0x" + address
                                + " in " + previous.source() + " and " + function.source());
                    }
                    calleesByAddress.put(address, directCallees);
                }
            }
        }
        Map<String, List<String>> callersByAddress = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : calleesByAddress.entrySet()) {
            for (String callee : entry.getValue()) {
                callersByAddress.computeIfAbsent(callee.substring(2), ignored -> new ArrayList<>())
                        .add("0x" + entry.getKey());
            }
        }
        Map<String, Function> completedFunctions = new TreeMap<>();
        for (Map.Entry<String, Function> entry : functions.entrySet()) {
            Function function = entry.getValue();
            completedFunctions.put(entry.getKey(), new Function(
                    function.address(), function.symbol(), function.source(), function.line(),
                    function.sourceSha256(), function.directCallees(),
                    List.copyOf(callersByAddress.getOrDefault(entry.getKey(), List.of()))));
        }
        return new FunctionIndex(sourceRoot.toAbsolutePath().normalize(), completedFunctions,
                HexFormat.of().formatHex(indexDigest.digest()));
    }

    public Path sourceRoot() {
        return sourceRoot;
    }

    public int size() {
        return functions.size();
    }

    public boolean containsAddress(int address) {
        return functions.containsKey(String.format("%08X", address));
    }

    public String fingerprint() {
        return fingerprint;
    }

    public SearchResult search(String query, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        List<Function> matches = functions.values().stream()
                .filter(function -> function.address().contains(normalized)
                        || function.symbol().toUpperCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(Function::address))
                .toList();
        return new SearchResult(matches.stream().limit(limit).toList(), matches.size());
    }

    public LookupResult lookup(String input, int maxChars) throws IOException {
        if (maxChars < 1000 || maxChars > 24000) {
            throw new IllegalArgumentException("maxChars must be between 1000 and 24000");
        }
        String address = normalizeAddress(input);
        Function function = functions.get(address);
        if (function == null) {
            return new LookupResult(null, 0, "", false);
        }

        Path sourceFile = sourceRoot.resolve(function.source()).normalize();
        if (!sourceFile.startsWith(sourceRoot)) {
            throw new IOException("Indexed source path escaped generated source root");
        }
        String text = Files.readString(sourceFile, StandardCharsets.UTF_8);
        int start = offsetOfLine(text, function.line());
        Matcher nextFunction = FUNCTION.matcher(text);
        int end = text.length();
        if (nextFunction.find(start + 1)) {
            end = nextFunction.start();
        }
        String excerpt = text.substring(start, end).stripTrailing();
        boolean truncated = excerpt.length() > maxChars;
        if (truncated) {
            excerpt = excerpt.substring(0, maxChars) + "\n... [source excerpt truncated; use the reported file and line for full code]";
        }
        return new LookupResult(function, 1, excerpt, truncated);
    }

    private static String normalizeAddress(String input) {
        Matcher matcher = ADDRESS.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("address must be a 32-bit guest address, e.g. 0x82139370");
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    private static int offsetOfLine(String source, int oneBasedLine) {
        int offset = 0;
        for (int line = 1; line < oneBasedLine; line++) {
            int newline = source.indexOf('\n', offset);
            if (newline < 0) {
                return source.length();
            }
            offset = newline + 1;
        }
        return offset;
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
