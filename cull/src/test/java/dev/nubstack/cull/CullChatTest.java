package dev.nubstack.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CullChatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final CullChat chat = new CullChat();

    private static ChunkLoad sample() {
        return new ChunkLoad("world", 12, -3, 57, 40, Map.of("ZOMBIE", 57));
    }

    @Test
    void resultLineClicksToTpByRank() {
        Component line = chat.resultLine(3, sample());
        assertEquals(List.of("/cull tp 3"), clicks(line, ClickEvent.Action.RUN_COMMAND));
    }

    @Test
    void resultLineHoverHasTypeBreakdown() {
        assertTrue(hovers(chat.resultLine(1, sample())).contains("ZOMBIE x57"));
    }

    @Test
    void resultLineHasCopyCoords() {
        Component line = chat.resultLine(1, sample());
        assertEquals(List.of("world 12 -3"), clicks(line, ClickEvent.Action.COPY_TO_CLIPBOARD));
        assertTrue(PLAIN.serialize(line).contains("[Copy coords]"));
        assertTrue(PLAIN.serialize(line).contains("57/40"));
    }

    @Test
    void helpShowsGlobalMaxAndNextCleanup() {
        String text = PLAIN.serialize(chat.help(node -> true, 40, 7));
        assertTrue(text.contains("Max per chunk: 40"));
        assertTrue(text.contains("Next cleanup in 7s"));
    }

    @Test
    void helpHidesAdminEntriesWithoutPermission() {
        String useOnly = PLAIN.serialize(chat.help(node -> node.equals("cull.use"), 40, 7));
        assertTrue(useOnly.contains("/cull scan"));
        assertFalse(useOnly.contains("/cull run"));
        assertFalse(useOnly.contains("/cull reload"));
        String admin = PLAIN.serialize(chat.help(node -> true, 40, 7));
        assertTrue(admin.contains("/cull run"));
        assertTrue(admin.contains("/cull reload"));
    }

    private static List<String> clicks(Component root, ClickEvent.Action action) {
        List<String> out = new ArrayList<>();
        walk(root, component -> {
            ClickEvent<?> click = component.clickEvent();
            if (click != null && click.action() == action) {
                out.add(assertInstanceOf(ClickEvent.Payload.Text.class, click.payload()).value());
            }
        });
        return out;
    }

    private static String hovers(Component root) {
        StringBuilder out = new StringBuilder();
        walk(root, component -> {
            HoverEvent<?> hover = component.hoverEvent();
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
                out.append(PLAIN.serialize((Component) hover.value())).append('\n');
            }
        });
        return out.toString();
    }

    private static void walk(Component component, Consumer<Component> visit) {
        visit.accept(component);
        component.children().forEach(child -> walk(child, visit));
    }
}
