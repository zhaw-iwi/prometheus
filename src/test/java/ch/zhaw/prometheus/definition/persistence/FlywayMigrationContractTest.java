package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {
    private static final List<String> LEGACY_TABLES = List.of(
            "action_storage_keys_from", "prompt_policy_storage_keys_from", "transition_actions",
            "transition_decisions", "state_transitions", "storage_entries", "event_state_path", "agent",
            "transition", "state", "action", "decision", "policy", "storage_entry", "storage", "event",
            "event_history");

    @Test
    void orderedMigrationExecutesAgainstDisposableMysqlModeDatabase() throws Exception {
        String databaseName = "designer_migration_" + System.nanoTime();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").load();

        var result = flyway.migrate();

        assertEquals(2, result.migrationsExecuted);
        assertEquals("2", flyway.info().current().getVersion().getVersion());
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
        assertTrue(tables.contains("access_code"));
        assertTrue(tables.contains("access_code_allowed_agent_type"));
        assertTrue(tables.contains("access_code_agent"));
        assertTrue(tables.contains("flyway_schema_history"));
        LEGACY_TABLES.forEach(table -> assertFalse(tables.contains(table), table));
    }

    @Test
    void cutoverPreservesAccessConfigurationAndRemovesForeignKeyConnectedLegacySchema() throws Exception {
        String databaseName = "designer_legacy_cutover_" + System.nanoTime();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("create table access_code (id binary(16) primary key, code varchar(5) not null, "
                    + "enabled boolean not null)");
            statement.execute("create table access_code_allowed_agent_type (id binary(16) primary key, "
                    + "access_code_id binary(16) not null, agent_type_key varchar(128) not null, "
                    + "foreign key (access_code_id) references access_code(id))");
            statement.execute("create table event_history (id binary(16) primary key)");
            statement.execute("create table event (id binary(16) primary key, event_history_id binary(16), "
                    + "foreign key (event_history_id) references event_history(id))");
            statement.execute("create table event_state_path (event_id binary(16), "
                    + "foreign key (event_id) references event(id))");
            statement.execute("create table policy (id binary(16) primary key)");
            statement.execute("create table state (id binary(16) primary key, policy_id binary(16), "
                    + "foreign key (policy_id) references policy(id))");
            statement.execute("create table transition (id binary(16) primary key, subsequent_state_id binary(16), "
                    + "foreign key (subsequent_state_id) references state(id))");
            statement.execute("create table action (id binary(16) primary key)");
            statement.execute("create table decision (id binary(16) primary key)");
            statement.execute("create table state_transitions (state_id binary(16), transitions_id binary(16), "
                    + "foreign key (state_id) references state(id), "
                    + "foreign key (transitions_id) references transition(id))");
            statement.execute("create table transition_actions (transition_id binary(16), actions_id binary(16), "
                    + "foreign key (transition_id) references transition(id), "
                    + "foreign key (actions_id) references action(id))");
            statement.execute("create table transition_decisions (transition_id binary(16), decisions_id binary(16), "
                    + "foreign key (transition_id) references transition(id), "
                    + "foreign key (decisions_id) references decision(id))");
            statement.execute("create table storage (id binary(16) primary key)");
            statement.execute("create table storage_entry (id binary(16) primary key)");
            statement.execute("create table storage_entries (storage_id binary(16), entries_id binary(16), "
                    + "foreign key (storage_id) references storage(id), "
                    + "foreign key (entries_id) references storage_entry(id))");
            statement.execute("create table action_storage_keys_from (action_id binary(16))");
            statement.execute("create table prompt_policy_storage_keys_from (policy_id binary(16))");
            statement.execute("create table agent (id binary(16) primary key, initial_state_id binary(16), "
                    + "storage_id binary(16), event_history_id binary(16), "
                    + "foreign key (initial_state_id) references state(id), "
                    + "foreign key (storage_id) references storage(id), "
                    + "foreign key (event_history_id) references event_history(id))");
            statement.execute("create table access_code_agent (id binary(16) primary key, "
                    + "access_code_id binary(16) not null, agent_id binary(16) not null, "
                    + "foreign key (access_code_id) references access_code(id), "
                    + "foreign key (agent_id) references agent(id))");
            statement.execute("insert into access_code values "
                    + "(X'00000000000000000000000000000001', 'Ab123', true)");
            statement.execute("insert into access_code_allowed_agent_type values "
                    + "(X'00000000000000000000000000000002', X'00000000000000000000000000000001', "
                    + "'core.talk_to_me')");
        }

        Flyway flyway = Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion("0")
                .locations("classpath:db/migration").load();
        var result = flyway.migrate();

        assertEquals(2, result.migrationsExecuted);
        Set<String> tables = tableNames(url);
        LEGACY_TABLES.forEach(table -> assertFalse(tables.contains(table), table));
        assertEquals(1, scalar(url, "select count(*) from access_code"));
        assertEquals(1, scalar(url, "select count(*) from access_code_allowed_agent_type"));
        assertEquals(0, scalar(url, "select count(*) from access_code_agent"));
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

    @Test
    void cutoverMigrationTargetsOnlyTheNamedLegacyGraphAndRebuildsScopedLinks() throws Exception {
        String resource = "/db/migration/V2__cut_over_to_declarative_runtime.sql";
        String sql;
        try (var input = getClass().getResourceAsStream(resource)) {
            assertTrue(input != null, resource);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertTrue(sql.contains("references declarative_agent_instance"));
        assertTrue(sql.contains("drop table if exists agent;"));
        assertTrue(sql.contains("drop table if exists state;"));
        assertTrue(sql.contains("drop table if exists transition;"));
        assertTrue(sql.contains("drop table if exists policy;"));
        LEGACY_TABLES.forEach(table -> assertTrue(sql.contains("drop table if exists " + table + ";"), table));
        assertFalse(sql.contains("drop table if exists agent_definition;"));
        assertFalse(sql.contains("drop table if exists agent_definition_revision;"));
        assertFalse(sql.contains("drop table if exists declarative_agent_instance;"));
        assertFalse(sql.matches("(?ms).*^\\s*(truncate|clean)\\b.*"));
    }

    private static Set<String> tableNames(String url) throws Exception {
        Set<String> tables = new TreeSet<>();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("select table_name from information_schema.tables "
                        + "where table_schema = 'public'")) {
            while (rows.next()) {
                tables.add(rows.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static int scalar(String url, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
