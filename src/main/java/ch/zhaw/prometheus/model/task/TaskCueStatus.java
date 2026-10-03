package ch.zhaw.prometheus.model.task;

import java.time.Instant;
import java.util.UUID;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** Advisory client readiness from already-loaded state; guards retain authority. */
public record TaskCueStatus(UUID epoch, String phase, int revision, boolean draft,
        boolean responseCaptured, long nextCueInMs, String window) {
    public static TaskCueStatus of(Agent agent, Instant now) {
        if (agent == null || agent.getCurrentState() == null || !(agent.getCurrentState().ownPolicy() instanceof TaskPolicy policy)) return null;
        var storage = policy.storage(); var events = agent.getEventHistory().toList();
        ExternalSpeech owner = null; int intent = -1;
        for (int i = 0; i < events.size(); i++) {
            var source = events.get(i).speechProvenance();
            if (source != null && source.origin() == SpeechProvenance.Origin.BACKEND_INTENT && source.epoch().equals(agent.executionEpoch())
                    && (!storage.containsKey(TaskMemory.SESSION) || source.sessionId().toString().equals(TaskMemory.text(storage, TaskMemory.SESSION, "")))) {
                owner = new ExternalSpeech(source.sessionId(), source.epoch()); intent = i;
            }
        }
        long after = Instant.parse(TaskMemory.text(storage, TaskMemory.AFTER, Instant.EPOCH.toString())).toEpochMilli();
        int revision = Integer.parseInt(TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        return new TaskCueStatus(agent.executionEpoch(), TaskMemory.phase(storage), revision, storage.containsKey(TaskMemory.DRAFT),
                TaskDecision.firstSample(events, owner) >= 0, Math.max(0, after - now.toEpochMilli()), revision + "." + after + "." + intent);
    }
}
