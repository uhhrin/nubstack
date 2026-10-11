
package dev.nubstack.loom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PlayerDataStore {

    private final DataSource dataSource;

    public PlayerDataStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS loom_player_data (
                       player_id VARCHAR(36) PRIMARY KEY,
                       inventory_blob LONGTEXT NOT NULL,
                       updated_at TIMESTAMP NOT NULL
                     )
                     """)) {
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(UUID playerId, String inventoryBlob) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     MERGE INTO loom_player_data (player_id, inventory_blob, updated_at)
                     KEY (player_id)
                     VALUES (?, ?, CURRENT_TIMESTAMP)
                     """)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, inventoryBlob);
            ps.executeUpdate();
        } catch (SQLException e) {
            // H2 MERGE / MariaDB upsert fallback
            try (Connection c = dataSource.getConnection();
                 PreparedStatement del = c.prepareStatement("DELETE FROM loom_player_data WHERE player_id = ?");
                 PreparedStatement ins = c.prepareStatement(
                         "INSERT INTO loom_player_data(player_id, inventory_blob, updated_at) VALUES (?,?,CURRENT_TIMESTAMP)")) {
                del.setString(1, playerId.toString());
                del.executeUpdate();
                ins.setString(1, playerId.toString());
                ins.setString(2, inventoryBlob);
                ins.executeUpdate();
            } catch (SQLException nested) {
                throw new IllegalStateException(nested);
            }
        }
    }

    public Optional<String> load(UUID playerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT inventory_blob FROM loom_player_data WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
