package dev.nubstack.gatekeep;

import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GatekeepTexts {

    static final PrefixedMessages MSG = Messages.prefixed("gatekeep");

    private GatekeepTexts() {
    }

    /** Deny screen text: configured message plus a support code with details on hover and copy on click. */
    public static Component denyMessage(Component configured, ConnectionGuard.Decision decision) {
        Component details = Component.text(
                decision.attempts() + " attempts in " + Durations.humanize(decision.window())
                        + ". You can try again in " + Durations.remaining(decision.retryAfter()) + ".",
                NamedTextColor.GRAY);
        Component code = Component.text(decision.supportCode(), NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(details))
                .clickEvent(ClickEvent.copyToClipboard(decision.supportCode()));
        return Component.text()
                .append(configured)
                .append(Component.newline())
                .append(Component.text("Reference: ", NamedTextColor.GRAY))
                .append(code)
                .build();
    }

    public static Component statusEntry(ConnectionGuard.Entry entry, int maxAttempts) {
        boolean throttled = entry.attempts() >= ConnectionGuard.effectiveLimit(maxAttempts, entry.reputation());
        Component address = Component.text(entry.key(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/gatekeep clear " + entry.key()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to suggest clear", NamedTextColor.GRAY)));
        Component line = Component.text()
                .append(address)
                .append(Component.text(" . " + entry.attempts() + "/" + maxAttempts, NamedTextColor.GRAY))
                .build();
        if (throttled) {
            line = line.append(Component.text(
                    " . throttled, retry " + Durations.remaining(entry.retryAfter()), NamedTextColor.YELLOW));
        }
        return line;
    }
}
