package dev.nubstack.tether;

import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Adventure builders for the click-to-copy link card.
 */
public final class LinkCard {

    public enum ClipboardPayload {
        CODE,
        FULL;

        public static ClipboardPayload parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return FULL;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "code" -> CODE;
                case "full" -> FULL;
                default -> throw new IllegalArgumentException(
                        "link.clipboard-payload must be 'code' or 'full', got: " + raw);
            };
        }
    }

    private LinkCard() {
    }

    public static String clipboardValue(String code, ClipboardPayload payload) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(payload, "payload");
        return payload == ClipboardPayload.CODE ? code : "!link " + code;
    }

    public static Component codeLabel(String code, String clipboardPayload) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(clipboardPayload, "clipboardPayload");
        return Component.text(code, NamedTextColor.GOLD, TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(clipboardPayload))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY)));
    }

    public static Component card(String code, ClipboardPayload payload, long ttlSeconds) {
        return card(code, payload, ttlSeconds, true);
    }

    public static Component card(String code, ClipboardPayload payload, long ttlSeconds, boolean slashCommands) {
        PrefixedMessages msg = Messages.prefixed("tether");
        String clipboard = clipboardValue(code, payload);

        return Component.text()
                .append(msg.info("Your link code expires in " + clock(ttlSeconds)))
                .append(Component.newline())
                .append(Component.text("         "))
                .append(codeLabel(code, clipboard))
                .append(Component.newline())
                .append(Component.text("         "))
                .append(msg.copyButton("Copy code", clipboard))
                .append(Component.space())
                .append(msg.runButton("Cancel code", "/tether cancel"))
                .append(Component.newline())
                .append(msg.info(instruction(code, slashCommands)))
                .build();
    }

    public static Component hardGateKick(String code, long ttlSeconds, boolean slashCommands) {
        return Component.text()
                .append(Component.text("Link your Discord to play.", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Code: ", NamedTextColor.GRAY))
                .append(Component.text(code, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (expires in " + clock(ttlSeconds) + ")", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(instruction(code, slashCommands), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Then rejoin.", NamedTextColor.GRAY))
                .build();
    }

    public static Component linkedSuccess(String discordTag) {
        return Messages.prefixed("tether").success("Linked as @" + tagOrDefault(discordTag));
    }

    public static Title successTitle(String discordTag) {
        return Title.title(
                Component.text("Linked", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("@" + tagOrDefault(discordTag), NamedTextColor.GRAY));
    }

    public static Sound successSound() {
        return Sound.sound(Key.key("minecraft", "entity.player.levelup"), Sound.Source.PLAYER, 1f, 1f);
    }

    private static String instruction(String code, boolean slashCommands) {
        return slashCommands
                ? "In Discord, send !link " + code + " (or use /link and paste the code)"
                : "In Discord, send: !link " + code;
    }

    private static String clock(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private static String tagOrDefault(String discordTag) {
        return discordTag == null || discordTag.isBlank() ? "Discord" : discordTag;
    }
}
