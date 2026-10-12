package dev.nubstack.aegis;

import java.time.Duration;
import java.util.Objects;

/**
 * Reloadable operator settings (messages, webhook, aliases). JDBC/Redis stay fixed.
 */
public final class AegisSettings {

    private final String bannedMessage;
    private final String mutedMessage;
    private final String webhookUrl;
    private final String appealCopyText;
    private final String rulesCommand;
    private final Duration staffCooldown;
    private final boolean aliasGban;
    private final boolean aliasGmute;
    private final boolean aliasGunban;
    private final boolean aliasGwarn;
    private final boolean aliasHist;
    private final boolean aliasCheckpunish;
    private final int historyPageSize;

    public AegisSettings(
            String bannedMessage,
            String mutedMessage,
            String webhookUrl,
            String appealCopyText,
            String rulesCommand,
            Duration staffCooldown,
            boolean aliasGban,
            boolean aliasGmute,
            boolean aliasGunban,
            boolean aliasGwarn,
            boolean aliasHist,
            boolean aliasCheckpunish,
            int historyPageSize) {
        this.bannedMessage = Objects.requireNonNull(bannedMessage, "bannedMessage");
        this.mutedMessage = Objects.requireNonNull(mutedMessage, "mutedMessage");
        this.webhookUrl = webhookUrl;
        this.appealCopyText = Objects.requireNonNull(appealCopyText, "appealCopyText");
        this.rulesCommand = Objects.requireNonNull(rulesCommand, "rulesCommand");
        this.staffCooldown = Objects.requireNonNull(staffCooldown, "staffCooldown");
        this.aliasGban = aliasGban;
        this.aliasGmute = aliasGmute;
        this.aliasGunban = aliasGunban;
        this.aliasGwarn = aliasGwarn;
        this.aliasHist = aliasHist;
        this.aliasCheckpunish = aliasCheckpunish;
        this.historyPageSize = historyPageSize < 1 ? 8 : historyPageSize;
    }

    public String bannedMessage() {
        return bannedMessage;
    }

    public String mutedMessage() {
        return mutedMessage;
    }

    public String webhookUrl() {
        return webhookUrl;
    }

    public String appealCopyText() {
        return appealCopyText;
    }

    public String rulesCommand() {
        return rulesCommand;
    }

    public Duration staffCooldown() {
        return staffCooldown;
    }

    public boolean aliasGban() {
        return aliasGban;
    }

    public boolean aliasGmute() {
        return aliasGmute;
    }

    public boolean aliasGunban() {
        return aliasGunban;
    }

    public boolean aliasGwarn() {
        return aliasGwarn;
    }

    public boolean aliasHist() {
        return aliasHist;
    }

    public boolean aliasCheckpunish() {
        return aliasCheckpunish;
    }

    public int historyPageSize() {
        return historyPageSize;
    }

    public String appealCode(Punishment punishment) {
        return punishment.id().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public boolean aliasEnabled(String label) {
        return switch (label.toLowerCase()) {
            case "gban" -> aliasGban;
            case "gmute" -> aliasGmute;
            case "gunban" -> aliasGunban;
            case "gwarn" -> aliasGwarn;
            case "hist" -> aliasHist;
            case "checkpunish" -> aliasCheckpunish;
            default -> true;
        };
    }

    public java.util.List<String> enabledAliases() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (aliasGban) {
            out.add("gban");
        }
        if (aliasGmute) {
            out.add("gmute");
        }
        if (aliasGunban) {
            out.add("gunban");
        }
        if (aliasGwarn) {
            out.add("gwarn");
        }
        if (aliasHist) {
            out.add("hist");
        }
        if (aliasCheckpunish) {
            out.add("checkpunish");
        }
        return java.util.List.copyOf(out);
    }
}
