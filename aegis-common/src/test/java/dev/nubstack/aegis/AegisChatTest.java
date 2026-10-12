package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class AegisChatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void historyLine_suggestsHistoryOnNameAndHoversReason() {
        Punishment row = sample(PunishmentType.BAN, true);
        Component line = AegisChat.historyLine(row);
        ClickEvent<?> click = findClick(line);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("/aegis history Steve", payload.value());
        assertTrue(PLAIN.serialize(hoverText(line)).contains("griefing"));
        assertTrue(PLAIN.serialize(hoverText(line)).contains("Admin"));
    }

    @Test
    void punishFeedback_hasUnbanHistoryAndCopyId() {
        Punishment row = sample(PunishmentType.BAN, true);
        Component feedback = AegisChat.punishFeedback(row);
        assertTrue(hasClickAction(feedback, ClickEvent.Action.RUN_COMMAND));
        assertTrue(hasClickAction(feedback, ClickEvent.Action.SUGGEST_COMMAND));
        assertTrue(hasClickAction(feedback, ClickEvent.Action.COPY_TO_CLIPBOARD));
        assertTrue(findCopyPayload(feedback).contains(row.id().toString()));
    }

    @Test
    void muteNotify_hoversRemainingAndRulesButton() {
        Punishment mute = new Punishment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Alex",
                PunishmentType.MUTE,
                "spam",
                UUID.randomUUID(),
                "Mod",
                Instant.now(),
                Instant.now().plus(Duration.ofHours(12)),
                true);
        Component notify = AegisChat.muteNotify(mute, "/rules");
        assertTrue(PLAIN.serialize(hoverText(notify)).toLowerCase().contains("ends in"));
        ClickEvent<?> click = findClick(notify);
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
    }

    @Test
    void banScreen_includesCopyableAppeal() {
        Punishment ban = sample(PunishmentType.BAN, true);
        AegisSettings settings = new AegisSettings(
                "<red>Banned: <reason> code <code></red>",
                "<red>muted</red>",
                null,
                "Appeal <code>",
                "/rules",
                Duration.ofSeconds(1),
                true, true, true, true, true, true,
                8);
        Component screen = AegisChat.banScreen(ban, settings);
        assertTrue(hasClickAction(screen, ClickEvent.Action.COPY_TO_CLIPBOARD));
        assertTrue(findCopyPayload(screen).contains(settings.appealCode(ban)));
    }

    @Test
    void lookupResult_copyButtons() {
        UUID id = UUID.randomUUID();
        Component lookup = AegisChat.lookupResult("Steve", id);
        assertTrue(findCopyPayload(lookup).contains(id.toString()));
    }

    private static Punishment sample(PunishmentType type, boolean active) {
        return new Punishment(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.randomUUID(),
                "Steve",
                type,
                "griefing",
                UUID.randomUUID(),
                "Admin",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                active);
    }

    private static boolean hasClickAction(Component component, ClickEvent.Action action) {
        if (component.clickEvent() != null && component.clickEvent().action() == action) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClickAction(child, action)) {
                return true;
            }
        }
        return false;
    }

    private static String findCopyPayload(Component component) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == ClickEvent.Action.COPY_TO_CLIPBOARD
                && component.clickEvent().payload() instanceof ClickEvent.Payload.Text text) {
            return text.value();
        }
        for (Component child : component.children()) {
            String nested = findCopyPayload(child);
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return "";
    }

    private static ClickEvent<?> findClick(Component component) {
        ClickEvent<?> found = findClickOrNull(component);
        if (found == null) {
            throw new AssertionError("No click event");
        }
        return found;
    }

    private static ClickEvent<?> findClickOrNull(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent<?> nested = findClickOrNull(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Component hoverText(Component component) {
        if (component.hoverEvent() != null && component.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT) {
            return (Component) component.hoverEvent().value();
        }
        for (Component child : component.children()) {
            try {
                return hoverText(child);
            } catch (AssertionError ignored) {
            }
        }
        throw new AssertionError("No hover");
    }
}
