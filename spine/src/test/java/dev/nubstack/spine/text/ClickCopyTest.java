package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

class ClickCopyTest {

    @Test
    void of_setsCopyPayload() {
        Component button = ClickCopy.of("Copy ID", "abc-123");
        ClickEvent<?> click = button.clickEvent();
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("abc-123", payload.value());
    }
}
