package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import java.util.List;
import java.util.random.RandomGenerator;

import ch.zhaw.prometheus.model.Storage;

final class HealthcareTherapyAppointmentContexts {
    static final String STORAGE_KEY = "therapyAppointmentContext";

    private static final List<TherapyAppointmentContext> CONTEXTS = List.of(
            new TherapyAppointmentContext(
                    "physiotherapy",
                    "physiotherapy",
                    "balance, strength, walking, mobility, and safe movement",
                    List.of("walking with support", "gentle strength practice", "mobility exercises")),
            new TherapyAppointmentContext(
                    "occupational_therapy",
                    "occupational therapy",
                    "daily activities such as dressing, bathing, eating, grip, and safe routines",
                    List.of("buttoning clothes", "practicing grip", "using adaptive utensils")),
            new TherapyAppointmentContext(
                    "activation",
                    "activation",
                    "gentle physical, cognitive, creative, or social activity for daily wellbeing",
                    List.of("a short movement activity", "a memory or thinking activity",
                            "a creative or social activity")));

    private HealthcareTherapyAppointmentContexts() {
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

