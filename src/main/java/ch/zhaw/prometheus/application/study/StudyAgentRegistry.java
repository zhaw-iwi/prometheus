package ch.zhaw.prometheus.application.study;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Single-process ownership index. Agent IDs are new and never rebound to an ordinary agent. */
@Component
public class StudyAgentRegistry {
    private final Set<UUID> privateAgents=ConcurrentHashMap.newKeySet();
    public StudyAgentRegistry(StudyAttemptRepository attempts){privateAgents.addAll(attempts.agentIds());}
    public boolean contains(UUID id){return privateAgents.contains(id);}
    public Set<UUID> ids(){return Set.copyOf(privateAgents);}
    void provision(UUID id){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Ownership requires the provisioning transaction");
        // Deny before commit/publication, including concurrent ordinary requests.
        privateAgents.add(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)privateAgents.remove(id);}
        });
    }
}
