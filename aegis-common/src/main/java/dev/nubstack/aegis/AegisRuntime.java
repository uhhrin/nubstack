package dev.nubstack.aegis;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.config.YamlConfig;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import dev.nubstack.spine.redis.RedisBus;
import dev.nubstack.spine.redis.RedisSettings;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Shared bootstrap for Paper and Velocity entrypoints.
 */
public final class AegisRuntime implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final ModerationService moderation;
    private final PunishmentBroadcaster broadcaster;
    private final String bannedMessage;
    private final String mutedMessage;

    private AegisRuntime(
            HikariDataSource dataSource,
            ModerationService moderation,
            PunishmentBroadcaster broadcaster,
            String bannedMessage,
            String mutedMessage) {
        this.dataSource = dataSource;
        this.moderation = moderation;
        this.broadcaster = broadcaster;
        this.bannedMessage = bannedMessage;
        this.mutedMessage = mutedMessage;
    }

    public static AegisRuntime start(Path dataDirectory, boolean useRedis) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Path configPath = dataDirectory.resolve("config.yml");
        YamlConfig config = YamlConfig.load(configPath);

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
        WebhookNotifier webhookNotifier = new WebhookNotifier(
                config.optionalString("discord.webhook-url").orElse(null));

        ModerationService[] holder = new ModerationService[1];
        PunishmentBroadcaster broadcaster;
        if (useRedis) {
            RedisSettings redisSettings = new RedisSettings(
                    config.requireString("redis.host"),
                    config.optionalInt("redis.port").orElse(6379),
                    config.optionalString("redis.password").orElse(null));
            RedisBus bus = RedisBus.open(redisSettings);
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

        String bannedMessage = config.optionalString("messages.banned")
                .orElse("<red>You are banned: <reason></red>");
        String mutedMessage = config.optionalString("messages.muted")
                .orElse("<red>You are muted: <reason></red>");

        return new AegisRuntime(dataSource, moderation, broadcaster, bannedMessage, mutedMessage);
    }

    public ModerationService moderation() {
        return moderation;
    }

    public String bannedMessage() {
        return bannedMessage;
    }

    public String mutedMessage() {
        return mutedMessage;
    }

    @Override
    public void close() {
        broadcaster.close();
        dataSource.close();
    }
}
