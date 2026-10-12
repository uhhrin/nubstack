package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class LoomMessagesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:10:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void restoring_isGrayActionBarText() {
        Component bar = LoomMessages.restoring();
        assertEquals("Restoring inventory...", plain(bar));
        assertEquals(NamedTextColor.GRAY, bar.color());
    }

    @Test
    void restored_isGreenActionBarText() {
        Component bar = LoomMessages.restored();
        assertEquals("Inventory restored", plain(bar));
        assertEquals(NamedTextColor.GREEN, bar.color());
    }

    @Test
    void waitingBar_countsDownAndFillsAsTimePasses() {
        BossBar start = LoomMessages.waitingBar(3, 3);
        assertEquals("Waiting for sync lock 3", plain(start.name()));
        assertEquals(0f, start.progress());
        BossBar later = LoomMessages.waitingBar(1, 3);
        assertEquals("Waiting for sync lock 1", plain(later.name()));
        assertEquals(2f / 3f, later.progress(), 0.001f);
    }

    @Test
    void waitingBar_clampsOutOfRangeValues() {
        assertEquals(1f, LoomMessages.waitingBar(0, 3).progress());
        assertEquals("Waiting for sync lock 0", plain(LoomMessages.waitingBar(-5, 3).name()));
        assertEquals(0f, LoomMessages.waitingBar(9, 3).progress());
        assertEquals(1f, LoomMessages.waitingBar(0, 0).progress());
    }

    @Test
    void status_showsAgeHolderBytesAndButtons() {
        PlayerDataStore.Snapshot snap = new PlayerDataStore.Snapshot(
                ID, "abcdef", NOW.minusSeconds(125), "hub", NOW.plusSeconds(20), 4L);
        Component out = LoomMessages.status("Steve", Optional.of(snap), NOW);
        String text = plain(out);
        assertTrue(text.contains("Steve"), text);
        assertTrue(text.contains("2m"), text);
        assertTrue(text.contains("hub"), text);
        assertTrue(text.contains("6 bytes"), text);
        assertTrue(text.contains("version 4"), text);

        ClickEvent<?> copy = clickWithAction(out, ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertEquals(ID.toString(), textPayload(copy));
        ClickEvent<?> inspect = clickWithAction(out, ClickEvent.Action.RUN_COMMAND);
        assertEquals("/loom inspect Steve", textPayload(inspect));
        ClickEvent<?> unlock = clickWithAction(out, ClickEvent.Action.SUGGEST_COMMAND);
        assertEquals("/loom unlock Steve", textPayload(unlock));
    }

    @Test
    void status_expiredLeaseReadsAsFree() {
        PlayerDataStore.Snapshot snap = new PlayerDataStore.Snapshot(
                ID, "abc", NOW.minusSeconds(5), "hub", NOW.minusSeconds(1), 1L);
        String text = plain(LoomMessages.status("Steve", Optional.of(snap), NOW));
        assertTrue(text.contains("free"), text);
        assertFalse(text.contains("held by hub"), text);
    }

    @Test
    void status_withoutRowSaysNoData() {
        String text = plain(LoomMessages.status("Alex", Optional.empty(), NOW));
        assertTrue(text.contains("no stored data"), text);
    }

    @Test
    void help_hidesAdminEntriesFromPlayers() {
        Predicate<String> player = permission -> permission.equals("loom.use");
        String text = plain(LoomMessages.help().build(player, 1, 10));
        assertFalse(text.contains("/loom unlock"), text);
        Predicate<String> admin = permission -> true;
        String adminText = plain(LoomMessages.help().build(admin, 1, 10));
        for (String sub : List.of("status", "unlock", "inspect", "force-save", "reload")) {
            assertTrue(adminText.contains("/loom " + sub), adminText);
        }
    }

    @Test
    void playerDenied_explainsSyncIsAutomatic() {
        assertTrue(plain(LoomMessages.playerDenied()).contains("sync runs automatically"));
    }

    @Test
    void inspectHeader_hasCopyUuidButtonAndHover() {
        Component out = LoomMessages.inspectHeader("Steve", ID);
        assertTrue(plain(out).contains("Steve"));
        ClickEvent<?> copy = clickWithAction(out, ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertEquals(ID.toString(), textPayload(copy));
        HoverEvent<?> hover = firstHover(out);
        assertNotNull(hover);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hover.action());
    }

    @Test
    void ageLabel_usesCompactDurations() {
        assertEquals("never", LoomMessages.age(Instant.EPOCH, NOW));
        assertEquals("0s ago", LoomMessages.age(NOW.plusSeconds(3), NOW));
        assertEquals("45s ago", LoomMessages.age(NOW.minus(Duration.ofSeconds(45)), NOW));
        assertEquals("1h 0m ago", LoomMessages.age(NOW.minus(Duration.ofHours(1)), NOW));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<Component> flatten(Component root) {
        List<Component> all = new ArrayList<>();
        collect(root, all);
        return all;
    }

    private static void collect(Component component, List<Component> into) {
        into.add(component);
        for (Component child : component.children()) {
            collect(child, into);
        }
    }

    private static ClickEvent<?> clickWithAction(Component root, ClickEvent.Action action) {
        return flatten(root).stream()
                .map(Component::clickEvent)
                .filter(click -> click != null && click.action() == action)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no click event " + action));
    }

    private static HoverEvent<?> firstHover(Component root) {
        return flatten(root).stream()
                .map(Component::hoverEvent)
                .filter(hover -> hover != null)
                .findFirst()
                .orElse(null);
    }

    private static String textPayload(ClickEvent<?> click) {
        return assertInstanceOf(ClickEvent.Payload.Text.class, click.payload()).value();
    }
}
