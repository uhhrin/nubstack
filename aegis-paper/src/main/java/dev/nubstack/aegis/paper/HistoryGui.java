package dev.nubstack.aegis.paper;

import dev.nubstack.aegis.AegisChat;
import dev.nubstack.aegis.Punishment;
import dev.nubstack.aegis.PunishmentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper history chest. shortcut: local holder instead of spine Menu (not landed),
 * when to upgrade: move to spine-paper Menu.builder.
 */
public final class HistoryGui implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public HistoryGui(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, String targetName, List<Punishment> all, boolean activeOnly, int page) {
        List<Punishment> filtered = new ArrayList<>();
        for (Punishment row : all) {
            if (!activeOnly || row.active()) {
                filtered.add(row);
            }
        }
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = (safePage - 1) * pageSize;
        int to = Math.min(from + pageSize, filtered.size());
        List<Punishment> slice = filtered.subList(from, to);

        Holder holder = new Holder(targetName, activeOnly, safePage, totalPages, slice);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(
                "History: " + targetName + " (" + safePage + "/" + totalPages + ")", NamedTextColor.DARK_RED));
        holder.inventory = inventory;

        for (int i = 0; i < slice.size(); i++) {
            inventory.setItem(i, icon(slice.get(i)));
        }
        inventory.setItem(45, button(Material.ARROW, "Prev page", NamedTextColor.YELLOW));
        inventory.setItem(49, button(
                activeOnly ? Material.LIME_DYE : Material.GRAY_DYE,
                activeOnly ? "Active only: ON" : "Active only: OFF",
                NamedTextColor.GREEN));
        inventory.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));

        sessions.put(viewer.getUniqueId(), new Session(targetName, all, activeOnly, safePage));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < holder.pageRows.size()) {
            Punishment row = holder.pageRows.get(slot);
            player.closeInventory();
            player.sendMessage(AegisChat.reasonDetail(row));
            return;
        }
        if (slot == 45) {
            open(player, session.targetName(), session.all(), session.activeOnly(), session.page() - 1);
        } else if (slot == 53) {
            open(player, session.targetName(), session.all(), session.activeOnly(), session.page() + 1);
        } else if (slot == 49) {
            open(player, session.targetName(), session.all(), !session.activeOnly(), 1);
        }
    }

    private static ItemStack icon(Punishment row) {
        Material material = switch (row.type()) {
            case BAN -> Material.BARRIER;
            case MUTE -> Material.PAPER;
            case WARN -> Material.BOOK;
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(row.type().name(), NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Reason: " + row.reason(), NamedTextColor.GRAY),
                Component.text("Staff: " + row.staffName(), NamedTextColor.GRAY),
                Component.text("Created: " + row.createdAt(), NamedTextColor.DARK_GRAY),
                Component.text(row.active() ? "Active" : "Inactive", NamedTextColor.DARK_GRAY),
                Component.text("ID: " + row.id(), NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack button(Material material, String label, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, color));
        stack.setItemMeta(meta);
        return stack;
    }

    private record Session(String targetName, List<Punishment> all, boolean activeOnly, int page) {
    }

    private static final class Holder implements InventoryHolder {
        private final String targetName;
        private final boolean activeOnly;
        private final int page;
        private final int totalPages;
        private final List<Punishment> pageRows;
        private Inventory inventory;

        private Holder(String targetName, boolean activeOnly, int page, int totalPages, List<Punishment> pageRows) {
            this.targetName = targetName;
            this.activeOnly = activeOnly;
            this.page = page;
            this.totalPages = totalPages;
            this.pageRows = pageRows;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
