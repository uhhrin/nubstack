package dev.nubstack.spine.text;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Fluent Adventure message builder with a standard plugin prefix.
 */
public final class PrefixedMessages {

    private final String name;

    PrefixedMessages(String name) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public Component info(String text) {
        return line(NamedTextColor.GRAY, text);
    }

    public Component success(String text) {
        return line(NamedTextColor.GREEN, text);
    }

    public Component warn(String text) {
        return line(NamedTextColor.YELLOW, text);
    }

    public Component error(String text) {
        return line(NamedTextColor.RED, text);
    }

    public Component copyButton(String label, String payload) {
        return bracketButton(label)
                .clickEvent(ClickEvent.copyToClipboard(Objects.requireNonNull(payload, "payload")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY)));
    }

    public Component suggestButton(String label, String command) {
        return bracketButton(label)
                .clickEvent(ClickEvent.suggestCommand(Objects.requireNonNull(command, "command")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to suggest", NamedTextColor.GRAY)));
    }

    public Component runButton(String label, String command) {
        return bracketButton(label)
                .clickEvent(ClickEvent.runCommand(Objects.requireNonNull(command, "command")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to run", NamedTextColor.GRAY)));
    }

    public Component hoverDetail(String main, String detail) {
        return Component.text(Objects.requireNonNull(main, "main"), NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text(Objects.requireNonNull(detail, "detail"), NamedTextColor.GRAY)));
    }

    public Component hoverDetail(String main, Component detail) {
        return Component.text(Objects.requireNonNull(main, "main"), NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Objects.requireNonNull(detail, "detail")));
    }

    public Component insert(String visible, String insertion) {
        return Component.text(Objects.requireNonNull(visible, "visible"), NamedTextColor.AQUA)
                .insertion(Objects.requireNonNull(insertion, "insertion"))
                .hoverEvent(HoverEvent.showText(Component.text("Shift-click to insert", NamedTextColor.GRAY)));
    }

    private Component line(NamedTextColor color, String text) {
        return prefix().append(Component.space()).append(Component.text(Objects.requireNonNull(text, "text"), color));
    }

    private Component prefix() {
        return Component.text()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text(name, NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.GRAY))
                .build();
    }

    private static Component bracketButton(String label) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(Objects.requireNonNull(label, "label"), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .build();
    }
}
