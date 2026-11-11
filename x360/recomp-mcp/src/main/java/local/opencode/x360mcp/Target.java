package local.opencode.x360mcp;

import java.nio.file.Path;

public record Target(
        String id,
        String name,
        Path originalXex,
        Path recompRoot,
        Path generatedSourceRoot,
        Path nativeExecutable,
        Path xeniaRoot) {}
