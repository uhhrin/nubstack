package dev.nubstack.loom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Read-only chest preview of a stored player inventory. Every click and drag is cancelled. */
final class InspectMenu implements InventoryHolder, Listener {

    private Inventory inventory;

    static void open(
            Player viewer,
            String targetName,
            PlayerDataStore.Snapshot snapshot,
            Optional<String> savedOnServer,
            ItemStack[] contents) {
        InspectMenu holder = new InspectMenu();
        Inventory chest = Bukkit.createInventory(
                holder, InspectLayout.SIZE, Component.text("Inventory of " + targetName + " (read-only)"));
        holder.inventory = chest;
        for (int slot = 0; slot < InspectLayout.SIZE; slot++) {
            int source = InspectLayout.sourceIndex(slot);
            if (source >= 0 && source < contents.length) {
                chest.setItem(slot, contents[source]);
            }
        }
        chest.setItem(InspectLayout.INFO_SLOT, infoItem(snapshot, savedOnServer));
        viewer.openInventory(chest);
    }

    private static ItemStack infoItem(PlayerDataStore.Snapshot snapshot, Optional<String> savedOnServer) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Stored snapshot", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        Instant savedAt = snapshot.updatedAt();
        meta.lore(List.of(
                line("Saved at: " + (Instant.EPOCH.equals(savedAt) ? "never" : savedAt.toString())),
                line("Server: " + savedOnServer.orElse("unknown")),
                line("Bytes: " + snapshot.blobBytes()),
                line("Version: " + snapshot.version())));
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InspectMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InspectMenu) {
            event.setCancelled(true);
        }
    }
}
