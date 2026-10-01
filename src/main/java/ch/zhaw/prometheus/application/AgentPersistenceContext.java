package ch.zhaw.prometheus.application;

import java.util.function.Supplier;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reuse loaded entities for one bounded turn; repository transactions still release JDBC connections. */
@Component
public class AgentPersistenceContext {
    private final EntityManagerFactory factory;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    public AgentPersistenceContext(EntityManagerFactory factory, org.springframework.transaction.PlatformTransactionManager transactions) {
        this.factory = factory;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
    }

    <T> T load(Supplier<T> work) {
        // Repository read-only transactions mark loaded entities read-only in Hibernate.
        // A short read/write transaction retains dirty snapshots for the later save.
        return transaction.execute(status -> work.get());
    }

    public <T> T call(Supplier<T> work) {
        // Existing transaction owners (including durable Live ingress) retain their boundary.
        if (TransactionSynchronizationManager.hasResource(factory)) return work.get();
        // Resource-local Hibernate sessions can otherwise retain JDBC until EntityManager.close().
        var manager = factory.unwrap(org.hibernate.SessionFactory.class).withOptions()
                .connectionHandlingMode(org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode
                        .DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION)
                .openSession();
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(manager));
        try {
            return work.get();
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            manager.close();
        }
    }

    void clear() {
        if (TransactionSynchronizationManager.getResource(factory) instanceof EntityManagerHolder holder)
            holder.getEntityManager().clear();
    }
}
