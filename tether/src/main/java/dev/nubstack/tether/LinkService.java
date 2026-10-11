
package dev.nubstack.tether;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

public final class LinkService {

    private final DataSource dataSource;
    private final Map<String, UUID> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public LinkService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS tether_links (
                       player_id VARCHAR(36) PRIMARY KEY,
                       discord_id VARCHAR(32) NOT NULL UNIQUE
                     )
                     """)) {
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String createCode(UUID playerId) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        pending.put(code, playerId);
        return code;
    }

    public Optional<UUID> consumeCode(String code) {
        return Optional.ofNullable(pending.remove(code));
    }

    public void link(UUID playerId, String discordId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement del = c.prepareStatement("DELETE FROM tether_links WHERE player_id = ? OR discord_id = ?");
             PreparedStatement ins = c.prepareStatement(
                     "INSERT INTO tether_links(player_id, discord_id) VALUES (?, ?)")) {
            del.setString(1, playerId.toString());
            del.setString(2, discordId);
            del.executeUpdate();
            ins.setString(1, playerId.toString());
            ins.setString(2, discordId);
            ins.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> discordId(UUID playerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT discord_id FROM tether_links WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isLinked(UUID playerId) {
        return discordId(playerId).isPresent();
    }
}
