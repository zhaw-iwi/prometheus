package ch.zhaw.prometheus.agentdefs.tdsr.davos;

import java.util.List;
import java.util.random.RandomGenerator;

import ch.zhaw.prometheus.model.Storage;

final class DavosTherapyAppointmentContexts {
    static final String STORAGE_KEY = "therapyAppointmentContext";

    private static final List<TherapyAppointmentContext> CONTEXTS = List.of(
            new TherapyAppointmentContext(
                    "physical_therapy",
                    "physical therapy",
                    "balance, strength, walking, and mobility",
                    List.of("walking with support", "gentle strength practice", "flexibility exercises")),
            new TherapyAppointmentContext(
                    "occupational_therapy",
                    "occupational therapy",
                    "daily activities such as dressing, bathing, eating, grip, and safe routines",
                    List.of("buttoning clothes", "practicing grip", "using adaptive utensils")),
            new TherapyAppointmentContext(
                    "speech_language_therapy",
                    "speech-language therapy",
                    "communication, safe swallowing, memory, and thinking support",
                    List.of("safe swallowing practice", "communication drills", "memory exercises")),
            new TherapyAppointmentContext(
                    "cognitive_mental_health_therapy",
                    "cognitive and mental health therapy",
                    "mood, anxiety, memory, adjustment, and positive reminiscence",
                    List.of("talking through worries", "reminiscence with photos or music",
                            "small memory-building activities")));

    private DavosTherapyAppointmentContexts() {
    }

    static List<TherapyAppointmentContext> all() {
        return CONTEXTS;
    }

    static TherapyAppointmentContext select(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        return CONTEXTS.get(random.nextInt(CONTEXTS.size()));
    }

    static void preselect(Storage storage, RandomGenerator random) {
        if (storage == null) {
            throw new IllegalArgumentException("storage must not be null");
        }
        if (storage.containsKey(STORAGE_KEY)) {
            return;
        }
        storage.put(STORAGE_KEY, Storage.toJsonElement(select(random)));
    }

    record TherapyAppointmentContext(String type, String label, String safeFocus, List<String> examples) {
    }
}
