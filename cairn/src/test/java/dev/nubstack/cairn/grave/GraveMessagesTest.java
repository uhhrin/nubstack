package dev.nubstack.cairn.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

class GraveMessagesTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void copyCoordsButton_usesClipboardPayload() {
        MutableComponent button = GraveMessages.copyCoordsButton(12, 64, -40);
        ClickEvent click = button.getStyle().getClickEvent();
        ClickEvent.CopyToClipboard copy = assertInstanceOf(ClickEvent.CopyToClipboard.class, click);
        assertEquals("12 64 -40", copy.value());
        HoverEvent hover = button.getStyle().getHoverEvent();
        HoverEvent.ShowText show = assertInstanceOf(HoverEvent.ShowText.class, hover);
        assertEquals("Click to copy", show.value().getString());
    }

    @Test
    void tpButton_runsAdminTpForTheGraveId() {
        MutableComponent button = GraveMessages.tpButton("4");
        ClickEvent.RunCommand run = assertInstanceOf(ClickEvent.RunCommand.class, button.getStyle().getClickEvent());
        assertEquals("/cairn admin tp 4", run.command());
        HoverEvent.ShowText show = assertInstanceOf(HoverEvent.ShowText.class, button.getStyle().getHoverEvent());
        assertEquals("Click to teleport to this grave", show.value().getString());
    }

    @Test
    void deathMessage_includesDimensionAndCopyButton_butNoTpForPlayers() {
        Component message = GraveMessages.deathMessage("4", "minecraft:overworld", 12, 64, -40, false);
        String plain = message.getString();
        assertTrue(plain.contains("minecraft:overworld"));
        assertTrue(plain.contains("12 64 -40"));
        assertTrue(plain.contains("[Copy coords]"));
        assertFalse(plain.contains("[Tp]"));
    }

    @Test
    void deathMessage_opsGetClickableTp() {
        Component message = GraveMessages.deathMessage("9", "minecraft:the_nether", 1, 80, 2, true);
        assertTrue(message.getString().contains("[Tp]"));
        boolean foundTp = false;
        for (Component part : flatten(message)) {
            if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) {
                assertEquals("/cairn admin tp 9", run.command());
                foundTp = true;
            }
        }
        assertTrue(foundTp);
    }

    @Test
    void expiredWhisper_isFixedText() {
        assertEquals("Grave expired; items dropped", GraveMessages.expiredWhisper().getString());
    }

    @Test
    void timeLeft_roundsToMinutesOrSeconds() {
        assertEquals("12m", GraveMessages.timeLeft(20 * 60 * 12));
        assertEquals("1m", GraveMessages.timeLeft(20 * 60 + 5));
        assertEquals("45s", GraveMessages.timeLeft(20 * 45));
        assertEquals("0s", GraveMessages.timeLeft(0));
    }

    @Test
    void graveLine_showsIdPlaceTimeAndCopy_adminsAlsoGetOwnerAndTp() {
        GraveRecord grave = new GraveRecord(
                "3", OWNER, "Steve", "minecraft:overworld", 12, 64, -40, 20 * 60 * 5, List.of(), null, null, 0);
        String player = GraveMessages.graveLine(grave, false).getString();
        assertTrue(player.contains("#3"));
        assertTrue(player.contains("minecraft:overworld"));
        assertTrue(player.contains("12 64 -40"));
        assertTrue(player.contains("5m"));
        assertTrue(player.contains("[Copy coords]"));
        assertFalse(player.contains("[Tp]"));
        assertFalse(player.contains("Steve"));

        String admin = GraveMessages.graveLine(grave, true).getString();
        assertTrue(admin.contains("Steve"));
        assertTrue(admin.contains("[Tp]"));
    }

    @Test
    void helpText_listsEveryCommandPath() {
        String help = GraveMessages.helpText().getString();
        for (String part : List.of(
                "/cairn locate", "/cairn list", "admin list", "admin tp", "admin key", "admin clear", "admin expire")) {
            assertTrue(help.contains(part), part);
        }
    }

    private static List<Component> flatten(Component root) {
        List<Component> out = new ArrayList<>();
        out.add(root);
        for (Component sibling : root.getSiblings()) {
            out.addAll(flatten(sibling));
        }
        return out;
    }
}
