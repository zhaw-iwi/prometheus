package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

/** Persist the event setting independently of mutable game/session storage. */
@Entity
public class SwissRoboticsDayPolicy extends GalaDinnerGamesPolicy {
    protected SwissRoboticsDayPolicy() {}
    public SwissRoboticsDayPolicy(Storage storage) { super(storage); }

    @Override String eventName() { return "Swiss Robotics Day"; }
    @Override String robotRole() { return "robot at Swiss Robotics Day"; }
    @Override String referenceContext() { return SwissRoboticsDayReference.context(); }

    @Override public String liveOpeningInstructions() {
        if (GalaDinnerLanguage.english(storage()))
            return "At the beginning of this new voice session only, greet the visible visitors once in English: "
                    + "\"Hello, I'm djee-djee from the SIRA Lab at ZHAW. Welcome to Swiss Robotics Day!\" "
                    + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
        return "At the beginning of this new voice session only, greet the visible visitors once in German: "
                + "\"Grüezi, ich bin djee-djee vom SIRA Lab der ZHAW. Willkommen am Swiss Robotics Day!\" "
                + "Then listen. Do not repeat this greeting on state changes or refreshed observations.";
    }
}
