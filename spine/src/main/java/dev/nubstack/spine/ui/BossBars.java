package dev.nubstack.spine.ui;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Platform-neutral boss bar progress helpers. Pass the bar to an Adventure Audience.
 */
public final class BossBars {

    private BossBars() {
    }

    public static BossBar progress(String label, float progress) {
        Objects.requireNonNull(label, "label");
        float clamped = Math.max(0f, Math.min(1f, progress));
        int percent = Math.round(clamped * 100f);
        Component name = Component.text(label + "... " + percent + "%", NamedTextColor.WHITE);
        return BossBar.bossBar(name, clamped, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }

    public static void updateProgress(BossBar bar, String label, float progress) {
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(label, "label");
        float clamped = Math.max(0f, Math.min(1f, progress));
        int percent = Math.round(clamped * 100f);
        bar.name(Component.text(label + "... " + percent + "%", NamedTextColor.WHITE));
        bar.progress(clamped);
    }

    public static String formatEta(String prefix, int position, Long etaSeconds) {
        Objects.requireNonNull(prefix, "prefix");
        if (etaSeconds == null) {
            return String.format(Locale.ROOT, "%s . #%d . estimating", prefix, position);
        }
        return String.format(Locale.ROOT, "%s . #%d . ~%ds", prefix, position, etaSeconds);
    }
}
