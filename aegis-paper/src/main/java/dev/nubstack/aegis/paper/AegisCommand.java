package dev.nubstack.aegis.paper;

import dev.nubstack.aegis.AegisChat;
import dev.nubstack.aegis.AegisCommandParser;
import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.aegis.PunishmentType;
import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.Messages;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "ban", "unban", "mute", "unmute", "warn", "history", "check", "reason",
            "rollback", "lookup", "reload", "confirm", "cancel");
    private static final List<String> DURATIONS = List.of("10m", "1h", "1d", "7d", "30d", "perm");

    private final JavaPlugin plugin;
    private final AegisRuntime runtime;
    private final HistoryGui historyGui;

    public AegisCommand(JavaPlugin plugin, AegisRuntime runtime, HistoryGui historyGui) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.historyGui = historyGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"aegis".equalsIgnoreCase(label) && !runtime.settings().aliasEnabled(label)) {
            sender.sendMessage(Messages.prefixed("aegis").error("Alias disabled: " + label));
            return true;
        }
        String[] effective = rewriteAlias(label, args);
        if (effective.length == 0) {
            sender.sendMessage(AegisChat.help(sender::hasPermission, 1));
            return true;
        }
        String sub = effective[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ban" -> punish(sender, PunishmentType.BAN, "aegis.ban", effective);
            case "mute" -> punish(sender, PunishmentType.MUTE, "aegis.mute", effective);
            case "warn" -> punish(sender, PunishmentType.WARN, "aegis.warn", effective);
            case "unban" -> revoke(sender, PunishmentType.BAN, "aegis.unban", effective);
            case "unmute" -> revoke(sender, PunishmentType.MUTE, "aegis.unmute", effective);
            case "history" -> history(sender, effective);
            case "check" -> check(sender, effective);
            case "reason" -> reason(sender, effective);
            case "rollback" -> rollback(sender, effective);
            case "lookup" -> lookup(sender, effective);
            case "reload" -> reload(sender);
            case "confirm" -> confirm(sender, effective, true);
            case "cancel" -> confirm(sender, effective, false);
            case "help" -> sender.sendMessage(AegisChat.help(
                    sender::hasPermission, AegisCommandParser.parsePage(effective)));
            default -> sender.sendMessage(Messages.prefixed("aegis").error("Unknown subcommand"));
        }
        return true;
    }

    static String[] rewriteAlias(String label, String[] args) {
        String lower = label.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "gban" -> prepend("ban", args);
            case "gmute" -> prepend("mute", args);
            case "gunban" -> prepend("unban", args);
            case "gwarn" -> prepend("warn", args);
            case "hist" -> prepend("history", args);
            case "checkpunish" -> prepend("check", args);
            default -> args;
        };
    }

    private static String[] prepend(String sub, String[] args) {
        String[] out = new String[args.length + 1];
        out[0] = sub;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    private void punish(CommandSender sender, PunishmentType type, String permission, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: " + permission));
            return;
        }
        AegisCommandParser.PunishArgs parsed;
        try {
            parsed = AegisCommandParser.parsePunish(type, args);
        } catch (AegisCommandParser.ParseException exception) {
            sender.sendMessage(Messages.prefixed("aegis").error(exception.getMessage()));
            return;
        }

        UUID staffId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        if (!runtime.cooldown().tryAcquire(staffId)) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Slow down ("
                    + runtime.cooldown().remaining(staffId).toSeconds() + "s)"));
            return;
        }

        if (parsed.permanent() && type != PunishmentType.WARN && sender instanceof Player player) {
            ConfirmChat.Prompt prompt = runtime.confirmChat().ask(
                    player.getUniqueId(),
                    "Permanently " + type.name().toLowerCase(Locale.ROOT) + " " + parsed.playerName() + "?",
                    "aegis",
                    () -> applyPunish(sender, type, parsed, staffId),
                    () -> sender.sendMessage(Messages.prefixed("aegis").warn("Cancelled")));
            sender.sendMessage(prompt.message());
            return;
        }
        applyPunish(sender, type, parsed, staffId);
    }

    private void applyPunish(
            CommandSender sender,
            PunishmentType type,
            AegisCommandParser.PunishArgs parsed,
            UUID staffId) {
        String staffName = sender.getName();
        // Offline name->UUID lookup stays off the main thread.
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(parsed.playerName());
            UUID targetId = target.getUniqueId();
            String targetName = target.getName() == null ? parsed.playerName() : target.getName();
            Duration duration = parsed.permanent() ? null : parsed.duration();
            Punishment punishment = runtime.moderation().punish(
                    type, targetId, targetName, staffId, staffName, parsed.reason(), duration);
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(AegisChat.punishFeedback(punishment));
                if (type == PunishmentType.BAN) {
                    Player online = Bukkit.getPlayer(targetId);
                    if (online != null) {
                        online.kick(AegisChat.banScreen(punishment, runtime.settings()));
                    }
                } else if (type == PunishmentType.MUTE) {
                    Player online = Bukkit.getPlayer(targetId);
                    if (online != null) {
                        online.sendMessage(AegisChat.muteNotify(punishment, runtime.settings().rulesCommand()));
                    }
                }
            });
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(Messages.prefixed("aegis").error("Failed: " + error.getMessage())));
            return null;
        });
    }

    private void revoke(CommandSender sender, PunishmentType type, String permission, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: " + permission));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis " + args[0] + " <player>"));
            return;
        }
        String playerName = args[1];
        UUID staffId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            boolean changed = runtime.moderation().revoke(type, target.getUniqueId(), staffId);
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                    changed ? Messages.prefixed("aegis").success("Revoked")
                            : Messages.prefixed("aegis").warn("No active punishment")));
        });
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.history")) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis history <player> [page]"));
            return;
        }
        String playerName = args[1];
        int page = AegisCommandParser.parsePage(args);
        int pageSize = runtime.settings().historyPageSize();
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            List<Punishment> pageRows = runtime.moderation().historyPage(target.getUniqueId(), page, pageSize);
            List<Punishment> all = runtime.moderation().history(target.getUniqueId(), 200);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (pageRows.isEmpty()) {
                    sender.sendMessage(Messages.prefixed("aegis").warn("No history"));
                    return;
                }
                for (Punishment row : pageRows) {
                    sender.sendMessage(AegisChat.historyLine(row));
                }
                if (sender instanceof Player player) {
                    historyGui.open(player, target.getName() == null ? playerName : target.getName(), all, false, page);
                }
            });
        });
    }

    private void check(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.history")) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis check <player>"));
            return;
        }
        String playerName = args[1];
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            List<Punishment> active = runtime.moderation().activeFor(target.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(AegisChat.checkSummary(playerName, active)));
        });
    }

    private void reason(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.history")) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis reason <id>"));
            return;
        }
        Optional<UUID> id = parseUuid(args[1]);
        if (id.isEmpty()) {
            sender.sendMessage(Messages.prefixed("aegis").error("Invalid id"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            Optional<Punishment> row = runtime.moderation().findById(id.get());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (row.isEmpty()) {
                    sender.sendMessage(Messages.prefixed("aegis").warn("Not found"));
                } else {
                    sender.sendMessage(AegisChat.reasonDetail(row.get()));
                }
            });
        });
    }

    private void rollback(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.unban") && !sender.hasPermission("aegis.unmute")) {
            sender.sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.unban or aegis.unmute"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis rollback <id>"));
            return;
        }
        Optional<UUID> id = parseUuid(args[1]);
        if (id.isEmpty()) {
            sender.sendMessage(Messages.prefixed("aegis").error("Invalid id"));
            return;
        }
        UUID staffId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            boolean changed = runtime.moderation().rollback(id.get(), staffId);
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                    changed ? Messages.prefixed("aegis").success("Rolled back")
                            : Messages.prefixed("aegis").warn("Nothing to rollback")));
        });
    }

    private void lookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.history")) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis lookup <uuid|name>"));
            return;
        }
        String token = args[1];
        CompletableFuture.runAsync(() -> {
            Optional<UUID> asUuid = parseUuid(token);
            final String name;
            final UUID id;
            if (asUuid.isPresent()) {
                id = asUuid.get();
                OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                name = offline.getName() == null ? token : offline.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(token);
                id = offline.getUniqueId();
                name = offline.getName() == null ? token : offline.getName();
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(AegisChat.lookupResult(name, id)));
        });
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("aegis.reload")) {
            sender.sendMessage(Messages.prefixed("aegis").error("Missing permission: aegis.reload"));
            return;
        }
        try {
            runtime.reloadSoft();
            sender.sendMessage(Messages.prefixed("aegis").success("Reloaded messages/webhook/aliases"));
        } catch (RuntimeException exception) {
            sender.sendMessage(Messages.prefixed("aegis").error("Reload failed: " + exception.getMessage()));
        }
    }

    private void confirm(CommandSender sender, String[] args, boolean accepted) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("aegis").error("Players only"));
            return;
        }
        if (args.length < 2) {
            return;
        }
        boolean ok = runtime.confirmChat().resolve(player.getUniqueId(), args[1], accepted);
        if (!ok) {
            sender.sendMessage(Messages.prefixed("aegis").warn("No pending confirmation"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String[] effective = rewriteAlias(alias, args);
        if (effective.length <= 1) {
            return filter(SUBS, effective.length == 0 ? "" : effective[0]);
        }
        String sub = effective[0].toLowerCase(Locale.ROOT);
        if (effective.length == 2 && needsPlayer(sub)) {
            return onlineNamesFirst(effective[1]);
        }
        if (effective.length == 3 && (sub.equals("ban") || sub.equals("mute"))) {
            return filter(DURATIONS, effective[2]);
        }
        return List.of();
    }

    private static boolean needsPlayer(String sub) {
        return switch (sub) {
            case "ban", "mute", "warn", "unban", "unmute", "history", "check", "lookup" -> true;
            default -> false;
        };
    }

    private static List<String> onlineNamesFirst(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                online.add(player.getName());
            }
        }
        return online;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
