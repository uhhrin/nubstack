
package dev.nubstack.cull;

import java.util.Arrays;
import java.util.Comparator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CullPlugin extends JavaPlugin implements Listener {

    private ChunkEntityLimiter limiter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        limiter = new ChunkEntityLimiter(getConfig().getInt("max-entities-per-chunk", 40));
        Bukkit.getPluginManager().registerEvents(this, this);
        long interval = getConfig().getLong("cleanup-interval-ticks", 200L);
        Bukkit.getScheduler().runTaskTimer(this, this::cleanup, interval, interval);
        getCommand("cull").setExecutor(this);
        getLogger().info("cull enabled");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Chunk chunk = event.getLocation().getChunk();
        int count = chunk.getEntities().length;
        if (!limiter.allows(count)) {
            event.setCancelled(true);
        }
    }

    private void cleanup() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                Entity[] entities = chunk.getEntities();
                int overflow = limiter.overflow(entities.length);
                if (overflow <= 0) {
                    continue;
                }
                Arrays.stream(entities)
                        .filter(entity -> !(entity instanceof Player))
                        .filter(entity -> entity.getType() != EntityType.ITEM_FRAME)
                        .sorted(Comparator.comparingDouble(entity -> -entity.getTicksLived()))
                        .limit(overflow)
                        .forEach(Entity::remove);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("cull max per chunk: " + getConfig().getInt("max-entities-per-chunk"));
        return true;
    }
}
