package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.math.BigDecimal;
import java.util.List;
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void transferMovesFundsAndWritesTwoRows() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        economy.deposit(a, money("20.00"), "start");
        EconomyService.TransferResult result = economy.transfer(a, b, money("7.50"), "pay");
        assertTrue(result.success());
        assertEquals(money("12.50"), economy.balance(a));
        assertEquals(money("7.50"), economy.balance(b));
        assertEquals(money("-7.50"), economy.receipt(result.fromTxId()).orElseThrow().delta());
        assertEquals(money("7.50"), economy.receipt(result.toTxId()).orElseThrow().delta());
    }

    @Test
    void transferWithInsufficientFundsLeavesBothBalancesUnchanged() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        economy.deposit(a, money("10.00"), "start");
        economy.deposit(b, money("3.00"), "start");
        EconomyService.TransferResult result = economy.transfer(a, b, money("50.00"), "pay");
        assertFalse(result.success());
        assertEquals(money("40.00"), result.shortfall());
        assertEquals(money("10.00"), economy.balance(a));
        assertEquals(money("3.00"), economy.balance(b));
        assertEquals(1, economy.history(a, 1, 10).size());
        assertEquals(1, economy.history(b, 1, 10).size());
    }

    @Test
    void transferToSelfIsRejected() {
        UUID a = UUID.randomUUID();
        economy.deposit(a, money("10.00"), "start");
        assertThrows(IllegalArgumentException.class, () -> economy.transfer(a, a, money("1.00"), "pay"));
        assertEquals(money("10.00"), economy.balance(a));
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> economy.deposit(a, money("-1.00"), "x"));
        assertThrows(IllegalArgumentException.class, () -> economy.withdraw(a, BigDecimal.ZERO, "x"));
        assertThrows(IllegalArgumentException.class, () -> economy.transfer(a, b, money("-5.00"), "x"));
    }

    @Test
    void withdrawFailsWhenShort() {
        UUID a = UUID.randomUUID();
        economy.deposit(a, money("5.00"), "start");
        assertFalse(economy.withdraw(a, money("100.00"), "nope").success());
        assertTrue(economy.withdraw(a, money("2.00"), "take").success());
        assertEquals(money("3.00"), economy.balance(a));
    }

    @Test
    void depositNeedsNoPlayerContext() {
        UUID console = UUID.randomUUID();
        long receipt = economy.deposit(console, money("1000.00"), "admin-give");
        assertEquals("admin-give", economy.receipt(receipt).orElseThrow().reason());
        assertEquals(money("1000.00"), economy.balance(console));
    }

    @Test
    void setBalanceRecordsDeltaAndAllowsZero() {
        UUID a = UUID.randomUUID();
        economy.deposit(a, money("30.00"), "start");
        long id = economy.setBalance(a, BigDecimal.ZERO, "admin-set");
        assertEquals(money("0.00"), economy.balance(a));
        assertEquals(money("-30.00"), economy.receipt(id).orElseThrow().delta());
        assertThrows(IllegalArgumentException.class, () -> economy.setBalance(a, money("-1"), "x"));
    }

    @Test
    void historyIsNewestFirstAndPaged() {
        UUID a = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            economy.deposit(a, money(i + ".00"), "n" + i);
        }
        List<EconomyService.TxRow> first = economy.history(a, 1, 2);
        List<EconomyService.TxRow> last = economy.history(a, 3, 2);
        assertEquals(List.of("n5", "n4"), first.stream().map(EconomyService.TxRow::reason).toList());
        assertEquals(List.of("n1"), last.stream().map(EconomyService.TxRow::reason).toList());
        assertThrows(IllegalArgumentException.class, () -> economy.history(a, 0, 2));
    }

    @Test
    void receiptMissingIsEmptyAndBlankReasonGetsDefault() {
        assertTrue(economy.receipt(9999L).isEmpty());
        UUID a = UUID.randomUUID();
        long id = economy.deposit(a, money("1.00"), "  ");
        assertEquals("unspecified", economy.receipt(id).orElseThrow().reason());
    }

    @Test
    void topOrdersByBalanceDescending() {
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        economy.deposit(low, money("5.00"), "x");
        economy.deposit(high, money("500.00"), "x");
        List<EconomyService.TopEntry> top = economy.top(1);
        assertEquals(1, top.size());
        assertEquals(high, top.get(0).playerId());
        assertThrows(IllegalArgumentException.class, () -> economy.top(0));
    }

    @Test
    void initIsIdempotentAndStoresSchemaVersion() {
        UUID a = UUID.randomUUID();
        economy.deposit(a, money("4.00"), "x");
        economy.init();
        assertEquals(EconomyService.SCHEMA_VERSION, economy.schemaVersion());
        assertEquals(money("4.00"), economy.balance(a));
    }
}
