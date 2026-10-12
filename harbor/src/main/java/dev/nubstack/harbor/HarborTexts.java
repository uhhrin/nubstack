package dev.nubstack.harbor;

import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import dev.nubstack.spine.ui.BossBars;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class HarborTexts {

    private static final PrefixedMessages MSG = Messages.prefixed("harbor");
    private static final DateTimeFormatter CHECK_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private HarborTexts() {
    }

    public static Component statusLine(
            String server,
            String healthLabel,
            int queueSize,
            int position,
            int priority,
            Instant lastCheck,
            boolean healthy) {
        Component serverName = Component.text(server, NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Last check: " + (lastCheck == null ? "never" : CHECK_TIME.format(lastCheck)),
                        NamedTextColor.GRAY)));
        if (healthy) {
            serverName = serverName.clickEvent(ClickEvent.suggestCommand("/server " + server));
        }

        String you = position > 0
                ? "you #" + position + " (priority " + priority + ")"
                : "you not queued";

        return Component.text()
                .append(serverName)
                .append(Component.text(" . ", NamedTextColor.DARK_GRAY))
                .append(Component.text(healthLabel, NamedTextColor.GRAY))
                .append(Component.text(" . ", NamedTextColor.DARK_GRAY))
                .append(Component.text("queue " + queueSize, NamedTextColor.GRAY))
                .append(Component.text(" . ", NamedTextColor.DARK_GRAY))
                .append(Component.text(you, NamedTextColor.WHITE))
                .append(Component.space())
                .append(MSG.runButton("Leave", "/harbor leave"))
                .append(Component.space())
                .append(MSG.runButton("Refresh", "/harbor status " + server))
                .build();
    }

    public static Component joinNowButton(String server) {
        return MSG.runButton("Join now", "/harbor join " + server);
    }

    public static String bossBarLabel(String server, int position, Long etaSeconds) {
        return BossBars.formatEta("Queue " + server, position, etaSeconds);
    }

    public static Component queueFull(String server) {
        return MSG.warn("Queue for " + server + " is full. Try another server or wait.");
    }

    public static Component unknownServer(String server) {
        return MSG.error("Unknown server '" + server + "'.");
    }
}
