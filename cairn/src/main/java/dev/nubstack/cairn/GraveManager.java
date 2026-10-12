package dev.nubstack.cairn;

import dev.nubstack.cairn.grave.GraveKey;
import dev.nubstack.cairn.grave.GraveMessages;
import dev.nubstack.cairn.grave.GravePlanner;
import dev.nubstack.cairn.grave.GraveRecord;
import dev.nubstack.cairn.grave.GraveRegistry;
import dev.nubstack.cairn.grave.GraveStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
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
 * Places, tracks, expires and protects death chests on the server thread.
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
    private int ticksSinceSave;

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

    void load() {
        try {
            registry.load(GraveStore.load(file));
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
        Direction facing = player.getDirection().getOpposite();
        BlockPos partner = null;
        BlockState first = chest(facing, ChestType.SINGLE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState candidate = chest(direction, ChestType.LEFT);
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, candidate);
            if (isFree(level, other)) {
                first = candidate;
                partner = other;
                break;
            }
        }
        registry.find(new GraveKey(dimensionOf(level), pos.getX(), pos.getY(), pos.getZ()))
                .ifPresent(stale -> registry.remove(stale.id()));

        GravePlanner.PackPlan<ItemStack> plan = GravePlanner.assignSlots(
                stacks, GravePlanner.CHEST_SLOTS, partner == null ? 1 : 2);
        level.setBlock(pos, first, Block.UPDATE_ALL);
        if (partner != null) {
            level.setBlock(partner, chest(first.getValue(ChestBlock.FACING), ChestType.RIGHT), Block.UPDATE_ALL);
        }
        fill(level, pos, plan.chests().getFirst());
        if (partner != null && plan.chests().size() > 1) {
            fill(level, partner, plan.chests().get(1));
        }
        for (ItemStack leftover : plan.leftover()) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover));
        }
        inventory.clearContent();

        GraveRecord grave = registry.add(new GraveRecord(
                registry.nextId(),
                player.getUUID(),
                player.getGameProfile().name(),
                dimensionOf(level),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                config.expireTicks()));
        registry.enforceMaxGraves(player.getUUID(), config.maxGravesPerPlayer(), evicted -> {
            release(player.level().getServer(), evicted);
            player.sendSystemMessage(GraveMessages.expiredWhisper());
        });
        save();
        player.sendSystemMessage(GraveMessages.deathMessage(
                grave.id(), grave.dimension(), grave.x(), grave.y(), grave.z(), isAdmin(player)));
        return Optional.of(grave);
    }

    void tick(MinecraftServer server) {
        if (registry.all().isEmpty()) {
            return;
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
        if (changed || ++ticksSinceSave >= SAVE_INTERVAL_TICKS) {
            ticksSinceSave = 0;
            save();
        }
    }

    /** Drops the contents of a grave chest and removes the block. The record must already be removed. */
    void release(MinecraftServer server, GraveRecord grave) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(grave.dimension())));
        if (level == null) {
            logger.warn("cairn grave {} is in unknown dimension {}", grave.id(), grave.dimension());
            return;
        }
        BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
        // shortcut: loads the chunk on the server thread, upgrade to a chunk ticket if expiry lag shows up
        level.getChunkAt(pos);
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CHEST)) {
            return;
        }
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
                        Block.popResource(level, half, stack.copy());
                    }
                }
                chest.clearContent();
            }
            level.setBlock(half, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Forgets graves whose chest is gone (broken, blown up, replaced). Graves in unloaded
     * chunks are skipped. Returns the number of records removed.
     */
    int clearStale(MinecraftServer server) {
        int removed = 0;
        for (GraveRecord grave : List.copyOf(registry.all())) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(grave.dimension())));
            BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
            boolean missing = level == null || (level.isLoaded(pos) && !level.getBlockState(pos).is(Blocks.CHEST));
            if (missing && registry.remove(grave.id())) {
                removed++;
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** Finds the grave owning a chest half at {@code pos}, given the block state at that position. */
    Optional<GraveRecord> graveAt(Level level, BlockPos pos, BlockState state) {
        String dimension = dimensionOf(level);
        Optional<GraveRecord> direct = registry.find(new GraveKey(dimension, pos.getX(), pos.getY(), pos.getZ()));
        if (direct.isPresent() || !state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return direct;
        }
        BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
        return registry.find(new GraveKey(dimension, other.getX(), other.getY(), other.getZ()));
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getBlockState(pos).isAir();
    }

    private static BlockState chest(Direction facing, ChestType type) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, type);
    }

    private static void fill(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int slot = 0; slot < stacks.size(); slot++) {
                chest.setItem(slot, stacks.get(slot));
            }
            return;
        }
        for (ItemStack stack : stacks) {
            Block.popResource(level, pos, stack);
        }
    }
}
