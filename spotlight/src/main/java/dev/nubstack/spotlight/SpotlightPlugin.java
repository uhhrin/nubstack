
package dev.nubstack.spotlight;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpotlightPlugin extends JavaPlugin {

    private int entityWeight = 1;
    private int hopperWeight = 8;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        entityWeight = getConfig().getInt("entity-weight", 1);
        hopperWeight = getConfig().getInt("hopper-weight", 8);
        getCommand("spotlight").setExecutor(this);
        getLogger().info("spotlight enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spotlight.scan")) {
            sender.sendMessage("No permission");
            return true;
        }
        LagReport report = scan();
        sender.sendMessage("Top lag chunks:");
        for (LagReport.ChunkCost cost : report.top(10)) {
            sender.sendMessage(cost.world() + " " + cost.chunkX() + "," + cost.chunkZ()
                    + " entities=" + cost.entities() + " hoppers=" + cost.hoppers()
                    + " score=" + cost.score());
        }
        return true;
    }

    LagReport scan() {
        LagReport report = new LagReport();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int entities = 0;
                for (Entity ignored : chunk.getEntities()) {
                    entities++;
                }
                int hoppers = 0;
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Hopper || state.getType() == Material.HOPPER) {
                        hoppers++;
                    }
                }
                int score = entities * entityWeight + hoppers * hopperWeight;
                if (score > 0) {
                    report.add(new LagReport.ChunkCost(
                            world.getName(), chunk.getX(), chunk.getZ(), entities, hoppers, score));
                }
            }
        }
        return report;
    }
}
