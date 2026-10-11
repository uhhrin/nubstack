
package dev.nubstack.ledger;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class LedgerPlugin extends JavaPlugin {

    private HikariDataSource dataSource;
    private EconomyService economy;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        dataSource = DataSources.create(new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "ledger",
                config.optionalInt("database.pool-size").orElse(5)));
        economy = new EconomyService(dataSource);
        economy.init();
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServicesManager().register(
                    Economy.class, new VaultEconomyHook(economy), this, ServicePriority.Normal);
            getLogger().info("Registered Vault economy");
        }
        getCommand("bal").setExecutor(this);
        getCommand("pay").setExecutor(this);
        getCommand("ledger").setExecutor(this);
        getLogger().info("ledger enabled");
    }

    @Override
    public void onDisable() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("bal")) {
            OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                sender.sendMessage("Specify a player");
                return true;
            }
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                BigDecimal bal = economy.balance(target.getUniqueId());
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage(target.getName() + " balance: " + bal));
            });
            return true;
        }
        if (name.equals("pay")) {
            if (!(sender instanceof Player from)) {
                sender.sendMessage("Players only");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("/pay <player> <amount>");
                return true;
            }
            OfflinePlayer to = Bukkit.getOfflinePlayer(args[0]);
            BigDecimal amount = new BigDecimal(args[1]);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean ok = economy.transfer(from.getUniqueId(), to.getUniqueId(), amount, "pay");
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage(ok ? "Paid " + amount : "Insufficient funds"));
            });
            return true;
        }
        if (name.equals("ledger") && args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("ledger.admin")) {
                sender.sendMessage("No permission");
                return true;
            }
            OfflinePlayer to = Bukkit.getOfflinePlayer(args[1]);
            BigDecimal amount = new BigDecimal(args[2]);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                economy.deposit(to.getUniqueId(), amount, "admin-give");
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("Deposited " + amount));
            });
            return true;
        }
        sender.sendMessage("/bal | /pay | /ledger give");
        return true;
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
