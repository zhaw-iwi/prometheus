package ch.zhaw.prometheus.agentdefs.usecases;

import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** RPS-preparation clone retaining reception and both games without co-created tasks. */
public class GalaDinnerReceptionMultiStateGames extends GalaDinnerReceptionRpsPreparation {
    public static final String KEY = "usecases.gala_dinner_reception_multi_state_games";

    @Override public String key() { return KEY; }
    @Override public Agent createAgent() { return createAgent(true, false); }

    @Override protected Agent completeAgent(GalaDinnerState reception, Storage storage, boolean activities) {
        return completeAgent(reception, storage, activities,
                "Gigi - Gala with multi-state RPS and guessing game",
                "German GIGI gala reception with conversation, information, multi-state RPS preparation and person guessing.");
    }
}
