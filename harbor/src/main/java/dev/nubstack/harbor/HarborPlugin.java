
package dev.nubstack.harbor;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.nubstack.spine.config.YamlConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

@Plugin(id = "harbor", name = "harbor", version = "0.1.0-SNAPSHOT", authors = {"nubstack"})
public final class HarborPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final QueueManager queues = new QueueManager();
    private final ServerHealthRegistry health = new ServerHealthRegistry();
    private Map<String, Integer> priorityPermissions = Map.of();
    private String defaultServer = "survival";

    @Inject
    public HarborPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        saveDefaultConfig();
        YamlConfig config = YamlConfig.load(dataDirectory.resolve("config.yml"));
        defaultServer = config.requireString("default-server");
        Map<String, Integer> priorities = new HashMap<>();
        // optional map section may be absent; parse known keys from flat optional strings later
        priorities.put("harbor.priority.vip", 10);
        priorities.put("harbor.priority.staff", 50);
        priorityPermissions = Map.copyOf(priorities);
        proxy.getEventManager().register(this, new HarborListener(this));
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("harbor").plugin(this).build(),
                new HarborCommand(this));
        long interval = config.optionalInt("health-interval-seconds").orElse(15);
        proxy.getScheduler().buildTask(this, this::refreshHealth)
                .repeat(interval, TimeUnit.SECONDS)
                .schedule();
        refreshHealth();
        logger.info("harbor enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        // nothing pooled
    }

    ProxyServer proxy() { return proxy; }
    QueueManager queues() { return queues; }
    ServerHealthRegistry health() { return health; }
    String defaultServer() { return defaultServer; }

    int priorityFor(com.velocitypowered.api.proxy.Player player) {
        int best = 0;
        for (Map.Entry<String, Integer> entry : priorityPermissions.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    void refreshHealth() {
        for (RegisteredServer server : proxy.getAllServers()) {
            server.ping().thenAccept(ping -> {
                int online = ping.getPlayers().map(p -> p.getOnline()).orElse(0);
                int max = ping.getPlayers().map(p -> p.getMax()).orElse(Integer.MAX_VALUE);
                health.update(server.getServerInfo().getName(), true, online, max);
            }).exceptionally(error -> {
                health.update(server.getServerInfo().getName(), false, 0, 0);
                return null;
            });
        }
    }

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
