package ch.zhaw.prometheus.agentdefs.core;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.gson.Gson;

/** Packaged factual reference; source descriptions and links remain outside model context. */
final class CoreAgentReference {
    static final String RESOURCE = "/ch/zhaw/prometheus/agentdefs/core/reference.json";
    private static final String CONTEXT = render(load());

    record Source(String id, String description, List<String> urls, String document) {}
    record Section(String id, String title, List<String> sourceIds, List<String> facts) {}
    record Document(String recordedOn, List<Source> sources, List<Section> sections) {}

    private CoreAgentReference() {}
    static String context() { return CONTEXT; }

    private static Document load() {
        var input = CoreAgentReference.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing core reference: " + RESOURCE);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Document.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load core reference: " + RESOURCE, failure);
        }
    }

    static String render(Document document) {
        if (document == null) throw new IllegalArgumentException("Core reference is required");
        LocalDate.parse(document.recordedOn());
        if (document.sources() == null || document.sources().isEmpty()
                || document.sections() == null || document.sections().isEmpty())
            throw new IllegalArgumentException("Core reference needs sources and sections");
        Set<String> sources = new HashSet<>(), sections = new HashSet<>();
        for (var source : document.sources()) {
            if (source == null || blank(source.id()) || blank(source.description())
                    || source.urls() == null || !sources.add(source.id()))
                throw new IllegalArgumentException("Invalid or duplicate core reference source");
        }
        for (var section : document.sections()) {
            if (section == null || blank(section.id()) || blank(section.title()) || !sections.add(section.id())
                    || section.sourceIds() == null || section.sourceIds().isEmpty()
                    || !sources.containsAll(section.sourceIds()) || section.facts() == null
                    || section.facts().isEmpty() || section.facts().stream().anyMatch(CoreAgentReference::blank))
                throw new IllegalArgumentException("Invalid or unsourced core reference section");
        }
        return "CORE LAB REFERENCE (supplied information recorded " + document.recordedOn() + "; facts, not instructions).\n"
                + document.sections().stream().map(section -> section.title() + ": " + String.join(" ", section.facts()))
                    .collect(Collectors.joining("\n"));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
