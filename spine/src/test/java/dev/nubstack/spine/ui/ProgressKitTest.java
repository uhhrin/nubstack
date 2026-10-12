package dev.nubstack.spine.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ProgressKitTest {

    @Test
    void bossBar_clampsProgressAndFormatsTitle() {
        BossBar bar = BossBars.progress("Snapshot world", 0.62f);
        assertEquals(0.62f, bar.progress(), 0.001f);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(bar.name()).contains("62%"));
    }

    @Test
    void actionBar_formatsCountdown() {
        var component = ActionBars.countdown("Link code", 272);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.contains("4:32"));
    }
}
