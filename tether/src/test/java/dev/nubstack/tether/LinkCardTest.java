package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class LinkCardTest {

    @Test
    void clipboardValue_codeVersusFull() {
        assertEquals("ABCD1234", LinkCard.clipboardValue("ABCD1234", LinkCard.ClipboardPayload.CODE));
        assertEquals("!link ABCD1234", LinkCard.clipboardValue("ABCD1234", LinkCard.ClipboardPayload.FULL));
    }

    @Test
    void codeLabel_usesCopyToClipboardAndHover() {
        Component label = LinkCard.codeLabel("ABCD1234", "!link ABCD1234");
        ClickEvent<?> click = label.clickEvent();
        assertNotNull(click);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action());
        ClickEvent.Payload.Text payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
        assertEquals("!link ABCD1234", payload.value());

        HoverEvent<?> hover = label.hoverEvent();
        assertNotNull(hover);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hover.action());

        TextComponent text = assertInstanceOf(TextComponent.class, label);
        assertEquals("ABCD1234", text.content());
        assertEquals(NamedTextColor.GOLD, text.color());
        assertTrue(text.hasDecoration(TextDecoration.BOLD));
    }

    @Test
    void card_includesCopyAndCancelButtons() {
        Component card = LinkCard.card("ABCD1234", LinkCard.ClipboardPayload.FULL, 300);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(card);
        assertTrue(plain.contains("ABCD1234"));
        assertTrue(plain.contains("Copy code"));
        assertTrue(plain.contains("Cancel code"));
        assertTrue(plain.contains("5:00"));
    }

    @Test
    void linkedSuccess_includesTag() {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(LinkCard.linkedSuccess("nub"));
        assertTrue(plain.contains("Linked as @nub"));
    }

    @Test
    void card_mentionsSlashOnlyWhenEnabled() {
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(LinkCard.card("ABCD1234", LinkCard.ClipboardPayload.CODE, 60, true))
                .contains("/link"));
        assertFalse(plain.serialize(LinkCard.card("ABCD1234", LinkCard.ClipboardPayload.CODE, 60, false))
                .contains("/link"));
    }

    @Test
    void card_buttonsRunCancelAndCopyPayload() {
        Component card = LinkCard.card("ABCD1234", LinkCard.ClipboardPayload.FULL, 300);
        assertTrue(hasClick(card, ClickEvent.Action.RUN_COMMAND, "/tether cancel"));
        assertTrue(hasClick(card, ClickEvent.Action.COPY_TO_CLIPBOARD, "!link ABCD1234"));
    }

    @Test
    void successTitle_showsLinkedTagAndSoundIsLevelUp() {
        net.kyori.adventure.title.Title title = LinkCard.successTitle("nub");
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("Linked", plain.serialize(title.title()));
        assertEquals("@nub", plain.serialize(title.subtitle()));
        assertEquals("minecraft:entity.player.levelup", LinkCard.successSound().name().asString());
    }

    @Test
    void hardGateKick_includesCodeAndExpiry() {
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String text = plain.serialize(LinkCard.hardGateKick("ABCD1234", 272, true));
        assertTrue(text.contains("ABCD1234"));
        assertTrue(text.contains("4:32"));
        assertTrue(text.contains("/link"));
    }

    private static boolean hasClick(Component root, ClickEvent.Action action, String value) {
        ClickEvent<?> click = root.clickEvent();
        if (click != null && click.action() == action
                && click.payload() instanceof ClickEvent.Payload.Text text && text.value().equals(value)) {
            return true;
        }
        return root.children().stream().anyMatch(child -> hasClick(child, action, value));
    }
}
