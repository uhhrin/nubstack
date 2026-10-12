package dev.nubstack.loom;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

/** Paper item serialization behind the versioned {@link InventoryCodec} framing. */
final class InventorySerializer {

    private InventorySerializer() {
    }

    static String serialize(ItemStack[] contents) {
        return InventoryCodec.encodeV1Base64(ItemStack.serializeItemsAsBytes(contents));
    }

    /**
     * @throws IllegalArgumentException when the blob is blank, not base64, or not readable
     */
    static ItemStack[] deserialize(String blob) {
        InventoryCodec.Decoded decoded = InventoryCodec.decodeBase64(blob);
        try {
            return switch (decoded.format()) {
                case V1 -> ItemStack.deserializeItemsFromBytes(decoded.payload());
                case LEGACY -> readLegacy(decoded.payload());
            };
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            throw new IllegalArgumentException("Unreadable inventory blob: " + e.getMessage(), e);
        }
    }

    // shortcut: reads the pre-versioned BukkitObjectOutputStream format, drop once no stored rows use it
    private static ItemStack[] readLegacy(byte[] payload) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(payload))) {
            int size = in.readInt();
            if (size < 0 || size > 256) {
                throw new IOException("Implausible legacy inventory size " + size);
            }
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        }
    }
}
