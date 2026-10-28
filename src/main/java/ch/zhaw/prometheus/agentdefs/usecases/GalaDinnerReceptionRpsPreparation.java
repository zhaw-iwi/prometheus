package ch.zhaw.prometheus.agentdefs.usecases;

import ch.zhaw.prometheus.model.Agent;

/** Separate Gala variant with a three-sign recognition check once per Live conversation. */
public class GalaDinnerReceptionRpsPreparation extends GalaDinnerReception {
    public static final String KEY = "usecases.gala_dinner_reception_rps_preparation";

    @Override public String key() { return KEY; }
    @Override public Agent createAgent() { return createAgent(true); }
}
