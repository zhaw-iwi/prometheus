package ch.zhaw.prometheus.agentdefs.usecases.gala;

/** Share the approved institutional facts; supply only Open-i's sourced sections. */
final class OpenIReference {
    static final String RESOURCE = "/ch/zhaw/prometheus/agentdefs/usecases/openi/reference.json";
    private static final String CONTEXT = GalaDinnerReference.eventContext(RESOURCE, "OPEN-I REFERENZ");

    private OpenIReference() {}
    static String context() { return CONTEXT; }
}
