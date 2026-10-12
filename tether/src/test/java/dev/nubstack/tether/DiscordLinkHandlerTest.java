package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordLinkHandlerTest {

    private record Linked(UUID playerId, String discordId, String tag) {
    }

    private HikariDataSource ds;
    private LinkService links;
    private DiscordLinkHandler handler;
    private final List<Linked> notified = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:handler-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "tether", 2));
        links = new LinkService(ds, 8, 300, 3);
        links.init();
        handler = new DiscordLinkHandler(links, new DiscordLinkHandler.Host() {
            @Override
            public void onLinked(UUID playerId, String discordId, String discordTag) {
                notified.add(new Linked(playerId, discordId, discordTag));
            }

            @Override
            public String playerName(UUID playerId) {
                return "Steve";
            }
        });
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void link_validCodeBindsAndNotifiesHost() {
        UUID player = UUID.randomUUID();
        String code = links.createCode(player);

        String reply = handler.link(code.toLowerCase(), "111111111111111111", "nub");

        assertTrue(reply.contains("Steve"));
        assertEquals(List.of(new Linked(player, "111111111111111111", "nub")), notified);
        assertEquals("111111111111111111", links.discordId(player).orElseThrow());
    }

    @Test
    void link_codeIsSingleUse() {
        String code = links.createCode(UUID.randomUUID());
        handler.link(code, "111111111111111111", "nub");
        String second = handler.link(code, "222222222222222222", "other");

        assertEquals(DiscordText.redeemFailure(LinkService.RedeemStatus.UNKNOWN), second);
        assertEquals(1, notified.size());
    }

    @Test
    void link_unknownCodeLeaksNothingAndNotifiesNobody() {
        String reply = handler.link("ZZZZZZZZ", "111111111111111111", "nub");

        assertEquals(DiscordText.redeemFailure(LinkService.RedeemStatus.UNKNOWN), reply);
        assertTrue(notified.isEmpty());
    }

    @Test
    void link_blankCodeShowsUsage() {
        assertTrue(handler.link("  ", "111111111111111111", "nub").contains("Usage"));
    }

    @Test
    void link_replacingExistingDiscordBindingIsReported() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        handler.link(links.createCode(first), "111111111111111111", "nub");

        String reply = handler.link(links.createCode(second), "111111111111111111", "nub");

        assertTrue(reply.contains("replaced"));
        assertFalse(links.isLinked(first));
        assertTrue(links.isLinked(second));
    }

    @Test
    void unlink_removesLinkForDiscordUser() {
        UUID player = UUID.randomUUID();
        links.link(player, "111111111111111111", "nub");

        String reply = handler.unlink("111111111111111111");

        assertTrue(reply.contains("Unlinked"));
        assertFalse(links.isLinked(player));
    }

    @Test
    void unlink_whenNotLinkedSaysSo() {
        assertTrue(handler.unlink("999999999999999999").contains("not linked"));
    }
}
