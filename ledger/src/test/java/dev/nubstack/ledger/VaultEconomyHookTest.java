package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import dev.nubstack.spine.db.DataSources;
import dev.nubstack.spine.db.DatabaseSettings;
import java.util.UUID;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultEconomyHookTest {

    private HikariDataSource ds;
    private VaultEconomyHook hook;

    @BeforeEach
    void setUp() {
        ds = DataSources.create(new DatabaseSettings(
                "jdbc:h2:mem:vault-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "", "ledger", 2));
        EconomyService service = new EconomyService(ds);
        service.init();
        hook = new VaultEconomyHook(service, new CurrencyFormat("Credits"));
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void negativeDepositFailsWithoutThrowing() {
        EconomyResponse response = assertDoesNotThrow(() -> hook.depositUuid(UUID.randomUUID(), -5.0));
        assertEquals(EconomyResponse.ResponseType.FAILURE, response.type);
    }

    @Test
    void overdraftFailsWithoutThrowing() {
        UUID id = UUID.randomUUID();
        assertEquals(EconomyResponse.ResponseType.SUCCESS, hook.depositUuid(id, 10.0).type);
        EconomyResponse response = assertDoesNotThrow(() -> hook.withdrawUuid(id, 25.0));
        assertEquals(EconomyResponse.ResponseType.FAILURE, response.type);
        assertEquals(10.0, response.balance);
    }

    @Test
    void zeroAndNaNAmountsFailWithoutThrowing() {
        UUID id = UUID.randomUUID();
        assertEquals(EconomyResponse.ResponseType.FAILURE, assertDoesNotThrow(() -> hook.depositUuid(id, 0.0)).type);
        assertEquals(EconomyResponse.ResponseType.FAILURE,
                assertDoesNotThrow(() -> hook.depositUuid(id, Double.NaN)).type);
    }
}
