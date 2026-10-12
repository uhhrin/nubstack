package dev.nubstack.spotlight;

import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import dev.nubstack.spine.ui.ActionBars;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class SpotlightPlugin extends JavaPlugin implements TabExecutor {

    private final PrefixedMessages msg = Messages.prefixed("spotlight");
    private ChunkWeights weights = new ChunkWeights(1, 8, 4, 2, 3, 5);
    private int chunksPerTick = 32;
    private int defaultLimit = 10;
    private @Nullable LagReport lastReport;
    private @Nullable Instant lastScanAt;
    private @Nullable ChunkWeights lastWeights;
    private boolean scanRunning;

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        reloadLocalConfig();
        getCommand("spotlight").setExecutor(this);
        getCommand("spotlight").setTabCompleter(this);
        getLogger().info("spotlight enabled");
    }

    private void reloadLocalConfig() {
        reloadConfig();
        weights = ChunkWeights.fromConfig(getConfig());
        chunksPerTick = Math.max(1, getConfig().getInt("chunks-per-tick", 32));
        defaultLimit = Math.max(1, getConfig().getInt("default-limit", 10));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "scan" -> beginScan(sender, args);
            case "here" -> here(sender);
            case "near" -> near(sender, args);
            case "tp" -> tp(sender, args);
            case "book" -> book(sender);
            case "weights" -> showWeights(sender);
            case "reload" -> reload(sender);
            case "help" -> showHelp(sender);
            default -> {
                sender.sendMessage(msg.error("Unknown subcommand. Try /spotlight"));
                yield true;
            }
        };
    }

    private boolean showHelp(CommandSender sender) {
        if (!sender.hasPermission("spotlight.scan") && !sender.hasPermission("spotlight.admin")) {
            deny(sender, "spotlight.scan");
            return true;
        }
        String age = lastScanAt == null
                ? "never"
                : Durations.humanize(Duration.between(lastScanAt, Instant.now())) + " ago";
        SpotlightChat chat = new SpotlightChat(weights);
        sender.sendMessage(chat.help(sender::hasPermission, age));
        return true;
    }

    private boolean beginScan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spotlight.scan")) {
            deny(sender, "spotlight.scan");
            return true;
        }
        if (scanRunning) {
            sender.sendMessage(msg.warn("A scan is already running"));
            return true;
        }
        String worldFilter = null;
        int limit = defaultLimit;
        if (args.length >= 2) {
            World world = Bukkit.getWorld(args[1]);
            if (world != null) {
                worldFilter = world.getName();
                if (args.length >= 3) {
                    limit = parsePositive(args[2], defaultLimit);
                }
            } else {
                limit = parsePositive(args[1], defaultLimit);
            }
        }
        List<Chunk> targets = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (worldFilter != null && !world.getName().equals(worldFilter)) {
                continue;
            }
            targets.addAll(Arrays.asList(world.getLoadedChunks()));
        }
        if (targets.isEmpty()) {
            sender.sendMessage(msg.warn("No loaded chunks to scan"));
            return true;
        }
        // shortcut: scan state lives in memory and one scan runs at a time, persist or queue if staff need history
        scanRunning = true;
        List<Chunk> queue = targets;
        LagReport report = new LagReport();
        ChunkWeights scanWeights = weights;
        long startedAt = System.nanoTime();
        int finalLimit = limit;
        sender.sendMessage(msg.info("Scanning " + targets.size() + " chunks (" + chunksPerTick + "/tick)..."));
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            try {
                for (Chunk chunk : ScanBudget.take(queue, chunksPerTick)) {
                    if (!chunk.isLoaded()) {
                        continue;
                    }
                    ChunkCounts counts = countChunk(chunk);
                    int score = counts.score(scanWeights);
                    if (score > 0) {
                        report.add(new LagReport.ChunkCost(
                                chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), counts, score));
                    }
                }
            } catch (RuntimeException exception) {
                task.cancel();
                scanRunning = false;
                getLogger().warning("spotlight scan aborted: " + exception);
                sender.sendMessage(msg.error("Scan failed, see console"));
                return;
            }
            if (!queue.isEmpty()) {
                return;
            }
            task.cancel();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            getLogger().info("spotlight scan finished in " + elapsedMs + " ms (" + report.size() + " scored)");
            lastReport = report;
            lastScanAt = Instant.now();
            lastWeights = scanWeights;
            scanRunning = false;
            deliverScan(sender, report, scanWeights, finalLimit, elapsedMs);
        }, 1L, 1L);
        return true;
    }

    private void deliverScan(
            CommandSender sender, LagReport report, ChunkWeights scanWeights, int limit, long elapsedMs) {
        SpotlightChat chat = new SpotlightChat(scanWeights);
        List<LagReport.ChunkCost> top = report.top(limit);
        sender.sendMessage(chat.scanHeader(top.size(), report.size(), elapsedMs));
        boolean canTp = sender.hasPermission("spotlight.tp");
        int rank = 1;
        for (LagReport.ChunkCost cost : top) {
            sender.sendMessage(chat.resultLine(rank++, cost, canTp));
        }
        sender.sendMessage(chat.afterScanButtons());
    }

    private boolean here(CommandSender sender) {
        if (!sender.hasPermission("spotlight.scan")) {
            deny(sender, "spotlight.scan");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        Chunk chunk = player.getLocation().getChunk();
        ChunkCounts counts = countChunk(chunk);
        int score = counts.score(weights);
        String text = "Chunk " + chunk.getX() + "," + chunk.getZ() + " score=" + score
                + " (" + counts.explain(weights) + ")";
        player.sendActionBar(ActionBars.text(text));
        sender.sendMessage(msg.info(text));
        return true;
    }

    private boolean near(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spotlight.scan")) {
            deny(sender, "spotlight.scan");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        if (lastReport == null || lastWeights == null) {
            sender.sendMessage(msg.warn("No scan yet. Run /spotlight scan first"));
            return true;
        }
        int radius = args.length >= 2 ? parsePositive(args[1], 2) : 2;
        Chunk chunk = player.getLocation().getChunk();
        List<LagReport.ChunkCost> near = lastReport.near(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), radius, defaultLimit);
        SpotlightChat chat = new SpotlightChat(lastWeights);
        sender.sendMessage(msg.info("Near you (r=" + radius + "): " + near.size() + " chunks"));
        boolean canTp = sender.hasPermission("spotlight.tp");
        for (LagReport.ChunkCost cost : near) {
            int rank = lastReport.rankOf(cost);
            if (rank < 1) {
                continue;
            }
            sender.sendMessage(chat.resultLine(rank, cost, canTp));
        }
        return true;
    }

    private boolean tp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spotlight.tp")) {
            deny(sender, "spotlight.tp");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg.error("Usage: /spotlight tp <rank>"));
            return true;
        }
        if (lastReport == null) {
            sender.sendMessage(msg.warn("No scan yet. Run /spotlight scan first"));
            return true;
        }
        int rank = parsePositive(args[1], -1);
        if (rank < 1) {
            sender.sendMessage(msg.error("Rank must be a positive integer"));
            return true;
        }
        LagReport.ChunkCost cost = lastReport.atRank(rank);
        if (cost == null) {
            sender.sendMessage(msg.error("No result at rank " + rank));
            return true;
        }
        World world = Bukkit.getWorld(cost.world());
        if (world == null) {
            sender.sendMessage(msg.error("World not loaded: " + cost.world()));
            return true;
        }
        int blockX = cost.chunkX() * 16 + 8;
        int blockZ = cost.chunkZ() * 16 + 8;
        int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
        Location dest = new Location(world, blockX + 0.5, y, blockZ + 0.5);
        player.teleportAsync(dest).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                player.sendMessage(msg.success("Teleported to #" + rank + " " + cost.world()
                        + " " + cost.chunkX() + "," + cost.chunkZ()));
            } else {
                player.sendMessage(msg.error("Teleport failed"));
            }
        });
        return true;
    }

    private boolean book(CommandSender sender) {
        if (!sender.hasPermission("spotlight.scan")) {
            deny(sender, "spotlight.scan");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.error("Players only"));
            return true;
        }
        if (lastReport == null || lastWeights == null) {
            sender.sendMessage(msg.warn("No scan yet. Run /spotlight scan first"));
            return true;
        }
        SpotlightChat chat = new SpotlightChat(lastWeights);
        List<Component> pages = chat.bookPages(lastReport.top(50));
        Book book = Book.builder()
                .title(Component.text("Spotlight"))
                .author(Component.text("spotlight"))
                .pages(pages)
                .build();
        player.openBook(book);
        return true;
    }

    private boolean showWeights(CommandSender sender) {
        if (!sender.hasPermission("spotlight.admin")) {
            deny(sender, "spotlight.admin");
            return true;
        }
        sender.sendMessage(new SpotlightChat(weights).weightsLine(weights));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("spotlight.admin")) {
            deny(sender, "spotlight.admin");
            return true;
        }
        reloadLocalConfig();
        sender.sendMessage(msg.success("Reloaded spotlight config"));
        return true;
    }

    ChunkCounts countChunk(Chunk chunk) {
        int entities = 0;
        int minecarts = 0;
        int itemFrames = 0;
        int villagers = 0;
        for (Entity entity : chunk.getEntities()) {
            entities++;
            if (entity instanceof Minecart || isMinecartType(entity.getType())) {
                minecarts++;
            }
            if (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                itemFrames++;
            }
            if (entity instanceof Villager) {
                villagers++;
            }
        }
        int hoppers = 0;
        int redstone = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Hopper || state.getType() == Material.HOPPER) {
                hoppers++;
            }
            if (isRedstoneComponent(state.getType())) {
                redstone++;
            }
        }
        return new ChunkCounts(entities, hoppers, minecarts, redstone, itemFrames, villagers);
    }

    private static boolean isMinecartType(EntityType type) {
        String name = type.name();
        return name.contains("MINECART");
    }

    private static boolean isRedstoneComponent(Material material) {
        return switch (material) {
            case REDSTONE_WIRE, REDSTONE_TORCH, REDSTONE_WALL_TORCH, REPEATER, COMPARATOR,
                    OBSERVER, DAYLIGHT_DETECTOR, TARGET, LIGHTNING_ROD, SCULK_SENSOR,
                    CALIBRATED_SCULK_SENSOR, TRAPPED_CHEST, DISPENSER, DROPPER,
                    PISTON, STICKY_PISTON, NOTE_BLOCK, LEVER -> true;
            default -> material.name().endsWith("_BUTTON") || material.name().endsWith("_PRESSURE_PLATE");
        };
    }

    private void deny(CommandSender sender, String node) {
        sender.sendMessage(msg.error("Missing permission " + node));
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
        if (args.length == 1) {
            return filter(List.of("scan", "here", "near", "tp", "book", "weights", "reload", "help"), args[0]);
        }
        if (args.length == 2 && "scan".equalsIgnoreCase(args[0])) {
            List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
            return filter(worlds, args[1]);
        }
        if (args.length == 2 && "tp".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "2", "3", "4", "5"), args[1]);
        }
        if (args.length == 2 && "near".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "2", "4", "8"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
