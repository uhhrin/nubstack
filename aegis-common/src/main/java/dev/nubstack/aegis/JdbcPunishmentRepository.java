package dev.nubstack.aegis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPunishmentRepository implements PunishmentRepository {

    private final DataSource dataSource;

    public JdbcPunishmentRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void initSchema() {
        String table = """
                CREATE TABLE IF NOT EXISTS aegis_punishments (
                  id VARCHAR(36) PRIMARY KEY,
                  target_id VARCHAR(36) NOT NULL,
                  target_name VARCHAR(16) NOT NULL,
                  type VARCHAR(16) NOT NULL,
                  reason VARCHAR(512) NOT NULL,
                  staff_id VARCHAR(36) NOT NULL,
                  staff_name VARCHAR(16) NOT NULL,
                  created_at TIMESTAMP NOT NULL,
                  expires_at TIMESTAMP NULL,
                  active BOOLEAN NOT NULL,
                  revoked_at TIMESTAMP NULL,
                  revoked_by VARCHAR(36) NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(table)) {
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize aegis schema", exception);
        }
        // Idempotent indexes for history/active lookups (H2 + MariaDB).
        executeIgnore("CREATE INDEX IF NOT EXISTS idx_aegis_target ON aegis_punishments (target_id)");
        executeIgnore(
                "CREATE INDEX IF NOT EXISTS idx_aegis_active_expires ON aegis_punishments (active, expires_at)");
    }

    private void executeIgnore(String sql) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException exception) {
            // shortcut: some JDBC drivers reject IF NOT EXISTS on indexes; table create still works
            throw new IllegalStateException("Failed to ensure aegis index: " + sql, exception);
        }
    }

    @Override
    public void insert(Punishment punishment) {
        String sql = """
                INSERT INTO aegis_punishments (
                  id, target_id, target_name, type, reason, staff_id, staff_name,
                  created_at, expires_at, active, revoked_at, revoked_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, punishment.id().toString());
            statement.setString(2, punishment.targetId().toString());
            statement.setString(3, punishment.targetName());
            statement.setString(4, punishment.type().name());
            statement.setString(5, punishment.reason());
            statement.setString(6, punishment.staffId().toString());
            statement.setString(7, punishment.staffName());
            statement.setTimestamp(8, Timestamp.from(punishment.createdAt()));
            if (punishment.expiresAt() == null) {
                statement.setNull(9, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(9, Timestamp.from(punishment.expiresAt()));
            }
            statement.setBoolean(10, punishment.active());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to insert punishment", exception);
        }
    }

    @Override
    public boolean deactivate(UUID targetId, PunishmentType type, UUID staffId) {
        String sql = """
                UPDATE aegis_punishments
                SET active = FALSE, revoked_at = ?, revoked_by = ?
                WHERE target_id = ? AND type = ? AND active = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, staffId.toString());
            statement.setString(3, targetId.toString());
            statement.setString(4, type.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to deactivate punishment", exception);
        }
    }

    @Override
    public boolean deactivateById(UUID punishmentId, UUID staffId) {
        String sql = """
                UPDATE aegis_punishments
                SET active = FALSE, revoked_at = ?, revoked_by = ?
                WHERE id = ? AND active = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, staffId.toString());
            statement.setString(3, punishmentId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to deactivate punishment by id", exception);
        }
    }

    @Override
    public Optional<Punishment> findById(UUID punishmentId) {
        String sql = "SELECT * FROM aegis_punishments WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, punishmentId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query punishment by id", exception);
        }
    }

    @Override
    public Optional<Punishment> findActive(UUID targetId, PunishmentType type) {
        String sql = """
                SELECT * FROM aegis_punishments
                WHERE target_id = ? AND type = ? AND active = TRUE
                ORDER BY created_at DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetId.toString());
            statement.setString(2, type.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query active punishment", exception);
        }
    }

    @Override
    public List<Punishment> history(UUID targetId, int limit, int offset) {
        String sql = """
                SELECT * FROM aegis_punishments
                WHERE target_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetId.toString());
            statement.setInt(2, limit);
            statement.setInt(3, Math.max(0, offset));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Punishment> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query punishment history", exception);
        }
    }

    @Override
    public List<Punishment> findActiveForTarget(UUID targetId) {
        String sql = """
                SELECT * FROM aegis_punishments
                WHERE target_id = ? AND active = TRUE
                ORDER BY created_at DESC
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Punishment> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query active punishments for target", exception);
        }
    }

    @Override
    public List<Punishment> findAllActive() {
        String sql = "SELECT * FROM aegis_punishments WHERE active = TRUE";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<Punishment> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(mapRow(resultSet));
            }
            return List.copyOf(rows);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query active punishments", exception);
        }
    }

    private static Punishment mapRow(ResultSet resultSet) throws SQLException {
        Timestamp expires = resultSet.getTimestamp("expires_at");
        return new Punishment(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("target_id")),
                resultSet.getString("target_name"),
                PunishmentType.valueOf(resultSet.getString("type")),
                resultSet.getString("reason"),
                UUID.fromString(resultSet.getString("staff_id")),
                resultSet.getString("staff_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                expires == null ? null : expires.toInstant(),
                resultSet.getBoolean("active"));
    }
}
