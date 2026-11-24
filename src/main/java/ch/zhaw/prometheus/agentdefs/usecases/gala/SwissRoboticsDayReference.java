package ch.zhaw.prometheus.agentdefs.usecases.gala;

/** Share the approved institutional facts; supply only the new event's sourced sections. */
final class SwissRoboticsDayReference {
    static final String RESOURCE = "/ch/zhaw/prometheus/agentdefs/usecases/swissroboticsday/reference.json";
    private static final String CONTEXT = GalaDinnerReference.eventContext(RESOURCE, "SWISS ROBOTICS DAY REFERENZ");

    private SwissRoboticsDayReference() {}
    static String context() { return CONTEXT; }
}
