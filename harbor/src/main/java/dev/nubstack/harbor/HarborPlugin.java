package dev.nubstack.harbor;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.text.Messages;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

@Plugin(id = "harbor", name = "harbor", version = "0.1.0-SNAPSHOT", authors = {"nubstack"})
public final class HarborPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final QueueManager queues = new QueueManager();
    private final ServerHealthRegistry health = new ServerHealthRegistry();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> joinNowNotified = new ConcurrentHashMap<>();
    private Map<String, Integer> priorityPermissions = Map.of();
    private String defaultServer = "survival";
    private Duration healthTimeout = Duration.ofSeconds(3);
    private ScheduledTask hudTask;

    @Inject
    public HarborPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        ConfigFiles.copyDefaultIfMissing(
                dataDirectory,
                "config.yml",
                () -> getClass().getClassLoader().getResourceAsStream("config.yml"));
        reloadConfig();
        proxy.getEventManager().register(this, new HarborListener(this));
        var meta = proxy.getCommandManager().metaBuilder("harbor")
                .aliases("queue")
                .plugin(this)
                .build();
        proxy.getCommandManager().register(meta, new HarborCommand(this));
        long interval = YamlConfig.load(dataDirectory.resolve("config.yml"))
                .optionalInt("health-interval-seconds")
                .orElse(15);
        proxy.getScheduler().buildTask(this, this::refreshHealth)
                .repeat(interval, TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, this::drainQueues)
                .repeat(2, TimeUnit.SECONDS)
                .schedule();
        hudTask = proxy.getScheduler().buildTask(this, this::refreshHud)
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
        refreshHealth();
        logger.info("harbor enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (hudTask != null) {
            hudTask.cancel();
        }
        for (Player player : proxy.getAllPlayers()) {
            clearBossBar(player);
        }
    }

    ProxyServer proxy() {
        return proxy;
    }

    QueueManager queues() {
        return queues;
    }

    ServerHealthRegistry health() {
        return health;
    }

    String defaultServer() {
        return defaultServer;
    }

    boolean reloadConfig() {
        YamlConfig config = YamlConfig.load(dataDirectory.resolve("config.yml"));
        defaultServer = config.requireString("default-server");
        priorityPermissions = PriorityPermissions.load(dataDirectory.resolve("config.yml"));
        queues.setMaxQueueSize(config.optionalInt("max-queue-size").orElse(200));
        healthTimeout = Duration.ofMillis(config.optionalInt("health-timeout-millis").orElse(3000));
        health.setThresholds(
                config.optionalInt("health-fail-threshold").orElse(2),
                config.optionalInt("health-success-threshold").orElse(2));
        return true;
    }

    int priorityFor(Player player) {
        int best = 0;
        for (Map.Entry<String, Integer> entry : priorityPermissions.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    void refreshHealth() {
        PingOptions options = PingOptions.builder().timeout(healthTimeout).build();
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            server.ping(options).thenAccept(ping -> {
                int online = ping.getPlayers().map(p -> p.getOnline()).orElse(0);
                int max = ping.getPlayers().map(p -> p.getMax()).orElse(Integer.MAX_VALUE);
                health.update(name, true, online, max);
                maybeNotifyJoinNow(name);
            }).exceptionally(error -> {
                health.update(name, false, 0, 0);
                joinNowNotified.remove(name);
                return null;
            });
        }
    }

    void enqueuePlayer(Player player, String server) {
        QueueManager.EnqueueResult result = queues.enqueue(
                server,
                queues.entry(player.getUniqueId(), player.getUsername(), priorityFor(player)));
        if (result == QueueManager.EnqueueResult.FULL) {
            player.sendMessage(HarborTexts.queueFull(server));
            return;
        }
        showQueued(player, server);
    }

    void showQueued(Player player, String server) {
        int position = queues.position(server, player.getUniqueId());
        int priority = queues.priorityOf(server, player.getUniqueId()).orElse(0);
        Long eta = queues.etaSeconds(position).isPresent()
                ? queues.etaSeconds(position).getAsLong()
                : null;
        player.sendMessage(Messages.prefixed("harbor").warn(
                "Server busy. Queued for " + server + " at #" + position));
        updateBossBar(player, server, position, eta);
        if (health.isAvailable(server) && !queues.isPaused(server)) {
            player.sendMessage(Component.text()
                    .append(Messages.prefixed("harbor").info("Target is ready. "))
                    .append(HarborTexts.joinNowButton(server))
                    .build());
        }
    }

    void clearBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    void sendThrough(Player player, RegisteredServer server) {
        clearBossBar(player);
        player.showTitle(Title.title(
                Component.text("Sending...", NamedTextColor.GREEN),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(200))));
        player.playSound(Sound.sound(
                Key.key("entity.experience_orb.pickup"),
                Sound.Source.MASTER,
                1f,
                1f));
        bypass.add(player.getUniqueId());
        player.createConnectionRequest(server).fireAndForget();
        queues.recordSend(Instant.now());
    }

    boolean consumeBypass(UUID playerId) {
        return bypass.remove(playerId);
    }

    private void maybeNotifyJoinNow(String server) {
        if (!health.isAvailable(server) || queues.isPaused(server) || queues.size(server) == 0) {
            return;
        }
        if (Boolean.TRUE.equals(joinNowNotified.putIfAbsent(server, Boolean.TRUE))) {
            return;
        }
        for (Player player : proxy.getAllPlayers()) {
            if (queues.position(server, player.getUniqueId()) > 0) {
                player.sendMessage(Component.text()
                        .append(Messages.prefixed("harbor").success(server + " is healthy. "))
                        .append(HarborTexts.joinNowButton(server))
                        .build());
            }
        }
    }

    private void drainQueues() {
        for (String serverName : queues.queuedServers()) {
            if (queues.isPaused(serverName) || !health.isAvailable(serverName)) {
                continue;
            }
            Optional<QueueEntry> next = queues.peek(serverName);
            if (next.isEmpty()) {
                continue;
            }
            Optional<Player> player = proxy.getPlayer(next.get().playerId());
            Optional<RegisteredServer> server = proxy.getServer(serverName);
            if (player.isEmpty() || server.isEmpty()) {
                queues.poll(serverName);
                continue;
            }
            // Wait for explicit join/send or auto-connect only the head when already attempting.
            // shortcut: auto-drain head every tick when healthy; upgrade when rejoin/party rules appear.
            queues.poll(serverName);
            sendThrough(player.get(), server.get());
        }
    }

    private void refreshHud() {
        for (Player player : proxy.getAllPlayers()) {
            Optional<String> server = queues.serverOf(player.getUniqueId());
            if (server.isEmpty()) {
                clearBossBar(player);
                continue;
            }
            int position = queues.position(server.get(), player.getUniqueId());
            Long eta = queues.etaSeconds(position).isPresent()
                    ? queues.etaSeconds(position).getAsLong()
                    : null;
            updateBossBar(player, server.get(), position, eta);
        }
    }

    private void updateBossBar(Player player, String server, int position, Long etaSeconds) {
        String label = HarborTexts.bossBarLabel(server, position, etaSeconds);
        float progress = position <= 1 ? 1f : Math.max(0.05f, 1f / position);
        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(
                    Component.text(label),
                    progress,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.name(Component.text(label));
        bar.progress(progress);
    }
}
