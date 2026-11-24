package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

/** Persist the Open-i setting independently of mutable game/session storage. */
@Entity
public class OpenIPolicy extends GalaDinnerGamesPolicy {
    protected OpenIPolicy() {}
    public OpenIPolicy(Storage storage) { super(storage); }

    @Override String eventName() { return "Open-i"; }
    @Override String robotRole() { return "robot at Open-i"; }
    @Override String referenceContext() { return OpenIReference.context(); }

    @Override public String liveOpeningInstructions() {
        if (GalaDinnerLanguage.english(storage()))
            return "At the beginning of this new voice session only, greet the visible visitors once in English: "
                    + "\"Hello, I'm djee-djee from the SIRA Lab at ZHAW. Welcome to Open-i!\" "
                    + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
        return "At the beginning of this new voice session only, greet the visible visitors once in German: "
                + "\"Grüezi, ich bin djee-djee vom SIRA Lab der ZHAW. Willkommen an der Open-i!\" "
                + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
    }
}
