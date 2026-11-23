package ch.zhaw.prometheus.application;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.*;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.model.policy.PromptMessage;

/** Durable latest-value capture, independent of the slower serialized decision turn. */
@Service
public class SceneTargetCaptureService {
    public record Admission(UUID epoch,boolean active,String snapshot) {}
    private record Capture(UUID epoch,int generation,JsonObject snapshot) {}
    private final AgentRepository agents;
    private final SceneTargetCaptureRepository captures;
    private final TransactionTemplate transaction;
    private final AgentTurnSerialiser ordering=new AgentTurnSerialiser();
    public SceneTargetCaptureService(AgentRepository agents,SceneTargetCaptureRepository captures,PlatformTransactionManager manager) {
        this.agents=agents;this.captures=captures;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(3);
    }
    public Optional<Admission> admit(UUID id,EventRequest request) {
        return ordering.call(id,()->transaction.execute(status->{
            var found=agents.findById(id);if(found.isEmpty())return Optional.empty();var agent=found.get();
            if(!HandFeatures.dynamicTargets(agent.getInteractionProfile())
                    ||!agent.getInteractionProfile().supportsObservation(Event.TYPE_SCENE_TARGETS)
                    ||!Event.TYPE_SCENE_TARGETS.equals(request.getType())||!Event.KIND_OBSERVATION.equals(request.getKind()))
                throw new IllegalArgumentException("Detected targets not declared");
            var saved=captures.findById(id).orElse(null);var data=new Storage();var existing=agent.getStorage();
            if(saved!=null&&saved.epoch().equals(agent.executionEpoch()))data.put(ObservedTargets.KEY,saved.snapshot());
            else if(existing.containsKey(ObservedTargets.KEY))data.put(ObservedTargets.KEY,existing.get(ObservedTargets.KEY).deepCopy());
            int generation=existing.containsKey(ObservedTargets.COUNTER)?existing.get(ObservedTargets.COUNTER).getAsInt():0;
            if(saved!=null)generation=Math.max(generation,saved.generation());
            data.put(ObservedTargets.COUNTER,new JsonPrimitive(generation));
            var event=new Event(request.getType(),request.getActor(),request.getKind(),request.getPayload());
            if(ObservedTargets.admit(data,id,event,Instant.now())) {
                var combined=new HashMap<>(existing);combined.putAll(data.toMap());HandStateBudget.require(combined);
                if(saved==null)saved=new SceneTargetCapture(id);
                saved.replace(agent.executionEpoch(),data.get(ObservedTargets.COUNTER).getAsInt(),ObservedTargets.snapshot(data));
                captures.saveAndFlush(saved);
            }
            var wire=ObservedTargets.snapshot(data);wire.remove("digest");
            return Optional.of(new Admission(agent.executionEpoch(),agent.isActive(),wire.toString()));
        }));
    }
    public boolean refresh(Agent agent) {
        if(!HandFeatures.dynamicTargets(agent.getInteractionProfile()))return false;
        // A new short transaction sees captures committed while a Live task transaction
        // was waiting for inference. Never change that transaction's isolation level.
        Capture capture=transaction.execute(status->captures.latest(agent.getId())
                .map(c->new Capture(c.getEpoch(),c.getGeneration(),JsonParser.parseString(c.getSnapshot()).getAsJsonObject())).orElse(null));
        return capture!=null&&capture.epoch().equals(agent.executionEpoch())
                &&agent.recordSceneCapture(capture.snapshot(),capture.generation(),Instant.now());
    }
    public LanguageModelGateway gateway(Agent agent,LanguageModelGateway delegate) {
        if(!HandFeatures.dynamicTargets(agent.getInteractionProfile()))return delegate;
        Thread decisionThread=Thread.currentThread();
        return new LanguageModelGateway() {
            @Override public String infer(InferenceRequest request) {
                try { return delegate.infer(request); }
                finally {
                    // Speculation, background actions and guard workers must never mutate
                    // the aggregate captured by the owning serialized decision thread.
                    if(Thread.currentThread()==decisionThread
                            &&(request.purpose()==InferencePurpose.EXTRACTION||request.purpose()==InferencePurpose.BEHAVIOUR))refresh(agent);
                }
            }
            @Override public String complete(List<PromptMessage> m) { return delegate.complete(m); }
            @Override public boolean decide(List<PromptMessage> m) { return delegate.decide(m); }
            @Override public JsonElement extract(List<PromptMessage> m) { return delegate.extract(m); }
            @Override public JsonElement summarise(List<PromptMessage> m) { return delegate.summarise(m); }
            @Override public String summariseOffline(List<PromptMessage> m) { return delegate.summariseOffline(m); }
            @Override public GuardInferenceOptions guardInferenceOptions() { return delegate.guardInferenceOptions(); }
            @Override public GuardInferenceExecutor guardExecutor() { return delegate.guardExecutor(); }
            @Override public Object guardCompatibilityKey(InferenceRequest request) { return delegate.guardCompatibilityKey(request); }
            @Override public boolean supportsBehaviourSpeculation() { return delegate.supportsBehaviourSpeculation(); }
        };
    }
}
