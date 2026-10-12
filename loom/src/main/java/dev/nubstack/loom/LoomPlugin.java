package dev.nubstack.loom;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class LoomPlugin extends JavaPlugin implements Listener {

    private final Clock clock = Clock.systemUTC();
    /** Players whose inventory was loaded here and whose lease this server holds. */
    private final Set<UUID> synced = ConcurrentHashMap.newKeySet();
    private final Map<UUID, JoinSession> joining = new ConcurrentHashMap<>();
    private final Set<UUID> leaseWarned = ConcurrentHashMap.newKeySet();
    /** Quit saves still in flight; a quick rejoin on this server must not load before they land. */
    private final Set<UUID> saving = ConcurrentHashMap.newKeySet();

    private HikariDataSource dataSource;
    private PlayerDataStore store;
    private volatile SyncCoordinator sync;
    private String serverId;
    private BukkitTask renewTask;

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        LoomSettings settings = LoomSettings.from(config, fallbackServerId());
        serverId = settings.serverId();
        dataSource = DataSources.create(new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "loom",
                config.optionalInt("database.pool-size").orElse(5)));
        store = new PlayerDataStore(dataSource, clock);
        store.init();
        sync = coordinator(settings);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new InspectMenu(), this);
        LoomCommand command = new LoomCommand(this);
        getCommand("loom").setExecutor(command);
        getCommand("loom").setTabCompleter(command);
        startRenewTask();
        getLogger().info("loom enabled as server-id '" + serverId + "'");
    }

    @Override
    public void onDisable() {
        if (renewTask != null) {
            renewTask.cancel();
        }
        joining.values().forEach(JoinSession::cancel);
        if (sync != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                JoinSession session = joining.remove(id);
                boolean wasSynced = synced.remove(id);
                try {
                    if (wasSynced) {
                        sync.saveAndRelease(id, serialize(player));
                    } else if (session != null && session.leaseHeld) {
                        sync.release(id);
                    }
                } catch (RuntimeException e) {
                    getLogger().severe("Could not save inventory of " + id + " on shutdown: " + e);
                }
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    SyncCoordinator sync() {
        return sync;
    }

    Clock clock() {
        return clock;
    }

    boolean isSynced(UUID playerId) {
        return synced.contains(playerId);
    }

    /** Re-reads sync tuning. The database settings and server-id need a restart. */
    LoomSettings reloadSettings() {
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        LoomSettings settings = LoomSettings.from(config, fallbackServerId());
        if (!settings.serverId().equals(serverId)) {
            throw new IllegalArgumentException("server-id changed, restart the server to apply it");
        }
        sync = coordinator(settings);
        startRenewTask();
        return settings;
    }

    String serialize(Player player) {
        return InventorySerializer.serialize(player.getInventory().getContents());
    }

    private SyncCoordinator coordinator(LoomSettings settings) {
        return new SyncCoordinator(
                store, clock, settings.serverId(), settings.leaseTtl(), settings.waitTimeout(), settings.failurePolicy());
    }

    private String fallbackServerId() {
        String fallback = "port-" + Bukkit.getPort();
        // shortcut: ports can collide across hosts, set server-id explicitly on every backend
        getLogger().fine("server-id not set, using " + fallback);
        return fallback;
    }

    private void startRenewTask() {
        if (renewTask != null) {
            renewTask.cancel();
        }
        long periodTicks = Math.max(20L, sync.leaseTtl().toSeconds() * 20L / 3L);
        renewTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::renewLeases, periodTicks, periodTicks);
    }

    private void renewLeases() {
        SyncCoordinator current = sync;
        for (UUID id : synced) {
            try {
                if (!current.renew(id) && leaseWarned.add(id)) {
                    getLogger().warning("Lost the sync lease of " + id + " (cleared or taken over); "
                            + "saving on quit will still be refused while another server holds it");
                }
            } catch (RuntimeException e) {
                getLogger().warning("Lease renewal failed for " + id + ": " + e);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JoinSession session = new JoinSession(player, clock.instant());
        joining.put(player.getUniqueId(), session);
        player.sendActionBar(LoomMessages.restoring());
        session.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> poll(session), 0L, 20L);
    }

    /** Runs off the main thread once per second until the lease is taken or the wait times out. */
    private void poll(JoinSession session) {
        if (session.cancelled) {
            return;
        }
        UUID id = session.player.getUniqueId();
        SyncCoordinator current = sync;
        SyncCoordinator.JoinDecision decision;
        try {
            decision = saving.contains(id) ? current.blocked(session.startedAt) : current.pollJoin(id, session.startedAt);
        } catch (RuntimeException e) {
            getLogger().warning("Sync lease check failed for " + id + ": " + e);
            decision = current.blocked(session.startedAt);
        }
        switch (decision) {
            case WAIT -> {
                int remaining = current.waitSecondsRemaining(session.startedAt);
                int total = (int) current.waitTimeout().toSeconds();
                Bukkit.getScheduler().runTask(this, () -> session.showWaiting(remaining, total));
            }
            case READY -> {
                session.cancelTask();
                session.leaseHeld = true;
                if (session.cancelled) {
                    releaseQuietly(id);
                    return;
                }
                loadAndApply(session, current);
            }
            default -> {
                session.cancelTask();
                Bukkit.getScheduler().runTask(this, () -> failJoin(session, current.failurePolicy()));
            }
        }
    }

    private void loadAndApply(JoinSession session, SyncCoordinator current) {
        UUID id = session.player.getUniqueId();
        Optional<String> blob;
        try {
            blob = current.loadBlob(id);
        } catch (RuntimeException e) {
            getLogger().severe("Could not load inventory of " + id + ": " + e);
            releaseQuietly(id);
            Bukkit.getScheduler().runTask(this, () -> {
                session.hideWaiting();
                joining.remove(id, session);
                session.player.sendMessage(LoomMessages.prefixed().error(
                        "Could not load your inventory. Your saved items are untouched and this session will not be saved."));
            });
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> applyOnMainThread(session, blob));
    }

    private void applyOnMainThread(JoinSession session, Optional<String> blob) {
        Player player = session.player;
        UUID id = player.getUniqueId();
        session.hideWaiting();
        if (session.cancelled || !player.isOnline()) {
            releaseQuietly(id);
            return;
        }
        joining.remove(id, session);
        if (blob.isPresent()) {
            try {
                ItemStack[] contents = InventorySerializer.deserialize(blob.get());
                player.getInventory().setContents(contents);
            } catch (RuntimeException e) {
                getLogger().severe("Stored inventory of " + id + " is unreadable, keeping the current one: " + e);
                releaseQuietly(id);
                player.sendMessage(LoomMessages.prefixed().error(
                        "Your stored inventory could not be read. You keep your current items and this session "
                                + "will not be saved. Ask staff to check the console."));
                return;
            }
        }
        leaseWarned.remove(id);
        synced.add(id);
        player.sendActionBar(LoomMessages.restored());
    }

    private void failJoin(JoinSession session, SyncCoordinator.FailurePolicy policy) {
        Player player = session.player;
        session.hideWaiting();
        joining.remove(player.getUniqueId(), session);
        if (!player.isOnline()) {
            return;
        }
        switch (policy) {
            case KICK -> player.kick(Component.text(
                    "Your inventory is still syncing from another server. Please rejoin in a few seconds."));
            case JOIN_EMPTY -> {
                player.getInventory().clear();
                player.sendMessage(LoomMessages.prefixed().warn(
                        "Sync lock timed out. You joined with an empty inventory and this session will not be saved. "
                                + "Your stored items are safe."));
            }
            case KEEP_LOCAL -> player.sendMessage(LoomMessages.prefixed().warn(
                    "Sync lock timed out. You keep this server's inventory and this session will not be saved."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        JoinSession session = joining.remove(id);
        if (session != null) {
            session.cancel();
            if (session.leaseHeld) {
                releaseAsync(id);
            }
            return;
        }
        if (!synced.remove(id)) {
            return;
        }
        leaseWarned.remove(id);
        SyncCoordinator current = sync;
        String blob;
        try {
            blob = serialize(player);
        } catch (RuntimeException e) {
            getLogger().severe("Could not serialize inventory of " + id + ", releasing lease without saving: " + e);
            releaseAsync(id);
            return;
        }
        saving.add(id);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (!current.saveAndRelease(id, blob)) {
                    getLogger().warning("Quit save of " + id + " refused: another server holds the lease");
                }
            } catch (RuntimeException e) {
                getLogger().severe("Quit save of " + id + " failed: " + e);
            } finally {
                saving.remove(id);
            }
        });
    }

    private void releaseAsync(UUID id) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> releaseQuietly(id));
    }

    private void releaseQuietly(UUID id) {
        try {
            sync.release(id);
        } catch (RuntimeException e) {
            getLogger().warning("Could not release lease of " + id + ": " + e);
        }
    }

    private static final class JoinSession {
        final Player player;
        final Instant startedAt;
        volatile boolean cancelled;
        volatile boolean leaseHeld;
        volatile BukkitTask task;
        private BossBar bar;

        JoinSession(Player player, Instant startedAt) {
            this.player = player;
            this.startedAt = startedAt;
        }

        void showWaiting(int remaining, int total) {
            if (cancelled || !player.isOnline()) {
                return;
            }
            BossBar next = LoomMessages.waitingBar(remaining, total);
            if (bar == null) {
                bar = next;
                player.showBossBar(bar);
            } else {
                bar.name(next.name());
                bar.progress(next.progress());
            }
        }

        void hideWaiting() {
            if (bar != null) {
                player.hideBossBar(bar);
                bar = null;
            }
        }

        void cancelTask() {
            BukkitTask current = task;
            if (current != null) {
                current.cancel();
            }
        }

        void cancel() {
            cancelled = true;
            cancelTask();
        }
    }
}
