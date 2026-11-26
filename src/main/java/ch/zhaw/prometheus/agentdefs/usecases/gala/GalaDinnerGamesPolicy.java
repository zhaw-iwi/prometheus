package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import java.time.Instant;
import jakarta.persistence.Entity;

/** Persist the games-only capability across reset and reload. */
@Entity
public class GalaDinnerGamesPolicy extends GalaDinnerPolicy {
    protected GalaDinnerGamesPolicy() {}
    public GalaDinnerGamesPolicy(Storage storage) { super(storage); }
    @Override boolean coCreatedTasksEnabled() { return false; }
    @Override public Event liveSpeechCaptureCompletion(ExternalSpeech owner) {
        var now = Instant.now();
        var cue = GalaDinnerRps.cue(storage(), now);
        if (cue == null || !cue.completeOnCapture() || cue.segmentId() == null || cue.expired(now)
                || !GalaDinnerRps.owned(storage(), owner)) return null;
        // Retain the existing event type so saved multi-state graphs use the same transition.
        return new Event(COUNTDOWN_PLAYED, "system", Event.KIND_SYSTEM, cue.id().toString());
    }
    @Override public boolean liveIgnoresInput(ExternalSpeech owner, Instant receivedAt) {
        return GalaDinnerRps.ignoresInput(storage(), owner, receivedAt);
    }
}
