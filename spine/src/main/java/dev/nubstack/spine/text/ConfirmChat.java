package dev.nubstack.spine.text;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Platform-neutral pending confirmation registry. Plugins wire
 * {@code /<root> confirm <token>} to {@link #resolve}.
 */
public final class ConfirmChat {

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public ConfirmChat() {
        this(Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public ConfirmChat(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public Prompt ask(UUID playerId, String promptText, String rootCommand, Runnable onYes, Runnable onNo) {
        return ask(playerId, promptText, rootCommand, onYes, onNo, () -> {
        });
    }

    public Prompt ask(
            UUID playerId,
            String promptText,
            String rootCommand,
            Runnable onYes,
            Runnable onNo,
            Runnable onExpire) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(promptText, "promptText");
        Objects.requireNonNull(rootCommand, "rootCommand");
        Objects.requireNonNull(onYes, "onYes");
        Objects.requireNonNull(onNo, "onNo");
        Objects.requireNonNull(onExpire, "onExpire");

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant expiresAt = clock.instant().plus(ttl);
        pending.put(token, new Pending(playerId, expiresAt, onYes, onNo, onExpire));

        String yesCmd = "/" + rootCommand + " confirm " + token;
        String noCmd = "/" + rootCommand + " cancel " + token;
        Component message = Component.text()
                .append(Component.text(promptText, NamedTextColor.YELLOW))
                .append(Component.space())
                .append(button("Confirm", yesCmd, NamedTextColor.GREEN))
                .append(Component.space())
                .append(button("Cancel", noCmd, NamedTextColor.RED))
                .build();
        return new Prompt(token, message);
    }

    /**
     * @return true if a pending prompt was consumed
     */
    public boolean resolve(UUID playerId, String token, boolean accepted) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        Pending found = pending.get(token);
        if (found == null) {
            return false;
        }
        if (!found.playerId().equals(playerId)) {
            throw new IllegalArgumentException("Confirmation token belongs to another player");
        }
        if (clock.instant().isAfter(found.expiresAt())) {
            pending.remove(token);
            found.onExpire().run();
            return false;
        }
        if (pending.remove(token, found)) {
            if (accepted) {
                found.onYes().run();
            } else {
                found.onNo().run();
            }
            return true;
        }
        return false;
    }

    public void purgeExpired() {
        Instant now = clock.instant();
        pending.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().expiresAt())) {
                entry.getValue().onExpire().run();
                return true;
            }
            return false;
        });
    }

    private static Component button(String label, String command, NamedTextColor color) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, color, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Click to " + label.toLowerCase(), NamedTextColor.GRAY)))
                .build();
    }

    public record Prompt(String token, Component message) {
    }

    private record Pending(UUID playerId, Instant expiresAt, Runnable onYes, Runnable onNo, Runnable onExpire) {
    }
}
