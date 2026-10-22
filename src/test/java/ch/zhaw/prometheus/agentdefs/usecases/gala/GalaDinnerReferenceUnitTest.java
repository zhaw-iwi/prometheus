package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskSpec;

class GalaDinnerReferenceUnitTest {
    @Test void referenceRetainsSuppliedFactsCorrectionsAndDatedQualifications() {
        String context = GalaDinnerReference.context();
        for (String fact : List.of(
                "9'000", "AACSB seit 2015", "EQUIS seit 2023", "AMBA seit 2025", "Triple Crown",
                "190 Partnerhochschulen", "Reto Steiner", "Theresa Schmiedel",
                "Institut der SML", "Socially Intelligent and Responsible Agents",
                "soziale Topologie", "experimentell", "Unitree G1", "PROMETHEUS",
                "rund 90", "17:30 Door Opening und Networking", "18:30 Seating und Welcome Words",
                "14- bis 23", "CHF 1'000", "Name nicht bekannt",
                "früheren Gästeprogramm", "23:30", "kein Live-Fortschritt",
                "20.10.2026", "aktuelle Verfügbarkeit", "August–Dezember 2026",
                "CHF 250 im ersten Jahr", "25 %", "WinLink", "Pascale Hohl",
                "Noemi Ott", "Selina Sen", "Alicia De Oliveira", "Stefanie Langhart",
                "Stadthausstrasse 14", "Neuwiesenstrasse 15", "Klosterstrasse 34",
                "Bring Your Idea to Life", "Boost Your Business", "Winti Ventures", "zwei- bis viermal"))
            assertTrue(context.contains(fact), fact);
        assertFalse(context.contains("17:30 arrival, welcome drink, seating"));
    }

    @ParameterizedTest @EnumSource(AgentEmbodiment.class)
    void everyStateHasOneReferenceAndFitsWithMaximumTaskGoals(AgentEmbodiment embodiment) {
        var agent = new GalaDinnerReception().createAgent();
        agent.setEmbodiment(embodiment);
        var memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
        // TaskSpec permits 400 UTF-16 characters; three-byte BMP text is the largest UTF-8 case.
        var spec = new TaskSpec("界".repeat(400), 5, List.of(new TaskSpec.Rule(
                Event.TYPE_HAND_SIGN, "sign", "eq", new JsonPrimitive("paper"), "Ein Witz",
                TaskSpec.Effect.ACT, 0.8, 1, 3)));
        memory.put(TaskMemory.SPEC, spec.json());
        memory.put(TaskMemory.DRAFT, spec.json());
        GalaDinnerMemory.put(memory, GalaDinnerSensing.CORRECTED, "true");
        GalaDinnerMemory.put(memory, TaskMemory.REVISION, Integer.toString(Integer.MAX_VALUE));
        var adapter = new LiveVoicePolicyAdapter();
        var projection = new LiveContextProjection(new PromptMessageAssembler(), adapter);
        String stablePolicy = adapter.sections(agent).get("policy");
        int maximum = 0;
        for (var state : agent.reachableStates()) {
            ReflectionTestUtils.setField(agent, "currentState", state);
            GalaDinnerMemory.put(memory, GalaDinnerMemory.MODE, state.ownPolicy() instanceof GalaDinnerTaskPolicy ? "TASK" : "RECEPTION");
            for (String phase : List.of("CONFIGURATION", "RUNNING", "WAITING", "PAUSED", "COMPLETED")) {
                GalaDinnerMemory.put(memory, TaskMemory.PHASE, phase);
                String instructions = assertDoesNotThrow(() -> adapter.instructions(agent), state.getName() + " / " + phase);
                int bytes = instructions.getBytes(StandardCharsets.UTF_8).length;
                maximum = Math.max(maximum, bytes);
                assertTrue(bytes <= 14400, state.getName() + ": " + bytes);
                assertEquals(1, occurrences(instructions, "GALA REFERENZ (Stand"));
                assertEquals(1, occurrences(instructions, "Triple Crown"));
                assertTrue(instructions.contains("ich bin djee-djee"));
                assertTrue(instructions.contains("not a question echo"));
                assertEquals(1, occurrences(instructions, "occasional dry self-irony"));
                assertTrue(instructions.contains("truthful status and one useful next step"));
                assertTrue(instructions.contains("never a made-up cause"));
                assertTrue(instructions.contains("not a guessed age or identity"));
                assertTrue(instructions.contains("waiting acknowledgment or promise"));
                assertTrue(instructions.contains("Wait silently for confirmed starting facts"));
                assertFalse(instructions.contains("You may acknowledge immediately"));
                assertFalse(instructions.contains("you may say you are checking"));
                assertTrue(instructions.contains("One response per visitor turn"));
                assertTrue(instructions.contains("You own Gala conversation"));
                assertTrue(instructions.contains("Generate <|SpawnThinking|>"));
                for (String heading : List.of("# Backchannel policy", "# Interruption policy", "# Delegation policy"))
                    assertEquals(1, occurrences(instructions, heading));
                assertFalse(instructions.contains("IPA"));
                assertFalse(instructions.contains("dʒ"));
                assertFalse(instructions.contains("GIGI"));
                assertFalse(instructions.contains("https://"));
                assertFalse(instructions.contains("http://"));
                assertTrue(projection.project(agent).startupInput().toString().contains("ich bin djee-djee"));
                assertEquals(stablePolicy, adapter.sections(agent).get("policy"), state.getName());
                assertEquals(2000, projection.project(agent).userSilenceMs());
                assertTrue(projection.project(agent).filterIsolatedInputActivity());
                if (state.ownPolicy() instanceof GalaDinnerTaskPolicy) {
                    var facts = projection.project(agent).activity().facts();
                    assertTrue(facts.contains("Agreed goal: " + spec.goal()));
                    assertTrue(facts.contains("Proposed goal: " + spec.goal()));
                    assertTrue(facts.contains("Task phase: " + phase));
                    assertFalse(instructions.contains(spec.goal()));
                }
                assertEquals("GIGI", agent.getPersonaName());
            }
        }
        System.out.println("Gala maximum Live instructions (" + embodiment + ", two maximum task goals): " + maximum + " bytes");
    }

    @Test void everySelectedCardIsBoundedAndSecrecyPrecedesTheTarget() {
        var agent = new GalaDinnerReception().createAgent();
        var memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter());
        GalaDinnerMemory.put(memory, GalaDinnerMemory.MODE, "GUESS");
        for (var person : GalaDinnerPeople.PEOPLE) {
            GalaDinnerMemory.put(memory, GalaDinnerMemory.PERSON, person.name());
            var context = assertDoesNotThrow(() -> projection.project(agent));
            assertTrue(context.instructions().contains("identity is SECRET"));
            assertFalse(context.instructions().contains(person.name()));
            assertTrue(context.activity().facts().contains("Secret target (never introduce it): " + person.name()));
            assertTrue(context.activity().facts().getBytes(StandardCharsets.UTF_8).length <= 4000);
        }
        GalaDinnerMemory.reception(memory);
        assertFalse(projection.project(agent).activity().facts().contains("Secret target"));
    }

    @Test void sourceMetadataIsExcludedAndUnsourcedSectionsAreRejected() {
        var source = new GalaDinnerReference.Source("briefing", "PRIVATE AUDIT NOTE",
                List.of("https://example.invalid/source"));
        var section = new GalaDinnerReference.Section("event", "Event", List.of("briefing"), List.of("Approved fact."));
        String rendered = GalaDinnerReference.render(new GalaDinnerReference.Document("2026-10-20",
                List.of(source), List.of(section)));
        assertTrue(rendered.contains("Approved fact."));
        assertFalse(rendered.contains("PRIVATE AUDIT NOTE"));
        assertFalse(rendered.contains("https://"));
        var unsourced = new GalaDinnerReference.Section("event", "Event", List.of("missing"), List.of("A fact."));
        assertThrows(IllegalArgumentException.class, () -> GalaDinnerReference.render(
                new GalaDinnerReference.Document("2026-10-20", List.of(source), List.of(unsourced))));
    }

    private static int occurrences(String text, String value) { return text.split(java.util.regex.Pattern.quote(value), -1).length - 1; }
}
