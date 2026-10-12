package dev.nubstack.cull;

import dev.nubstack.spine.config.ConfigFiles;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CullPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int DEFAULT_SCAN_LIMIT = 10;

    private final CullChat chat = new CullChat();
    private final CullStats stats = new CullStats();
    private CullSettings settings = CullSettings.of(
            CullSettings.DEFAULT_MAX, Map.of(), CullMode.ALL, List.of(), List.of(),
            EnumSet.allOf(Protection.class), CullSettings.DEFAULT_INTERVAL_TICKS);
    private BukkitTask cleanupTask;
    private long nextCleanupAtMillis;
    private List<ChunkLoad> lastScan = List.of();

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("cull").setExecutor(this);
        getCommand("cull").setTabCompleter(this);
        getLogger().info("cull enabled");
    }

    private void loadSettings() {
        reloadConfig();
        CullMode mode = CullMode.parse(getConfig().getString("mode", "all")).orElseGet(() -> {
            getLogger().warning("Unknown mode in config, using all");
            return CullMode.ALL;
        });
        Map<String, Integer> worlds = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("worlds");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                worlds.put(name, section.getInt(name));
            }
        }
        Set<Protection> protections = EnumSet.noneOf(Protection.class);
        for (Protection protection : Protection.values()) {
            if (getConfig().getBoolean("protect." + protection.configKey(), true)) {
                protections.add(protection);
            }
        }
        settings = CullSettings.of(
                getConfig().getInt("max-entities-per-chunk", CullSettings.DEFAULT_MAX),
                worlds,
                mode,
                getConfig().getStringList("allow-types"),
                getConfig().getStringList("deny-types"),
                protections,
                getConfig().getLong("cleanup-interval-ticks", CullSettings.DEFAULT_INTERVAL_TICKS));
        scheduleCleanup();
    }

    private void scheduleCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        long interval = settings.intervalTicks();
        nextCleanupAtMillis = System.currentTimeMillis() + interval * 50L;
        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            cleanup();
            nextCleanupAtMillis = System.currentTimeMillis() + interval * 50L;
        }, interval, interval);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        int cap = settings.capFor(location.getWorld().getName());
        if (new ChunkEntityLimiter(cap).allows(location.getChunk().getEntities().length)) {
            return;
        }
        if (settings.eligible(event.getEntityType().name(), event.getEntity() instanceof Enemy)) {
            event.setCancelled(true);
            stats.spawnCancelled();
        }
    }

    // shortcut: cleanup and scans walk every loaded chunk in one tick, batch them like spotlight when chunk counts reach the thousands
    private int cleanup() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            ChunkEntityLimiter limiter = new ChunkEntityLimiter(settings.capFor(world.getName()));
            for (Chunk chunk : world.getLoadedChunks()) {
                Entity[] entities = chunk.getEntities();
                int overflow = limiter.overflow(entities.length);
                if (overflow <= 0) {
                    continue;
                }
                List<CullCandidate<Entity>> candidates = new ArrayList<>();
                for (Entity entity : entities) {
                    if (!(entity instanceof Player)) {
                        candidates.add(candidate(entity));
                    }
                }
                List<Entity> victims = CullPlanner.pickVictims(settings, candidates, overflow);
                victims.forEach(Entity::remove);
                removed += victims.size();
            }
        }
        stats.culled(removed);
        return removed;
    }

    private static CullCandidate<Entity> candidate(Entity entity) {
        Set<Protection> traits = EnumSet.noneOf(Protection.class);
        if (entity.customName() != null) {
            traits.add(Protection.NAMED);
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            traits.add(Protection.TAMED);
        }
        if (entity instanceof LivingEntity living && living.isLeashed()) {
            traits.add(Protection.LEASHED);
        }
        if (entity instanceof AbstractVillager) {
            traits.add(Protection.VILLAGERS);
        }
        if (entity instanceof ArmorStand) {
            traits.add(Protection.ARMOR_STANDS);
        }
        if (!entity.getPassengers().isEmpty()) {
            traits.add(Protection.VEHICLES);
        }
        return new CullCandidate<>(
                entity, entity.getType().name(), entity instanceof Enemy, entity.getTicksLived(), traits);
    }

    private static ChunkLoad load(Chunk chunk, int cap) {
        Map<String, Integer> byType = new TreeMap<>();
        Entity[] entities = chunk.getEntities();
        for (Entity entity : entities) {
            byType.merge(entity.getType().name(), 1, Integer::sum);
        }
        return new ChunkLoad(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), entities.length, cap, byType);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cull.use")) {
            return deny(sender, "cull.use");
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            long next = Math.max(0L, (nextCleanupAtMillis - System.currentTimeMillis()) / 1000L);
            sender.sendMessage(chat.help(sender::hasPermission, settings.defaultMax(), next));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender, args);
            case "scan" -> scan(sender, args);
            case "here" -> here(sender);
            case "tp" -> tp(sender, args);
            case "run" -> run(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(chat.error("Unknown subcommand. Try /cull"));
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender, String[] args) {
        List<World> worlds = args.length >= 2 ? worldList(args[1]) : Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            sender.sendMessage(chat.error("Unknown world " + args[1]));
            return true;
        }
        sender.sendMessage(chat.info("Mode " + settings.mode().name().toLowerCase(Locale.ROOT)
                + ", cancelled spawns " + stats.cancelledSpawns()
                + ", culled entities " + stats.culledEntities()));
        for (World world : worlds) {
            int cap = settings.capFor(world.getName());
            int over = 0;
            int chunks = 0;
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks++;
                if (chunk.getEntities().length > cap) {
                    over++;
                }
            }
            sender.sendMessage(chat.info(world.getName() + ": cap " + cap + ", " + chunks
                    + " loaded chunks, " + over + " over cap"));
        }
        return true;
    }

    private boolean scan(CommandSender sender, String[] args) {
        List<World> worlds = Bukkit.getWorlds();
        int limit = DEFAULT_SCAN_LIMIT;
        if (args.length >= 2) {
            List<World> named = worldList(args[1]);
            if (!named.isEmpty()) {
                worlds = named;
                if (args.length >= 3) {
                    limit = parsePositive(args[2], DEFAULT_SCAN_LIMIT);
                }
            } else {
                limit = parsePositive(args[1], DEFAULT_SCAN_LIMIT);
            }
        }
        List<ChunkLoad> loads = new ArrayList<>();
        for (World world : worlds) {
            int cap = settings.capFor(world.getName());
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkLoad load = load(chunk, cap);
                if (load.count() > 0) {
                    loads.add(load);
                }
            }
        }
        lastScan = ChunkLoad.ranked(loads);
        int shown = Math.min(limit, lastScan.size());
        sender.sendMessage(chat.success("Top " + shown + " of " + lastScan.size() + " chunks with entities"));
        for (int i = 0; i < shown; i++) {
            sender.sendMessage(chat.resultLine(i + 1, lastScan.get(i)));
        }
        return true;
    }

    private boolean here(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(chat.error("Players only"));
            return true;
        }
        Chunk chunk = player.getLocation().getChunk();
        ChunkLoad load = load(chunk, settings.capFor(chunk.getWorld().getName()));
        sender.sendMessage(chat.info("Chunk " + load.chunkX() + "," + load.chunkZ() + ": "
                + load.count() + "/" + load.cap() + " (" + load.breakdown() + ")"));
        return true;
    }

    private boolean tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(chat.error("Players only"));
            return true;
        }
        int rank = args.length >= 2 ? parsePositive(args[1], -1) : -1;
        if (rank < 1) {
            sender.sendMessage(chat.error("Usage: /cull tp <rank>"));
            return true;
        }
        if (rank > lastScan.size()) {
            sender.sendMessage(chat.warn("No result at rank " + rank + ". Run /cull scan first"));
            return true;
        }
        ChunkLoad target = lastScan.get(rank - 1);
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            sender.sendMessage(chat.error("World not loaded: " + target.world()));
            return true;
        }
        int x = target.chunkX() * 16 + 8;
        int z = target.chunkZ() * 16 + 8;
        Location dest = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        player.teleportAsync(dest).thenAccept(ok -> player.sendMessage(Boolean.TRUE.equals(ok)
                ? chat.success("Teleported to #" + rank)
                : chat.error("Teleport failed")));
        return true;
    }

    private boolean run(CommandSender sender) {
        if (!sender.hasPermission("cull.admin")) {
            return deny(sender, "cull.admin");
        }
        sender.sendMessage(chat.success("Cleanup removed " + cleanup() + " entities"));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("cull.admin")) {
            return deny(sender, "cull.admin");
        }
        loadSettings();
        sender.sendMessage(chat.success("Reloaded cull config"));
        return true;
    }

    private boolean deny(CommandSender sender, String node) {
        sender.sendMessage(chat.error("Missing permission " + node));
        return true;
    }

    private static List<World> worldList(String name) {
        World world = Bukkit.getWorld(name);
        return world == null ? List.of() : List.of(world);
    }

    private static int parsePositive(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cull.use")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status", "scan", "here", "tp"));
            if (sender.hasPermission("cull.admin")) {
                subs.add("run");
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("scan"))) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return filter(List.of("1", "2", "3", "4", "5"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
