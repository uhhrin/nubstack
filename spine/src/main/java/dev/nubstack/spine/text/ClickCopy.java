package dev.nubstack.spine.text;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Standalone copy-to-clipboard button.
 */
public final class ClickCopy {

    private ClickCopy() {
    }

    public static Component of(String label, String payload) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(payload, "payload");
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.copyToClipboard(payload))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY)))
                .build();
    }
}
