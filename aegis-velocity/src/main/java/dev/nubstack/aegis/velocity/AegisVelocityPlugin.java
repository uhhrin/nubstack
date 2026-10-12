package dev.nubstack.aegis.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nubstack.aegis.AegisRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(
        id = "aegis",
        name = "aegis",
        version = "0.1.0-SNAPSHOT",
        description = "Network bans synced over Redis",
        authors = {"nubstack"})
public final class AegisVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private AegisRuntime runtime;

    @Inject
    public AegisVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        saveDefaultConfig();
        runtime = AegisRuntime.start(dataDirectory, true);
        proxy.getEventManager().register(this, new AegisVelocityListener(runtime));
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("aegis").plugin(this).build(),
                new AegisVelocityCommand(this, proxy, runtime));
        // shortcut: alias registration is startup-only; reload toggles reject at execute time
        for (String alias : runtime.settings().enabledAliases()) {
            proxy.getCommandManager().register(
                    proxy.getCommandManager().metaBuilder(alias).plugin(this).build(),
                    new AegisVelocityCommand(this, proxy, runtime, alias));
        }
        logger.info("aegis enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    ProxyServer proxy() {
        return proxy;
    }

    private void saveDefaultConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.yml");
            if (!Files.exists(config)) {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (input == null) {
                        throw new IllegalStateException("Bundled config.yml missing");
                    }
                    Files.copy(input, config);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create aegis config", exception);
        }
    }
}
