package dev.nubstack.gatekeep;

import dev.nubstack.spine.config.ConfigException;
import dev.nubstack.spine.config.YamlConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

record GatekeepConfig(ConnectionGuard.Settings settings, String denyMessage) {

    static final String DEFAULT_DENY =
            "<red>Too many connection attempts from your address. Please wait a moment and try again.</red>";

    private static final List<String> DEFAULT_EXEMPT = List.of("127.0.0.0/8", "::1/128");
    private static final List<String> DOCKER_PRIVATE = List.of("172.16.0.0/12", "192.168.0.0/16", "10.0.0.0/8");

    static GatekeepConfig load(Path path) {
        YamlConfig config = YamlConfig.load(path);
        List<String> ranges = new ArrayList<>(exemptList(path));
        if (config.optionalBoolean("exempt-docker-private", false)) {
            ranges.addAll(DOCKER_PRIVATE);
        }
        List<Cidr> exempt = new ArrayList<>();
        try {
            for (String range : ranges) {
                exempt.add(Cidr.parse(range));
            }
            ConnectionGuard.Settings settings = new ConnectionGuard.Settings(
                    config.optionalInt("max-attempts").orElse(5),
                    Duration.ofSeconds(config.optionalInt("window-seconds").orElse(60)),
                    config.optionalBoolean("ipv6-group-64", true),
                    exempt);
            return new GatekeepConfig(settings, config.optionalString("deny-message").orElse(DEFAULT_DENY));
        } catch (IllegalArgumentException exception) {
            throw new ConfigException("Invalid gatekeep config: " + exception.getMessage(), exception);
        }
    }

    /**
     * shortcut: YamlConfig has no string-list reader; read the exempt list with SnakeYAML.
     * Upgrade when spine exposes one.
     */
    private static List<String> exemptList(Path path) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(50);
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = new Yaml(options).load(in);
            Object value = loaded instanceof Map<?, ?> root && root.containsKey("exempt")
                    ? root.get("exempt")
                    : DEFAULT_EXEMPT;
            if (!(value instanceof List<?> list)) {
                throw new ConfigException("Expected a list at 'exempt'");
            }
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(String.valueOf(item));
            }
            return out;
        } catch (IOException exception) {
            throw new ConfigException("Failed to read config: " + path, exception);
        }
    }
}
