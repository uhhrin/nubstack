package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class PrefixedMessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void info_includesPluginPrefix() {
        Component message = Messages.prefixed("tether").info("hello");
        String plain = PLAIN.serialize(message);
        assertTrue(plain.contains("[tether]"));
        assertTrue(plain.contains("hello"));
    }

    @Test
    void copyButton_setsCopyToClipboardPayload() {
        Component button = Messages.prefixed("tether").copyButton("Copy", "ABCD");
        ClickEvent<?> click = findClick(button);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("ABCD", payload.value());
        assertTrue(PLAIN.serialize(hoverText(button)).toLowerCase().contains("copy"));
    }

    @Test
    void suggestButton_setsSuggestCommand() {
        Component button = Messages.prefixed("ledger").suggestButton("Pay", "/pay Steve ");
        ClickEvent<?> click = findClick(button);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("/pay Steve ", payload.value());
    }

    @Test
    void runButton_setsRunCommand() {
        Component button = Messages.prefixed("harbor").runButton("Leave", "/harbor leave");
        ClickEvent<?> click = findClick(button);
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("/harbor leave", payload.value());
    }

    @Test
    void hoverDetail_attachesHoverText() {
        Component detail = Messages.prefixed("aegis").hoverDetail("Ban", "full reason here");
        assertTrue(PLAIN.serialize(hoverText(detail)).contains("full reason here"));
        assertTrue(PLAIN.serialize(detail).contains("Ban"));
    }

    @Test
    void insert_setsInsertion() {
        Component withInsert = Messages.prefixed("aegis").insert("Steve", "Steve");
        TextComponent text = findTextWithInsertion(withInsert);
        assertEquals("Steve", text.insertion());
    }

    private static ClickEvent<?> findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent<?> nested = findClick(child);
            if (nested != null) {
                return nested;
            }
        }
        throw new AssertionError("No click event on " + PLAIN.serialize(component));
    }

    private static Component hoverText(Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            return (Component) hover.value();
        }
        for (Component child : component.children()) {
            try {
                return hoverText(child);
            } catch (AssertionError ignored) {
                // keep searching
            }
        }
        throw new AssertionError("No hover text on " + PLAIN.serialize(component));
    }

    private static TextComponent findTextWithInsertion(Component component) {
        if (component instanceof TextComponent text && text.insertion() != null) {
            return text;
        }
        for (Component child : component.children()) {
            TextComponent nested = findTextWithInsertion(child);
            if (nested != null) {
                return nested;
            }
        }
        throw new AssertionError("No insertion on component");
    }
}
