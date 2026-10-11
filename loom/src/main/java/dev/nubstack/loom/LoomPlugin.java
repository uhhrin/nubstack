
package dev.nubstack.loom;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class LoomPlugin extends JavaPlugin implements Listener {

    private HikariDataSource dataSource;
    private PlayerDataStore store;
    private TransferLock locks;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        dataSource = DataSources.create(new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "loom",
                config.optionalInt("database.pool-size").orElse(5)));
        store = new PlayerDataStore(dataSource);
        store.init();
        locks = new TransferLock(Duration.ofSeconds(config.optionalInt("lock-seconds").orElse(30)));
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("loom").setExecutor(this);
        getLogger().info("loom enabled");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player);
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (!locks.tryLock(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(this, () ->
                        player.kick(net.kyori.adventure.text.Component.text("Data sync in progress")));
                return;
            }
            try {
                store.load(player.getUniqueId()).ifPresent(blob ->
                        Bukkit.getScheduler().runTask(this, () -> applyInventory(player, blob)));
            } finally {
                locks.unlock(player.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String blob = serializeInventory(player);
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> store.save(id, blob));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("loom sync is active");
        return true;
    }

    private void savePlayer(Player player) {
        store.save(player.getUniqueId(), serializeInventory(player));
    }

    private static String serializeInventory(Player player) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            ItemStack[] contents = player.getInventory().getContents();
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void applyInventory(Player player, String blob) {
        try {
            byte[] data = Base64.getDecoder().decode(blob);
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data));
            int size = in.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            in.close();
            player.getInventory().setContents(contents);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void saveDefaultConfigFile() {
        try {
            Path folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            Path config = folder.resolve("config.yml");
            if (!Files.exists(config)) {
                try (InputStream in = getResource("config.yml")) {
                    Files.copy(in, config);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
