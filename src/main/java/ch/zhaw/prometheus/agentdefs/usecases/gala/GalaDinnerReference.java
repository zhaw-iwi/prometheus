package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/** One packaged reference; provenance stays outside model instructions. */
final class GalaDinnerReference {
    static final String RESOURCE = "/ch/zhaw/prometheus/agentdefs/usecases/gala/reference.json";
    private static final Document DOCUMENT = load();
    private static final String CONTEXT = render(DOCUMENT);
    private static final String RECEPTION_CONTEXT = render(DOCUMENT, false);
    private static final String GAMES_CONTEXT = render(DOCUMENT, true, false);

    record Source(String id, String description, List<String> urls) {}
    record Section(String id, String title, List<String> sourceIds, List<String> facts,
            String activityFacts, String receptionOnlyActivityFacts, String gamesOnlyActivityFacts) {
        Section(String id, String title, List<String> sourceIds, List<String> facts) {
            this(id, title, sourceIds, facts, null, null, null);
        }
    }
    record Document(String recordedOn, List<Source> sources, List<Section> sections) {}

    private GalaDinnerReference() {}

    static String context() { return CONTEXT; }
    static Document document() { return DOCUMENT; }
    static String context(boolean activities) { return activities ? CONTEXT : RECEPTION_CONTEXT; }
    static String context(boolean activities, boolean coCreatedTasks) {
        return !activities ? RECEPTION_CONTEXT : coCreatedTasks ? CONTEXT : GAMES_CONTEXT;
    }

    /** Approved institutional sections shared by event clones; Gala event/venue sections stay separate. */
    static final Set<String> INSTITUTION_SECTIONS = Set.of("sml", "iwi", "sira", "gigi");

    /** Render the shared institutional facts followed by one event's own sourced sections. */
    static String eventContext(String resource, String title) {
        var event = load(resource);
        var sections = new ArrayList<>(DOCUMENT.sections().stream()
                .filter(section -> INSTITUTION_SECTIONS.contains(section.id())).toList());
        var sourceIds = sections.stream().flatMap(section -> section.sourceIds().stream()).collect(Collectors.toSet());
        var sources = new ArrayList<>(DOCUMENT.sources().stream().filter(source -> sourceIds.contains(source.id())).toList());
        sources.addAll(event.sources());
        sections.addAll(event.sections());
        return render(new Document(event.recordedOn(), sources, sections), true, false, title);
    }

    private static Document load() {
        return load(RESOURCE);
    }
    static Document load(String resource) {
        var input = GalaDinnerReference.class.getResourceAsStream(resource);
        if (input == null) throw new IllegalStateException("Missing event reference: " + resource);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Document.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load event reference: " + resource, failure);
        }
    }

    static String render(Document document) {
        return render(document, true);
    }
    static String render(Document document, boolean activities) {
        return render(document, activities, activities);
    }
    static String render(Document document, boolean activities, boolean coCreatedTasks) {
        return render(document, activities, coCreatedTasks, "GALA REFERENZ");
    }
    static String render(Document document, boolean activities, boolean coCreatedTasks, String title) {
        if (document == null) throw new IllegalArgumentException("Gala reference is required");
        LocalDate.parse(document.recordedOn());
        if (document.sources() == null || document.sources().isEmpty()
                || document.sections() == null || document.sections().isEmpty())
            throw new IllegalArgumentException("Gala reference needs sources and sections");
        Set<String> sourceIds = new HashSet<>(), sectionIds = new HashSet<>();
        for (var source : document.sources()) {
            if (source == null || blank(source.id()) || blank(source.description())
                    || source.urls() == null || !sourceIds.add(source.id()))
                throw new IllegalArgumentException("Invalid or duplicate Gala reference source");
        }
        for (var section : document.sections()) {
            if (section == null || blank(section.id()) || blank(section.title()) || !sectionIds.add(section.id())
                    || section.sourceIds() == null || section.sourceIds().isEmpty()
                    || !sourceIds.containsAll(section.sourceIds()) || section.facts() == null
                    || section.facts().isEmpty() || section.facts().stream().anyMatch(GalaDinnerReference::blank))
                throw new IllegalArgumentException("Invalid or unsourced Gala reference section");
            if ((section.activityFacts() != null || section.receptionOnlyActivityFacts() != null || section.gamesOnlyActivityFacts() != null)
                    && (blank(section.activityFacts()) || blank(section.receptionOnlyActivityFacts()) || blank(section.gamesOnlyActivityFacts())))
                throw new IllegalArgumentException("All Gala capability variants are required");
        }
        // Render approved facts only: no source URLs, audit descriptions or duplicate summaries.
        return title + " (Stand der bereitgestellten Angaben: " + document.recordedOn() + ").\n"
                + document.sections().stream()
                    .map(section -> section.title() + ": " + String.join(" ", section.facts())
                            + (section.activityFacts() == null ? "" : " " + (!activities ? section.receptionOnlyActivityFacts()
                                    : coCreatedTasks ? section.activityFacts() : section.gamesOnlyActivityFacts())))
                    .collect(Collectors.joining("\n"));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
