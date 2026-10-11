package dev.nubstack.spine.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;

/**
 * Creates HikariCP pools from validated settings.
 */
public final class DataSources {

    private DataSources() {
    }

    public static HikariDataSource create(DatabaseSettings settings) {
        Objects.requireNonNull(settings, "settings");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setPoolName(settings.poolName());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        config.setInitializationFailTimeout(1_000L);
        return new HikariDataSource(config);
    }
}
