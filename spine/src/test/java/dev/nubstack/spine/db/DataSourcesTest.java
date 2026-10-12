package dev.nubstack.spine.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class DataSourcesTest {

    @Test
    void create_opensUsablePool() throws Exception {
        DatabaseSettings settings = new DatabaseSettings(
                "jdbc:h2:mem:spine;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "spine-test",
                2);

        try (HikariDataSource dataSource = DataSources.create(settings);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertEquals("spine-test", dataSource.getPoolName());
        }
    }

    @Test
    void settings_rejectInvalidPoolSize() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSettings(
                "jdbc:h2:mem:x",
                "sa",
                "",
                "bad",
                0));
    }

    @Test
    void settings_rejectBlankJdbcUrl() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSettings(
                " ",
                "sa",
                "",
                "bad",
                1));
    }

    @Test
    void create_setsDriverClassForKnownJdbcUrls() {
        assertEquals("org.h2.Driver", DataSources.driverClassName("jdbc:h2:mem:x"));
        assertEquals("org.mariadb.jdbc.Driver", DataSources.driverClassName("jdbc:mariadb://db:3306/x"));
        assertEquals("org.mariadb.jdbc.Driver", DataSources.driverClassName("jdbc:mysql://db:3306/x"));
    }
}
