package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void parse_rendersMiniMessageToPlainText() {
        Component component = Messages.parse("<red>Hello</red> <bold>world</bold>");
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertEquals("Hello world", plain);
    }

    @Test
    void parse_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> Messages.parse("  "));
    }

    @Test
    void plain_stripsFormatting() {
        assertTrue(Messages.plain("<green>ok</green>").contains("ok"));
    }
}
