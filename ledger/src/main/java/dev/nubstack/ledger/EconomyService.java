package dev.nubstack.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class EconomyService {

    public static final int SCHEMA_VERSION = 1;

    private final DataSource dataSource;

    public EconomyService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void init() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // AUTO_INCREMENT works on MariaDB and H2 MySQL mode (test JDBC URL).
                try (Statement st = c.createStatement()) {
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS ledger_schema (
                              id INT PRIMARY KEY,
                              version INT NOT NULL
                            )
                            """);
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS ledger_accounts (
                              player_id VARCHAR(36) PRIMARY KEY,
                              balance DECIMAL(20,2) NOT NULL
                            )
                            """);
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS ledger_transactions (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              player_id VARCHAR(36) NOT NULL,
                              delta DECIMAL(20,2) NOT NULL,
                              reason VARCHAR(128) NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            )
                            """);
                }
                boolean hasRow;
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT version FROM ledger_schema WHERE id = 1");
                     ResultSet rs = check.executeQuery()) {
                    hasRow = rs.next();
                }
                if (!hasRow) {
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO ledger_schema (id, version) VALUES (1, ?)")) {
                        insert.setInt(1, SCHEMA_VERSION);
                        insert.executeUpdate();
                    }
                } else {
                    try (PreparedStatement update = c.prepareStatement(
                            "UPDATE ledger_schema SET version = ? WHERE id = 1")) {
                        update.setInt(1, SCHEMA_VERSION);
                        update.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize ledger schema", e);
        }
    }

    public int schemaVersion() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT version FROM ledger_schema WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("ledger_schema row missing");
            }
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public BigDecimal balance(UUID playerId) {
        try (Connection c = dataSource.getConnection()) {
            return balance(c, playerId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long deposit(UUID playerId, BigDecimal amount, String reason) {
        requirePositive(amount);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureAccount(c, playerId);
                BigDecimal current = lockBalance(c, playerId);
                BigDecimal next = current.add(amount).setScale(2, RoundingMode.HALF_UP);
                updateBalance(c, playerId, next);
                long id = insertTx(c, playerId, amount, reason);
                c.commit();
                return id;
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

    public OptionalLongResult withdraw(UUID playerId, BigDecimal amount, String reason) {
        requirePositive(amount);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureAccount(c, playerId);
                BigDecimal current = lockBalance(c, playerId);
                if (current.compareTo(amount) < 0) {
                    c.rollback();
                    return OptionalLongResult.fail();
                }
                BigDecimal next = current.subtract(amount).setScale(2, RoundingMode.HALF_UP);
                updateBalance(c, playerId, next);
                long id = insertTx(c, playerId, amount.negate(), reason);
                c.commit();
                return OptionalLongResult.ok(id);
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

    /**
     * Locks both accounts in fixed UUID order inside one SQL transaction.
     */
    public TransferResult transfer(UUID from, UUID to, BigDecimal amount, String reason) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot transfer to yourself");
        }
        requirePositive(amount);
        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = from.compareTo(to) < 0 ? to : from;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureAccount(c, first);
                ensureAccount(c, second);
                BigDecimal balFirst = lockBalance(c, first);
                BigDecimal balSecond = lockBalance(c, second);
                BigDecimal fromBal = from.equals(first) ? balFirst : balSecond;
                BigDecimal toBal = to.equals(first) ? balFirst : balSecond;
                if (fromBal.compareTo(amount) < 0) {
                    c.rollback();
                    return TransferResult.insufficient(amount.subtract(fromBal).setScale(2, RoundingMode.HALF_UP));
                }
                updateBalance(c, from, fromBal.subtract(amount).setScale(2, RoundingMode.HALF_UP));
                updateBalance(c, to, toBal.add(amount).setScale(2, RoundingMode.HALF_UP));
                long fromTx = insertTx(c, from, amount.negate(), reason);
                long toTx = insertTx(c, to, amount, reason);
                c.commit();
                return TransferResult.ok(fromTx, toTx);
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

    public long setBalance(UUID playerId, BigDecimal amount, String reason) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        BigDecimal target = amount.setScale(2, RoundingMode.HALF_UP);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ensureAccount(c, playerId);
                BigDecimal current = lockBalance(c, playerId);
                BigDecimal delta = target.subtract(current).setScale(2, RoundingMode.HALF_UP);
                updateBalance(c, playerId, target);
                long id = insertTx(c, playerId, delta, reason);
                c.commit();
                return id;
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

    public List<TxRow> history(UUID playerId, int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        int offset = (page - 1) * pageSize;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, player_id, delta, reason, created_at
                     FROM ledger_transactions
                     WHERE player_id = ?
                     ORDER BY id DESC
                     LIMIT ? OFFSET ?
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<TxRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(readTx(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<TxRow> receipt(long id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT id, player_id, delta, reason, created_at
                     FROM ledger_transactions WHERE id = ?
                     """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readTx(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<TopEntry> top(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT player_id, balance FROM ledger_accounts
                     ORDER BY balance DESC
                     LIMIT ?
                     """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TopEntry> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new TopEntry(
                            UUID.fromString(rs.getString(1)),
                            rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP)));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static BigDecimal balance(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT balance FROM ledger_accounts WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    private static void ensureAccount(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement check = c.prepareStatement(
                "SELECT 1 FROM ledger_accounts WHERE player_id = ?")) {
            check.setString(1, playerId.toString());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO ledger_accounts(player_id, balance) VALUES (?, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setBigDecimal(2, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            insert.executeUpdate();
        } catch (SQLException duplicate) {
            // Concurrent create; row exists for FOR UPDATE.
        }
    }

    private static BigDecimal lockBalance(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT balance FROM ledger_accounts WHERE player_id = ? FOR UPDATE")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Missing account " + playerId);
                }
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    private static void updateBalance(Connection c, UUID playerId, BigDecimal next) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ledger_accounts SET balance = ? WHERE player_id = ?")) {
            ps.setBigDecimal(1, next);
            ps.setString(2, playerId.toString());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Failed to update balance for " + playerId);
            }
        }
    }

    private static long insertTx(Connection c, UUID playerId, BigDecimal delta, String reason)
            throws SQLException {
        String safeReason = reason == null || reason.isBlank() ? "unspecified" : reason;
        if (safeReason.length() > 128) {
            safeReason = safeReason.substring(0, 128);
        }
        try (PreparedStatement tx = c.prepareStatement("""
                INSERT INTO ledger_transactions(player_id, delta, reason, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            tx.setString(1, playerId.toString());
            tx.setBigDecimal(2, delta);
            tx.setString(3, safeReason);
            tx.executeUpdate();
            try (ResultSet keys = tx.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("No transaction id generated");
                }
                return keys.getLong(1);
            }
        }
    }

    private static TxRow readTx(ResultSet rs) throws SQLException {
        return new TxRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_id")),
                rs.getBigDecimal("delta").setScale(2, RoundingMode.HALF_UP),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record TxRow(long id, UUID playerId, BigDecimal delta, String reason, java.time.Instant createdAt) {
    }

    public record TopEntry(UUID playerId, BigDecimal balance) {
    }

    public record TransferResult(boolean success, long fromTxId, long toTxId, BigDecimal shortfall) {
        public static TransferResult ok(long fromTxId, long toTxId) {
            return new TransferResult(true, fromTxId, toTxId, BigDecimal.ZERO);
        }

        public static TransferResult insufficient(BigDecimal shortfall) {
            return new TransferResult(false, -1L, -1L, shortfall);
        }
    }

    public record OptionalLongResult(boolean success, long id) {
        public static OptionalLongResult ok(long id) {
            return new OptionalLongResult(true, id);
        }

        public static OptionalLongResult fail() {
            return new OptionalLongResult(false, -1L);
        }
    }
}
