
package dev.nubstack.amber;

import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class AmberPlugin extends JavaPlugin {

    private SnapshotService snapshots;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path dir = getDataFolder().toPath().resolve(getConfig().getString("snapshot-dir", "snapshots"));
        snapshots = new SnapshotService(dir, getConfig().getInt("retain", 5));
        getCommand("amber").setExecutor(this);
        getLogger().info("amber enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("amber.admin")) {
            sender.sendMessage("No permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/amber snapshot [world] | /amber list");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    var list = snapshots.list();
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (list.isEmpty()) {
                            sender.sendMessage("No snapshots");
                            return;
                        }
                        list.forEach(path -> sender.sendMessage(path.getFileName().toString()));
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("Failed: " + e.getMessage()));
                }
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("snapshot")) {
            String worldName = args.length > 1 ? args[1] : getConfig().getString("default-world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage("World not loaded");
                return true;
            }
            world.save();
            Path folder = world.getWorldFolder().toPath();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    Path created = snapshots.create(worldName, folder);
                    Bukkit.getScheduler().runTask(this, () ->
                            sender.sendMessage("Snapshot saved: " + created.getFileName()));
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("Failed: " + e.getMessage()));
                }
            });
            return true;
        }
        sender.sendMessage("/amber snapshot [world] | /amber list");
        return true;
    }
}
