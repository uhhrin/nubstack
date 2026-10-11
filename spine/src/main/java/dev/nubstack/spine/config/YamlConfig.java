package dev.nubstack.spine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Thin YAML map wrapper with dotted-path lookups for shared plugin config files.
 */
public final class YamlConfig {

    private final Map<String, Object> root;

    private YamlConfig(Map<String, Object> root) {
        this.root = root;
    }

    public static YamlConfig load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("Config file does not exist: " + path);
        }
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(50);
        Yaml yaml = new Yaml(options);
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new ConfigException("Config root must be a map: " + path);
            }
            return new YamlConfig(castMap(map));
        } catch (IOException exception) {
            throw new ConfigException("Failed to read config: " + path, exception);
        }
    }

    public String requireString(String path) {
        Object value = requireValue(path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigException("Expected non-blank string at '" + path + "'");
        }
        return text.trim();
    }

    public Optional<String> optionalString(String path) {
        Object value = resolve(path);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConfigException("Expected non-blank string at '" + path + "'");
        }
        return Optional.of(text.trim());
    }

    public int requireInt(String path) {
        Object value = requireValue(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException exception) {
                throw new ConfigException("Expected integer at '" + path + "'", exception);
            }
        }
        throw new ConfigException("Expected integer at '" + path + "'");
    }

    public OptionalInt optionalInt(String path) {
        Object value = resolve(path);
        if (value == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(requireInt(path));
    }

    public boolean requireBoolean(String path) {
        Object value = requireValue(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return false;
            }
        }
        throw new ConfigException("Expected boolean at '" + path + "'");
    }

    public YamlConfig section(String path) {
        Object value = requireValue(path);
        if (!(value instanceof Map<?, ?> map)) {
            throw new ConfigException("Expected map at '" + path + "'");
        }
        return new YamlConfig(castMap(map));
    }

    private Object requireValue(String path) {
        Object value = resolve(path);
        if (value == null) {
            throw new ConfigException("Missing config value '" + path + "'");
        }
        return value;
    }

    private Object resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new ConfigException("Config path must not be blank");
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ConfigException("Config keys must be strings");
            }
            copy.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
