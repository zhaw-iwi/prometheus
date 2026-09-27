package ch.zhaw.prometheus.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.event.SpeechProvenance.Association;
import ch.zhaw.prometheus.model.event.SpeechProvenance.Origin;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.AgentRepository;

/** Trusted adapter boundary only. There is no client-authored assistant event endpoint. */
@Service
public class ExternalSpeechRecordingService {
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final ExternalSpeechOwnership ownership;
    private final AgentBehaviourBroadcaster behaviour;
    private final AgentMonitorBroadcaster monitor;
    private final TransactionTemplate transaction;
    public ExternalSpeechRecordingService(AgentApplicationService turns, AgentRepository agents,
            ExternalSpeechOwnership ownership, AgentBehaviourBroadcaster behaviour, AgentMonitorBroadcaster monitor,
            PlatformTransactionManager transactions) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.behaviour = behaviour; this.monitor = monitor;
        this.transaction = new TransactionTemplate(transactions);
    }
    public Optional<Event> record(UUID id, ExternalSpeech owner, String segmentId, String text,
            List<UUID> intentIds, Association association, boolean complete) {
        if (segmentId == null || !segmentId.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException("Invalid speech segment identity");
        return turns.serialized(id, () -> {
            boolean[] created = {false};
            Optional<Event> result = transaction.execute(status -> {
                var agent = agents.findById(id).orElse(null);
                if (agent == null || !owner.equals(ownership.current(agent))) return Optional.empty();
                for (Event existing : agent.getEventHistory().toList()) {
                    var provenance = existing.speechProvenance();
                    if (provenance != null && provenance.origin() == Origin.NATIVE && provenance.sessionId().equals(owner.sessionId())
                            && segmentId.equals(provenance.segmentId())) return Optional.of(existing);
                }
                List<UUID> sources = intentIds == null ? List.of() : List.copyOf(intentIds);
                if ((association == Association.NONE) != sources.isEmpty()) throw new IllegalArgumentException("Invalid narration association");
                for (UUID source : sources) {
                    boolean valid = agent.getEventHistory().toList().stream().anyMatch(event -> source.equals(event.getId())
                            && event.speechProvenance() != null && event.speechProvenance().origin() == Origin.BACKEND_INTENT
                            && event.speechProvenance().sessionId().equals(owner.sessionId())
                            && event.speechProvenance().epoch().equals(owner.epoch()));
                    if (!valid) throw new IllegalArgumentException("Narration source is outside the speech session");
                }
                agent.recordExternalSpeech(text, new SpeechProvenance(Origin.NATIVE, owner.sessionId(), owner.epoch(),
                        segmentId, association, sources, complete));
                var saved = agents.saveAndFlush(agent); created[0] = true;
                // Cascade merge may replace a newly appended event; return/publish its persisted identity.
                return saved.getEventHistory().toList().stream().filter(event -> event.speechProvenance() != null
                        && owner.sessionId().equals(event.speechProvenance().sessionId())
                        && segmentId.equals(event.speechProvenance().segmentId())).findFirst();
            });
            if (created[0]) {
                AfterCommit.run(() -> {
                    result.ifPresent(event -> behaviour.publish(id, event));
                    agents.findById(id).ifPresent(monitor::publish);
                });
            }
            return result;
        });
    }
}
