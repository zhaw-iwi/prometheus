package ch.zhaw.prometheus.application;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class AfterCommit {
    private AfterCommit() {}
    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
