package dev.nubstack.aegis.paper;

import dev.nubstack.aegis.AegisRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisPaperPlugin extends JavaPlugin {

    private AegisRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        runtime = AegisRuntime.start(getDataFolder().toPath(), true);
        AegisCommand command = new AegisCommand(this, runtime);
        org.bukkit.command.PluginCommand pluginCommand = getCommand("aegis");
        if (pluginCommand == null) {
            getLogger().severe("Command 'aegis' missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new AegisPaperListener(this, runtime), this);
        getLogger().info("aegis enabled");
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    private void saveDefaultConfigFile() {
        Path folder = getDataFolder().toPath();
        try {
            Files.createDirectories(folder);
            Path config = folder.resolve("config.yml");
            if (!Files.exists(config)) {
                try (InputStream input = getResource("config.yml")) {
                    if (input == null) {
                        throw new IllegalStateException("Bundled config.yml missing");
                    }
                    Files.copy(input, config);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create aegis config", exception);
        }
    }
}
