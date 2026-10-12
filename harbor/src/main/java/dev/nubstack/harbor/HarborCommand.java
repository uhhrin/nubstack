package dev.nubstack.harbor;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;

public final class HarborCommand implements SimpleCommand {

    private static final String ADMIN = "harbor.admin";

    private final HarborPlugin plugin;

    public HarborCommand(HarborPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();
        if (args.length == 0) {
            sendStatus(source, plugin.defaultServer());
            source.sendMessage(help(source));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> source.sendMessage(help(source));
            case "status" -> sendStatus(source, args.length > 1 ? args[1] : plugin.defaultServer());
            case "leave" -> leave(source);
            case "join" -> join(source, args);
            case "list" -> list(source);
            case "reload" -> reload(source);
            case "admin" -> admin(source, args);
            default -> source.sendMessage(Messages.prefixed("harbor").error("Unknown subcommand. Try /harbor help."));
        }
    }

    private void sendStatus(CommandSource source, String server) {
        if (plugin.proxy().getServer(server).isEmpty()) {
            source.sendMessage(HarborTexts.unknownServer(server));
            return;
        }
        int position = -1;
        int priority = 0;
        if (source instanceof Player player) {
            position = plugin.queues().position(server, player.getUniqueId());
            priority = plugin.queues().priorityOf(server, player.getUniqueId()).orElse(plugin.priorityFor(player));
        }
        var health = plugin.health().get(server);
        String label = plugin.queues().isPaused(server)
                ? "paused"
                : plugin.health().statusLabel(server);
        source.sendMessage(HarborTexts.statusLine(
                server,
                label,
                plugin.queues().size(server),
                position,
                priority,
                health == null ? null : health.checkedAt(),
                plugin.health().isAvailable(server) && !plugin.queues().isPaused(server)));
    }

    private void leave(CommandSource source) {
        if (!(source instanceof Player player)) {
            source.sendMessage(Messages.prefixed("harbor").error("Players only."));
            return;
        }
        boolean removed = plugin.queues().leave(player.getUniqueId());
        plugin.clearBossBar(player);
        source.sendMessage(Messages.prefixed("harbor").success(removed ? "Left queue." : "Not queued."));
    }

    private void join(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            source.sendMessage(Messages.prefixed("harbor").error("Players only."));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Messages.prefixed("harbor").warn("Usage: /harbor join <server>"));
            return;
        }
        String serverName = args[1];
        Optional<RegisteredServer> server = plugin.proxy().getServer(serverName);
        if (server.isEmpty()) {
            source.sendMessage(HarborTexts.unknownServer(serverName));
            return;
        }
        if (plugin.health().isAvailable(serverName) && !plugin.queues().isPaused(serverName)) {
            plugin.queues().leave(player.getUniqueId());
            plugin.sendThrough(player, server.get());
            return;
        }
        plugin.enqueuePlayer(player, serverName);
    }

    private void list(CommandSource source) {
        List<String> servers = new ArrayList<>(plugin.queues().queuedServers());
        for (RegisteredServer registered : plugin.proxy().getAllServers()) {
            String name = registered.getServerInfo().getName();
            if (!servers.contains(name)) {
                servers.add(name);
            }
        }
        if (servers.isEmpty()) {
            source.sendMessage(Messages.prefixed("harbor").info("No registered servers."));
            return;
        }
        for (String server : servers) {
            String label = plugin.queues().isPaused(server) ? "paused" : plugin.health().statusLabel(server);
            source.sendMessage(Messages.prefixed("harbor").info(
                    server + " . " + label + " . queue " + plugin.queues().size(server)));
        }
    }

    private void reload(CommandSource source) {
        if (!source.hasPermission(ADMIN)) {
            source.sendMessage(Messages.prefixed("harbor").error("No permission."));
            return;
        }
        plugin.reloadConfig();
        plugin.refreshHealth();
        source.sendMessage(Messages.prefixed("harbor").success("Reloaded harbor config."));
    }

    private void admin(CommandSource source, String[] args) {
        if (!source.hasPermission(ADMIN)) {
            source.sendMessage(Messages.prefixed("harbor").error("No permission."));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(Messages.prefixed("harbor").warn(
                    "Usage: /harbor admin <pause|resume|clear|kickqueue|send> ..."));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pause" -> {
                String server = requireServerArg(source, args, 2);
                if (server == null) {
                    return;
                }
                plugin.queues().pause(server);
                source.sendMessage(Messages.prefixed("harbor").success("Paused sends for " + server + "."));
            }
            case "resume" -> {
                String server = requireServerArg(source, args, 2);
                if (server == null) {
                    return;
                }
                plugin.queues().resume(server);
                source.sendMessage(Messages.prefixed("harbor").success("Resumed sends for " + server + "."));
            }
            case "clear" -> {
                String server = requireServerArg(source, args, 2);
                if (server == null) {
                    return;
                }
                List<QueueEntry> cleared = plugin.queues().clear(server);
                for (QueueEntry entry : cleared) {
                    plugin.proxy().getPlayer(entry.playerId()).ifPresent(player -> {
                        plugin.clearBossBar(player);
                        player.sendMessage(Messages.prefixed("harbor").warn(
                                "Queue for " + server + " was cleared by staff."));
                    });
                }
                source.sendMessage(Messages.prefixed("harbor").success(
                        "Cleared " + cleared.size() + " from " + server + "."));
            }
            case "kickqueue" -> {
                if (args.length < 3) {
                    source.sendMessage(Messages.prefixed("harbor").warn("Usage: /harbor admin kickqueue <player>"));
                    return;
                }
                Optional<Player> target = plugin.proxy().getPlayer(args[2]);
                if (target.isEmpty()) {
                    source.sendMessage(Messages.prefixed("harbor").error("Player not online."));
                    return;
                }
                boolean removed = plugin.queues().leave(target.get().getUniqueId());
                plugin.clearBossBar(target.get());
                if (removed) {
                    target.get().sendMessage(Messages.prefixed("harbor").warn("Removed from queue by staff."));
                }
                source.sendMessage(Messages.prefixed("harbor").success(
                        removed ? "Removed " + args[2] + " from queue." : args[2] + " was not queued."));
            }
            case "send" -> {
                if (args.length < 4) {
                    source.sendMessage(Messages.prefixed("harbor").warn(
                            "Usage: /harbor admin send <player> <server>"));
                    return;
                }
                Optional<Player> target = plugin.proxy().getPlayer(args[2]);
                Optional<RegisteredServer> server = plugin.proxy().getServer(args[3]);
                if (target.isEmpty()) {
                    source.sendMessage(Messages.prefixed("harbor").error("Player not online."));
                    return;
                }
                if (server.isEmpty()) {
                    source.sendMessage(HarborTexts.unknownServer(args[3]));
                    return;
                }
                plugin.queues().leave(target.get().getUniqueId());
                plugin.sendThrough(target.get(), server.get());
                source.sendMessage(Messages.prefixed("harbor").success(
                        "Sending " + args[2] + " to " + args[3] + "."));
            }
            default -> source.sendMessage(Messages.prefixed("harbor").error("Unknown admin action."));
        }
    }

    private String requireServerArg(CommandSource source, String[] args, int index) {
        if (args.length <= index) {
            source.sendMessage(Messages.prefixed("harbor").warn("Server name required."));
            return null;
        }
        String server = args[index];
        if (plugin.proxy().getServer(server).isEmpty()) {
            source.sendMessage(HarborTexts.unknownServer(server));
            return null;
        }
        return server;
    }

    private Component help(CommandSource source) {
        return HelpBuilder.root("harbor")
                .entry("/harbor", "Status for default server + help", null)
                .entry("/harbor status [server]", "Queue, health, your position", null)
                .entry("/harbor join <server>", "Enter queue or join when healthy", null)
                .entry("/harbor leave", "Leave your queue", null)
                .entry("/harbor list", "Summary of servers and queues", null)
                .entry("/harbor admin pause|resume|clear <server>", "Control queue sends", ADMIN)
                .entry("/harbor admin kickqueue <player>", "Remove a player from queue", ADMIN)
                .entry("/harbor admin send <player> <server>", "Bypass queue", ADMIN)
                .entry("/harbor reload", "Reload config", ADMIN)
                .build(source::hasPermission, 1, 20);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("status", "join", "leave", "list", "help", "admin", "reload");
        }
        if (args.length == 1) {
            return filter(List.of("status", "join", "leave", "list", "help", "admin", "reload"), args[0]);
        }
        if (args.length == 2 && ("status".equalsIgnoreCase(args[0]) || "join".equalsIgnoreCase(args[0]))) {
            return filter(serverNames(), args[1]);
        }
        if ("admin".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("pause", "resume", "clear", "kickqueue", "send"), args[1]);
            }
            if (args.length == 3 && List.of("pause", "resume", "clear", "send").contains(args[1].toLowerCase(Locale.ROOT))) {
                return filter(serverNames(), args[2]);
            }
            if (args.length == 3 && "kickqueue".equalsIgnoreCase(args[1])) {
                return filter(playerNames(), args[2]);
            }
            if (args.length == 4 && "send".equalsIgnoreCase(args[1])) {
                return filter(serverNames(), args[3]);
            }
        }
        return List.of();
    }

    private List<String> serverNames() {
        List<String> names = new ArrayList<>();
        for (RegisteredServer server : plugin.proxy().getAllServers()) {
            names.add(server.getServerInfo().getName());
        }
        return names;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.proxy().getAllPlayers()) {
            names.add(player.getUsername());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
