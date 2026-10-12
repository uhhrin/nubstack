package dev.nubstack.cairn;

import dev.nubstack.cairn.grave.GraveKeyItem;
import dev.nubstack.cairn.grave.XpPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side death graves for vanilla clients.
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

        ServerLifecycleEvents.SERVER_STARTED.register(graves::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> graves.save());
        ServerTickEvents.END_SERVER_TICK.register(graves::tick);

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
            if (!alive) {
                graves.deliverPendingKey(newPlayer);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (graves.tryOpenAt(serverPlayer, hit.getBlockPos())
                    || graves.tryOpenAt(serverPlayer, hit.getBlockPos().relative(hit.getDirection()))) {
                return InteractionResult.SUCCESS_SERVER;
            }
            if (GraveKeyItem.isGraveKey(serverPlayer.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        // Via / older clients often skip UseBlock on non-container blocks; key use still raycasts.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!GraveKeyItem.isGraveKey(serverPlayer.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            HitResult hit = serverPlayer.pick(serverPlayer.blockInteractionRange(), 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK
                    && graves.tryOpenAt(serverPlayer, ((BlockHitResult) hit).getBlockPos())) {
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (graves.tryOpen(serverPlayer, entity)) {
                return InteractionResult.SUCCESS_SERVER;
            }
            if (GraveKeyItem.isGraveKey(serverPlayer.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            if (graves.isGraveMarker(world, pos)) {
                // Admins may break to clear the marker; loot stays until expire/clear.
                return GraveManager.isAdmin(serverPlayer);
            }
            return true;
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext, selection) -> new CairnCommands(graves).register(dispatcher));

        LOGGER.info("cairn enabled");
    }

    private void keepExperience(ServerPlayer player) {
        keptExperience.put(player.getUUID(),
                new Experience(player.experienceLevel, player.experienceProgress, player.totalExperience));
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
