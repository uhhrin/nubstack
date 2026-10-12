package dev.nubstack.harbor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HarborTextsTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void statusLineIncludesButtonsAndSuggestWhenHealthy() {
        Component line = HarborTexts.statusLine(
                "Hub",
                "healthy",
                12,
                4,
                10,
                Instant.parse("2026-01-01T12:00:00Z"),
                true);

        String plain = PLAIN.serialize(line);
        assertTrue(plain.contains("Hub"));
        assertTrue(plain.contains("healthy"));
        assertTrue(plain.contains("queue 12"));
        assertTrue(plain.contains("#4"));
        assertTrue(plain.contains("priority 10"));
        assertTrue(plain.contains("[Leave]"));
        assertTrue(plain.contains("[Refresh]"));

        ClickEvent<?> leave = findClick(line, ClickEvent.Action.RUN_COMMAND, "/harbor leave");
        assertEquals(ClickEvent.Action.RUN_COMMAND, leave.action());
        ClickEvent.Payload.Text leavePayload = assertInstanceOf(ClickEvent.Payload.Text.class, leave.payload());
        assertEquals("/harbor leave", leavePayload.value());

        ClickEvent<?> refresh = findClick(line, ClickEvent.Action.RUN_COMMAND, "/harbor status Hub");
        ClickEvent.Payload.Text refreshPayload = assertInstanceOf(ClickEvent.Payload.Text.class, refresh.payload());
        assertEquals("/harbor status Hub", refreshPayload.value());

        ClickEvent<?> suggest = findClick(line, ClickEvent.Action.SUGGEST_COMMAND, "/server Hub");
        ClickEvent.Payload.Text suggestPayload = assertInstanceOf(ClickEvent.Payload.Text.class, suggest.payload());
        assertEquals("/server Hub", suggestPayload.value());

        Component hover = hoverOnServerName(line, "Hub");
        assertTrue(PLAIN.serialize(hover).contains("Last check"));
    }

    @Test
    void joinNowButtonRunsJoinCommand() {
        Component button = HarborTexts.joinNowButton("minigame");
        ClickEvent<?> click = findClick(button, ClickEvent.Action.RUN_COMMAND, "/harbor join minigame");
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("/harbor join minigame", payload.value());
        assertTrue(PLAIN.serialize(button).contains("[Join now]"));
    }

    @Test
    void bossBarLabelUsesEtaHelper() {
        assertEquals("Queue minigame . #4 . ~45s", HarborTexts.bossBarLabel("minigame", 4, 45L));
        assertEquals("Queue minigame . #4 . estimating", HarborTexts.bossBarLabel("minigame", 4, null));
    }

    private static ClickEvent<?> findClick(Component component, ClickEvent.Action action, String value) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == action
                && component.clickEvent().payload() instanceof ClickEvent.Payload.Text text
                && value.equals(text.value())) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            try {
                return findClick(child, action, value);
            } catch (AssertionError ignored) {
                // keep searching
            }
        }
        throw new AssertionError("Missing click " + action + " " + value + " in " + PLAIN.serialize(component));
    }

    private static Component hoverOnServerName(Component component, String server) {
        if (PLAIN.serialize(component).equals(server) && component.hoverEvent() != null
                && component.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT) {
            return (Component) component.hoverEvent().value();
        }
        for (Component child : component.children()) {
            try {
                return hoverOnServerName(child, server);
            } catch (AssertionError ignored) {
                // keep searching
            }
        }
        throw new AssertionError("No hover on server name " + server);
    }
}
