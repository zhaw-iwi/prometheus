package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/** Share the approved institutional facts; supply only the new event's sourced sections. */
final class SwissRoboticsDayReference {
    static final String RESOURCE = "/ch/zhaw/prometheus/agentdefs/usecases/swissroboticsday/reference.json";
    private static final String CONTEXT = render();

    private SwissRoboticsDayReference() {}
    static String context() { return CONTEXT; }

    private static String render() {
        var shared = GalaDinnerReference.document();
        var event = GalaDinnerReference.load(RESOURCE);
        var sections = new ArrayList<>(shared.sections().stream()
                .filter(section -> Set.of("sml", "iwi", "sira", "gigi").contains(section.id())).toList());
        var sourceIds = sections.stream().flatMap(section -> section.sourceIds().stream()).collect(Collectors.toSet());
        var sources = new ArrayList<>(shared.sources().stream().filter(source -> sourceIds.contains(source.id())).toList());
        sources.addAll(event.sources());
        sections.addAll(event.sections());
        return GalaDinnerReference.render(new GalaDinnerReference.Document(event.recordedOn(), sources, sections),
                true, false, "SWISS ROBOTICS DAY REFERENZ");
    }
}
