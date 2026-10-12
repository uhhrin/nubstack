package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ConfirmChatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void ask_runsYesOnceAndRejectsReuse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ConfirmChat confirm = new ConfirmChat(clock, Duration.ofSeconds(30));
        UUID player = UUID.randomUUID();
        AtomicBoolean yes = new AtomicBoolean();
        AtomicBoolean no = new AtomicBoolean();

        ConfirmChat.Prompt prompt = confirm.ask(
                player,
                "Confirm ban?",
                "aegis",
                () -> yes.set(true),
                () -> no.set(true));

        assertTrue(PLAIN.serialize(prompt.message()).contains("Confirm"));
        ClickEvent<?> yesClick = findRun(prompt.message());
        assertTrue(((ClickEvent.Payload.Text) yesClick.payload()).value().contains("confirm"));

        assertTrue(confirm.resolve(player, prompt.token(), true));
        assertTrue(yes.get());
        assertFalse(no.get());
        assertFalse(confirm.resolve(player, prompt.token(), true));
    }

    @Test
    void ask_expiresWithFakeClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ConfirmChat confirm = new ConfirmChat(clock, Duration.ofSeconds(10));
        UUID player = UUID.randomUUID();
        AtomicBoolean expired = new AtomicBoolean();

        ConfirmChat.Prompt prompt = confirm.ask(
                player,
                "Pay?",
                "ledger",
                () -> {
                },
                () -> {
                },
                () -> expired.set(true));

        clock.advance(Duration.ofSeconds(11));
        assertFalse(confirm.resolve(player, prompt.token(), true));
        confirm.purgeExpired();
        assertTrue(expired.get());
    }

    @Test
    void resolve_rejectsWrongPlayer() {
        ConfirmChat confirm = new ConfirmChat(Clock.systemUTC(), Duration.ofMinutes(1));
        UUID owner = UUID.randomUUID();
        ConfirmChat.Prompt prompt = confirm.ask(owner, "x", "demo", () -> {
        }, () -> {
        });
        assertThrows(IllegalArgumentException.class, () -> confirm.resolve(UUID.randomUUID(), prompt.token(), true));
    }

    private static ClickEvent<?> findRun(Component component) {
        if (component.clickEvent() != null && component.clickEvent().action() == ClickEvent.Action.RUN_COMMAND) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            try {
                return findRun(child);
            } catch (AssertionError ignored) {
                // keep searching
            }
        }
        throw new AssertionError("No run click");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
