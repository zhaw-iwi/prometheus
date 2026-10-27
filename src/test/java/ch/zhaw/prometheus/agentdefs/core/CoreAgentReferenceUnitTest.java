package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreAgentReferenceUnitTest {
    @Test void packagedFactsCoverRequestedInstitutionsAndPeopleWithoutEventOrAuditText() {
        String facts = CoreAgentReference.context();
        for (String value : List.of("Institute of Information Systems", ValerianCorePrompts.SIRA_NAME,
                "Theresa Schmiedel", "Marc Styger", "Jasmin Grassmugg", "Alexandre de Spindler",
                "Unitree G1", "Robot Operator & Robot Show Designer", "PROMISE", "Social Behaviour Regulation",
                "PROMETHEUS", "participatory design", "20 October 2026", "not a confirmed formal Director title"))
            assertTrue(facts.contains(value), value);
        for (String excluded : List.of("Reto Steiner", "SML", "School of Management", "Gala Dinner", "Laureline",
                "http", "ALEXANDREDESPINDLER.md", "markdowns/persons", "iwi-briefing", "not independently reverified"))
            assertFalse(facts.contains(excluded), excluded);
        assertTrue(ValerianPersona.context().contains(facts));
        assertTrue(ValerianPersona.CHARACTER.contains("fictional"));
        assertTrue(ValerianPersona.CHARACTER.contains("Impulsiveness is tone, never permission to act"));
    }

    @Test void renderingUsesFactsOnlyAndRejectsBrokenProvenance() {
        var source = new CoreAgentReference.Source("approved", "Audit text", List.of("https://example.org"), "private/source.md");
        var section = new CoreAgentReference.Section("one", "A title", List.of("approved"), List.of("A fact."));
        var valid = new CoreAgentReference.Document("2026-10-27", List.of(source), List.of(section));
        String rendered = CoreAgentReference.render(valid);
        assertTrue(rendered.endsWith("A title: A fact."));
        assertFalse(rendered.contains("Audit text"));
        assertFalse(rendered.contains("https://"));
        assertFalse(rendered.contains("private/source.md"));
        assertThrows(IllegalArgumentException.class, () -> CoreAgentReference.render(
                new CoreAgentReference.Document("2026-10-27", List.of(source), List.of(
                        new CoreAgentReference.Section("one", "Title", List.of("unknown"), List.of("Fact"))))));
        assertThrows(IllegalArgumentException.class, () -> CoreAgentReference.render(
                new CoreAgentReference.Document("2026-10-27", List.of(source, source), List.of(section))));
        assertThrows(IllegalArgumentException.class, () -> CoreAgentReference.render(
                new CoreAgentReference.Document("2026-10-27", List.of(source), List.of(section, section))));
        assertThrows(IllegalArgumentException.class, () -> CoreAgentReference.render(
                new CoreAgentReference.Document("2026-10-27", List.of(source), List.of(
                        new CoreAgentReference.Section("one", "Title", List.of("approved"), List.of(" "))))));
    }
}
