package dev.nubstack.spine.ui;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Action bar text helpers. Send the component with {@code audience.sendActionBar(...)}.
 */
public final class ActionBars {

    private ActionBars() {
    }

    public static Component text(String message) {
        return Component.text(Objects.requireNonNull(message, "message"), NamedTextColor.GRAY);
    }

    public static Component countdown(String label, long remainingSeconds) {
        Objects.requireNonNull(label, "label");
        long safe = Math.max(0L, remainingSeconds);
        long minutes = safe / 60L;
        long seconds = safe % 60L;
        String clock = String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        return Component.text(label + " . " + clock + " left", NamedTextColor.GOLD);
    }
}
