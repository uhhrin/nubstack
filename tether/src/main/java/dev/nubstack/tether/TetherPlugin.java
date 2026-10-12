package dev.nubstack.tether;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.ConfigException;
import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import dev.nubstack.spine.ui.ActionBars;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TetherPlugin extends JavaPlugin implements Listener {

    private static final long SOFT_NAG_DELAY_TICKS = 40L;

    private final PrefixedMessages msg = Messages.prefixed("tether");
    private final LinkCountdown countdown = new LinkCountdown(Clock.systemUTC());
    private final Object lifecycle = new Object();
    private HikariDataSource dataSource;
    private BukkitTask countdownTask;
    private volatile TetherSettings settings;
    private volatile LinkService links;
    private volatile DiscordBridge bridge;
    private volatile TetherSettings.GateMode gate = TetherSettings.GateMode.OFF;

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        TetherSettings loaded;
        try {
            loaded = loadSettings();
        } catch (ConfigException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dataSource = DataSources.create(new DatabaseSettings(
                loaded.jdbcUrl(), loaded.dbUser(), loaded.dbPassword(), "tether", loaded.poolSize()));
        synchronized (lifecycle) {
            apply(loaded);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        TetherCommand command = new TetherCommand(this);
        PluginCommand tether = getCommand("tether");
        tether.setExecutor(command);
        tether.setTabCompleter(command);
        countdownTask = Bukkit.getScheduler().runTaskTimer(this, this::tickCountdown, 20L, 20L);
        getLogger().info("tether enabled (gate=" + gate.name().toLowerCase() + ", discord="
                + (settings.discordEnabled() ? "on" : "off") + ")");
    }

    @Override
    public void onDisable() {
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        synchronized (lifecycle) {
            DiscordBridge current = bridge;
            bridge = null;
            if (current != null) {
                current.close();
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    LinkService links() {
        return links;
    }

    LinkCountdown countdown() {
        return countdown;
    }

    String bridgeStatus() {
        DiscordBridge current = bridge;
        if (!settings.discordEnabled()) {
            return "off (no token)";
        }
        return current == null ? "failed to start" : current.status();
    }

    String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }

    /** Runs database work off the main thread and reports failures to the sender. */
    void db(CommandSender sender, Runnable work) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "tether database operation failed", e);
                sender.sendMessage(msg.error("Database error, see the console."));
            }
        });
    }

    /**
     * Shows the link card, reusing a pending code unless {@code renew}.
     *
     * @param quietIfLinked true for the join nag, false to tell a linked player about their link
     */
    void issueCard(Player player, boolean renew, boolean quietIfLinked) {
        if (!settings.discordEnabled()) {
            if (!quietIfLinked) {
                player.sendMessage(msg.warn("Discord is not configured on this server."));
            }
            return;
        }
        db(player, () -> {
            LinkService service = links;
            Optional<LinkService.LinkRow> linked = service.linkByPlayer(player.getUniqueId());
            if (linked.isPresent()) {
                if (!quietIfLinked) {
                    String tag = linked.get().discordTag() == null ? linked.get().discordId() : linked.get().discordTag();
                    player.sendMessage(msg.info("Already linked as @" + tag + ". Use /tether unlink to change it."));
                }
                return;
            }
            String code;
            long remaining;
            Optional<LinkService.PendingCode> pending = renew ? Optional.empty() : service.pendingCode(player.getUniqueId());
            if (pending.isPresent()) {
                code = pending.get().code();
                remaining = pending.get().remainingSeconds(Clock.systemUTC());
            } else {
                code = service.createCode(player.getUniqueId());
                remaining = settings.ttlSeconds();
            }
            countdown.track(player.getUniqueId(), remaining);
            player.sendMessage(LinkCard.card(code, settings.clipboardPayload(), remaining, settings.preferSlashCommands()));
        });
    }

    /** Re-reads config.yml. Database settings need a restart. */
    void reload(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (lifecycle) {
                TetherSettings next;
                try {
                    next = loadSettings();
                } catch (ConfigException e) {
                    sender.sendMessage(msg.error("Reload failed, keeping old settings: " + e.getMessage()));
                    return;
                }
                TetherSettings old = settings;
                boolean dbChanged = !old.jdbcUrl().equals(next.jdbcUrl())
                        || !old.dbUser().equals(next.dbUser())
                        || !old.dbPassword().equals(next.dbPassword())
                        || old.poolSize() != next.poolSize();
                DiscordBridge previous = bridge;
                bridge = null;
                if (previous != null) {
                    previous.close();
                }
                apply(next);
                sender.sendMessage(msg.success("Reloaded config.yml."));
                if (dbChanged) {
                    sender.sendMessage(msg.warn("database.* changes need a server restart."));
                }
            }
        });
    }

    private TetherSettings loadSettings() {
        Path file = getDataFolder().toPath().resolve("config.yml");
        return TetherSettings.from(YamlConfig.load(file));
    }

    /** Swaps in new settings and starts the bridge. Caller holds {@code lifecycle}. */
    private void apply(TetherSettings next) {
        settings = next;
        links = new LinkService(dataSource, next.codeLength(), next.ttlSeconds(), next.maxRedeemAttempts());
        links.init();
        gate = next.gateMode();
        if (!next.discordEnabled()) {
            if (gate != TetherSettings.GateMode.OFF) {
                // shortcut: gate needs Discord to be satisfiable, so it is skipped without a token
                getLogger().warning("gate.mode is " + gate.name().toLowerCase()
                        + " but discord.token is blank; gate disabled so players are not locked out");
                gate = TetherSettings.GateMode.OFF;
            }
            getLogger().warning("Discord token empty; linking and chat bridge disabled");
            return;
        }
        DiscordLinkHandler handler = new DiscordLinkHandler(links, new DiscordLinkHandler.Host() {
            @Override
            public void onLinked(UUID playerId, String discordId, String discordTag) {
                runSync(() -> announceLinked(playerId, discordTag));
            }

            @Override
            public String playerName(UUID playerId) {
                return nameOf(playerId);
            }
        });
        DiscordBridge candidate = new DiscordBridge(next, handler, this::broadcastFromDiscord, getLogger());
        try {
            candidate.start();
            bridge = candidate;
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Discord bridge failed to start (check discord.token)", e);
            candidate.close();
        }
    }

    private void runSync(Runnable task) {
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private void announceLinked(UUID playerId, String discordTag) {
        countdown.stop(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.sendMessage(LinkCard.linkedSuccess(discordTag));
        player.showTitle(LinkCard.successTitle(discordTag));
        player.playSound(LinkCard.successSound());
        player.sendActionBar(Component.empty());
    }

    private void broadcastFromDiscord(String author, String content) {
        // Component.text never parses tags, so Discord users cannot inject formatting or click events
        Component line = Component.text()
                .append(Component.text("[Discord] ", NamedTextColor.BLUE))
                .append(Component.text(author, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(content, NamedTextColor.GRAY))
                .build();
        runSync(() -> Bukkit.broadcast(line));
    }

    private void tickCountdown() {
        countdown.tick((id, left) -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                countdown.stop(id);
                return;
            }
            player.sendActionBar(ActionBars.countdown("Link code", left));
        }, id -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendActionBar(Component.empty());
                player.sendMessage(msg.warn("Your link code expired. Run /tether link renew for a new one."));
            }
        });
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (gate != TetherSettings.GateMode.HARD) {
            return;
        }
        // shortcut: no bypass permission, pre-login has no permission context; move the check to join if ops need one
        try {
            LinkService service = links;
            if (service.isLinked(event.getUniqueId())) {
                return;
            }
            Optional<LinkService.PendingCode> pending = service.pendingCode(event.getUniqueId());
            String code = pending.map(LinkService.PendingCode::code)
                    .orElseGet(() -> service.createCode(event.getUniqueId()));
            long remaining = pending.map(p -> p.remainingSeconds(Clock.systemUTC()))
                    .orElse((long) settings.ttlSeconds());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LinkCard.hardGateKick(code, remaining, settings.preferSlashCommands()));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "tether gate check failed; denying join", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Link service unavailable. Try again shortly."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (gate != TetherSettings.GateMode.SOFT) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && player.hasPermission("tether.link")) {
                issueCard(player, false, true);
            }
        }, SOFT_NAG_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        countdown.stop(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        DiscordBridge current = bridge;
        if (current == null) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        current.sendChat(event.getPlayer().getName(), plain);
    }
}
