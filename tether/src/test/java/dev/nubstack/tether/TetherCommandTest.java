package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TetherCommandTest {

    @Test
    void normalize_linkAliasPrependsLink() {
        assertArrayEquals(new String[] {"link"}, TetherCommand.normalize("link", new String[0]));
        assertArrayEquals(new String[] {"link", "renew"}, TetherCommand.normalize("link", new String[] {"renew"}));
    }

    @Test
    void normalize_discordAliasMapsToStatus() {
        assertArrayEquals(new String[] {"status"}, TetherCommand.normalize("discord", new String[0]));
    }

    @Test
    void normalize_tetherLabelKeepsArgs() {
        assertArrayEquals(new String[] {"admin", "codes"},
                TetherCommand.normalize("tether", new String[] {"admin", "codes"}));
    }

    @Test
    void looksLikeDiscordId_acceptsSnowflakesOnly() {
        assertTrue(TetherCommand.looksLikeDiscordId("123456789012345678"));
        assertFalse(TetherCommand.looksLikeDiscordId("Steve"));
        assertFalse(TetherCommand.looksLikeDiscordId("1234"));
    }

    @Test
    void complete_filtersByPermissionAndPrefix() {
        List<String> player = TetherCommand.complete(new String[] {"l"}, perm -> perm.equals("tether.link"));
        assertEquals(List.of("link"), player);

        List<String> none = TetherCommand.complete(new String[] {""}, perm -> false);
        assertEquals(List.of("help"), none);

        List<String> admin = TetherCommand.complete(new String[] {"admin", ""}, perm -> true);
        assertEquals(List.of("lookup", "unlink", "codes"), admin);
    }

    @Test
    void bridgeCommandMatching_requiresWordBoundary() {
        assertTrue(DiscordBridge.isCommand("!link ABCD", "!link"));
        assertTrue(DiscordBridge.isCommand("!LINK", "!link"));
        assertFalse(DiscordBridge.isCommand("!linkage", "!link"));
        assertFalse(DiscordBridge.isCommand("!unlink", "!link"));
    }

    @Test
    void complete_linkOffersRenew() {
        assertEquals(List.of("renew"), TetherCommand.complete(new String[] {"link", "r"}, perm -> true));
    }
}
