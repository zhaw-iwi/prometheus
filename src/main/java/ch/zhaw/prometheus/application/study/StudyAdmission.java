package ch.zhaw.prometheus.application.study;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Bounded, non-queuing single-process presence leases; no transaction waits for a slot. */
@Component
public class StudyAdmission {
    private final int maximum;
    private final Duration lifetime;
    private final Clock clock;
    private final Map<UUID,Instant> leases=new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public StudyAdmission(@Value("${prometheus.study.max-active:4}")int maximum,
            @Value("${prometheus.study.presence-seconds:120}")int seconds){this(maximum,seconds,Clock.systemUTC());}
    StudyAdmission(int maximum,int seconds,Clock clock){
        if(maximum<0 || maximum>50 || seconds<120 || seconds>600)throw new IllegalArgumentException("Invalid study admission limits");
        this.maximum=maximum;this.lifetime=Duration.ofSeconds(seconds);this.clock=clock;
    }
    public record Status(int active,int maximum,int leaseSeconds){}
    public boolean enabled(){return maximum>0;}
    public synchronized Status status(){expire();return new Status(leases.size(),maximum,(int)lifetime.toSeconds());}
    public synchronized void acquire(UUID attempt){
        if(!enabled())return;expire();boolean fresh=!leases.containsKey(attempt);
        if(fresh && leases.size()>=maximum)throw StudySessions.failure(HttpStatus.TOO_MANY_REQUESTS,"All study places are occupied. Your assignment and saved work are unchanged; try again shortly.");
        leases.put(attempt,clock.instant().plus(lifetime));
        if(fresh && TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)release(attempt);}
        });
    }
    public synchronized void release(UUID attempt){leases.remove(attempt);}
    public void releaseAfterCommit(UUID attempt){
        if(!TransactionSynchronizationManager.isSynchronizationActive()){release(attempt);return;}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){release(attempt);}});
    }
    private void expire(){Instant now=clock.instant();leases.values().removeIf(expiry->!now.isBefore(expiry));}
}
