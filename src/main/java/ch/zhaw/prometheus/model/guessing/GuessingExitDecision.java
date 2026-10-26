package ch.zhaw.prometheus.model.guessing;

import java.util.Locale;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import jakarta.persistence.*;

/** A binary game answer or give-up/reveal must not be mistaken for closing the whole interaction. */
@Entity
public class GuessingExitDecision extends Decision {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER) private Storage guessingStorage;
    protected GuessingExitDecision() {}
    public GuessingExitDecision(Storage storage) {
        super(new NoOpPolicy()); guessingStorage = storage;
    }
    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        if (events.isEmpty() || !Event.TYPE_USER_UTTERANCE.equals(events.toList().getLast().getType())) return false;
        String text = events.toList().getLast().getPayload();
        if (GuessingMemory.giveUp(text) || GuessingMemory.active(guessingStorage)
                && (GuessingMemory.harmless(text) || text.trim().toLowerCase(Locale.ROOT).matches("(yes|no|yes please|no thanks)[.!]?"))) return false;
        return true; // The following ordinary StaticDecision checks closing intent in context.
    }
}
