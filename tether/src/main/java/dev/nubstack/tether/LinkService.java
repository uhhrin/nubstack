package dev.nubstack.tether;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class LinkService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public enum RedeemStatus {
        OK,
        UNKNOWN,
        EXPIRED,
        LOCKED
    }

    public record RedeemResult(RedeemStatus status, UUID playerId) {
        public static RedeemResult ok(UUID playerId) {
            return new RedeemResult(RedeemStatus.OK, Objects.requireNonNull(playerId, "playerId"));
        }

        public static RedeemResult fail(RedeemStatus status) {
            if (status == RedeemStatus.OK) {
                throw new IllegalArgumentException("OK requires playerId");
            }
            return new RedeemResult(status, null);
        }

        public boolean success() {
            return status == RedeemStatus.OK;
        }
    }

    public record LinkRow(UUID playerId, String discordId, String discordTag, Instant linkedAt) {
    }

    public record PendingCode(String code, UUID playerId, Instant expiresAt, int attempts) {
        public long remainingSeconds(Clock clock) {
            long left = expiresAt.getEpochSecond() - clock.instant().getEpochSecond();
            return Math.max(0L, left);
        }
    }

    private final DataSource dataSource;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final int codeLength;
    private final int ttlSeconds;
    private final int maxRedeemAttempts;

    public LinkService(DataSource dataSource, int codeLength, int ttlSeconds, int maxRedeemAttempts) {
        this(dataSource, Clock.systemUTC(), codeLength, ttlSeconds, maxRedeemAttempts);
    }

    public LinkService(DataSource dataSource, Clock clock, int codeLength, int ttlSeconds, int maxRedeemAttempts) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (codeLength < 4 || codeLength > 16) {
            throw new IllegalArgumentException("codeLength must be 4..16");
        }
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        if (maxRedeemAttempts < 1) {
            throw new IllegalArgumentException("maxRedeemAttempts must be positive");
        }
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.maxRedeemAttempts = maxRedeemAttempts;
    }

    public void init() {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement links = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS tether_links (
                      player_id VARCHAR(36) PRIMARY KEY,
                      discord_id VARCHAR(32) NOT NULL UNIQUE,
                      discord_tag VARCHAR(64),
                      linked_at BIGINT NOT NULL
                    )
                    """)) {
                links.execute();
            }
            try (PreparedStatement codes = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS tether_codes (
                      code VARCHAR(32) PRIMARY KEY,
                      player_id VARCHAR(36) NOT NULL UNIQUE,
                      expires_at BIGINT NOT NULL,
                      attempts INT NOT NULL DEFAULT 0
                    )
                    """)) {
                codes.execute();
            }
            migrateLegacyLinks(c);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void migrateLegacyLinks(Connection c) throws SQLException {
        // shortcut: only adds columns when absent; drop once all installs use the new schema
        try (PreparedStatement probe = c.prepareStatement("SELECT discord_tag, linked_at FROM tether_links WHERE 1=0")) {
            probe.executeQuery();
        } catch (SQLException ignored) {
            try (PreparedStatement alterTag = c.prepareStatement(
                    "ALTER TABLE tether_links ADD COLUMN IF NOT EXISTS discord_tag VARCHAR(64)");
                 PreparedStatement alterAt = c.prepareStatement(
                         "ALTER TABLE tether_links ADD COLUMN IF NOT EXISTS linked_at BIGINT")) {
                alterTag.execute();
                alterAt.execute();
            } catch (SQLException alterFailed) {
                // H2 / MariaDB dialect differences: ignore if columns already exist via CREATE
            }
        }
    }

    public String createCode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        purgeExpired();
        cancelCode(playerId);
        String code = generateCode();
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tether_codes(code, player_id, expires_at, attempts) VALUES (?, ?, ?, 0)")) {
            ps.setString(1, code);
            ps.setString(2, playerId.toString());
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
            return code;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<PendingCode> pendingCode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        purgeExpired();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT code, player_id, expires_at, attempts FROM tether_codes WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapPending(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean cancelCode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tether_codes WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public RedeemResult redeem(String rawCode) {
        Objects.requireNonNull(rawCode, "rawCode");
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            return RedeemResult.fail(RedeemStatus.UNKNOWN);
        }
        // no purge first: an expired row must still be found so the caller can say EXPIRED
        try (Connection c = dataSource.getConnection()) {
            PendingCode pending;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT code, player_id, expires_at, attempts FROM tether_codes WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return RedeemResult.fail(RedeemStatus.UNKNOWN);
                    }
                    pending = mapPending(rs);
                }
            }
            long now = clock.instant().getEpochSecond();
            if (pending.expiresAt().getEpochSecond() <= now) {
                deleteCode(c, code);
                return RedeemResult.fail(RedeemStatus.EXPIRED);
            }
            if (pending.attempts() >= maxRedeemAttempts) {
                deleteCode(c, code);
                return RedeemResult.fail(RedeemStatus.LOCKED);
            }
            // Successful redeem consumes the code. Failed attempts bump the counter elsewhere
            // only when callers report a wrong-code path; here presence of the row is the check.
            deleteCode(c, code);
            return RedeemResult.ok(pending.playerId());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Records a failed redeem attempt for an existing code (wrong channel misuse / guessing).
     */
    public RedeemResult noteFailedAttempt(String rawCode) {
        Objects.requireNonNull(rawCode, "rawCode");
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            return RedeemResult.fail(RedeemStatus.UNKNOWN);
        }
        try (Connection c = dataSource.getConnection()) {
            PendingCode pending;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT code, player_id, expires_at, attempts FROM tether_codes WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return RedeemResult.fail(RedeemStatus.UNKNOWN);
                    }
                    pending = mapPending(rs);
                }
            }
            long now = clock.instant().getEpochSecond();
            if (pending.expiresAt().getEpochSecond() <= now) {
                deleteCode(c, code);
                return RedeemResult.fail(RedeemStatus.EXPIRED);
            }
            int next = pending.attempts() + 1;
            if (next >= maxRedeemAttempts) {
                deleteCode(c, code);
                return RedeemResult.fail(RedeemStatus.LOCKED);
            }
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE tether_codes SET attempts = ? WHERE code = ?")) {
                upd.setInt(1, next);
                upd.setString(2, code);
                upd.executeUpdate();
            }
            return RedeemResult.fail(RedeemStatus.UNKNOWN);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @deprecated use {@link #redeem(String)} */
    @Deprecated
    public Optional<UUID> consumeCode(String code) {
        RedeemResult result = redeem(code);
        return result.success() ? Optional.of(result.playerId()) : Optional.empty();
    }

    public void link(UUID playerId, String discordId) {
        link(playerId, discordId, null);
    }

    public void link(UUID playerId, String discordId, String discordTag) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(discordId, "discordId");
        long linkedAt = clock.instant().getEpochSecond();
        try (Connection c = dataSource.getConnection();
             PreparedStatement del = c.prepareStatement(
                     "DELETE FROM tether_links WHERE player_id = ? OR discord_id = ?");
             PreparedStatement ins = c.prepareStatement(
                     "INSERT INTO tether_links(player_id, discord_id, discord_tag, linked_at) VALUES (?, ?, ?, ?)")) {
            del.setString(1, playerId.toString());
            del.setString(2, discordId);
            del.executeUpdate();
            ins.setString(1, playerId.toString());
            ins.setString(2, discordId);
            ins.setString(3, discordTag);
            ins.setLong(4, linkedAt);
            ins.executeUpdate();
            cancelCode(playerId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean unlink(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tether_links WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<LinkRow> linkByPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_id, discord_id, discord_tag, linked_at FROM tether_links WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapLink(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<LinkRow> linkByDiscordId(String discordId) {
        Objects.requireNonNull(discordId, "discordId");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT player_id, discord_id, discord_tag, linked_at FROM tether_links WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapLink(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> discordId(UUID playerId) {
        return linkByPlayer(playerId).map(LinkRow::discordId);
    }

    public boolean isLinked(UUID playerId) {
        return linkByPlayer(playerId).isPresent();
    }

    public int purgeExpired() {
        long now = clock.instant().getEpochSecond();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tether_codes WHERE expires_at <= ?")) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int pendingCodeCount() {
        purgeExpired();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM tether_codes");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<PendingCode> listPendingCodes(int limit) {
        purgeExpired();
        int capped = Math.max(1, Math.min(limit, 100));
        List<PendingCode> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT code, player_id, expires_at, attempts FROM tether_codes ORDER BY expires_at ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && out.size() < capped) {
                    out.add(mapPending(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String generateCode() {
        char[] chars = new char[codeLength];
        for (int i = 0; i < codeLength; i++) {
            chars[i] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
        }
        return new String(chars);
    }

    private static void deleteCode(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM tether_codes WHERE code = ?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    private static PendingCode mapPending(ResultSet rs) throws SQLException {
        return new PendingCode(
                rs.getString("code"),
                UUID.fromString(rs.getString("player_id")),
                Instant.ofEpochSecond(rs.getLong("expires_at")),
                rs.getInt("attempts"));
    }

    private static LinkRow mapLink(ResultSet rs) throws SQLException {
        long linkedAt = rs.getLong("linked_at");
        if (rs.wasNull()) {
            linkedAt = 0L;
        }
        return new LinkRow(
                UUID.fromString(rs.getString("player_id")),
                rs.getString("discord_id"),
                rs.getString("discord_tag"),
                Instant.ofEpochSecond(linkedAt));
    }
}
