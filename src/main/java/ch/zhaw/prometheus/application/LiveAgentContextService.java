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
    private LiveTaskResponseService taskResponses;
    @org.springframework.beans.factory.annotation.Autowired
    void taskResponses(LiveTaskResponseService service) { this.taskResponses = service; }
    public LiveAgentContextService(ScopedDemoService demo, AgentApplicationService turns, AgentRepository agents,
            LiveContextProjection projection, PlatformTransactionManager transactions, ExternalSpeechOwnership ownership) {
        this.demo = demo; this.turns = turns; this.agents = agents; this.projection = projection;
        this.transaction = new TransactionTemplate(transactions);
        this.ownership = ownership;
    }
    public boolean supported(String code, UUID id) {
        return eligibility(code, id).supported();
    }
    public record Eligibility(boolean supported, long departureGraceMs) {}
    public Eligibility eligibility(String code, UUID id) {
        return turns.serialized(id, () -> transaction.execute(status -> {
            if (demo.getAgentInfo(code, id).isEmpty()) return new Eligibility(false, 0);
            return agents.findById(id).map(agent -> {
                boolean supported = projection.supports(agent);
                var leaf = ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
                long grace = supported && agent.getInteractionProfile().isPresenceControlledLive()
                        && leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external
                        ? Math.max(0, Math.min(2000, external.liveDepartureGraceMs())) : 0;
                return new Eligibility(supported, grace);
            }).orElse(new Eligibility(false, 0));
        }));
    }
    public Optional<LiveContextSnapshot> snapshot(String code, UUID id) {
        return turns.serialized(id, () -> transaction.execute(status -> {
            if (demo.getAgentInfo(code, id).isEmpty()) return Optional.empty();
            return agents.findById(id).map(projection::project);
        }));
    }

    public Optional<LiveContextSnapshot> claim(String code, UUID id, UUID session) {
        return turns.serialized(id, () -> {
            if (ownership.hasOwner(id)) throw new LiveSessionUnavailableException(true);
            var result = transaction.execute(status -> {
                if (demo.getAgentInfo(code, id).isEmpty()) return Optional.<LiveContextSnapshot>empty();
                return agents.findById(id).map(agent -> {
                    if (!projection.supports(agent)) throw new IllegalArgumentException("Unsupported Live policy");
                    var leaf = ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
                    if (leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy external) {
                        external.beginLiveConversation(agent.getEventHistory());
                        agents.save(agent);
                    }
                    return projection.project(agent);
                });
            });
            result.ifPresent(context -> {
                ownership.acquire(id, new ch.zhaw.prometheus.model.policy.ExternalSpeech(session, context.epoch()),
                        demo.speechScope(code, id).orElseThrow(DemoAccessDeniedException::new));
                turns.discardSpeculation(id, "external_speech_started");
            });
            return result;
        });
    }

    public void closed(UUID id, ch.zhaw.prometheus.model.policy.ExternalSpeech owner) {
        turns.serialized(id, () -> {
            String eventType = transaction.execute(status -> agents.findById(id)
                    .filter(agent -> owner.equals(ownership.current(agent)))
                    .map(agent -> agent.getCurrentState().ownPolicy())
                    .filter(ch.zhaw.prometheus.model.policy.ExternalVoicePolicy.class::isInstance)
                    .map(ch.zhaw.prometheus.model.policy.ExternalVoicePolicy.class::cast)
                    .map(ch.zhaw.prometheus.model.policy.ExternalVoicePolicy::liveSessionEndEventType).orElse(null));
            if (eventType != null) turns.acknowledge(id, new ch.zhaw.prometheus.controllers.views.EventRequest(
                    eventType, "system", ch.zhaw.prometheus.model.event.Event.KIND_SYSTEM, "live_session_ended"));
            return null;
        });
    }

    public record Update(LiveContextSnapshot context, java.util.List<ch.zhaw.prometheus.application.live.LiveContextDelivery.Narration> narrations) {}
    public Optional<Update> refresh(UUID id, ch.zhaw.prometheus.model.policy.ExternalSpeech owner) {
        return turns.serialized(id, () -> transaction.execute(status -> agents.findById(id)
                .filter(agent -> owner.equals(ownership.current(agent))).map(agent -> {
                    if (taskResponses != null && taskResponses.expire(agent, owner)) agents.saveAndFlush(agent);
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
