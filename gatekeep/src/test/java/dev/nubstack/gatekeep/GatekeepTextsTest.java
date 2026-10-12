package dev.nubstack.gatekeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class GatekeepTextsTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final ConnectionGuard.Decision DENIED = new ConnectionGuard.Decision(
            false, "1.2.3.4", 5, Duration.ofSeconds(60), Duration.ofSeconds(42), "GK-7F2A");

    @Test
    void denyMessageShowsSupportCodeAndAvoidsBanWording() {
        Component message = GatekeepTexts.denyMessage(Component.text("Slow down a little."), DENIED);
        String plain = PLAIN.serialize(message);
        assertTrue(plain.contains("Slow down a little."));
        assertTrue(plain.contains("GK-7F2A"));
        assertFalse(plain.toLowerCase().contains("ban"));
    }

    @Test
    void denyMessageHoverListsAttemptsWindowAndRetry() {
        Component message = GatekeepTexts.denyMessage(Component.text("x"), DENIED);
        Component code = find(message, c -> c.hoverEvent() != null);
        assertNotNull(code);
        Component hover = (Component) code.hoverEvent().value();
        String plain = PLAIN.serialize(hover);
        assertTrue(plain.contains("5 attempts"));
        assertTrue(plain.contains("1 minute"));
        assertTrue(plain.contains("42s"));
    }

    @Test
    void denyMessageSupportCodeCopiesToClipboard() {
        Component message = GatekeepTexts.denyMessage(Component.text("x"), DENIED);
        Component code = find(message, c -> c.clickEvent() != null);
        assertNotNull(code);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, code.clickEvent().action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, code.clickEvent().payload());
        assertEquals("GK-7F2A", payload.value());
    }

    @Test
    void statusEntryAddressSuggestsClear() {
        ConnectionGuard.Entry entry = new ConnectionGuard.Entry("1.2.3.4", 5, 10, Duration.ofSeconds(30));
        Component line = GatekeepTexts.statusEntry(entry, 5);
        Component ip = find(line, c -> c.clickEvent() != null);
        assertNotNull(ip);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, ip.clickEvent().action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, ip.clickEvent().payload());
        assertEquals("/gatekeep clear 1.2.3.4", payload.value());
        String plain = PLAIN.serialize(line);
        assertTrue(plain.contains("1.2.3.4"));
        assertTrue(plain.contains("5/5"));
        assertTrue(plain.contains("throttled"));
    }

    @Test
    void statusEntryUnderLimitIsNotMarkedThrottled() {
        ConnectionGuard.Entry entry = new ConnectionGuard.Entry("1.2.3.4", 1, 0, Duration.ZERO);
        assertFalse(PLAIN.serialize(GatekeepTexts.statusEntry(entry, 5)).contains("throttled"));
    }

    private static Component find(Component root, java.util.function.Predicate<Component> test) {
        if (test.test(root)) {
            return root;
        }
        for (Component child : root.children()) {
            Component found = find(child, test);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
