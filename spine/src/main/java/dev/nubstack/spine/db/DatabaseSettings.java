package dev.nubstack.spine.db;

/**
 * JDBC pool settings shared by nubstack plugins.
 */
public record DatabaseSettings(
        String jdbcUrl,
        String username,
        String password,
        String poolName,
        int maximumPoolSize) {

    public DatabaseSettings {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be blank");
        }
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (poolName == null || poolName.isBlank()) {
            throw new IllegalArgumentException("poolName must not be blank");
        }
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be >= 1");
        }
        jdbcUrl = jdbcUrl.trim();
        poolName = poolName.trim();
    }
}
