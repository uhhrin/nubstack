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
        // Paper/Velocity plugin classloaders do not register JDBC drivers with the
        // system DriverManager. Force Hikari to load the driver from this loader.
        String driverClassName = driverClassName(settings.jdbcUrl());
        if (driverClassName != null) {
            config.setDriverClassName(driverClassName);
        }
        return new HikariDataSource(config);
    }

    static String driverClassName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        if (jdbcUrl.startsWith("jdbc:mariadb:") || jdbcUrl.startsWith("jdbc:mysql:")) {
            return "org.mariadb.jdbc.Driver";
        }
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        return null;
    }
}
