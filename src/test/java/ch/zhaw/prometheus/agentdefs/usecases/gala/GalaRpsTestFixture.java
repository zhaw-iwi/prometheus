package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;

/** Explicit simulated capture/playback for tests whose subject begins after RPS readiness. */
final class GalaRpsTestFixture {
    static Event countdownPlayed(Agent agent, PolicyRuntime runtime) {
        var policy = (GalaDinnerPolicy) agent.getCurrentState().ownPolicy();
        var storage = policy.storage(); var owner = runtime.externalSpeech();
        var speech = agent.recordExternalSpeech("Zeig mir deine Hand. Eins, zwei, drei.",
                new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                        UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), true));
        policy.liveSpeechCaptured(speech, owner, Instant.now(), Instant.now());
        return agent.acknowledge(new Event(GalaDinnerPolicy.COUNTDOWN_PLAYED, "system", Event.KIND_SYSTEM,
                text(storage, GalaDinnerRps.INTRO, "")), runtime);
    }
}
