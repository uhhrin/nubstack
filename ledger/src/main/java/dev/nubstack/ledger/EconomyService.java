
package dev.nubstack.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

public final class EconomyService {

    private final DataSource dataSource;

    public EconomyService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement accounts = c.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS ledger_accounts (
                       player_id VARCHAR(36) PRIMARY KEY,
                       balance DECIMAL(20,2) NOT NULL
                     )
                     """);
             PreparedStatement tx = c.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS ledger_transactions (
                       id IDENTITY PRIMARY KEY,
                       player_id VARCHAR(36) NOT NULL,
                       delta DECIMAL(20,2) NOT NULL,
                       reason VARCHAR(128) NOT NULL,
                       created_at TIMESTAMP NOT NULL
                     )
                     """)) {
            accounts.execute();
            tx.execute();
        } catch (SQLException e) {
            // MariaDB identity fallback
            try (Connection c = dataSource.getConnection();
                 PreparedStatement accounts = c.prepareStatement("""
                         CREATE TABLE IF NOT EXISTS ledger_accounts (
                           player_id VARCHAR(36) PRIMARY KEY,
                           balance DECIMAL(20,2) NOT NULL
                         )
                         """);
                 PreparedStatement tx = c.prepareStatement("""
                         CREATE TABLE IF NOT EXISTS ledger_transactions (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           player_id VARCHAR(36) NOT NULL,
                           delta DECIMAL(20,2) NOT NULL,
                           reason VARCHAR(128) NOT NULL,
                           created_at TIMESTAMP NOT NULL
                         )
                         """)) {
                accounts.execute();
                tx.execute();
            } catch (SQLException nested) {
                throw new IllegalStateException(nested);
            }
        }
    }

    public BigDecimal balance(UUID playerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT balance FROM ledger_accounts WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized boolean deposit(UUID playerId, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return apply(playerId, amount, reason);
    }

    public synchronized boolean withdraw(UUID playerId, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (balance(playerId).compareTo(amount) < 0) {
            return false;
        }
        return apply(playerId, amount.negate(), reason);
    }

    public synchronized boolean transfer(UUID from, UUID to, BigDecimal amount, String reason) {
        if (!withdraw(from, amount, reason)) {
            return false;
        }
        deposit(to, amount, reason);
        return true;
    }

    private boolean apply(UUID playerId, BigDecimal delta, String reason) {
        BigDecimal next = balance(playerId).add(delta).setScale(2, RoundingMode.HALF_UP);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement upsert = c.prepareStatement("""
                         DELETE FROM ledger_accounts WHERE player_id = ?
                         """);
                 PreparedStatement insert = c.prepareStatement("""
                         INSERT INTO ledger_accounts(player_id, balance) VALUES (?, ?)
                         """);
                 PreparedStatement tx = c.prepareStatement("""
                         INSERT INTO ledger_transactions(player_id, delta, reason, created_at)
                         VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                         """)) {
                upsert.setString(1, playerId.toString());
                upsert.executeUpdate();
                insert.setString(1, playerId.toString());
                insert.setBigDecimal(2, next);
                insert.executeUpdate();
                tx.setString(1, playerId.toString());
                tx.setBigDecimal(2, delta);
                tx.setString(3, reason);
                tx.executeUpdate();
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
