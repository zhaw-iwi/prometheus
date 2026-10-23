package ch.zhaw.prometheus.agentdefs.usecases;

import java.util.List;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerReceptionOnlyPolicy;
import ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerState;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;

/** Reception-only backup sharing the full Gala's encounter, knowledge and sensing behavior. */
public class GalaDinnerReceptionSingleState extends GalaDinnerReception {
    public static final String KEY = "usecases.gala_dinner_reception_single_state";

    @Override public String key() { return KEY; }

    @Override public Agent createAgent() {
        var storage = new Storage();
        var reception = new GalaDinnerState("Gala reception", new GalaDinnerReceptionOnlyPolicy(storage), true);
        addReceptionTransitions(List.of(reception), reception, storage, "RECEPTION_TURN");
        addAmbientTransitions(reception, reception, storage);
        return completeAgent(reception, storage, false);
    }
}
