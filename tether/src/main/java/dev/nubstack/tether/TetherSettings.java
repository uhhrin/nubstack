package dev.nubstack.tether;

import dev.nubstack.spine.config.ConfigException;
import dev.nubstack.spine.config.YamlConfig;
import java.util.Locale;
import java.util.Objects;

/**
 * Parsed tether config. Blank Discord token is valid (Discord features stay off).
 */
public final class TetherSettings {

    public enum GateMode {
        OFF,
        SOFT,
        HARD;

        public static GateMode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return OFF;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "off" -> OFF;
                case "soft" -> SOFT;
                case "hard" -> HARD;
                default -> throw new ConfigException("gate.mode must be off|soft|hard, got: " + raw);
            };
        }
    }

    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int poolSize;
    private final String discordToken;
    private final String channelId;
    private final String guildId;
    private final int codeLength;
    private final int ttlSeconds;
    private final int maxRedeemAttempts;
    private final LinkCard.ClipboardPayload clipboardPayload;
    private final GateMode gateMode;
    private final boolean preferSlashCommands;
    private final int outboundQueueCapacity;

    private TetherSettings(
            String jdbcUrl,
            String dbUser,
            String dbPassword,
            int poolSize,
            String discordToken,
            String channelId,
            String guildId,
            int codeLength,
            int ttlSeconds,
            int maxRedeemAttempts,
            LinkCard.ClipboardPayload clipboardPayload,
            GateMode gateMode,
            boolean preferSlashCommands,
            int outboundQueueCapacity) {
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.poolSize = poolSize;
        this.discordToken = discordToken;
        this.channelId = channelId;
        this.guildId = guildId;
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.maxRedeemAttempts = maxRedeemAttempts;
        this.clipboardPayload = clipboardPayload;
        this.gateMode = gateMode;
        this.preferSlashCommands = preferSlashCommands;
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    public static TetherSettings from(YamlConfig config) {
        Objects.requireNonNull(config, "config");
        GateMode gate = GateMode.parse(gateModeText(config));
        if (gate == GateMode.OFF && config.optionalBoolean("require-link-to-join", false)) {
            // shortcut: legacy key maps to hard until operators migrate, remove after configs drop require-link-to-join
            gate = GateMode.HARD;
        }
        int codeLength = config.optionalInt("link.code-length").orElse(8);
        if (codeLength < 4 || codeLength > 16) {
            throw new ConfigException("link.code-length must be 4..16");
        }
        int ttlSeconds = config.optionalInt("link.ttl-seconds").orElse(300);
        if (ttlSeconds < 30 || ttlSeconds > 3600) {
            throw new ConfigException("link.ttl-seconds must be 30..3600");
        }
        int maxAttempts = config.optionalInt("link.max-redeem-attempts").orElse(5);
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new ConfigException("link.max-redeem-attempts must be 1..20");
        }
        int queueCap = config.optionalInt("discord.outbound-queue-capacity").orElse(64);
        if (queueCap < 1 || queueCap > 1000) {
            throw new ConfigException("discord.outbound-queue-capacity must be 1..1000");
        }
        return new TetherSettings(
                config.requireString("database.jdbc-url"),
                config.requireString("database.username"),
                config.optionalString("database.password").orElse(""),
                config.optionalInt("database.pool-size").orElse(5),
                config.optionalString("discord.token").orElse(""),
                config.optionalString("discord.channel-id").orElse(""),
                config.optionalString("discord.guild-id").orElse(""),
                codeLength,
                ttlSeconds,
                maxAttempts,
                LinkCard.ClipboardPayload.parse(config.optionalString("link.clipboard-payload").orElse("full")),
                gate,
                config.optionalBoolean("discord.prefer-slash-commands", true),
                queueCap);
    }

    /** SnakeYAML reads an unquoted {@code off} as boolean false, so map it back instead of failing. */
    private static String gateModeText(YamlConfig config) {
        try {
            return config.optionalString("gate.mode").orElse(null);
        } catch (ConfigException notAString) {
            if (!config.optionalBoolean("gate.mode", true)) {
                return "off";
            }
            throw new ConfigException("gate.mode must be off|soft|hard");
        }
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public int poolSize() {
        return poolSize;
    }

    public String discordToken() {
        return discordToken;
    }

    public boolean discordEnabled() {
        return !discordToken.isBlank();
    }

    public boolean hasDiscordToken() {
        return discordEnabled();
    }

    public String channelId() {
        return channelId;
    }

    public String guildId() {
        return guildId;
    }

    public int codeLength() {
        return codeLength;
    }

    public int ttlSeconds() {
        return ttlSeconds;
    }

    public int maxRedeemAttempts() {
        return maxRedeemAttempts;
    }

    public LinkCard.ClipboardPayload clipboardPayload() {
        return clipboardPayload;
    }

    public GateMode gateMode() {
        return gateMode;
    }

    public boolean preferSlashCommands() {
        return preferSlashCommands;
    }

    public int outboundQueueCapacity() {
        return outboundQueueCapacity;
    }
}
