package dev.nubstack.amber;

import dev.nubstack.amber.SnapshotService.SnapshotRef;
import dev.nubstack.spine.text.ConfirmChat;
import dev.nubstack.spine.text.PrefixedMessages;
import dev.nubstack.spine.ui.BossBars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class AmberCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("snapshot", "list", "info", "restore", "delete", "prune", "cancel", "reload");
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private static final int MAX_LABEL = 64;
    private static final PrefixedMessages MSG = AmberMessages.prefixed();

    private static final SnapshotService.FreeSpace FREE_SPACE = path -> Files.getFileStore(path).getUsableSpace();

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private final JavaPlugin plugin;
    private final ConfirmChat confirms = new ConfirmChat();
    private final Set<String> restoring = ConcurrentHashMap.newKeySet();
    private volatile SnapshotService service;
    private volatile AmberSettings settings;

    // shortcut: runs on the async copy thread; CraftWorld#setAutoSave is a plain flag write, move it to the main
    // thread if Paper starts asserting thread ownership there.
    private static final SnapshotService.AutosaveControl AUTOSAVE = new SnapshotService.AutosaveControl() {
        @Override
        public boolean isAutoSave(String worldName) {
            World world = Bukkit.getWorld(worldName);
            return world == null || world.isAutoSave();
        }

        @Override
        public void setAutoSave(String worldName, boolean enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setAutoSave(enabled);
            }
        }
    };

    AmberCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads {@code config.yml} into a fresh service.
     *
     * @throws IllegalArgumentException when the configuration is invalid; the previous settings stay active
     */
    void reload() {
        plugin.reloadConfig();
        var config = plugin.getConfig();
        AmberSettings next = AmberSettings.of(
                plugin.getDataFolder().toPath(),
                config.getString("snapshot-dir", "snapshots"),
                config.getInt("retain", 5),
                config.getString("default-world", "world"),
                config.getStringList("exclude"),
                config.getBoolean("move-players-on-restore", true));
        this.service = new SnapshotService(next.snapshotDir(), next.retain(), next.exclude());
        this.settings = next;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(AmberMessages.ADMIN)) {
            sender.sendMessage(MSG.error("No permission"));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "snapshot" -> snapshot(sender, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "delete" -> delete(sender, args);
            case "restore" -> restore(sender, args);
            case "prune" -> prune(sender);
            case "cancel" -> cancel(sender, args);
            case "confirm" -> confirm(sender, args);
            case "reload" -> reloadCommand(sender);
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(AmberMessages.ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return startingWith(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "snapshot", "list" -> startingWith(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
                case "info", "restore", "delete" -> startingWith(service.snapshotNames(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private void help(CommandSender sender) {
        SnapshotService svc = service;
        async(sender, () -> AmberMessages.help(sender::hasPermission, svc.diskUsedBytes()));
    }

    private void snapshot(CommandSender sender, String[] args) {
        String worldName = args.length > 1 ? args[1] : settings.defaultWorld();
        String label = args.length > 2 ? String.join(" ", List.of(args).subList(2, args.length)).trim() : "";
        if (label.length() > MAX_LABEL) {
            sender.sendMessage(MSG.error("Label is limited to " + MAX_LABEL + " characters"));
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(MSG.error("World not loaded: " + worldName));
            return;
        }
        if (service.isSnapshotInProgress(worldName) || restoring.contains(worldName)) {
            sender.sendMessage(MSG.error("A snapshot or restore of " + worldName + " is already running"));
            return;
        }
        startSnapshot(
                sender,
                world,
                label,
                "Snapshot " + worldName,
                ref -> sender.sendMessage(AmberMessages.created(ref)),
                error -> sender.sendMessage(MSG.error("Snapshot failed: " + error.getMessage())));
    }

    /**
     * Saves the world on the calling (main) thread, then copies on an async task while showing a boss bar.
     */
    private void startSnapshot(
            CommandSender sender,
            World world,
            String label,
            String barTitle,
            Consumer<SnapshotRef> onDone,
            Consumer<Exception> onFail) {
        SnapshotService svc = service;
        String worldName = world.getName();
        Path folder = world.getWorldFolder().toPath();
        world.save();
        BossBar bar = BossBars.progress(barTitle, 0f);
        if (sender instanceof Player player) {
            player.showBossBar(bar);
        }
        AtomicInteger lastPercent = new AtomicInteger(-1);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SnapshotRef ref = svc.create(worldName, folder, label, AUTOSAVE, FREE_SPACE, progress -> {
                    int percent = Math.round(progress.fraction() * 100f);
                    if (lastPercent.getAndSet(percent) != percent) {
                        Bukkit.getScheduler().runTask(plugin, () -> BossBars.updateProgress(bar, barTitle, progress.fraction()));
                    }
                });
                Bukkit.getScheduler().runTask(plugin, () -> {
                    hideBar(sender, bar);
                    onDone.accept(ref);
                });
            } catch (IOException | RuntimeException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    hideBar(sender, bar);
                    onFail.accept(e);
                });
            }
        });
    }

    private static void hideBar(CommandSender sender, BossBar bar) {
        if (sender instanceof Player player) {
            player.hideBossBar(bar);
        }
    }

    private void list(CommandSender sender, String[] args) {
        SnapshotService svc = service;
        String world = args.length > 1 ? args[1] : null;
        async(sender, () -> AmberMessages.list(svc.list(world)));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MSG.error("Usage: /amber info <file>"));
            return;
        }
        SnapshotService svc = service;
        async(sender, () -> AmberMessages.info(svc.info(args[1])));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MSG.error("Usage: /amber delete <file>"));
            return;
        }
        SnapshotService svc = service;
        asyncThen(sender, () -> svc.info(args[1]), ref -> {
            String file = ref.path().getFileName().toString();
            ConfirmChat.Prompt prompt = confirms.ask(
                    idOf(sender),
                    "Delete snapshot " + file + "?",
                    "amber",
                    () -> async(sender, () -> {
                        svc.delete(file);
                        return MSG.success("Deleted " + file);
                    }),
                    () -> sender.sendMessage(MSG.info("Delete cancelled")));
            sender.sendMessage(prompt.message());
        });
    }

    private void prune(CommandSender sender) {
        SnapshotService svc = service;
        int retain = settings.retain();
        async(sender, () -> {
            svc.prune();
            return MSG.success("Retention applied: keeping the newest " + retain);
        });
    }

    private void cancel(CommandSender sender, String[] args) {
        if (args.length > 1) {
            resolve(sender, args[1], false);
            return;
        }
        sender.sendMessage(service.cancel()
                ? MSG.warn("Cancelling running snapshot")
                : MSG.info("No snapshot is running"));
    }

    private void confirm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MSG.error("Usage: /amber confirm <token>"));
            return;
        }
        resolve(sender, args[1], true);
    }

    private void resolve(CommandSender sender, String token, boolean accepted) {
        try {
            if (!confirms.resolve(idOf(sender), token, accepted)) {
                sender.sendMessage(MSG.error("Nothing to confirm, or it expired"));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MSG.error("That confirmation belongs to someone else"));
        }
    }

    private void reloadCommand(CommandSender sender) {
        if (!service.isIdle()) {
            sender.sendMessage(MSG.error("Cannot reload while a snapshot or restore is running"));
            return;
        }
        try {
            reload();
            sender.sendMessage(MSG.success("Configuration reloaded"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MSG.error("Config rejected, keeping previous settings: " + e.getMessage()));
        }
    }

    private void restore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MSG.error("Usage: /amber restore <file> [world]"));
            return;
        }
        String typedWorld = args.length > 2 ? args[2] : null;
        SnapshotService svc = service;
        asyncThen(sender, () -> svc.info(args[1]), ref -> {
            if (typedWorld == null) {
                sender.sendMessage(AmberMessages.restoreTypePrompt(ref));
                return;
            }
            if (!typedWorld.equals(ref.world())) {
                sender.sendMessage(MSG.error("World name does not match the snapshot's world: " + ref.world()));
                return;
            }
            String problem = restoreProblem(svc, ref);
            if (problem != null) {
                sender.sendMessage(MSG.error(problem));
                return;
            }
            String file = ref.path().getFileName().toString();
            ConfirmChat.Prompt prompt = confirms.ask(
                    idOf(sender),
                    "Restore " + file + " into " + ref.world() + "? A safety snapshot is taken first.",
                    "amber",
                    () -> beginRestore(sender, svc, ref),
                    () -> sender.sendMessage(MSG.info("Restore cancelled")));
            sender.sendMessage(prompt.message());
        });
    }

    /**
     * @return why the restore cannot start now, or null
     */
    private String restoreProblem(SnapshotService svc, SnapshotRef ref) {
        String name = ref.world();
        World world = Bukkit.getWorld(name);
        if (world == null) {
            return "World " + name + " is not loaded; load it before restoring";
        }
        if (world.equals(Bukkit.getWorlds().getFirst())) {
            // shortcut: the main world cannot be unloaded while the server runs; upgrade only with a restart flow.
            return "The main server world cannot be unloaded at runtime. Stop the server and copy the snapshot "
                    + "over the world folder instead";
        }
        if (restoring.contains(name) || svc.isSnapshotInProgress(name)) {
            return "A snapshot or restore of " + name + " is already running";
        }
        if (!world.getPlayers().isEmpty()) {
            if (!settings.movePlayers()) {
                return "Players are in " + name + "; move them out or enable move-players-on-restore";
            }
            World fallback = Bukkit.getWorld(settings.defaultWorld());
            if (fallback == null || fallback.equals(world)) {
                return "Players are in " + name + " and default-world " + settings.defaultWorld()
                        + " is not a different loaded world to move them to";
            }
        }
        return null;
    }

    private void beginRestore(CommandSender sender, SnapshotService svc, SnapshotRef ref) {
        String problem = restoreProblem(svc, ref);
        if (problem != null) {
            sender.sendMessage(MSG.error(problem));
            return;
        }
        String name = ref.world();
        World world = Bukkit.getWorld(name);
        restoring.add(name);
        svc.beginRestore(ref.path());
        Runnable finish = () -> {
            svc.endRestore(ref.path());
            restoring.remove(name);
        };
        sender.sendMessage(MSG.info("Taking a safety snapshot first"));
        startSnapshot(
                sender,
                world,
                "pre-restore",
                "Safety snapshot",
                safety -> swapWorld(sender, svc, ref, safety, finish),
                error -> {
                    sender.sendMessage(MSG.error("Restore aborted, world untouched: " + error.getMessage()));
                    finish.run();
                });
    }

    private void swapWorld(CommandSender sender, SnapshotService svc, SnapshotRef ref, SnapshotRef safety, Runnable finish) {
        String name = ref.world();
        String safetyFile = safety.path().getFileName().toString();
        World world = Bukkit.getWorld(name);
        String problem = world == null ? "World " + name + " is no longer loaded" : null;
        List<Player> players = world == null ? List.of() : List.copyOf(world.getPlayers());
        World fallback = Bukkit.getWorld(settings.defaultWorld());
        if (problem == null && !players.isEmpty()) {
            if (!settings.movePlayers() || fallback == null || fallback.equals(world)) {
                problem = "Players are in " + name;
            } else {
                Location spawn = fallback.getSpawnLocation();
                players.forEach(player -> player.teleport(spawn));
            }
        }
        if (problem == null) {
            WorldCreator creator = WorldCreator.name(name).copy(world);
            Path folder = world.getWorldFolder().toPath();
            if (!Bukkit.unloadWorld(world, true)) {
                problem = "World " + name + " could not be unloaded";
            } else {
                sender.sendMessage(MSG.info("World unloaded, swapping files"));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Path moved = null;
                    Exception failure = null;
                    try {
                        moved = svc.swapIn(ref.path(), name, folder, SnapshotService.stamp(Instant.now()));
                    } catch (IOException | RuntimeException e) {
                        failure = e;
                    }
                    Path movedTo = moved;
                    Exception failed = failure;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        World loaded = Bukkit.createWorld(creator);
                        finish.run();
                        if (failed != null) {
                            sender.sendMessage(MSG.error("Restore failed, original world put back: " + failed.getMessage()));
                        } else if (loaded == null) {
                            sender.sendMessage(MSG.error("Files restored but world " + name + " failed to load; see console"));
                        } else {
                            String previous = movedTo.getFileName().toString();
                            sender.sendMessage(Component.text()
                                    .append(MSG.success("Restored " + name + ". Previous state kept as " + previous))
                                    .append(Component.space())
                                    .append(MSG.copyButton("Copy folder name", previous))
                                    .build());
                        }
                    });
                });
                return;
            }
        }
        sender.sendMessage(MSG.error("Restore aborted: " + problem + ". Safety snapshot kept: " + safetyFile));
        finish.run();
    }

    private void async(CommandSender sender, IoSupplier<Component> task) {
        asyncThen(sender, task, sender::sendMessage);
    }

    /**
     * Runs {@code task} off the main thread, then hands the result (or an error line) back on the main thread.
     */
    private <T> void asyncThen(CommandSender sender, IoSupplier<T> task, Consumer<T> onMain) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result = null;
            String error = null;
            try {
                result = task.get();
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            T value = result;
            String message = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (message != null) {
                    sender.sendMessage(MSG.error(message));
                } else {
                    onMain.accept(value);
                }
            });
        });
    }

    private static UUID idOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private static List<String> startingWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
