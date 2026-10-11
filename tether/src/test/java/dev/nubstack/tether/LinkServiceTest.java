
package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkServiceTest {
    private HikariDataSource ds;
    private LinkService links;

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:tether-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "tether", 2));
        links = new LinkService(ds);
        links.init();
    }

    @AfterEach
    void tearDown() { ds.close(); }

    @Test
    void linksViaCode() {
        UUID player = UUID.randomUUID();
        String code = links.createCode(player);
        assertEquals(player, links.consumeCode(code).orElseThrow());
        links.link(player, "1234567890");
        assertTrue(links.isLinked(player));
        assertEquals("1234567890", links.discordId(player).orElseThrow());
    }
}
