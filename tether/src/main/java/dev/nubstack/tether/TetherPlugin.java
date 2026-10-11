package dev.nubstack.tether;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TetherPlugin extends JavaPlugin implements Listener {

    private HikariDataSource dataSource;
    private LinkService links;
    private JDA jda;
    private String channelId = "";
    private boolean requireLink;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        YamlConfig config = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"));
        dataSource = DataSources.create(new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "tether",
                config.optionalInt("database.pool-size").orElse(5)));
        links = new LinkService(dataSource);
        links.init();
        requireLink = config.optionalBoolean("require-link-to-join", false);
        channelId = config.optionalString("discord.channel-id").orElse("");
        String token = config.optionalString("discord.token").orElse("");
        if (!token.isBlank()) {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onMessageReceived(MessageReceivedEvent event) {
                            if (event.getAuthor().isBot()) {
                                return;
                            }
                            if (!event.getChannel().getId().equals(channelId)) {
                                return;
                            }
                            String content = event.getMessage().getContentDisplay();
                            if (content.startsWith("!link ")) {
                                String code = content.substring(6).trim();
                                links.consumeCode(code).ifPresent(playerId -> {
                                    links.link(playerId, event.getAuthor().getId());
                                    event.getChannel().sendMessage("Linked to Minecraft account.").queue();
                                });
                                return;
                            }
                            Bukkit.getScheduler().runTask(TetherPlugin.this, () ->
                                    Bukkit.broadcast(Component.text(
                                            "[Discord] " + event.getAuthor().getName() + ": " + content)));
                        }
                    })
                    .build();
        } else {
            getLogger().warning("Discord token empty; linking/chat bridge disabled");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("tether").setExecutor(this);
        getLogger().info("tether enabled");
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!requireLink) {
            return;
        }
        if (!links.isLinked(event.getUniqueId())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Link Discord with /tether link"));
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (jda == null || channelId.isBlank()) {
            return;
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        String player = event.getPlayer().getName();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        channel.sendMessage("**" + player + "**: " + plain).queue();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("link")) {
            sender.sendMessage("/tether link");
            return true;
        }
        String code = links.createCode(player.getUniqueId());
        sender.sendMessage("In Discord, send: !link " + code);
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
