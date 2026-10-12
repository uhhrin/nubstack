package dev.nubstack.loom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC player inventory store with cross-server leases and versioned blobs.
 * Every lease or blob change runs in one transaction under a row lock.
 */
public final class PlayerDataStore {

    private final DataSource dataSource;
    private final Clock clock;

    public PlayerDataStore(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    public PlayerDataStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void init() {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, """
                    CREATE TABLE IF NOT EXISTS loom_player_data (
                      player_id VARCHAR(36) PRIMARY KEY,
                      inventory_blob LONGTEXT NOT NULL,
                      updated_at TIMESTAMP NOT NULL
                    )
                    """);
            // Tables created by the first loom release lack the lease columns.
            execute(connection, "ALTER TABLE loom_player_data ADD COLUMN IF NOT EXISTS locked_by VARCHAR(64) NULL");
            execute(connection, "ALTER TABLE loom_player_data ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP NULL");
            execute(connection,
                    "ALTER TABLE loom_player_data ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0");
            execute(connection, """
                    CREATE TABLE IF NOT EXISTS loom_audit (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      player_id VARCHAR(36) NOT NULL,
                      event_type VARCHAR(32) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      detail VARCHAR(255),
                      created_at TIMESTAMP NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Takes or extends the lease for {@code serverId}.
     *
     * @return false while a different server holds an unexpired lease
     */
    public boolean tryAcquire(UUID playerId, String serverId, Duration leaseTtl) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        return inTransaction(connection -> {
            Instant now = clock.instant();
            ensureRow(connection, playerId, now);
            Snapshot snap = selectForUpdate(connection, playerId);
            if (isHeldByOther(snap, serverId, now)) {
                return false;
            }
            setLease(connection, playerId, serverId, now.plus(leaseTtl));
            return true;
        });
    }

    /**
     * Extends a lease this server already holds.
     *
     * @return false when another server took over or the row is gone
     */
    public boolean renew(UUID playerId, String serverId, Duration leaseTtl) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        return inTransaction(connection -> {
            Instant now = clock.instant();
            Optional<Snapshot> snap = findForUpdate(connection, playerId);
            if (snap.isEmpty() || !serverId.equals(snap.get().lockedBy())) {
                return false;
            }
            setLease(connection, playerId, serverId, now.plus(leaseTtl));
            return true;
        });
    }

    /**
     * Clears the lease without touching the blob, for sessions that never loaded data.
     *
     * @return true when this server held the lease
     */
    public boolean release(UUID playerId, String serverId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(serverId, "serverId");
        return inTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE loom_player_data SET locked_by = NULL, locked_until = NULL
                    WHERE player_id = ? AND locked_by = ?
                    """)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, serverId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    /**
     * Writes the blob, bumps the version, and clears the lease in one transaction.
     *
     * @return false when another server holds an unexpired lease
     */
    public boolean saveAndRelease(UUID playerId, String serverId, String inventoryBlob) {
        return write(playerId, serverId, inventoryBlob, null);
    }

    /**
     * Writes the blob and bumps the version but keeps (renews) the lease, for saves while the player is online.
     *
     * @return false when another server holds an unexpired lease
     */
    public boolean saveKeepingLease(UUID playerId, String serverId, String inventoryBlob, Duration leaseTtl) {
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        return write(playerId, serverId, inventoryBlob, leaseTtl);
    }

    private boolean write(UUID playerId, String serverId, String inventoryBlob, Duration keepLeaseFor) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(inventoryBlob, "inventoryBlob");
        return inTransaction(connection -> {
            Instant now = clock.instant();
            ensureRow(connection, playerId, now);
            Snapshot snap = selectForUpdate(connection, playerId);
            if (isHeldByOther(snap, serverId, now)) {
                return false;
            }
            boolean keep = keepLeaseFor != null;
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE loom_player_data
                    SET inventory_blob = ?, updated_at = ?, locked_by = ?, locked_until = ?, version = version + 1
                    WHERE player_id = ?
                    """)) {
                update.setString(1, inventoryBlob);
                update.setTimestamp(2, Timestamp.from(now));
                update.setString(3, keep ? serverId : null);
                update.setTimestamp(4, keep ? Timestamp.from(now.plus(keepLeaseFor)) : null);
                update.setString(5, playerId.toString());
                update.executeUpdate();
            }
            audit(connection, "save", playerId, serverId, "bytes=" + inventoryBlob.length());
            return true;
        });
    }

    public Optional<Snapshot> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readSnapshot(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clears any lease and records an {@code unlock} audit row. */
    public void forceUnlock(UUID playerId, String actor) {
        Objects.requireNonNull(playerId, "playerId");
        String who = actor == null || actor.isBlank() ? "unknown" : actor;
        inTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE loom_player_data SET locked_by = NULL, locked_until = NULL WHERE player_id = ?
                    """)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            }
            audit(connection, "unlock", playerId, who, "forced");
            return null;
        });
    }

    public void audit(String eventType, UUID playerId, String serverId, String detail) {
        inTransaction(connection -> {
            audit(connection, eventType, playerId, serverId, detail);
            return null;
        });
    }

    public List<AuditEntry> recentAudit(UUID playerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT event_type, server_id, detail, created_at
                     FROM loom_audit WHERE player_id = ? ORDER BY id DESC LIMIT ?
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditEntry> rows = new ArrayList<>();
                while (rs.next()) {
                    Timestamp created = rs.getTimestamp(4);
                    rows.add(new AuditEntry(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            created == null ? Instant.EPOCH : created.toInstant()));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String SELECT = """
            SELECT player_id, inventory_blob, updated_at, locked_by, locked_until, version
            FROM loom_player_data""";

    @FunctionalInterface
    private interface TxBody<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T inTransaction(TxBody<T> body) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = body.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(Connection connection, String eventType, UUID playerId, String serverId, String detail)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO loom_audit (player_id, event_type, server_id, detail, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, eventType);
            ps.setString(3, serverId);
            ps.setString(4, detail == null ? "" : detail);
            ps.setTimestamp(5, Timestamp.from(clock.instant()));
            ps.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static void ensureRow(Connection connection, UUID playerId, Instant now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO loom_player_data (player_id, inventory_blob, updated_at, version)
                VALUES (?, '', ?, 0)
                """)) {
            ps.setString(1, playerId.toString());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            String state = e.getSQLState();
            if (state == null || !state.startsWith("23")) {
                throw e;
            }
            // row already exists
        }
    }

    private static void setLease(Connection connection, UUID playerId, String serverId, Instant until)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE loom_player_data SET locked_by = ?, locked_until = ? WHERE player_id = ?
                """)) {
            ps.setString(1, serverId);
            ps.setTimestamp(2, Timestamp.from(until));
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    private static Snapshot selectForUpdate(Connection connection, UUID playerId) throws SQLException {
        return findForUpdate(connection, playerId).orElseThrow();
    }

    private static Optional<Snapshot> findForUpdate(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE player_id = ? FOR UPDATE")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readSnapshot(rs)) : Optional.empty();
            }
        }
    }

    private static boolean isHeldByOther(Snapshot snap, String serverId, Instant now) {
        String holder = snap.lockedBy();
        return holder != null
                && !holder.isBlank()
                && !holder.equals(serverId)
                && snap.lockedUntil() != null
                && snap.lockedUntil().isAfter(now);
    }

    private static Snapshot readSnapshot(ResultSet rs) throws SQLException {
        String blob = rs.getString(2);
        Timestamp updated = rs.getTimestamp(3);
        Timestamp lockedUntil = rs.getTimestamp(5);
        return new Snapshot(
                UUID.fromString(rs.getString(1)),
                blob == null ? "" : blob,
                updated == null ? Instant.EPOCH : updated.toInstant(),
                rs.getString(4),
                lockedUntil == null ? null : lockedUntil.toInstant(),
                rs.getLong(6));
    }

    public record Snapshot(
            UUID playerId,
            String inventoryBlob,
            Instant updatedAt,
            String lockedBy,
            Instant lockedUntil,
            long version) {

        /** Stored text size in bytes (the blob is ASCII base64). */
        public int blobBytes() {
            return inventoryBlob.length();
        }

        public boolean hasInventory() {
            return !inventoryBlob.isBlank();
        }

        public boolean isLeased(Instant now) {
            return lockedBy != null && !lockedBy.isBlank() && lockedUntil != null && lockedUntil.isAfter(now);
        }
    }

    public record AuditEntry(String eventType, String serverId, String detail, Instant createdAt) {
    }
}
