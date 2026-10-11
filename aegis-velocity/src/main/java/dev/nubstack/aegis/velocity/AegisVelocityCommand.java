package dev.nubstack.aegis.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.DurationParser;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.aegis.PunishmentType;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class AegisVelocityCommand implements SimpleCommand {

    private final AegisVelocityPlugin plugin;
    private final ProxyServer proxy;
    private final AegisRuntime runtime;

    public AegisVelocityCommand(AegisVelocityPlugin plugin, ProxyServer proxy, AegisRuntime runtime) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.runtime = runtime;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text(
                    "Usage: /aegis <ban|unban|mute|unmute|warn|history>", NamedTextColor.YELLOW));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ban" -> punish(invocation, PunishmentType.BAN, "aegis.ban", args);
            case "mute" -> punish(invocation, PunishmentType.MUTE, "aegis.mute", args);
            case "warn" -> punish(invocation, PunishmentType.WARN, "aegis.warn", args);
            case "unban" -> revoke(invocation, PunishmentType.BAN, "aegis.unban", args);
            case "unmute" -> revoke(invocation, PunishmentType.MUTE, "aegis.unmute", args);
            case "history" -> history(invocation, args);
            default -> invocation.source().sendMessage(Component.text("Unknown subcommand", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aegis.use");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("ban", "unban", "mute", "unmute", "warn", "history");
        }
        return List.of();
    }

    private void punish(Invocation invocation, PunishmentType type, String permission, String[] args) {
        if (!invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            invocation.source().sendMessage(Component.text(
                    "Usage: /aegis " + type.name().toLowerCase(Locale.ROOT) + " <player> [duration] <reason>",
                    NamedTextColor.YELLOW));
            return;
        }
        String playerName = args[1];
        Duration duration = null;
        int reasonStart = 2;
        if (type != PunishmentType.WARN && args.length >= 4) {
            try {
                Optional<Duration> parsed = DurationParser.parse(args[2]);
                if (parsed.isPresent()) {
                    duration = parsed.get();
                    reasonStart = 3;
                }
            } catch (IllegalArgumentException ignored) {
                reasonStart = 2;
            }
        }
        if (reasonStart >= args.length) {
            invocation.source().sendMessage(Component.text("Reason required", NamedTextColor.RED));
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length));
        UUID staffId = invocation.source() instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        String staffName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        Duration finalDuration = duration;

        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() ->
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes()));
            String targetName = online.map(Player::getUsername).orElse(playerName);
            Punishment punishment = runtime.moderation().punish(
                    type, targetId, targetName, staffId, staffName, reason, finalDuration);
            proxy.getScheduler().buildTask(plugin, () -> {
                invocation.source().sendMessage(Component.text(
                        type.name() + " applied to " + punishment.targetName(), NamedTextColor.GREEN));
                if (type == PunishmentType.BAN) {
                    online.ifPresent(player -> player.disconnect(Component.text("Banned: " + reason, NamedTextColor.RED)));
                }
            }).schedule();
        }).exceptionally(error -> {
            proxy.getScheduler().buildTask(plugin, () ->
                    invocation.source().sendMessage(Component.text("Failed: " + error.getMessage(), NamedTextColor.RED)))
                    .schedule();
            return null;
        });
    }

    private void revoke(Invocation invocation, PunishmentType type, String permission, String[] args) {
        if (!invocation.source().hasPermission(permission)) {
            invocation.source().sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /aegis " + args[0] + " <player>", NamedTextColor.YELLOW));
            return;
        }
        String playerName = args[1];
        UUID staffId = invocation.source() instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() ->
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes()));
            boolean changed = runtime.moderation().revoke(type, targetId, staffId);
            proxy.getScheduler().buildTask(plugin, () -> invocation.source().sendMessage(Component.text(
                    changed ? "Revoked" : "No active punishment",
                    changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW))).schedule();
        });
    }

    private void history(Invocation invocation, String[] args) {
        if (!invocation.source().hasPermission("aegis.history")) {
            invocation.source().sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /aegis history <player>", NamedTextColor.YELLOW));
            return;
        }
        String playerName = args[1];
        CompletableFuture.runAsync(() -> {
            Optional<Player> online = proxy.getPlayer(playerName);
            UUID targetId = online.map(Player::getUniqueId).orElseGet(() ->
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes()));
            List<Punishment> rows = runtime.moderation().history(targetId, 20);
            proxy.getScheduler().buildTask(plugin, () -> {
                if (rows.isEmpty()) {
                    invocation.source().sendMessage(Component.text("No history", NamedTextColor.YELLOW));
                    return;
                }
                for (Punishment row : rows) {
                    invocation.source().sendMessage(Component.text(
                            row.type() + " by " + row.staffName() + ": " + row.reason()
                                    + (row.active() ? " [active]" : ""),
                            NamedTextColor.GRAY));
                }
            }).schedule();
        });
    }
}
