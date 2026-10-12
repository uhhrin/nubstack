package dev.nubstack.spotlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class SpotlightChatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final ChunkWeights WEIGHTS = new ChunkWeights(1, 8, 4, 2, 3, 5);
    private final SpotlightChat chat = new SpotlightChat(WEIGHTS);

    @Test
    void resultLine_runCommandWhenCanTp() {
        Component line = chat.resultLine(1, sampleCost(), true);
        assertEquals(List.of("/spotlight tp 1"), clicks(line, ClickEvent.Action.RUN_COMMAND));
    }

    @Test
    void resultLine_suggestTpWhenCannotTp() {
        Component line = chat.resultLine(2, sampleCost(), false);
        assertEquals(List.of("/tp @s 200 ~ -40"), clicks(line, ClickEvent.Action.SUGGEST_COMMAND));
        assertTrue(clicks(line, ClickEvent.Action.RUN_COMMAND).isEmpty());
    }

    @Test
    void resultLine_hoverContainsScoreMath() {
        Component line = chat.resultLine(1, sampleCost(), true);
        assertTrue(hovers(line).contains("40 entities x1 + 6 hoppers x8 = 88"));
    }

    @Test
    void resultLine_hasCopyCoordsButton() {
        Component line = chat.resultLine(1, sampleCost(), true);
        assertEquals(List.of("world 12 -3"), clicks(line, ClickEvent.Action.COPY_TO_CLIPBOARD));
        assertTrue(PLAIN.serialize(line).contains("[Copy coords]"));
    }

    @Test
    void afterScanButtons_rescanAndBook() {
        Component buttons = chat.afterScanButtons();
        List<String> run = clicks(buttons, ClickEvent.Action.RUN_COMMAND);
        assertEquals(Set.of("/spotlight scan", "/spotlight book"), Set.copyOf(run));
        String text = PLAIN.serialize(buttons);
        assertTrue(text.contains("[Rescan]"));
        assertTrue(text.contains("[Book view]"));
    }

    @Test
    void help_hidesAdminEntriesWithoutPermission() {
        String scanOnly = PLAIN.serialize(chat.help(node -> node.equals("spotlight.scan"), "never"));
        assertTrue(scanOnly.contains("Last scan: never"));
        assertTrue(scanOnly.contains("/spotlight scan"));
        assertFalse(scanOnly.contains("/spotlight weights"));
        String admin = PLAIN.serialize(chat.help(node -> true, "2 minutes ago"));
        assertTrue(admin.contains("/spotlight weights"));
        assertTrue(admin.contains("/spotlight reload"));
    }

    @Test
    void help_tpEntryNeedsTpPermission() {
        String scanOnly = PLAIN.serialize(chat.help(node -> node.equals("spotlight.scan"), "never"));
        assertFalse(scanOnly.contains("/spotlight tp"));
    }

    @Test
    void bookPages_splitSixPerPage() {
        List<LagReport.ChunkCost> costs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            costs.add(sampleCost());
        }
        List<Component> pages = chat.bookPages(costs);
        assertEquals(2, pages.size());
        assertTrue(PLAIN.serialize(pages.get(1)).startsWith("#7 "));
    }

    @Test
    void bookPages_emptyHasPlaceholder() {
        List<Component> pages = chat.bookPages(List.of());
        assertEquals(1, pages.size());
        assertEquals("No scored chunks.", PLAIN.serialize(pages.getFirst()));
    }

    private static LagReport.ChunkCost sampleCost() {
        return new LagReport.ChunkCost("world", 12, -3, new ChunkCounts(40, 6, 0, 0, 0, 0), 88);
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

    private static void walk(Component component, java.util.function.Consumer<Component> visit) {
        visit.accept(component);
        component.children().forEach(child -> walk(child, visit));
    }
}
