package ch.zhaw.prometheus.definition.prompt;

import java.util.List;
import java.util.stream.Collectors;

import ch.zhaw.prometheus.definition.document.PromptDefinition;
import ch.zhaw.prometheus.definition.document.PromptSection;

public final class PromptComposer {
    public static final String SECTION_SEPARATOR = "\n\n";

    public String compose(PromptDefinition prompt) {
        if (prompt == null) {
            return "";
        }
        return compose(prompt.sections());
    }

    public String compose(List<PromptSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        return sections.stream()
                .map(PromptComposer::requireContent)
                .map(PromptComposer::normalizeLineEndings)
                .collect(Collectors.joining(SECTION_SEPARATOR));
    }

    public PromptDefinition normalize(PromptDefinition prompt) {
        if (prompt == null) {
            return null;
        }
        return new PromptDefinition(prompt.sections().stream()
                .map(section -> new PromptSection(section.id(), section.kind(),
                        normalizeLineEndings(requireContent(section))))
                .toList());
    }

    public static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String requireContent(PromptSection section) {
        if (section == null || section.content() == null || section.content().isBlank()) {
            throw new IllegalArgumentException("Prompt sections must contain nonblank content");
        }
        return section.content();
    }
}
