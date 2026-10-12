package dev.nubstack.aegis.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nubstack.aegis.AegisChat;
import dev.nubstack.aegis.AegisCommandParser;
import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.aegis.PunishmentType;
import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.Messages;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Network-truth /aegis on Velocity. Offline name UUIDs match Paper offline-mode
 * OfflinePlayer semantics ({@code OfflinePlayer:<name>}).
 */
public final class AegisVelocityCommand implements SimpleCommand {

    private static final List<String> SUBS = List.of(
            "ban", "unban", "mute", "unmute", "warn", "history", "check", "reason",
            "rollback", "lookup", "reload", "confirm", "cancel", "help");
    private static final List<String> DURATIONS = List.of("10m", "1h", "1d", "7d", "30d", "perm");

    private final AegisVelocityPlugin plugin;
    private final ProxyServer proxy;
    private final AegisRuntime runtime;
    private final String invokedLabel;

    public AegisVelocityCommand(AegisVelocityPlugin plugin, ProxyServer proxy, AegisRuntime runtime) {
        this(plugin, proxy, runtime, "aegis");
    }

    public AegisVelocityCommand(
            AegisVelocityPlugin plugin, ProxyServer proxy, AegisRuntime runtime, String invokedLabel) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.runtime = runtime;
        this.invokedLabel = invokedLabel;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!"aegis".equalsIgnoreCase(invokedLabel) && !runtime.settings().aliasEnabled(invokedLabel)) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error("Alias disabled: " + invokedLabel));
            return;
        }
        String[] effective = rewriteAlias(invokedLabel, invocation.arguments());
        if (effective.length == 0) {
            invocation.source().sendMessage(AegisChat.help(invocation.source()::hasPermission, 1));
            return;
        }
        String sub = effective[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ban" -> punish(invocation, PunishmentType.BAN, "aegis.ban", effective);
            case "mute" -> punish(invocation, PunishmentType.MUTE, "aegis.mute", effective);
            case "warn" -> punish(invocation, PunishmentType.WARN, "aegis.warn", effective);
            case "unban" -> revoke(invocation, PunishmentType.BAN, "aegis.unban", effective);
            case "unmute" -> revoke(invocation, PunishmentType.MUTE, "aegis.unmute", effective);
            case "history" -> history(invocation, effective);
            case "check" -> check(invocation, effective);
            case "reason" -> reason(invocation, effective);
            case "rollback" -> rollback(invocation, effective);
            case "lookup" -> lookup(invocation, effective);
            case "reload" -> reload(invocation);
            case "confirm" -> confirm(invocation, effective, true);
            case "cancel" -> confirm(invocation, effective, false);
            case "help" -> invocation.source().sendMessage(AegisChat.help(
                    invocation.source()::hasPermission, AegisCommandParser.parsePage(effective)));
            default -> invocation.source().sendMessage(Messages.prefixed("aegis").error("Unknown subcommand"));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aegis.use")
                || !"aegis".equalsIgnoreCase(invokedLabel);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] effective = rewriteAlias(invokedLabel, invocation.arguments());
        if (effective.length <= 1) {
            String prefix = effective.length == 0 ? "" : effective[0];
            return filter(SUBS, prefix);
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

    private void punish(Invocation invocation, PunishmentType type, String permission, String[] args) {
        if (!invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: " + permission));
            return;
        }
        AegisCommandParser.PunishArgs parsed;
        try {
            parsed = AegisCommandParser.parsePunish(type, args);
        } catch (AegisCommandParser.ParseException exception) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(exception.getMessage()));
            return;
        }

        UUID staffId = invocation.source() instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        if (!runtime.cooldown().tryAcquire(staffId)) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn("Slow down ("
                    + runtime.cooldown().remaining(staffId).toSeconds() + "s)"));
            return;
        }

        if (parsed.permanent() && type != PunishmentType.WARN && invocation.source() instanceof Player player) {
            ConfirmChat.Prompt prompt = runtime.confirmChat().ask(
                    player.getUniqueId(),
                    "Permanently " + type.name().toLowerCase(Locale.ROOT) + " " + parsed.playerName() + "?",
                    "aegis",
                    () -> applyPunish(invocation, type, parsed, staffId),
                    () -> invocation.source().sendMessage(Messages.prefixed("aegis").warn("Cancelled")));
            invocation.source().sendMessage(prompt.message());
            return;
        }
        applyPunish(invocation, type, parsed, staffId);
    }

    private void applyPunish(
            Invocation invocation,
            PunishmentType type,
            AegisCommandParser.PunishArgs parsed,
            UUID staffId) {
        String staffName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(parsed.playerName());
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() -> offlineUuid(parsed.playerName()));
            String targetName = online.map(Player::getUsername).orElse(parsed.playerName());
            Duration duration = parsed.permanent() ? null : parsed.duration();
            Punishment punishment = runtime.moderation().punish(
                    type, targetId, targetName, staffId, staffName, parsed.reason(), duration);
            proxy.getScheduler().buildTask(plugin, () -> {
                invocation.source().sendMessage(AegisChat.punishFeedback(punishment));
                if (type == PunishmentType.BAN) {
                    online.ifPresent(player ->
                            player.disconnect(AegisChat.banScreen(punishment, runtime.settings())));
                } else if (type == PunishmentType.MUTE) {
                    online.ifPresent(player -> player.sendMessage(
                            AegisChat.muteNotify(punishment, runtime.settings().rulesCommand())));
                }
            }).schedule();
        }).exceptionally(error -> {
            proxy.getScheduler().buildTask(plugin, () ->
                    invocation.source().sendMessage(Messages.prefixed("aegis").error(
                            "Failed: " + error.getMessage())))
                    .schedule();
            return null;
        });
    }

    private void revoke(Invocation invocation, PunishmentType type, String permission, String[] args) {
        if (!invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: " + permission));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn(
                    "Usage: /aegis " + args[0] + " <player>"));
            return;
        }
        String playerName = args[1];
        UUID staffId = invocation.source() instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() -> offlineUuid(playerName));
            boolean changed = runtime.moderation().revoke(type, targetId, staffId);
            proxy.getScheduler().buildTask(plugin, () -> invocation.source().sendMessage(
                    changed ? Messages.prefixed("aegis").success("Revoked")
                            : Messages.prefixed("aegis").warn("No active punishment"))).schedule();
        });
    }

    private void history(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.history")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn(
                    "Usage: /aegis history <player> [page]"));
            return;
        }
        String playerName = args[1];
        int page = AegisCommandParser.parsePage(args);
        int pageSize = runtime.settings().historyPageSize();
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() -> offlineUuid(playerName));
            List<Punishment> rows = runtime.moderation().historyPage(targetId, page, pageSize);
            proxy.getScheduler().buildTask(plugin, () -> {
                if (rows.isEmpty()) {
                    invocation.source().sendMessage(Messages.prefixed("aegis").warn("No history"));
                    return;
                }
                for (Punishment row : rows) {
                    invocation.source().sendMessage(AegisChat.historyLine(row));
                }
            }).schedule();
        });
    }

    private void check(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.history")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis check <player>"));
            return;
        }
        String playerName = args[1];
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() -> offlineUuid(playerName));
            List<Punishment> active = runtime.moderation().activeFor(targetId);
            proxy.getScheduler().buildTask(plugin, () ->
                    invocation.source().sendMessage(AegisChat.checkSummary(playerName, active))).schedule();
        });
    }

    private void reason(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.history")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis reason <id>"));
            return;
        }
        Optional<UUID> id = parseUuid(args[1]);
        if (id.isEmpty()) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error("Invalid id"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            Optional<Punishment> row = runtime.moderation().findById(id.get());
            proxy.getScheduler().buildTask(plugin, () -> {
                if (row.isEmpty()) {
                    invocation.source().sendMessage(Messages.prefixed("aegis").warn("Not found"));
                } else {
                    invocation.source().sendMessage(AegisChat.reasonDetail(row.get()));
                }
            }).schedule();
        });
    }

    private void rollback(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.unban")
                && !invocation.source().hasPermission("aegis.unmute")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.unban or aegis.unmute"));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn("Usage: /aegis rollback <id>"));
            return;
        }
        Optional<UUID> id = parseUuid(args[1]);
        if (id.isEmpty()) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error("Invalid id"));
            return;
        }
        UUID staffId = invocation.source() instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            boolean changed = runtime.moderation().rollback(id.get(), staffId);
            proxy.getScheduler().buildTask(plugin, () -> invocation.source().sendMessage(
                    changed ? Messages.prefixed("aegis").success("Rolled back")
                            : Messages.prefixed("aegis").warn("Nothing to rollback"))).schedule();
        });
    }

    private void lookup(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.history")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.history"));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn(
                    "Usage: /aegis lookup <uuid|name>"));
            return;
        }
        String token = args[1];
        CompletableFuture.runAsync(() -> {
            Optional<UUID> asUuid = parseUuid(token);
            final String name;
            final UUID id;
            if (asUuid.isPresent()) {
                id = asUuid.get();
                name = proxy.getPlayer(id).map(Player::getUsername).orElse(token);
            } else {
                Optional<Player> online = proxy.getPlayer(token);
                id = online.map(Player::getUniqueId).orElseGet(() -> offlineUuid(token));
                name = online.map(Player::getUsername).orElse(token);
            }
            proxy.getScheduler().buildTask(plugin, () ->
                    invocation.source().sendMessage(AegisChat.lookupResult(name, id))).schedule();
        });
    }

    private void reload(Invocation invocation) {
        if (!invocation.source().hasPermission("aegis.reload")) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Missing permission: aegis.reload"));
            return;
        }
        try {
            runtime.reloadSoft();
            invocation.source().sendMessage(Messages.prefixed("aegis").success(
                    "Reloaded messages/webhook/aliases"));
        } catch (RuntimeException exception) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error(
                    "Reload failed: " + exception.getMessage()));
        }
    }

    private void confirm(Invocation invocation, String[] args, boolean accepted) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Messages.prefixed("aegis").error("Players only"));
            return;
        }
        if (args.length < 2) {
            return;
        }
        boolean ok = runtime.confirmChat().resolve(player.getUniqueId(), args[1], accepted);
        if (!ok) {
            invocation.source().sendMessage(Messages.prefixed("aegis").warn("No pending confirmation"));
        }
    }

    private static UUID offlineUuid(String name) {
        // shortcut: offline-mode UUID only; online-mode offline names need Mojang lookup when staff care
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean needsPlayer(String sub) {
        return switch (sub) {
            case "ban", "mute", "warn", "unban", "unmute", "history", "check", "lookup" -> true;
            default -> false;
        };
    }

    private List<String> onlineNamesFirst(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> online = new ArrayList<>();
        for (Player player : proxy.getAllPlayers()) {
            if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(lower)) {
                online.add(player.getUsername());
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
