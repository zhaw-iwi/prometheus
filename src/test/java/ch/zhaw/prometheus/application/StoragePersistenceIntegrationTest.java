package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonPrimitive;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.repositories.StorageRepository;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
class StoragePersistenceIntegrationTest {
    @Autowired StorageRepository storages;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;

    @Test void existingJoinRowsReloadAndRemovalCostsDoNotGrowWithStorageSize() throws Exception {
        var storage = new Storage();
        for (int i = 0; i < 100; i++) storage.put("key" + i, new JsonPrimitive(i));
        UUID id = storages.saveAndFlush(storage).getID();
        // The old list mapping stored these same two columns, without a position.
        // Reload with that legacy table shape (no composite PK) before exercising updates.
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            boolean primary;
            try (var keys = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, "storage_entries")) {
                primary = keys.next();
            }
            if (primary) {
                statement.execute("alter table storage_entries add index idx_legacy_storage (storage_id)");
                statement.execute("alter table storage_entries drop primary key");
            }
        }
        var stats = factory.unwrap(SessionFactory.class).getStatistics();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var loaded = storages.findById(id).orElseThrow();
            assertEquals(100, loaded.toMap().size());
            stats.clear();
            loaded.remove("key50"); loaded.put("replacement", new JsonPrimitive(true));
            storages.flush();
            assertTrue(stats.getPrepareStatementCount() <= 4, "Only the removed/added entry and its own link may change: "
                    + stats.getPrepareStatementCount());
        });
        var detached = storages.findById(id).orElseThrow();
        assertFalse(detached.containsKey("key50")); assertTrue(detached.get("replacement").getAsBoolean());
        assertEquals(100, detached.toMap().size());
        String version = detached.writeVersion("key1");
        detached.put("key1", new JsonPrimitive(1));
        assertNotEquals(version, detached.writeVersion("key1"), "Identical explicit writes still fence background work");
        detached.remove("replacement");
        var merged = storages.saveAndFlush(detached);
        var reloaded = storages.findById(id).orElseThrow();
        assertEquals(99, reloaded.toMap().size());
        assertEquals(merged.writeVersion("key1"), reloaded.writeVersion("key1"));
        storages.deleteById(id);
        assertFalse(storages.existsById(id));
    }
}
