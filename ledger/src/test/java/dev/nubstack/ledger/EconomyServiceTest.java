
package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EconomyServiceTest {
    private HikariDataSource ds;
    private EconomyService economy;

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:ledger-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "ledger", 2));
        economy = new EconomyService(ds);
        economy.init();
    }

    @AfterEach
    void tearDown() { ds.close(); }

    @Test
    void transferMovesFunds() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        economy.deposit(a, new BigDecimal("20.00"), "start");
        assertTrue(economy.transfer(a, b, new BigDecimal("7.50"), "pay"));
        assertEquals(new BigDecimal("12.50"), economy.balance(a));
        assertEquals(new BigDecimal("7.50"), economy.balance(b));
        assertFalse(economy.withdraw(a, new BigDecimal("100.00"), "nope"));
    }
}
