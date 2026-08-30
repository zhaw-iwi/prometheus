package ch.zhaw.prometheus.definition.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.definition.document.PromptDefinition;
import ch.zhaw.prometheus.definition.document.PromptSection;

class PromptComposerUnitTest {
    private final PromptComposer composer = new PromptComposer();

    @Test
    void composesExactSectionOrderSeparatorAndNormalizedLineEndings() {
        PromptDefinition prompt = new PromptDefinition(List.of(
                new PromptSection("persona", "persona", "First\r\nline"),
                new PromptSection("objective", "objective", " Second section "),
                new PromptSection("boundary", "boundary", "Third\rsection")));

        assertEquals("First\nline\n\n Second section \n\nThird\nsection", this.composer.compose(prompt));
    }

    @Test
    void normalizationPreservesIdsKindsOrderingAndNonLineEndingWhitespace() {
        PromptDefinition original = new PromptDefinition(List.of(
                new PromptSection("starter", "starter", "  Hello\r\nthere  ")));

        PromptDefinition normalized = this.composer.normalize(original);

        assertEquals("starter", normalized.sections().get(0).id());
        assertEquals("starter", normalized.sections().get(0).kind());
        assertEquals("  Hello\nthere  ", normalized.sections().get(0).content());
        assertEquals("  Hello\r\nthere  ", original.sections().get(0).content());
    }

    @Test
    void emptyPromptComposesToEmptyAndBlankSectionIsRejected() {
        assertEquals("", this.composer.compose(new PromptDefinition(List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> this.composer.compose(new PromptDefinition(List.of(
                        new PromptSection("blank", "objective", "  \n")))));
    }
}
