package dev.nubstack.amber;

import dev.nubstack.spine.config.ConfigFiles;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class AmberPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        ConfigFiles.copyDefaultIfMissing(getDataFolder().toPath(), "config.yml", () -> getResource("config.yml"));
        AmberCommand command = new AmberCommand(this);
        try {
            command.reload();
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var amber = Objects.requireNonNull(getCommand("amber"), "amber command missing from plugin.yml");
        amber.setExecutor(command);
        amber.setTabCompleter(command);
        getLogger().info("amber enabled");
    }
}
