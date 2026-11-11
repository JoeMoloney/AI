package local.opencode.x360mcp;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class TargetRegistry {
    private final Path configPath;
    private final Map<String, Target> targets;

    private TargetRegistry(Path configPath, Map<String, Target> targets) {
        this.configPath = configPath;
        this.targets = Map.copyOf(targets);
    }

    public static TargetRegistry load(Path configPath) throws IOException {
        if (!Files.isRegularFile(configPath)) {
            throw new IOException("Target registry not found: " + configPath
                    + ". Create it from x360/targets.example.properties; no game paths are guessed.");
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath)) {
            properties.load(reader);
        }

        List<String> ids = new ArrayList<>();
        for (String value : properties.getProperty("target.ids", "").split(",")) {
            String id = value.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("Target registry has no target.ids entries: " + configPath);
        }

        Map<String, Target> targets = new LinkedHashMap<>();
        for (String id : ids) {
            String prefix = "target." + id + ".";
            Target target = new Target(
                    id,
                    required(properties, prefix + "name"),
                    path(properties, prefix + "originalXex"),
                    path(properties, prefix + "recompRoot"),
                    path(properties, prefix + "generatedSourceRoot"),
                    path(properties, prefix + "nativeExecutable"),
                    path(properties, prefix + "xeniaRoot"));
            if (targets.put(id, target) != null) {
                throw new IOException("Duplicate target id in registry: " + id);
            }
        }
        return new TargetRegistry(configPath.toAbsolutePath().normalize(), targets);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required registry property: " + key);
        }
        return value.trim();
    }

    private static Path path(Properties properties, String key) throws IOException {
        return Path.of(required(properties, key)).toAbsolutePath().normalize();
    }

    public Path configPath() {
        return configPath;
    }

    public List<Target> list() {
        return targets.values().stream().toList();
    }

    public Target require(String id) {
        Target target = targets.get(id);
        if (target == null) {
            throw new IllegalArgumentException("Unknown target '" + id + "'. Configured targets: "
                    + String.join(", ", targets.keySet()));
        }
        return target;
    }
}
