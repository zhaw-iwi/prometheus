package ch.zhaw.prometheus.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveContextSnapshot;
import ch.zhaw.prometheus.repositories.AgentRepository;

/** Reads a fresh agent under the same per-agent serialization used for runtime mutations. */
@Service
public class LiveAgentContextService {
    private final ScopedDemoService demo;
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final LiveContextProjection projection;
    private final TransactionTemplate transaction;
    private final ExternalSpeechOwnership ownership;
    public LiveAgentContextService(ScopedDemoService demo, AgentApplicationService turns, AgentRepository agents,
            LiveContextProjection projection, PlatformTransactionManager transactions, ExternalSpeechOwnership ownership) {
        this.demo = demo; this.turns = turns; this.agents = agents; this.projection = projection;
        this.transaction = new TransactionTemplate(transactions);
        this.ownership = ownership;
    }
    public boolean supported(String code, UUID id) {
        return turns.serialized(id, () -> Boolean.TRUE.equals(transaction.execute(status ->
                demo.getAgentInfo(code, id).isPresent() && agents.findById(id).map(projection::supports).orElse(false))));
    }
    public Optional<LiveContextSnapshot> snapshot(String code, UUID id) {
        return turns.serialized(id, () -> transaction.execute(status -> {
            if (demo.getAgentInfo(code, id).isEmpty()) return Optional.empty();
            return agents.findById(id).map(projection::project);
        }));
    }

    public Optional<LiveContextSnapshot> claim(String code, UUID id, UUID session) {
        return turns.serialized(id, () -> {
            var result = snapshot(code, id);
            result.ifPresent(context -> {
                ownership.acquire(id, new ch.zhaw.prometheus.model.policy.ExternalSpeech(session, context.epoch()));
                turns.discardSpeculation(id, "external_speech_started");
            });
            return result;
        });
    }

    public record Update(LiveContextSnapshot context, java.util.List<ch.zhaw.prometheus.application.live.LiveContextDelivery.Narration> narrations) {}
    public Optional<Update> refresh(UUID id, ch.zhaw.prometheus.model.policy.ExternalSpeech owner) {
        return turns.serialized(id, () -> transaction.execute(status -> agents.findById(id)
                .filter(agent -> owner.equals(ownership.current(agent))).map(agent -> {
                    var narrations = agent.getEventHistory().toList().stream()
                            .filter(ch.zhaw.prometheus.model.event.ConversationProjection::isIntent)
                            .filter(event -> owner.sessionId().equals(event.speechProvenance().sessionId()) && owner.epoch().equals(event.speechProvenance().epoch()))
                            .map(event -> new ch.zhaw.prometheus.application.live.LiveContextDelivery.Narration(event.getId(),
                                    ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(event.getPayload()).getSpeech()))
                            .filter(value -> value.sourceId() != null && value.text() != null && !value.text().isBlank()).toList();
                    return new Update(projection.project(agent), narrations);
                })));
    }
}
