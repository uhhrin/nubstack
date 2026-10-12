package dev.nubstack.harbor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

final class PriorityPermissions {

    private PriorityPermissions() {
    }

    /**
     * shortcut: YamlConfig cannot iterate map keys with dotted permission names; parse with SnakeYAML.
     * Upgrade when spine exposes a string-keyed map reader.
     */
    static Map<String, Integer> load(Path configPath) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(50);
        Yaml yaml = new Yaml(options);
        try (InputStream in = Files.newInputStream(configPath)) {
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> root)) {
                return Map.of();
            }
            if (!(root.get("priority-permissions") instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    continue;
                }
                if (entry.getValue() instanceof Number number) {
                    out.put(key, number.intValue());
                } else if (entry.getValue() instanceof String text) {
                    out.put(key, Integer.parseInt(text.trim()));
                }
            }
            return Map.copyOf(out);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read priority-permissions", exception);
        }
    }
}
