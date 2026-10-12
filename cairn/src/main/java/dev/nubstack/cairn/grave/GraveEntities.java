package dev.nubstack.cairn.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Spawns Interaction (click) + BlockDisplay (always-visible marker) + ItemDisplay (pack model).
 */
public final class GraveEntities {

    private static final Identifier GRAVE_MODEL = Identifier.fromNamespaceAndPath("cairn", "grave");
    private static final float HITBOX_WIDTH = 1.1f;
    private static final float HITBOX_HEIGHT = 1.4f;
    private static final float DISPLAY_SCALE = 1.0f;

    private GraveEntities() {
    }

    public static Interaction spawnInteraction(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        Interaction interaction = EntityTypes.INTERACTION.create(level, EntitySpawnReason.COMMAND);
        if (interaction == null) {
            throw new IllegalStateException("could not create interaction entity");
        }
        interaction.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        applySize(interaction, HITBOX_WIDTH, HITBOX_HEIGHT);
        if (!level.addFreshEntity(interaction)) {
            throw new IllegalStateException("could not add interaction entity at " + pos);
        }
        return interaction;
    }

    /**
     * Visible without a resource pack (mossy stone brick wall). ItemDisplay alone was
     * vanishing / invisible for clients.
     */
    public static Display.BlockDisplay spawnBlockDisplay(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (display == null) {
            throw new IllegalStateException("could not create block display entity");
        }
        display.snapTo(pos.getX() + 0.25, pos.getY(), pos.getZ() + 0.25, 0.0f, 0.0f);
        CompoundTag nbt = new CompoundTag();
        CompoundTag blockState = new CompoundTag();
        blockState.putString("Name", "minecraft:mossy_stone_brick_wall");
        nbt.put("block_state", blockState);
        CompoundTag transformation = new CompoundTag();
        transformation.putFloat("scale", DISPLAY_SCALE);
        nbt.put("transformation", transformation);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
        if (!level.addFreshEntity(display)) {
            throw new IllegalStateException("could not add block display at " + pos);
        }
        return display;
    }

    /** Optional pack overlay; failures here must not break the grave. */
    public static Display.ItemDisplay spawnItemDisplay(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (display == null) {
            throw new IllegalStateException("could not create item display entity");
        }
        display.snapTo(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, 0.0f, 0.0f);
        CompoundTag nbt = new CompoundTag();
        CompoundTag transformation = new CompoundTag();
        transformation.putFloat("scale", 0.9f);
        nbt.put("transformation", transformation);
        nbt.putString("billboard", "center");
        nbt.putString("item_display", "fixed");
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
        setGraveItem(display);
        if (!level.addFreshEntity(display)) {
            throw new IllegalStateException("could not add item display at " + pos);
        }
        return display;
    }

    public static void setGraveItem(Display.ItemDisplay display) {
        ItemStack model = new ItemStack(Items.STONE);
        model.set(DataComponents.ITEM_MODEL, GRAVE_MODEL);
        display.getSlot(0).set(model);
    }

    public static void discard(ServerLevel level, GraveRecord grave) {
        discard(level, grave.interactionId());
        discard(level, grave.displayId());
    }

    public static void discard(ServerLevel level, java.util.UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = level.getEntity(id);
        if (entity != null) {
            entity.discard();
        }
    }

    public static void scaleDisplay(ServerLevel level, java.util.UUID displayId, float scale) {
        if (displayId == null) {
            return;
        }
        Entity entity = level.getEntity(displayId);
        if (!(entity instanceof Display display)) {
            return;
        }
        CompoundTag nbt = new CompoundTag();
        CompoundTag transformation = new CompoundTag();
        transformation.putFloat("scale", Math.max(0.05f, scale));
        nbt.put("transformation", transformation);
        nbt.putInt("interpolation_duration", 5);
        ItemStack held = ItemStack.EMPTY;
        if (display instanceof Display.ItemDisplay itemDisplay) {
            held = itemDisplay.getSlot(0).get().copy();
        }
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
        if (display instanceof Display.ItemDisplay itemDisplay) {
            if (!held.isEmpty()) {
                itemDisplay.getSlot(0).set(held);
            } else {
                setGraveItem(itemDisplay);
            }
        }
    }

    private static void applySize(Interaction interaction, float width, float height) {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("width", width);
        nbt.putFloat("height", height);
        nbt.putBoolean("response", true);
        interaction.load(TagValueInput.create(
                ProblemReporter.DISCARDING, interaction.level().registryAccess(), nbt));
    }
}
