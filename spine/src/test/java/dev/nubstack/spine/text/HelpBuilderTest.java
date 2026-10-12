package dev.nubstack.spine.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HelpBuilderTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void page_filtersByPermissionAndSuggestsUsage() {
        HelpBuilder help = HelpBuilder.root("aegis")
                .entry("/aegis ban <player>", "Ban a player", "aegis.ban")
                .entry("/aegis history <player>", "View history", "aegis.history")
                .entry("/aegis reload", "Reload messages", "aegis.admin");

        Component page = help.build(perm -> Set.of("aegis.ban", "aegis.history").contains(perm), 1, 10);
        String plain = PLAIN.serialize(page);
        assertTrue(plain.contains("ban"));
        assertTrue(plain.contains("history"));
        assertTrue(!plain.contains("reload") || plain.toLowerCase().contains("no permission"));

        ClickEvent<?> click = findSuggest(page);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
    }

    @Test
    void page_paginatesEntries() {
        HelpBuilder help = HelpBuilder.root("demo");
        for (int i = 1; i <= 5; i++) {
            help.entry("/demo " + i, "Item " + i, null);
        }
        Component page1 = help.build(perm -> true, 1, 2);
        Component page2 = help.build(perm -> true, 2, 2);
        assertTrue(PLAIN.serialize(page1).contains("/demo 1"));
        assertTrue(PLAIN.serialize(page2).contains("/demo 3"));
    }

    private static ClickEvent<?> findSuggest(Component component) {
        ClickEvent<?> found = findSuggestOrNull(component);
        if (found == null) {
            throw new AssertionError("No suggest click");
        }
        return found;
    }

    private static ClickEvent<?> findSuggestOrNull(Component component) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent<?> nested = findSuggestOrNull(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
