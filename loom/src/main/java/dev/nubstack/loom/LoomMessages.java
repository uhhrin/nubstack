package dev.nubstack.loom;

import dev.nubstack.spine.text.Durations;
import dev.nubstack.spine.text.HelpBuilder;
import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Player and staff facing text. Pure Adventure so click and hover payloads are unit-testable. */
final class LoomMessages {

    static final String ADMIN = "loom.admin";
    static final String USE = "loom.use";

    private static final PrefixedMessages LOOM = Messages.prefixed("loom");

    private LoomMessages() {
    }

    static PrefixedMessages prefixed() {
        return LOOM;
    }

    static Component restoring() {
        return Component.text("Restoring inventory...", NamedTextColor.GRAY);
    }

    static Component restored() {
        return Component.text("Inventory restored", NamedTextColor.GREEN);
    }

    // shortcut: spine BossBars.progress prints a percent, loom needs a seconds countdown, so the bar is built here
    static BossBar waitingBar(int secondsRemaining, int totalSeconds) {
        int shown = Math.max(0, secondsRemaining);
        float progress = totalSeconds <= 0 ? 1f : (totalSeconds - shown) / (float) totalSeconds;
        return BossBar.bossBar(
                Component.text("Waiting for sync lock " + shown, NamedTextColor.WHITE),
                Math.max(0f, Math.min(1f, progress)),
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS);
    }

    static Component playerDenied() {
        return LOOM.info("Inventory sync runs automatically when you switch servers.");
    }

    static HelpBuilder help() {
        return HelpBuilder.root("loom")
                .entry("/loom", "Show this help", USE)
                .entry("/loom status [player]", "Last save, lease holder, stored size", ADMIN)
                .entry("/loom inspect <player>", "Read-only preview of the stored inventory", ADMIN)
                .entry("/loom force-save [player]", "Save an online player now", ADMIN)
                .entry("/loom unlock <player>", "Clear a stuck sync lease", ADMIN)
                .entry("/loom reload", "Reload config.yml (not database or server-id)", ADMIN);
    }

    static Component status(String playerName, Optional<PlayerDataStore.Snapshot> snapshot, Instant now) {
        if (snapshot.isEmpty()) {
            return LOOM.info(playerName + ": no stored data");
        }
        PlayerDataStore.Snapshot snap = snapshot.get();
        String lease = snap.isLeased(now) ? "held by " + snap.lockedBy() : "free";
        UUID id = snap.playerId();
        return Component.text()
                .append(LOOM.info(playerName + ": saved " + age(snap.updatedAt(), now)
                        + ", lease " + lease
                        + ", " + snap.blobBytes() + " bytes, version " + snap.version()))
                .append(Component.space())
                .append(LOOM.copyButton("Copy UUID", id.toString()))
                .append(Component.space())
                .append(LOOM.runButton("Inspect", "/loom inspect " + playerName))
                .append(Component.space())
                .append(LOOM.suggestButton("Unlock", "/loom unlock " + playerName))
                .build();
    }

    static Component inspectHeader(String playerName, UUID id) {
        return Component.text()
                .append(LOOM.info("Previewing stored inventory of "))
                .append(LOOM.hoverDetail(playerName, id.toString()))
                .append(Component.space())
                .append(LOOM.copyButton("Copy UUID", id.toString()))
                .build();
    }

    static String age(Instant savedAt, Instant now) {
        if (savedAt == null || savedAt.equals(Instant.EPOCH)) {
            return "never";
        }
        Duration since = Duration.between(savedAt, now);
        return Durations.remaining(since.isNegative() ? Duration.ZERO : since) + " ago";
    }
}
