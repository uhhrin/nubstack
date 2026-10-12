package dev.nubstack.cairn.grave;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Transferable grave key. Uses trial_key so it cannot be placed as a block (tripwire hooks can).
 */
public final class GraveKeyItem {

    public static final String NBT_GRAVE_ID = "cairn_grave_id";
    private static final Identifier KEY_MODEL = Identifier.fromNamespaceAndPath("cairn", "grave_key");

    private GraveKeyItem() {
    }

    public static ItemStack create(String graveId, String ownerName) {
        Objects.requireNonNull(graveId, "graveId");
        Objects.requireNonNull(ownerName, "ownerName");
        ItemStack stack = new ItemStack(Items.TRIAL_KEY);
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_GRAVE_ID, graveId);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Grave Key #" + graveId)
                .withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Opens " + ownerName + "'s grave").withStyle(ChatFormatting.GRAY),
                Component.literal("Transferable · not placeable").withStyle(ChatFormatting.DARK_GRAY))));
        stack.set(DataComponents.ITEM_MODEL, KEY_MODEL);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static Optional<String> graveIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(NBT_GRAVE_ID)) {
            return Optional.empty();
        }
        String id = tag.getStringOr(NBT_GRAVE_ID, "");
        return id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    public static boolean matches(ItemStack stack, String graveId) {
        Objects.requireNonNull(graveId, "graveId");
        return graveIdOf(stack).filter(graveId::equals).isPresent();
    }

    public static boolean isGraveKey(ItemStack stack) {
        return graveIdOf(stack).isPresent();
    }

    /** Pure helper for tests: whether a grave id string is a non-blank key payload. */
    public static boolean isValidGraveId(String graveId) {
        return graveId != null && !graveId.isBlank();
    }
}
