package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    record Source(String id, String description, List<String> urls) {}
    record Section(String id, String title, List<String> sourceIds, List<String> facts) {}
    record Document(String recordedOn, List<Source> sources, List<Section> sections) {}

    private GalaDinnerReference() {}

    static String context() { return CONTEXT; }

    private static Document load() {
        var input = GalaDinnerReference.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing Gala reference: " + RESOURCE);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Document.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load Gala reference: " + RESOURCE, failure);
        }
    }

    static String render(Document document) {
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
        }
        // Render approved facts only: no source URLs, audit descriptions or duplicate summaries.
        return "GALA REFERENZ (Stand der bereitgestellten Angaben: " + document.recordedOn() + ").\n"
                + document.sections().stream()
                    .map(section -> section.title() + ": " + String.join(" ", section.facts()))
                    .collect(Collectors.joining("\n"));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
