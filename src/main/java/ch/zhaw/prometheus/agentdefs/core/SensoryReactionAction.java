package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;

/** Observe every refresh locally, but create at most one expiring comment for a meaningful change. */
@Entity
public class SensoryReactionAction extends Action {
    private long stabilityMs, conversationQuietMs, remarkCooldownMs;
    protected SensoryReactionAction() {}
    public SensoryReactionAction(Storage storage, String instructions, long stabilityMs, long quietMs, long cooldownMs) {
        super(ValerianCoreAgentFactory.corePromptPolicy(instructions, null), storage, TaskMemory.REPLY);
        this.stabilityMs = stabilityMs; this.conversationQuietMs = quietMs; this.remarkCooldownMs = cooldownMs; blocking();
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage(); var now = Instant.now();
        if (events.isEmpty()) return;
        SensoryRemarks.supersede(storage, events.toList().getLast(), now);
        if (!runtime.taskContinuation().getAsBoolean() || TaskMemory.active(storage) || "PAUSED".equals(TaskMemory.phase(storage))) return;
        var change = SensoryRemarks.change(events, storage, now, stabilityMs, conversationQuietMs, remarkCooldownMs).orElse(null);
        if (change == null) return;
        SensoryRemarks.attempted(storage, now);
        if (runtime.externalSpeech() != null) { SensoryRemarks.occasion(storage, change, now); return; }
        var policy = ValerianCoreAgentFactory.corePromptPolicy(getPolicy().describe() + "\n" + SensoryRemarks.guidance(change), null);
        try {
            var plan = policy.onRespond(null, events, runtime.promptMessageAssembler(), runtime.languageModelGateway());
            // A provider delay cannot turn expired evidence into a current remark.
            if (runtime.taskContinuation().getAsBoolean() && Instant.now().isBefore(change.observed().plusSeconds(15)) && plan != null)
                storage.put(TaskMemory.REPLY, TaskMemory.plan(plan.toJsonObject()).toJsonObject());
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(SensoryReactionAction.class).warn("Sensory remark discarded after generation or validation failure");
        }
    }
}
