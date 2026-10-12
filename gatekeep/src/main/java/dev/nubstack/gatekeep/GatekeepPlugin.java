package dev.nubstack.gatekeep;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.nubstack.spine.config.ConfigFiles;
import dev.nubstack.spine.text.ConfirmChat;
import java.nio.file.Path;
import java.time.Clock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

@Plugin(id = "gatekeep", name = "gatekeep", version = "0.1.0-SNAPSHOT", authors = {"nubstack"})
public final class GatekeepPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfirmChat confirm = new ConfirmChat();
    private ConnectionGuard guard;
    private volatile Component denyMessage;

    @Inject
    public GatekeepPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        ConfigFiles.copyDefaultIfMissing(
                dataDirectory,
                "config.yml",
                () -> getClass().getClassLoader().getResourceAsStream("config.yml"));
        GatekeepConfig config = GatekeepConfig.load(dataDirectory.resolve("config.yml"));
        guard = new ConnectionGuard(config.settings(), Clock.systemUTC());
        denyMessage = MiniMessage.miniMessage().deserialize(config.denyMessage());
        proxy.getEventManager().register(this, new GatekeepListener(this));
        var meta = proxy.getCommandManager().metaBuilder("gatekeep").plugin(this).build();
        proxy.getCommandManager().register(meta, new GatekeepCommand(this));
        logger.info("gatekeep enabled");
    }

    /** Re-reads config.yml. Throws if the file is invalid; the previous settings stay active. */
    void reload() {
        GatekeepConfig config = GatekeepConfig.load(dataDirectory.resolve("config.yml"));
        guard.configure(config.settings());
        denyMessage = MiniMessage.miniMessage().deserialize(config.denyMessage());
    }

    ConnectionGuard guard() {
        return guard;
    }

    Component denyMessage() {
        return denyMessage;
    }

    ConfirmChat confirm() {
        return confirm;
    }

    Logger logger() {
        return logger;
    }
}
