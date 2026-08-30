package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {
    @Test
    void orderedMigrationExecutesAgainstDisposableMysqlModeDatabase() throws Exception {
        String databaseName = "designer_migration_" + System.nanoTime();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").load();

        var result = flyway.migrate();

        assertEquals(1, result.migrationsExecuted);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        Set<String> tables = new TreeSet<>();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("select table_name from information_schema.tables "
                        + "where table_schema = 'public'")) {
            while (rows.next()) {
                tables.add(rows.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        assertTrue(tables.contains("agent_definition"));
        assertTrue(tables.contains("agent_definition_revision"));
        assertTrue(tables.contains("declarative_agent_instance"));
        assertTrue(tables.contains("flyway_schema_history"));
    }

    @Test
    void milestoneMigrationHasNoDestructiveOrAccessCodeTargets() throws Exception {
        String resource = "/db/migration/V1__create_declarative_agent_aggregates.sql";
        String sql;
        try (var input = getClass().getResourceAsStream(resource)) {
            assertTrue(input != null, resource);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertFalse(sql.matches("(?ms).*^\\s*(drop|truncate|delete|clean)\\b.*"));
        assertFalse(sql.contains("access_code"));
        assertFalse(sql.contains(" agent "));
        assertEquals(3, sql.split("create table ", -1).length - 1);
        assertEquals(3, sql.split("foreign key", -1).length - 1);
        assertEquals(4, sql.split(" json ", -1).length - 1);
    }
}
