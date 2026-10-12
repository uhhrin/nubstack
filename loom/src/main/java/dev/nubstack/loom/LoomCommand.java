package dev.nubstack.loom;

import dev.nubstack.spine.config.ConfigException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /loom}: staff tooling. Players only learn that sync is automatic. */
final class LoomCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("help", "status", "unlock", "inspect", "force-save", "reload");

    private final LoomPlugin plugin;

    LoomCommand(LoomPlugin plugin) {
        this.plugin = plugin;
    }

    private record Target(UUID id, String name) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(LoomMessages.ADMIN)) {
            sender.sendMessage(LoomMessages.playerDenied());
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sender.sendMessage(LoomMessages.help().build(sender::hasPermission, 1, 10));
            case "status" -> status(sender, args);
            case "unlock" -> unlock(sender, args);
            case "inspect" -> inspect(sender, args);
            case "force-save" -> forceSave(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(LoomMessages.prefixed().error("Unknown subcommand. Try /loom help"));
        }
        return true;
    }

    private void status(CommandSender sender, String[] args) {
        Optional<Target> target = target(sender, args, "/loom status <player>");
        target.ifPresent(t -> async(() -> {
            Optional<PlayerDataStore.Snapshot> snapshot = plugin.sync().store().find(t.id());
            reply(sender, LoomMessages.status(t.name(), snapshot, plugin.clock().instant()));
        }, sender));
    }

    private void unlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(LoomMessages.prefixed().error("Usage: /loom unlock <player>"));
            return;
        }
        Optional<Target> target = lookup(sender, args[1]);
        target.ifPresent(t -> async(() -> {
            plugin.sync().store().forceUnlock(t.id(), sender.getName());
            reply(sender, LoomMessages.prefixed().success("Cleared the sync lease of " + t.name()));
        }, sender));
    }

    private void inspect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(LoomMessages.prefixed().error("Only players can open the inspect preview."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(LoomMessages.prefixed().error("Usage: /loom inspect <player>"));
            return;
        }
        lookup(sender, args[1]).ifPresent(t -> async(() -> {
            PlayerDataStore store = plugin.sync().store();
            Optional<PlayerDataStore.Snapshot> snapshot = store.find(t.id()).filter(PlayerDataStore.Snapshot::hasInventory);
            if (snapshot.isEmpty()) {
                reply(sender, LoomMessages.prefixed().warn(t.name() + " has no stored inventory"));
                return;
            }
            Optional<String> savedOn = store.recentAudit(t.id(), 20).stream()
                    .filter(row -> row.eventType().equals("save"))
                    .map(PlayerDataStore.AuditEntry::serverId)
                    .findFirst();
            Bukkit.getScheduler().runTask(plugin, () -> openPreview(viewer, t, snapshot.get(), savedOn));
        }, sender));
    }

    private void openPreview(
            Player viewer, Target target, PlayerDataStore.Snapshot snapshot, Optional<String> savedOn) {
        if (!viewer.isOnline()) {
            return;
        }
        ItemStack[] contents;
        try {
            contents = InventorySerializer.deserialize(snapshot.inventoryBlob());
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Stored inventory of " + target.id() + " is unreadable: " + e);
            viewer.sendMessage(LoomMessages.prefixed().error("The stored inventory of " + target.name()
                    + " could not be read. See the console."));
            return;
        }
        viewer.sendMessage(LoomMessages.inspectHeader(target.name(), target.id()));
        InspectMenu.open(viewer, target.name(), snapshot, savedOn, contents);
    }

    private void forceSave(CommandSender sender, String[] args) {
        Optional<Target> target = target(sender, args, "/loom force-save <player>");
        target.ifPresent(t -> {
            Player online = Bukkit.getPlayer(t.id());
            if (online == null || !plugin.isSynced(t.id())) {
                sender.sendMessage(LoomMessages.prefixed().warn(
                        t.name() + " is not online here with a loaded inventory, nothing to save"));
                return;
            }
            String blob;
            try {
                blob = plugin.serialize(online);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Force-save serialization failed for " + t.id() + ": " + e);
                sender.sendMessage(LoomMessages.prefixed().error("Could not serialize the inventory. See the console."));
                return;
            }
            async(() -> {
                boolean saved = plugin.sync().saveKeepingLease(t.id(), blob);
                reply(sender, saved
                        ? LoomMessages.prefixed().success("Saved the inventory of " + t.name())
                        : LoomMessages.prefixed().error("Save refused, another server holds the lease of " + t.name()));
            }, sender);
        });
    }

    private void reload(CommandSender sender) {
        try {
            LoomSettings settings = plugin.reloadSettings();
            sender.sendMessage(LoomMessages.prefixed().success("Reloaded: lease " + settings.leaseTtl().toSeconds()
                    + "s, wait " + settings.waitTimeout().toSeconds() + "s, policy "
                    + settings.failurePolicy().name().toLowerCase(Locale.ROOT).replace('_', '-')));
        } catch (IllegalArgumentException | ConfigException e) {
            sender.sendMessage(LoomMessages.prefixed().error("Reload failed, keeping the old settings: " + e.getMessage()));
        }
    }

    private Optional<Target> target(CommandSender sender, String[] args, String usage) {
        if (args.length >= 2) {
            return lookup(sender, args[1]);
        }
        if (sender instanceof Player self) {
            return Optional.of(new Target(self.getUniqueId(), self.getName()));
        }
        sender.sendMessage(LoomMessages.prefixed().error("Usage: " + usage));
        return Optional.empty();
    }

    private Optional<Target> lookup(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName()));
        }
        // cached lookup only, a Mojang request would block the main thread
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            sender.sendMessage(LoomMessages.prefixed().error("Unknown player " + name + " (they must have joined before)"));
            return Optional.empty();
        }
        return Optional.of(new Target(offline.getUniqueId(), offline.getName() == null ? name : offline.getName()));
    }

    private void async(Runnable work, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                plugin.getLogger().severe("loom command failed: " + e);
                reply(sender, LoomMessages.prefixed().error("Database error. See the console."));
            }
        });
    }

    private void reply(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(LoomMessages.ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && List.of("status", "unlock", "inspect", "force-save").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
