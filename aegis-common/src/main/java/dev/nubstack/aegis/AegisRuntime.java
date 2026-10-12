package dev.nubstack.aegis;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import dev.nubstack.spine.redis.RedisBus;
import dev.nubstack.spine.redis.RedisSettings;
import dev.nubstack.spine.text.ConfirmChat;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared bootstrap for Paper and Velocity entrypoints.
 */
public final class AegisRuntime implements AutoCloseable {

    private final Path dataDirectory;
    private final HikariDataSource dataSource;
    private final ModerationService moderation;
    private final PunishmentBroadcaster broadcaster;
    private final ConfirmChat confirmChat;
    private final AtomicReference<AegisSettings> settings;
    private final AtomicReference<StaffCooldown> cooldown;

    private AegisRuntime(
            Path dataDirectory,
            HikariDataSource dataSource,
            ModerationService moderation,
            PunishmentBroadcaster broadcaster,
            ConfirmChat confirmChat,
            AegisSettings settings) {
        this.dataDirectory = dataDirectory;
        this.dataSource = dataSource;
        this.moderation = moderation;
        this.broadcaster = broadcaster;
        this.confirmChat = confirmChat;
        this.settings = new AtomicReference<>(settings);
        this.cooldown = new AtomicReference<>(new StaffCooldown(settings.staffCooldown()));
    }

    public static AegisRuntime start(Path dataDirectory, boolean useRedis) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Path configPath = dataDirectory.resolve("config.yml");
        YamlConfig config = YamlConfig.load(configPath);
        AegisSettings settings = loadSettings(config);

        DatabaseSettings databaseSettings = new DatabaseSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                "aegis",
                config.optionalInt("database.pool-size").orElse(5));

        HikariDataSource dataSource = DataSources.create(databaseSettings);
        JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource);
        repository.initSchema();

        PunishmentCache cache = new PunishmentCache();
        WebhookNotifier webhookNotifier = new WebhookNotifier(settings.webhookUrl());

        ModerationService[] holder = new ModerationService[1];
        PunishmentBroadcaster broadcaster;
        if (useRedis) {
            RedisSettings redisSettings = new RedisSettings(
                    config.requireString("redis.host"),
                    config.optionalInt("redis.port").orElse(6379),
                    config.optionalString("redis.password").orElse(null));
            RedisBus bus = RedisBus.open(redisSettings);
            // shortcut: dropped Redis does not auto-rebuild subscriptions here,
            // when to upgrade: health-check recreate of RedisBus + reload cache from SQL.
            broadcaster = new RedisPunishmentBroadcaster(bus, event -> {
                if (holder[0] != null) {
                    holder[0].applyRemote(event);
                }
            });
        } else {
            broadcaster = new LocalPunishmentBroadcaster();
        }

        ModerationService moderation = new ModerationService(
                repository,
                cache,
                broadcaster,
                webhookNotifier);
        holder[0] = moderation;
        moderation.loadCache();

        return new AegisRuntime(
                dataDirectory,
                dataSource,
                moderation,
                broadcaster,
                new ConfirmChat(),
                settings);
    }

    public static AegisSettings loadSettings(YamlConfig config) {
        int cooldownMs = config.optionalInt("staff-cooldown-ms").orElse(1500);
        return new AegisSettings(
                config.optionalString("messages.banned").orElse("<red>You are banned: <reason></red>"),
                config.optionalString("messages.muted").orElse("<red>You are muted: <reason></red>"),
                config.optionalString("discord.webhook-url").orElse(null),
                config.optionalString("appeal.copy-text").orElse("Appeal code: <code>"),
                config.optionalString("rules-command").orElse("/rules"),
                Duration.ofMillis(Math.max(0, cooldownMs)),
                config.optionalBoolean("aliases.gban", true),
                config.optionalBoolean("aliases.gmute", true),
                config.optionalBoolean("aliases.gunban", true),
                config.optionalBoolean("aliases.gwarn", true),
                config.optionalBoolean("aliases.hist", true),
                config.optionalBoolean("aliases.checkpunish", true),
                config.optionalInt("history.page-size").orElse(8));
    }

    /**
     * Reloads messages, webhook, templates/alias toggles, and cooldown. Not JDBC/Redis.
     */
    public void reloadSoft() {
        YamlConfig config = YamlConfig.load(dataDirectory.resolve("config.yml"));
        AegisSettings next = loadSettings(config);
        settings.set(next);
        cooldown.set(new StaffCooldown(next.staffCooldown()));
        moderation.replaceWebhook(new WebhookNotifier(next.webhookUrl()));
    }

    public ModerationService moderation() {
        return moderation;
    }

    public ConfirmChat confirmChat() {
        return confirmChat;
    }

    public AegisSettings settings() {
        return settings.get();
    }

    public StaffCooldown cooldown() {
        return cooldown.get();
    }

    public String bannedMessage() {
        return settings.get().bannedMessage();
    }

    public String mutedMessage() {
        return settings.get().mutedMessage();
    }

    @Override
    public void close() {
        broadcaster.close();
        dataSource.close();
    }
}
