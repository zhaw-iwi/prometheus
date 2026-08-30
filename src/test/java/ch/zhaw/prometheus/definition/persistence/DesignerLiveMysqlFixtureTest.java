package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Schema setup and cleanup boundary for the opt-in packaged designer browser smoke. */
@Tag("local-db-smoke")
class DesignerLiveMysqlFixtureTest {
    private static final String ENABLE_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE";
    private static final String SCHEMA_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA";

    @Test
    void prepareDedicatedSchema() {
        Target target = Target.required();
        try {
            target.recreate();
            assertTrue(target.schemaExists());
        } catch (Throwable failure) {
            target.dropQuietly();
            throw sanitized("setup", failure);
        }
    }

    @Test
    void removeDedicatedSchema() {
        Target target = Target.required();
        try {
            target.drop();
            assertFalse(target.schemaExists());
        } catch (Throwable failure) {
            throw sanitized("cleanup", failure);
        }
    }

    private static AssertionError sanitized(String phase, Throwable failure) {
        SQLException sql = sqlCause(failure);
        String detail = sql == null ? rootCause(failure).getClass().getSimpleName()
                : "SQLState=" + sql.getSQLState() + ", vendorCode=" + sql.getErrorCode();
        return new AssertionError("Dedicated designer browser schema " + phase + " failed (" + detail + ")");
    }

    private static SQLException sqlCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record Target(String schema, String serverUrl, String dedicatedUrl, String username, String password) {
        static Target required() {
            if (!"true".equalsIgnoreCase(System.getenv(ENABLE_ENV))) {
                throw new IllegalStateException(ENABLE_ENV + " must be exactly true");
            }
            Properties configured = new Properties();
            try (InputStream input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
                configured.load(input);
            } catch (Exception failure) {
                throw new IllegalStateException("Local application properties are unavailable");
            }
            String normalUrl = require(configured, "spring.datasource.url");
            String username = require(configured, "spring.datasource.username");
            String password = require(configured, "spring.datasource.password");
            String lowerUrl = normalUrl.toLowerCase(Locale.ROOT);
            if (!normalUrl.startsWith("jdbc:mysql://") || lowerUrl.contains("password=") || lowerUrl.contains("user=")) {
                throw new IllegalStateException("Configured datasource must be MySQL with separate credentials");
            }
            String remainder = normalUrl.substring("jdbc:mysql://".length());
            int slash = remainder.indexOf('/');
            if (slash < 1 || slash == remainder.length() - 1) {
                throw new IllegalStateException("Configured MySQL URL must contain a normal database name");
            }
            String authority = remainder.substring(0, slash);
            String databaseAndQuery = remainder.substring(slash + 1);
            int queryIndex = databaseAndQuery.indexOf('?');
            String normalSchema = queryIndex < 0 ? databaseAndQuery : databaseAndQuery.substring(0, queryIndex);
            String query = queryIndex < 0 ? "" : databaseAndQuery.substring(queryIndex);
            String schema = LocalMysqlSmokeSafety.requireDedicatedSchema(System.getenv(SCHEMA_ENV), normalSchema);
            return new Target(schema, "jdbc:mysql://" + authority + "/" + query,
                    "jdbc:mysql://" + authority + "/" + schema + query, username, password);
        }

        void recreate() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.createStatement()) {
                statement.execute("drop database if exists `" + this.schema + "`");
                statement.execute("create database `" + this.schema
                        + "` character set utf8mb4 collate utf8mb4_bin");
            }
        }

        void drop() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.createStatement()) {
                statement.execute("drop database if exists `" + this.schema + "`");
            }
        }

        void dropQuietly() {
            try {
                drop();
            } catch (SQLException ignored) {
                // The verified dedicated target can be removed by the documented cleanup command after connectivity returns.
            }
        }

        boolean schemaExists() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.prepareStatement(
                            "select count(*) from information_schema.schemata where schema_name = ?")) {
                statement.setString(1, this.schema);
                try (var rows = statement.executeQuery()) {
                    return rows.next() && rows.getInt(1) > 0;
                }
            }
        }

        private static String require(Properties properties, String name) {
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required local datasource property is unavailable: " + name);
            }
            return value.trim();
        }
    }
}
