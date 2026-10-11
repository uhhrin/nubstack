package dev.nubstack.cairn;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CairnMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("cairn");
    private static final int EXPIRE_TICKS = 20 * 60 * 15;
    private final Map<BlockPos, Integer> graves = new ConcurrentHashMap<>();

    @Override
    public void onInitializeServer() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            if (!level.getBlockState(pos).isAir()) {
                pos = pos.above();
            }
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                int slot = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.isEmpty() || slot >= chest.getContainerSize()) {
                        continue;
                    }
                    chest.setItem(slot++, stack.copy());
                    stack.setCount(0);
                }
                for (ItemStack stack : player.getInventory().armor) {
                    if (stack.isEmpty() || slot >= chest.getContainerSize()) {
                        continue;
                    }
                    chest.setItem(slot++, stack.copy());
                    stack.setCount(0);
                }
                for (ItemStack stack : player.getInventory().offhand) {
                    if (stack.isEmpty() || slot >= chest.getContainerSize()) {
                        continue;
                    }
                    chest.setItem(slot++, stack.copy());
                    stack.setCount(0);
                }
                chest.setCustomName(Component.literal(player.getGameProfile().getName() + "'s grave"));
            }
            graves.put(pos.immutable(), EXPIRE_TICKS);
            player.sendSystemMessage(Component.literal("Your grave was placed at " + pos.toShortString()));
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (graves.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<BlockPos, Integer>> iterator = graves.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = iterator.next();
                int left = entry.getValue() - 1;
                if (left > 0) {
                    entry.setValue(left);
                    continue;
                }
                BlockPos pos = entry.getKey();
                if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        ItemStack stack = chest.getItem(i);
                        if (!stack.isEmpty()) {
                            world.addFreshEntity(new ItemEntity(
                                    world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
                        }
                    }
                }
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                iterator.remove();
            }
        });

        LOGGER.info("cairn enabled");
    }
}
