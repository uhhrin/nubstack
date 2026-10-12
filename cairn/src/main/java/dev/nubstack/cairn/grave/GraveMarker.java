package dev.nubstack.cairn.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * World marker for a grave. Must be a block the client will send a use packet
 * for (walls often do not through Via). Loot stays in {@link GraveRecord}, never
 * in the block entity.
 */
public final class GraveMarker {

    // shortcut: barrel until custom model pack works; upgrade when ItemDisplay is reliable
    public static final BlockState BLOCK = Blocks.BARREL.defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.UP);

    private GraveMarker() {
    }

    public static boolean isMarker(BlockState state) {
        return state.is(Blocks.BARREL)
                // leftover from the wall marker while graves migrate on ensureMarkers
                || state.is(Blocks.MOSSY_STONE_BRICK_WALL);
    }

    public static void place(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        level.setBlock(pos, BLOCK, Block.UPDATE_ALL);
    }

    public static void clear(ServerLevel level, BlockPos pos) {
        if (isMarker(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
