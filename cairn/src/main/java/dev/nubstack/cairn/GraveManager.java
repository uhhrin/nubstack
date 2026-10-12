package dev.nubstack.cairn;

import dev.nubstack.cairn.grave.GraveKey;
import dev.nubstack.cairn.grave.GraveKeyItem;
import dev.nubstack.cairn.grave.GraveMarker;
import dev.nubstack.cairn.grave.GraveMessages;
import dev.nubstack.cairn.grave.GravePlanner;
import dev.nubstack.cairn.grave.GraveRecord;
import dev.nubstack.cairn.grave.GraveRegistry;
import dev.nubstack.cairn.grave.GraveStore;
import dev.nubstack.cairn.grave.ItemSnbt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;

/**
 * Places, tracks, expires and key-gates death graves on the server thread.
 *
 * World marker is a barrel (loot stays in the grave record, not the block entity).
 *
 * shortcut: vanishing-cursed items are stored in the grave instead of destroyed,
 * upgrade when strict vanilla parity is needed.
 */
final class GraveManager {

    private static final int SAVE_INTERVAL_TICKS = 20 * 60;

    private final CairnConfig config;
    private final GraveRegistry registry = new GraveRegistry();
    private final Path file;
    private final Logger logger;
    private final java.util.Map<UUID, String> pendingKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private int ticksSinceSave;
    private MinecraftServer server;

    GraveManager(CairnConfig config, Path file, Logger logger) {
        this.config = config;
        this.file = file;
        this.logger = logger;
    }

    GraveRegistry registry() {
        return registry;
    }

    CairnConfig config() {
        return config;
    }

    static boolean isAdmin(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    void load(MinecraftServer server) {
        this.server = server;
        try {
            registry.load(GraveStore.load(file));
            migrateLegacyChests(server);
            ensureMarkers(server);
            logger.info("cairn loaded {} grave(s)", registry.all().size());
        } catch (IOException | RuntimeException e) {
            registry.clear();
            logger.warn("cairn could not load graves from {}, starting empty", file, e);
        }
    }

    void save() {
        try {
            GraveStore.save(file, registry.snapshot());
        } catch (IOException e) {
            logger.warn("cairn could not save graves to {}", file, e);
        }
    }

    /**
     * Moves the player's inventory into a grave. Call before vanilla drops run.
     * Returns empty when nothing was moved, so vanilla drops proceed untouched.
     */
    Optional<GraveRecord> createGrave(ServerPlayer player) {
        ServerLevel level = player.level();
        List<ItemStack> stacks = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                stacks.add(inventory.getItem(i).copy());
            }
        }
        if (stacks.isEmpty()) {
            return Optional.empty();
        }
        BlockPos death = player.blockPosition();
        Optional<GravePlanner.IntPos> found = GravePlanner.findSafePos(
                new GravePlanner.IntPos(death.getX(), death.getY(), death.getZ()),
                level.getMinY(),
                level.getMaxY(),
                config.searchRadius(),
                (x, y, z) -> isFree(level, new BlockPos(x, y, z)));
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No safe space for a grave here; your items dropped normally"));
            return Optional.empty();
        }
        BlockPos pos = new BlockPos(found.get().x(), found.get().y(), found.get().z());
        registry.find(new GraveKey(dimensionOf(level), pos.getX(), pos.getY(), pos.getZ()))
                .ifPresent(stale -> {
                    registry.remove(stale.id());
                    finalizeRemove(level.getServer(), stale, false);
                });

        GravePlanner.PackPlan<ItemStack> plan = GravePlanner.assignSlots(stacks, GravePlanner.CHEST_SLOTS, 2);
        List<ItemStack> stored = new ArrayList<>();
        for (List<ItemStack> chest : plan.chests()) {
            stored.addAll(chest);
        }
        for (ItemStack leftover : plan.leftover()) {
            level.addFreshEntity(new ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover));
        }
        inventory.clearContent();

        GraveMarker.place(level, pos);
        List<String> snbt = ItemSnbt.encodeAll(level.registryAccess(), stored);

        GraveRecord grave = registry.add(new GraveRecord(
                registry.nextId(),
                player.getUUID(),
                player.getGameProfile().name(),
                dimensionOf(level),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                config.expireTicks(),
                snbt,
                null,
                null,
                0));
        registry.enforceMaxGraves(player.getUUID(), config.maxGravesPerPlayer(), evicted -> {
            release(player.level().getServer(), evicted);
            player.sendSystemMessage(GraveMessages.expiredWhisper());
        });
        pendingKeys.put(player.getUUID(), grave.id());
        save();
        player.sendSystemMessage(GraveMessages.deathMessage(
                grave.id(), grave.dimension(), grave.x(), grave.y(), grave.z(), isAdmin(player)));
        return Optional.of(grave);
    }

    /** Delivers a pending grave key after respawn. */
    void deliverPendingKey(ServerPlayer player) {
        String graveId = pendingKeys.remove(player.getUUID());
        if (graveId == null) {
            return;
        }
        registry.findById(graveId).ifPresent(grave -> giveKey(player, grave));
    }

    void tick(MinecraftServer server) {
        this.server = server;
        if (registry.all().isEmpty()) {
            return;
        }
        if (server.getTickCount() % 40 == 0) {
            ensureMarkers(server);
        }
        boolean changed = false;
        for (ServerLevel level : server.getAllLevels()) {
            for (GraveRecord expired : registry.tickDimension(dimensionOf(level), 1)) {
                release(server, expired);
                ServerPlayer owner = server.getPlayerList().getPlayer(expired.ownerId());
                if (owner != null) {
                    owner.sendSystemMessage(GraveMessages.expiredWhisper());
                }
                changed = true;
            }
        }
        for (GraveRecord grave : List.copyOf(registry.all())) {
            if (!grave.isDespawning()) {
                continue;
            }
            int left = grave.despawnAnimTicks() - 1;
            ServerLevel level = levelOf(server, grave);
            if (level != null) {
                playDespawnFx(level, grave, left);
            }
            if (left <= 0) {
                registry.remove(grave.id());
                if (level != null) {
                    GraveMarker.clear(level, new BlockPos(grave.x(), grave.y(), grave.z()));
                }
                stripKeys(server, grave.id());
                changed = true;
            } else {
                registry.replace(grave.withDespawnAnim(left));
                changed = true;
            }
        }
        if (changed || ++ticksSinceSave >= SAVE_INTERVAL_TICKS) {
            ticksSinceSave = 0;
            save();
        }
    }

    void release(MinecraftServer server, GraveRecord grave) {
        dropContents(server, grave);
        startDespawn(server, grave.withItems(List.of()));
    }

    void destroyNow(MinecraftServer server, GraveRecord grave) {
        dropContents(server, grave);
        finalizeRemove(server, grave, true);
    }

    private void dropContents(MinecraftServer server, GraveRecord grave) {
        ServerLevel level = levelOf(server, grave);
        if (level == null) {
            logger.warn("cairn grave {} is in unknown dimension {}", grave.id(), grave.dimension());
            return;
        }
        BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
        level.getChunkAt(pos);
        for (ItemStack stack : ItemSnbt.decodeAll(level.registryAccess(), grave.itemsSnbt())) {
            Block.popResource(level, pos, stack);
        }
    }

    void startDespawn(MinecraftServer server, GraveRecord grave) {
        Optional<GraveRecord> current = registry.findById(grave.id());
        GraveRecord base = current.orElse(grave);
        // ticksRemaining 0 + isDespawning so tickDimension will not re-expire and reset the anim.
        GraveRecord fading = base.withItems(List.of()).withTicks(0).withDespawnAnim(GraveRecord.DESPAWN_ANIM_TICKS);
        if (registry.findById(base.id()).isEmpty()) {
            registry.add(fading);
        } else {
            registry.replace(fading);
        }
        ServerLevel level = levelOf(server, base);
        if (level != null) {
            playDespawnFx(level, base, GraveRecord.DESPAWN_ANIM_TICKS);
        }
        save();
    }

    private void finalizeRemove(MinecraftServer server, GraveRecord grave, boolean stripKeys) {
        ServerLevel level = levelOf(server, grave);
        if (level != null) {
            GraveMarker.clear(level, new BlockPos(grave.x(), grave.y(), grave.z()));
        }
        if (stripKeys) {
            stripKeys(server, grave.id());
        }
    }

    boolean tryOpen(ServerPlayer player, net.minecraft.world.entity.Entity entity) {
        Optional<GraveRecord> found = registry.findByEntity(entity.getUUID());
        if (found.isEmpty()) {
            return false;
        }
        return openIfAllowed(player, found.get());
    }

    boolean tryOpenAt(ServerPlayer player, BlockPos pos) {
        Optional<GraveRecord> found = findNear(player.level(), pos);
        return found.filter(grave -> openIfAllowed(player, grave)).isPresent();
    }

    private boolean openIfAllowed(ServerPlayer player, GraveRecord grave) {
        if (grave.isDespawning()) {
            player.sendSystemMessage(Component.literal("This grave is fading away"), true);
            return true;
        }
        if (!mayOpen(player, grave)) {
            player.sendSystemMessage(Component.literal("Hold the matching grave key to open this"));
            return true;
        }
        openMenu(player, grave);
        return true;
    }

    boolean mayOpen(ServerPlayer player, GraveRecord grave) {
        if (isAdmin(player)) {
            return true;
        }
        return GraveKeyItem.matches(player.getMainHandItem(), grave.id())
                || GraveKeyItem.matches(player.getOffhandItem(), grave.id());
    }

    boolean isGraveMarker(Level level, BlockPos pos) {
        return graveAt(level, pos).isPresent() && GraveMarker.isMarker(level.getBlockState(pos));
    }

    void openMenu(ServerPlayer player, GraveRecord grave) {
        ServerLevel level = player.level();
        List<ItemStack> stacks = ItemSnbt.decodeAll(level.registryAccess(), grave.itemsSnbt());
        int rows = stacks.size() > GravePlanner.CHEST_SLOTS ? 6 : 3;
        int size = rows * 9;
        SimpleContainer container = new SimpleContainer(size) {
            @Override
            public boolean stillValid(Player viewer) {
                return viewer.distanceToSqr(grave.x() + 0.5, grave.y() + 0.5, grave.z() + 0.5) <= 64.0;
            }
        };
        for (int i = 0; i < stacks.size() && i < size; i++) {
            container.setItem(i, stacks.get(i));
        }
        String graveId = grave.id();
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, viewer) -> new ChestMenu(
                        rows == 6
                                ? net.minecraft.world.inventory.MenuType.GENERIC_9x6
                                : net.minecraft.world.inventory.MenuType.GENERIC_9x3,
                        syncId,
                        inv,
                        container,
                        rows) {
                    @Override
                    public void removed(Player removed) {
                        super.removed(removed);
                        syncContainer(graveId, container);
                    }
                },
                Component.literal("Grave #" + grave.id())));
    }

    void syncContainer(String graveId, SimpleContainer container) {
        Optional<GraveRecord> found = registry.findById(graveId);
        if (found.isEmpty() || server == null) {
            return;
        }
        GraveRecord grave = found.get();
        if (grave.isDespawning()) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        List<String> snbt = ItemSnbt.encodeAll(server.registryAccess(), stacks);
        GraveRecord updated = grave.withItems(snbt);
        registry.replace(updated);
        if (updated.isEmpty()) {
            startDespawn(server, updated);
        } else {
            save();
        }
    }

    ItemStack reissueKey(String graveId) {
        return registry.findById(graveId)
                .map(grave -> GraveKeyItem.create(grave.id(), grave.ownerName()))
                .orElse(ItemStack.EMPTY);
    }

    void giveKey(ServerPlayer player, GraveRecord grave) {
        ItemStack key = GraveKeyItem.create(grave.id(), grave.ownerName());
        if (!player.getInventory().add(key)) {
            player.drop(key, false);
        }
    }

    void stripKeys(MinecraftServer server, String graveId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (GraveKeyItem.matches(stack, graveId)) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    int clearStale(MinecraftServer server) {
        int removed = 0;
        for (GraveRecord grave : List.copyOf(registry.all())) {
            if (grave.isDespawning()) {
                continue;
            }
            ServerLevel level = levelOf(server, grave);
            BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
            boolean missing = level == null
                    || (level.isLoaded(pos) && !GraveMarker.isMarker(level.getBlockState(pos)));
            if (missing && registry.remove(grave.id())) {
                if (level != null) {
                    for (ItemStack stack : ItemSnbt.decodeAll(level.registryAccess(), grave.itemsSnbt())) {
                        Block.popResource(level, pos, stack);
                    }
                }
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    Optional<GraveRecord> graveAt(Level level, BlockPos pos) {
        return registry.find(new GraveKey(dimensionOf(level), pos.getX(), pos.getY(), pos.getZ()));
    }

    Optional<GraveRecord> findNear(Level level, BlockPos pos) {
        String dimension = dimensionOf(level);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Optional<GraveRecord> found = registry.find(new GraveKey(
                            dimension, pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void migrateLegacyChests(MinecraftServer server) {
        boolean changed = false;
        for (GraveRecord grave : List.copyOf(registry.all())) {
            if (!grave.itemsSnbt().isEmpty()) {
                continue;
            }
            ServerLevel level = levelOf(server, grave);
            if (level == null) {
                continue;
            }
            BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.CHEST)) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            List<BlockPos> halves = new ArrayList<>();
            halves.add(pos);
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                halves.add(ChestBlock.getConnectedBlockPos(pos, state));
            }
            for (BlockPos half : halves) {
                if (level.getBlockEntity(half) instanceof ChestBlockEntity chest) {
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        ItemStack stack = chest.getItem(i);
                        if (!stack.isEmpty()) {
                            stacks.add(stack.copy());
                        }
                    }
                    chest.clearContent();
                }
                level.setBlock(half, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            GraveMarker.place(level, pos);
            registry.replace(grave
                    .withItems(ItemSnbt.encodeAll(level.registryAccess(), stacks))
                    .withEntities(null, null));
            changed = true;
            logger.info("cairn migrated legacy chest grave #{}", grave.id());
        }
        if (changed) {
            save();
        }
    }

    private void ensureMarkers(MinecraftServer server) {
        boolean changed = false;
        for (GraveRecord grave : List.copyOf(registry.all())) {
            if (grave.isDespawning()) {
                continue;
            }
            ServerLevel level = levelOf(server, grave);
            if (level == null) {
                continue;
            }
            BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
            level.getChunkAt(pos);
            // Replace wall leftovers and missing markers with the current barrel marker.
            if (!level.getBlockState(pos).is(Blocks.BARREL)) {
                GraveMarker.place(level, pos);
                logger.info("cairn re-placed marker for grave #{} at {} {} {}", grave.id(), grave.x(), grave.y(), grave.z());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void playDespawnFx(ServerLevel level, GraveRecord grave, int ticksLeft) {
        double x = grave.x() + 0.5;
        double y = grave.y() + 0.5;
        double z = grave.z() + 0.5;
        level.sendParticles(ParticleTypes.SOUL, x, y, z, 6, 0.25, 0.35, 0.25, 0.01);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 4, 0.2, 0.2, 0.2, 0.02);
        if (ticksLeft == GraveRecord.DESPAWN_ANIM_TICKS || ticksLeft == 1) {
            level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        if (ticksLeft <= 1) {
            GraveMarker.clear(level, new BlockPos(grave.x(), grave.y(), grave.z()));
        }
    }

    private static ServerLevel levelOf(MinecraftServer server, GraveRecord grave) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(grave.dimension())));
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getBlockState(pos).isAir();
    }
}
