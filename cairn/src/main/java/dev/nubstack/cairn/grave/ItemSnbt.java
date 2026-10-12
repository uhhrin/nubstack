package dev.nubstack.cairn.grave;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/** Encode/decode ItemStacks as SNBT for grave persistence. */
public final class ItemSnbt {

    private ItemSnbt() {
    }

    public static String encode(HolderLookup.Provider registries, ItemStack stack) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return "";
        }
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC
                .encodeStart(ops, stack)
                .result()
                .map(Tag::toString)
                .orElse("");
    }

    public static ItemStack decode(HolderLookup.Provider registries, String snbt) {
        Objects.requireNonNull(registries, "registries");
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            Tag tag = TagParser.parseCompoundFully(snbt);
            DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        } catch (CommandSyntaxException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    public static List<String> encodeAll(HolderLookup.Provider registries, List<ItemStack> stacks) {
        List<String> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                String snbt = encode(registries, stack);
                if (!snbt.isBlank()) {
                    out.add(snbt);
                }
            }
        }
        return List.copyOf(out);
    }

    public static List<ItemStack> decodeAll(HolderLookup.Provider registries, List<String> snbts) {
        List<ItemStack> out = new ArrayList<>();
        for (String snbt : snbts) {
            ItemStack stack = decode(registries, snbt);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }
}
