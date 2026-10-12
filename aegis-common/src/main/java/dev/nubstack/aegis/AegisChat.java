package dev.nubstack.aegis;

import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Interactive chat components for staff and target feedback.
 */
public final class AegisChat {

    private static final PrefixedMessages MSG = Messages.prefixed("aegis");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private AegisChat() {
    }

    public static Component help(Predicate<String> hasPermission, int page) {
        return HelpBuilder.root("aegis")
                .entry("/aegis ban <player> [dur] <reason>", "Ban a player", "aegis.ban")
                .entry("/aegis mute <player> [dur] <reason>", "Mute a player", "aegis.mute")
                .entry("/aegis warn <player> <reason>", "Warn a player", "aegis.warn")
                .entry("/aegis unban <player>", "Revoke ban", "aegis.unban")
                .entry("/aegis unmute <player>", "Revoke mute", "aegis.unmute")
                .entry("/aegis history <player> [page]", "Punishment history", "aegis.history")
                .entry("/aegis check <player>", "Active punishments", "aegis.history")
                .entry("/aegis reason <id>", "Show audit row", "aegis.history")
                .entry("/aegis rollback <id>", "Revoke by audit id", "aegis.unban")
                .entry("/aegis lookup <uuid|name>", "Resolve name/uuid", "aegis.history")
                .entry("/aegis reload", "Reload messages/webhook", "aegis.reload")
                .build(hasPermission, page, 8);
    }

    public static Component historyLine(Punishment row) {
        Objects.requireNonNull(row, "row");
        String expires = row.expiresAt() == null ? "permanent" : TIME.format(row.expiresAt());
        Component hover = Component.text()
                .append(Component.text("Reason: " + row.reason(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Staff: " + row.staffName(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Created: " + TIME.format(row.createdAt()), NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("Expires: " + expires, NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("ID: " + row.id(), NamedTextColor.DARK_GRAY))
                .build();

        Component name = Component.text(row.targetName(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/aegis history " + row.targetName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to suggest history", NamedTextColor.GRAY)));

        String status = row.active() ? "active" : "inactive";
        return Component.text()
                .append(Component.text(row.type().name(), NamedTextColor.YELLOW).hoverEvent(HoverEvent.showText(hover)))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(name)
                .append(Component.text(" by " + row.staffName() + " [" + status + "]", NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(hover)))
                .build();
    }

    public static Component punishFeedback(Punishment punishment) {
        Objects.requireNonNull(punishment, "punishment");
        String id = punishment.id().toString();
        String unbanCmd = punishment.type() == PunishmentType.MUTE
                ? "/aegis unmute " + punishment.targetName()
                : "/aegis unban " + punishment.targetName();
        if (punishment.type() == PunishmentType.WARN) {
            unbanCmd = "/aegis history " + punishment.targetName();
        }
        Component line = Component.text()
                .append(MSG.success(punishment.type().name() + " applied to " + punishment.targetName()))
                .append(Component.space())
                .append(punishment.type() == PunishmentType.WARN
                        ? MSG.suggestButton("History", "/aegis history " + punishment.targetName())
                        : MSG.runButton(punishment.type() == PunishmentType.MUTE ? "Unmute" : "Unban", unbanCmd))
                .append(Component.space())
                .append(MSG.suggestButton("History", "/aegis history " + punishment.targetName()))
                .append(Component.space())
                .append(MSG.copyButton("Copy ID", id))
                .build();
        return line;
    }

    public static Component muteNotify(Punishment mute, String rulesCommand) {
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(rulesCommand, "rulesCommand");
        String remainingLabel = remainingLabel(mute);
        Component body = Component.text("You are muted: " + mute.reason(), NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Ends in " + remainingLabel, NamedTextColor.GRAY)));
        return Component.text()
                .append(body)
                .append(Component.space())
                .append(MSG.runButton("Rules", rulesCommand.startsWith("/") ? rulesCommand : "/" + rulesCommand))
                .build();
    }

    public static Component banScreen(Punishment ban, AegisSettings settings) {
        Objects.requireNonNull(ban, "ban");
        Objects.requireNonNull(settings, "settings");
        String code = settings.appealCode(ban);
        String template = settings.bannedMessage()
                .replace("<reason>", ban.reason())
                .replace("<appeal>", code)
                .replace("<code>", code);
        Component base = MINI.deserialize(template);
        String copyPayload = settings.appealCopyText()
                .replace("<code>", code)
                .replace("<appeal>", code);
        return Component.text()
                .append(base)
                .append(Component.newline())
                .append(MSG.copyButton("Copy appeal", copyPayload))
                .build();
    }

    public static Component reasonDetail(Punishment row) {
        Objects.requireNonNull(row, "row");
        return Component.text()
                .append(MSG.info(row.type() + " " + row.targetName()))
                .append(Component.newline())
                .append(Component.text("Reason: " + row.reason(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Staff: " + row.staffName(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Created: " + TIME.format(row.createdAt()), NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("Active: " + row.active(), NamedTextColor.DARK_GRAY))
                .append(Component.space())
                .append(MSG.copyButton("Copy ID", row.id().toString()))
                .build();
    }

    public static Component lookupResult(String name, java.util.UUID id) {
        return Component.text()
                .append(MSG.info(name + " = " + id))
                .append(Component.space())
                .append(MSG.copyButton("Copy UUID", id.toString()))
                .append(Component.space())
                .append(MSG.copyButton("Copy name", name))
                .build();
    }

    public static Component checkSummary(String playerName, List<Punishment> active) {
        if (active.isEmpty()) {
            return MSG.warn("No active punishments for " + playerName);
        }
        Component out = MSG.info("Active for " + playerName + ":");
        for (Punishment row : active) {
            out = out.append(Component.newline()).append(historyLine(row));
        }
        return out;
    }

    public static String remainingLabel(Punishment punishment) {
        if (punishment.expiresAt() == null) {
            return "never";
        }
        Duration left = Duration.between(Instant.now(), punishment.expiresAt());
        if (left.isNegative() || left.isZero()) {
            return "0s";
        }
        return Durations.remaining(left);
    }

    public static String typeIconName(PunishmentType type) {
        return switch (type) {
            case BAN -> "BARRIER";
            case MUTE -> "PAPER";
            case WARN -> "BOOK";
        };
    }
}
