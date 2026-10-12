package dev.nubstack.cairn;

import dev.nubstack.cairn.grave.AccessMode;
import dev.nubstack.cairn.grave.GraveRecord;
import dev.nubstack.cairn.grave.XpPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side death chests for vanilla clients.
 */
public final class CairnMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("cairn");

    private record Experience(int level, float progress, int total) {
    }

    private final Map<UUID, Experience> keptExperience = new ConcurrentHashMap<>();

    @Override
    public void onInitializeServer() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        CairnConfig config = loadConfig(configDir.resolve("cairn.json"));
        GraveManager graves = new GraveManager(config, configDir.resolve("cairn-graves.json"), LOGGER);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> graves.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> graves.save());
        ServerTickEvents.END_SERVER_TICK.register(graves::tick);

        // Runs before vanilla drops, so moving the inventory here means nothing is dropped twice.
        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
                return true;
            }
            if (config.xpPolicy() == XpPolicy.KEEP) {
                keepExperience(player);
            }
            graves.createGrave(player);
            return true;
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            Experience saved = keptExperience.remove(oldPlayer.getUUID());
            if (saved != null && !alive) {
                newPlayer.experienceLevel = saved.level();
                newPlayer.experienceProgress = saved.progress();
                newPlayer.totalExperience = saved.total();
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            Optional<GraveRecord> grave = graves.graveAt(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos()));
            if (grave.isPresent() && !mayAccess(config.accessMode(), grave.get(), serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal("This grave belongs to " + grave.get().ownerName()), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            Optional<GraveRecord> grave = graves.graveAt(world, pos, state);
            return grave.isEmpty() || mayAccess(config.accessMode(), grave.get(), serverPlayer);
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide()) {
                graves.graveAt(world, pos, state).ifPresent(grave -> {
                    graves.registry().remove(grave.id());
                    graves.save();
                });
            }
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext, selection) -> new CairnCommands(graves).register(dispatcher));

        LOGGER.info("cairn enabled");
    }

    private static boolean mayAccess(AccessMode mode, GraveRecord grave, ServerPlayer player) {
        return mode.allows(grave.ownerId(), player.getUUID(), GraveManager.isAdmin(player));
    }

    private void keepExperience(ServerPlayer player) {
        keptExperience.put(player.getUUID(),
                new Experience(player.experienceLevel, player.experienceProgress, player.totalExperience));
        // vanilla drops orbs from the level, so zero it before drops run
        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.totalExperience = 0;
    }

    private static CairnConfig loadConfig(Path path) {
        try {
            return CairnConfig.loadOrCreate(path);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("cairn could not read {}, using defaults", path, e);
            return new CairnConfig();
        }
    }
}
