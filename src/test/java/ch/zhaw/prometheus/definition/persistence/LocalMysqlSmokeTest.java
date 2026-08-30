package ch.zhaw.prometheus.definition.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.PrometheusApplication;
import ch.zhaw.prometheus.definition.application.ActiveAgentDefinitionCatalog;
import ch.zhaw.prometheus.definition.application.BundledDefinitionImporter;
import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentInstanceService;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;

/** Explicitly opted-in destructive smoke against one verified dedicated local MySQL schema. */
@Tag("local-db-smoke")
class LocalMysqlSmokeTest {
    private static final String ENABLE_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE";
    private static final String SCHEMA_ENV = "PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA";
    private static final String SMOKE_KEY = "core.rock_scissor_paper";
    private static final String PRESERVED_CODE = "Smk91";
    private static final List<String> LEGACY_TABLES = List.of(
            "action_storage_keys_from", "prompt_policy_storage_keys_from", "transition_actions",
            "transition_decisions", "state_transitions", "storage_entries", "event_state_path", "agent",
            "transition", "state", "action", "decision", "policy", "storage_entry", "storage", "event",
            "event_history");

    @Test
    void provesFinalMigrationLifecycleAndPinnedRuntimeAcrossRestart() {
        SmokeTarget target = SmokeTarget.required();
        String phase = "dedicated schema setup";
        try {
            target.recreateAndSeedLegacySchema();

            UUID instanceId;
            long firstRevisionId;
            String exportedRevision;
            phase = "first Spring startup and bundled import";
            try (ConfigurableApplicationContext first = start(target)) {
                ActiveAgentDefinitionCatalog catalog = first.getBean(ActiveAgentDefinitionCatalog.class);
                assertEquals(BundledDefinitionCatalog.loadMainCatalog().definitions().size(), catalog.list().size());
                var repeatImport = first.getBean(BundledDefinitionImporter.class).importMainCatalog();
                assertEquals(0, repeatImport.createdDefinitions());
                assertEquals(0, repeatImport.createdRevisions());
                assertEquals(12, repeatImport.unchangedRevisions());

                DefinitionLifecycleService lifecycle = first.getBean(DefinitionLifecycleService.class);
                var revisionOne = lifecycle.requireActiveRevision(SMOKE_KEY);
                firstRevisionId = revisionOne.id();

                phase = "deterministic RPS runtime persistence";
                DeclarativeAgentInstanceService instances = first.getBean(DeclarativeAgentInstanceService.class);
                AgentRuntimeContext runtime = runtimeContext();
                var creation = instances.create(SMOKE_KEY, runtime);
                instanceId = creation.instance().id();
                assertEquals(firstRevisionId, creation.instance().definitionRevisionId());
                var reveal = instances.acknowledge(instanceId, userEvent("ready", "start"), runtime);
                assertEquals(List.of("start_to_reveal"), reveal.result().acceptedTransitionIds());
                var execution = instances.acknowledge(instanceId, handEvent("scissor", "reveal"), runtime);
                assertEquals(1, execution.instance().storage().get("rps_rounds").value().size());
                assertEquals("result", execution.instance().activeLeafStateId());
                assertTrue(execution.instance().started());
                assertFalse(execution.instance().history().isEmpty());

                phase = "draft publication, export, activation, and old revision archive";
                var draftTwo = lifecycle.createDraft(fixtureRevision(2, "MySQL smoke revision two"),
                        DefinitionProvenance.DESIGNER, "local-db-smoke");
                assertEquals(DefinitionStatus.DRAFT, draftTwo.status());
                var publishedTwo = lifecycle.publish(SMOKE_KEY, 2, draftTwo.optimisticVersion());
                exportedRevision = publishedTwo.canonicalJson();
                var identity = lifecycle.requireDefinition(SMOKE_KEY);
                lifecycle.activate(SMOKE_KEY, 2, identity.optimisticVersion());
                var stillPinned = instances.find(instanceId).orElseThrow().instance();
                assertEquals(firstRevisionId, stillPinned.definitionRevisionId());
                assertEquals(publishedTwo.id(), lifecycle.requireActiveRevision(SMOKE_KEY).id());
                var archived = lifecycle.archive(SMOKE_KEY, 1,
                        lifecycle.requireRevision(SMOKE_KEY, 1).optimisticVersion());
                assertEquals(DefinitionStatus.ARCHIVED, archived.status());
            }

            phase = "second Spring startup, reload, and archived execution";
            try (ConfigurableApplicationContext second = start(target)) {
                DefinitionLifecycleService lifecycle = second.getBean(DefinitionLifecycleService.class);
                assertEquals(exportedRevision, lifecycle.requireRevision(SMOKE_KEY, 2).canonicalJson());
                assertEquals(DefinitionStatus.ARCHIVED, lifecycle.requireRevision(SMOKE_KEY, 1).status());
                var repeatImport = second.getBean(BundledDefinitionImporter.class).importMainCatalog();
                assertEquals(12, repeatImport.unchangedRevisions());

                DeclarativeAgentInstanceService instances = second.getBean(DeclarativeAgentInstanceService.class);
                var reloaded = instances.find(instanceId).orElseThrow().instance();
                assertEquals(firstRevisionId, reloaded.definitionRevisionId());
                assertEquals(1, reloaded.storage().get("rps_rounds").value().size());
                assertFalse(reloaded.history().isEmpty());
                AgentRuntimeContext runtime = runtimeContext();
                var secondReveal = instances.acknowledge(instanceId, userEvent("again", "result"), runtime);
                assertEquals(List.of("result_to_reveal"), secondReveal.result().acceptedTransitionIds());
                var executedArchived = instances.acknowledge(instanceId, handEvent("paper", "reveal"), runtime);
                assertEquals(firstRevisionId, executedArchived.instance().definitionRevisionId());
                assertEquals(2, executedArchived.instance().storage().get("rps_rounds").value().size());
                assertTrue(second.getBean(DeclarativeAgentRepository.class)
                        .existsByDefinitionRevisionId(firstRevisionId));

                DefinitionRepository definitions = second.getBean(DefinitionRepository.class);
                assertEquals(12, definitions.findActiveRevisions().size());
                assertNotNull(second.getBean(AccessCodeRepository.class).findByCode(PRESERVED_CODE).orElse(null));
            }

            phase = "final schema inspection";
            assertEquals(1, target.scalar("select count(*) from access_code where code = '" + PRESERVED_CODE + "'"));
            assertEquals(1, target.scalar("select count(*) from access_code_allowed_agent_type "
                    + "where agent_type_key = 'core.talk_to_me'"));
            Set<String> tables = target.tableNames();
            LEGACY_TABLES.forEach(table -> assertFalse(tables.contains(table), table));
            assertTrue(tables.containsAll(Set.of("agent_definition", "agent_definition_revision",
                    "declarative_agent_instance", "access_code", "access_code_allowed_agent_type",
                    "access_code_agent", "flyway_schema_history")));
            phase = "dedicated schema cleanup";
            target.dropDedicatedSchema();
            assertFalse(target.schemaExists());
            System.out.println("Dedicated MySQL smoke completed for schema " + target.schema());
        } catch (Throwable failure) {
            throw sanitizedFailure(phase, failure);
        } finally {
            target.dropDedicatedSchemaQuietly();
        }
    }

    private static ConfigurableApplicationContext start(SmokeTarget target) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", target.dedicatedUrl());
        properties.put("spring.datasource.username", target.username());
        properties.put("spring.datasource.password", target.password());
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.jpa.show-sql", "false");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.main.web-application-type", "none");
        properties.put("spring.main.banner-mode", "off");
        properties.put("debug", "false");
        properties.put("logging.level.root", "WARN");
        properties.put("logging.level.com.zaxxer.hikari", "ERROR");
        SpringApplication application = new SpringApplication(PrometheusApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("dedicatedMysqlSmoke", properties)));
        return application.run("--debug=false", "--logging.level.root=WARN", "--spring.main.banner-mode=off");
    }

    private static AgentRuntimeContext runtimeContext() {
        return new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(new ScriptedRpsModelGateway()),
                new Random(91));
    }

    private static RuntimeEvent userEvent(String payload, String leaf) {
        return new RuntimeEvent("obs.user_utterance", "user", "observation", payload,
                List.of("context", leaf));
    }

    private static RuntimeEvent handEvent(String sign, String leaf) {
        return new RuntimeEvent("obs.hand.sign", "sensor", "observation", "{\"sign\":\"" + sign + "\"}",
                List.of("context", leaf));
    }

    private static String fixtureRevision(int revision, String description) throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (InputStream input = LocalMysqlSmokeTest.class
                .getResourceAsStream("/agent-definitions/catalog/main/core/rock_scissor_paper/revision-1.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled RPS definition fixture");
            }
            ObjectNode document = (ObjectNode) json.readTree(input);
            document.put("revision", revision);
            ((ObjectNode) document.path("metadata")).put("description", description);
            return json.writeValueAsString(document);
        }
    }

    private static AssertionError sanitizedFailure(String phase, Throwable failure) {
        SQLException sql = sqlCause(failure);
        String detail = sql == null ? rootCause(failure).getClass().getSimpleName()
                : "SQLState=" + sql.getSQLState() + ", vendorCode=" + sql.getErrorCode();
        return new AssertionError("Dedicated MySQL smoke failed during " + phase + " (" + detail + ")");
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

    private static final class ScriptedRpsModelGateway implements RuntimeModelGateway {
        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            return RuntimeBehaviour.speechOnly("deterministic RPS smoke response");
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            String payload = invocation.history().isEmpty() ? "" : invocation.history().getLast().payload();
            if ("stop".equals(payload)) {
                return true;
            }
            if ("ready".equals(payload)) {
                return prompt.contains("ready to start a round");
            }
            if ("again".equals(payload)) {
                return prompt.contains("play another round");
            }
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            throw new AssertionError("RPS has no extraction action");
        }
    }

    private record SmokeTarget(String schema, String serverUrl, String dedicatedUrl, String username,
            String password) {
        static SmokeTarget required() {
            if (!"true".equalsIgnoreCase(System.getenv(ENABLE_ENV))) {
                throw new IllegalStateException(ENABLE_ENV + " must be exactly true");
            }
            String requestedSchema = System.getenv(SCHEMA_ENV);
            Properties configured = new Properties();
            Path applicationProperties = Path.of("src/main/resources/application.properties");
            try (InputStream input = Files.newInputStream(applicationProperties)) {
                configured.load(input);
            } catch (Exception failure) {
                throw new IllegalStateException("Local application properties are unavailable");
            }
            String normalUrl = require(configured, "spring.datasource.url");
            String username = require(configured, "spring.datasource.username");
            String password = require(configured, "spring.datasource.password");
            String lowerUrl = normalUrl.toLowerCase(Locale.ROOT);
            if (!normalUrl.startsWith("jdbc:mysql://") || lowerUrl.contains("password=")
                    || lowerUrl.contains("user=")) {
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
            String schema = LocalMysqlSmokeSafety.requireDedicatedSchema(requestedSchema, normalSchema);
            String serverUrl = "jdbc:mysql://" + authority + "/" + query;
            String dedicatedUrl = "jdbc:mysql://" + authority + "/" + schema + query;
            return new SmokeTarget(schema, serverUrl, dedicatedUrl, username, password);
        }

        void recreateAndSeedLegacySchema() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.createStatement()) {
                statement.execute("drop database if exists `" + this.schema + "`");
                statement.execute("create database `" + this.schema
                        + "` character set utf8mb4 collate utf8mb4_bin");
            }
            try (var connection = DriverManager.getConnection(this.dedicatedUrl, this.username, this.password);
                    var statement = connection.createStatement()) {
                for (String ddl : legacySchemaDdl()) {
                    statement.execute(ddl);
                }
                statement.execute("insert into access_code values "
                        + "(unhex('00000000000000000000000000000001'), '" + PRESERVED_CODE + "', true)");
                statement.execute("insert into access_code_allowed_agent_type values "
                        + "(unhex('00000000000000000000000000000002'), "
                        + "unhex('00000000000000000000000000000001'), 'core.talk_to_me')");
                statement.execute("insert into agent(id) values "
                        + "(unhex('00000000000000000000000000000003'))");
                statement.execute("insert into access_code_agent values "
                        + "(unhex('00000000000000000000000000000004'), "
                        + "unhex('00000000000000000000000000000001'), "
                        + "unhex('00000000000000000000000000000003'))");
            }
        }

        int scalar(String sql) throws SQLException {
            try (var connection = DriverManager.getConnection(this.dedicatedUrl, this.username, this.password);
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery(sql)) {
                if (!rows.next()) {
                    throw new SQLException("Scalar query returned no row");
                }
                return rows.getInt(1);
            }
        }

        Set<String> tableNames() throws SQLException {
            Set<String> names = new TreeSet<>();
            try (var connection = DriverManager.getConnection(this.dedicatedUrl, this.username, this.password);
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select table_name from information_schema.tables "
                            + "where table_schema = '" + this.schema + "'")) {
                while (rows.next()) {
                    names.add(rows.getString(1).toLowerCase(Locale.ROOT));
                }
            }
            return names;
        }

        void dropDedicatedSchemaQuietly() {
            try {
                dropDedicatedSchema();
            } catch (SQLException ignored) {
                // The guarded target is left for manual inspection only when the server cannot be reached for cleanup.
            }
        }

        void dropDedicatedSchema() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.createStatement()) {
                statement.execute("drop database if exists `" + this.schema + "`");
            }
        }

        boolean schemaExists() throws SQLException {
            try (var connection = DriverManager.getConnection(this.serverUrl, this.username, this.password);
                    var statement = connection.prepareStatement("select count(*) from information_schema.schemata "
                            + "where schema_name = ?")) {
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

        private static List<String> legacySchemaDdl() {
            return List.of(
                    "create table access_code (id binary(16) primary key, code varchar(5) collate utf8mb4_bin not null, enabled boolean not null, constraint uk_access_code_code unique(code))",
                    "create table access_code_allowed_agent_type (id binary(16) primary key, access_code_id binary(16) not null, agent_type_key varchar(128) collate utf8mb4_bin not null, constraint uk_access_code_allowed_agent_type unique(access_code_id, agent_type_key), constraint fk_access_code_allowed_type_code foreign key(access_code_id) references access_code(id) on delete cascade)",
                    "create table event_history (id binary(16) primary key)",
                    "create table event (id binary(16) primary key, event_history_id binary(16), constraint smoke_event_history foreign key(event_history_id) references event_history(id))",
                    "create table event_state_path (event_id binary(16), constraint smoke_event_path foreign key(event_id) references event(id))",
                    "create table policy (id binary(16) primary key)",
                    "create table state (id binary(16) primary key, policy_id binary(16), constraint smoke_state_policy foreign key(policy_id) references policy(id))",
                    "create table transition (id binary(16) primary key, subsequent_state_id binary(16), constraint smoke_transition_state foreign key(subsequent_state_id) references state(id))",
                    "create table action (id binary(16) primary key)",
                    "create table decision (id binary(16) primary key)",
                    "create table state_transitions (state_id binary(16), transitions_id binary(16), constraint smoke_state_transitions_state foreign key(state_id) references state(id), constraint smoke_state_transitions_transition foreign key(transitions_id) references transition(id))",
                    "create table transition_actions (transition_id binary(16), actions_id binary(16), constraint smoke_transition_actions_transition foreign key(transition_id) references transition(id), constraint smoke_transition_actions_action foreign key(actions_id) references action(id))",
                    "create table transition_decisions (transition_id binary(16), decisions_id binary(16), constraint smoke_transition_decisions_transition foreign key(transition_id) references transition(id), constraint smoke_transition_decisions_decision foreign key(decisions_id) references decision(id))",
                    "create table storage (id binary(16) primary key)",
                    "create table storage_entry (id binary(16) primary key)",
                    "create table storage_entries (storage_id binary(16), entries_id binary(16), constraint smoke_storage_entries_storage foreign key(storage_id) references storage(id), constraint smoke_storage_entries_entry foreign key(entries_id) references storage_entry(id))",
                    "create table action_storage_keys_from (action_id binary(16))",
                    "create table prompt_policy_storage_keys_from (policy_id binary(16))",
                    "create table agent (id binary(16) primary key, initial_state_id binary(16), storage_id binary(16), event_history_id binary(16), constraint smoke_agent_state foreign key(initial_state_id) references state(id), constraint smoke_agent_storage foreign key(storage_id) references storage(id), constraint smoke_agent_history foreign key(event_history_id) references event_history(id))",
                    "create table access_code_agent (id binary(16) primary key, access_code_id binary(16) not null, agent_id binary(16) not null, constraint smoke_access_agent_code foreign key(access_code_id) references access_code(id), constraint smoke_access_agent_legacy foreign key(agent_id) references agent(id))");
        }
    }
}

final class LocalMysqlSmokeSafety {
    private LocalMysqlSmokeSafety() {
    }

    static String requireDedicatedSchema(String requestedSchema, String normalSchema) {
        if (requestedSchema == null || !requestedSchema.matches("prometheus_designer_smoke_[a-z0-9_]+")) {
            throw new IllegalStateException("PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA must name a dedicated smoke schema");
        }
        if (normalSchema == null || normalSchema.isBlank() || normalSchema.equalsIgnoreCase(requestedSchema)) {
            throw new IllegalStateException("Dedicated smoke schema must differ from the normal database");
        }
        return requestedSchema;
    }
}
