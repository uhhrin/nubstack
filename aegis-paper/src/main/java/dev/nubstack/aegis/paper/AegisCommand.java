package dev.nubstack.aegis.paper;

import dev.nubstack.aegis.AegisRuntime;
import dev.nubstack.aegis.DurationParser;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.aegis.PunishmentType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final AegisRuntime runtime;

    public AegisCommand(JavaPlugin plugin, AegisRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /aegis <ban|unban|mute|unmute|warn|history>", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ban" -> punish(sender, PunishmentType.BAN, "aegis.ban", args);
            case "mute" -> punish(sender, PunishmentType.MUTE, "aegis.mute", args);
            case "warn" -> punish(sender, PunishmentType.WARN, "aegis.warn", args);
            case "unban" -> revoke(sender, PunishmentType.BAN, "aegis.unban", args);
            case "unmute" -> revoke(sender, PunishmentType.MUTE, "aegis.unmute", args);
            case "history" -> history(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand", NamedTextColor.RED));
        }
        return true;
    }

    private void punish(CommandSender sender, PunishmentType type, String permission, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /aegis " + type.name().toLowerCase(Locale.ROOT)
                    + " <player> [duration] <reason>", NamedTextColor.YELLOW));
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
            sender.sendMessage(Component.text("Reason required", NamedTextColor.RED));
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length));
        UUID staffId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        String staffName = sender.getName();
        Duration finalDuration = duration;

        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (target.getUniqueId().version() == 0 && !target.hasPlayedBefore() && !target.isOnline()) {
                // still allow UUID assignment from name lookup offline mode
            }
            UUID targetId = target.getUniqueId();
            String targetName = target.getName() == null ? playerName : target.getName();
            Punishment punishment = runtime.moderation().punish(
                    type, targetId, targetName, staffId, staffName, reason, finalDuration);
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text(
                        type.name() + " applied to " + punishment.targetName(),
                        NamedTextColor.GREEN));
                if (type == PunishmentType.BAN) {
                    Player online = Bukkit.getPlayer(targetId);
                    if (online != null) {
                        online.kick(Component.text("Banned: " + reason, NamedTextColor.RED));
                    }
                }
            });
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(Component.text("Failed: " + error.getMessage(), NamedTextColor.RED)));
            return null;
        });
    }

    private void revoke(CommandSender sender, PunishmentType type, String permission, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /aegis " + args[0] + " <player>", NamedTextColor.YELLOW));
            return;
        }
        String playerName = args[1];
        UUID staffId = sender instanceof Player player ? player.getUniqueId() : new UUID(0L, 0L);
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            boolean changed = runtime.moderation().revoke(type, target.getUniqueId(), staffId);
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                    changed ? "Revoked" : "No active punishment",
                    changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(Component.text("Failed: " + error.getMessage(), NamedTextColor.RED)));
            return null;
        });
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aegis.history")) {
            sender.sendMessage(Component.text("No permission", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /aegis history <player>", NamedTextColor.YELLOW));
            return;
        }
        String playerName = args[1];
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            List<Punishment> rows = runtime.moderation().history(target.getUniqueId(), 20);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (rows.isEmpty()) {
                    sender.sendMessage(Component.text("No history", NamedTextColor.YELLOW));
                    return;
                }
                for (Punishment row : rows) {
                    sender.sendMessage(Component.text(
                            row.type() + " by " + row.staffName() + ": " + row.reason()
                                    + (row.active() ? " [active]" : ""),
                            NamedTextColor.GRAY));
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("ban", "unban", "mute", "unmute", "warn", "history"), args[0]);
        }
        return List.of();
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
}
