
package dev.nubstack.gatekeep;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nubstack.spine.config.YamlConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;

@Plugin(id = "gatekeep", name = "gatekeep", version = "0.1.0-SNAPSHOT", authors = {"nubstack"})
public final class GatekeepPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private ConnectionGuard guard;
    private String denyMessage;

    @Inject
    public GatekeepPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        saveDefaultConfig();
        YamlConfig config = YamlConfig.load(dataDirectory.resolve("config.yml"));
        guard = new ConnectionGuard(
                config.optionalInt("max-attempts").orElse(5),
                Duration.ofSeconds(config.optionalInt("window-seconds").orElse(60)));
        denyMessage = config.optionalString("deny-message")
                .orElse("<red>Too many connections. Try again later.</red>");
        proxy.getEventManager().register(this, new GatekeepListener(this));
        logger.info("gatekeep enabled");
    }

    ConnectionGuard guard() { return guard; }
    String denyMessage() { return denyMessage; }

    private void saveDefaultConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.yml");
            if (!Files.exists(config)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
