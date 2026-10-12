package dev.nubstack.amber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nubstack.amber.SnapshotService.SnapshotRef;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class AmberMessagesTest {

    private static final SnapshotRef REF = new SnapshotRef(
            Path.of("snaps", "20260101-120000-000-world-pre-event"),
            "world",
            "pre event",
            Instant.parse("2026-01-01T12:00:00Z"),
            3L * 1024 * 1024);

    @Test
    void formatsSizes() {
        assertEquals("0 B", AmberMessages.formatSize(0));
        assertEquals("1023 B", AmberMessages.formatSize(1023));
        assertEquals("1.0 KiB", AmberMessages.formatSize(1024));
        assertEquals("3.0 MiB", AmberMessages.formatSize(3L * 1024 * 1024));
        assertEquals("2.5 GiB", AmberMessages.formatSize((long) (2.5 * 1024 * 1024 * 1024)));
    }

    @Test
    void listLineHasCopyRestoreDeleteButtons() {
        List<ClickEvent> clicks = clicks(AmberMessages.listLine(REF));
        assertTrue(clicks.contains(ClickEvent.copyToClipboard("20260101-120000-000-world-pre-event")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/amber restore 20260101-120000-000-world-pre-event")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/amber delete 20260101-120000-000-world-pre-event")));
        assertEquals(3, clicks.size());
    }

    @Test
    void listLineHoverShowsWorldSizeTimeAndLabel() {
        String hover = hovers(AmberMessages.listLine(REF)).stream()
                .map(AmberMessagesTest::plain)
                .filter(text -> text.contains("World:"))
                .findFirst()
                .orElseThrow();
        assertTrue(hover.contains("World: world"));
        assertTrue(hover.contains("Size: 3.0 MiB"));
        assertTrue(hover.contains("Created: 2026-01-01 12:00:00 UTC"));
        assertTrue(hover.contains("Label: pre event"));
    }

    @Test
    void listIsEmptyMessageOrHeaderWithLines() {
        assertEquals("[amber] No snapshots", plain(AmberMessages.list(List.of())));
        String text = plain(AmberMessages.list(List.of(REF, REF)));
        assertTrue(text.contains("Snapshots (2), newest first"));
    }

    @Test
    void createdOffersCopyAndListButtons() {
        List<ClickEvent> clicks = clicks(AmberMessages.created(REF));
        assertTrue(clicks.contains(ClickEvent.copyToClipboard("20260101-120000-000-world-pre-event")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/amber list world")));
    }

    @Test
    void infoOffersFilenameAndPathCopy() {
        List<ClickEvent> clicks = clicks(AmberMessages.info(REF));
        assertEquals(2, clicks.size());
        assertTrue(clicks.contains(ClickEvent.copyToClipboard(REF.path().toAbsolutePath().toString())));
        assertTrue(plain(AmberMessages.info(REF)).contains("Label: pre event"));
    }

    @Test
    void restoreTypePromptSuggestsTypingWorldName() {
        Component prompt = AmberMessages.restoreTypePrompt(REF);
        assertEquals(
                List.of(ClickEvent.suggestCommand("/amber restore 20260101-120000-000-world-pre-event world")),
                clicks(prompt));
    }

    @Test
    void helpFiltersByPermissionAndShowsDiskUsed() {
        String admin = plain(AmberMessages.help(permission -> true, 2048));
        assertTrue(admin.contains("/amber restore <file>"));
        assertTrue(admin.contains("Snapshot disk used: 2.0 KiB"));
        String guest = plain(AmberMessages.help(permission -> false, 0));
        assertFalse(guest.contains("/amber restore <file>"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<ClickEvent> clicks(Component root) {
        List<ClickEvent> out = new ArrayList<>();
        walk(root, component -> {
            if (component.clickEvent() != null) {
                out.add(component.clickEvent());
            }
        });
        return out;
    }

    private static List<Component> hovers(Component root) {
        List<Component> out = new ArrayList<>();
        walk(root, component -> {
            if (component.hoverEvent() instanceof HoverEvent<?> hover && hover.value() instanceof Component text) {
                out.add(text);
            }
        });
        return out;
    }

    private static void walk(Component component, java.util.function.Consumer<Component> visit) {
        visit.accept(component);
        component.children().forEach(child -> walk(child, visit));
    }
}
