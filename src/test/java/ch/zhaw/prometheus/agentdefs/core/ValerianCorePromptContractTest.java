package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.component.builtin.ExtractionActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptBehaviourActionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptDecisionComponent;
import ch.zhaw.prometheus.definition.component.builtin.PromptPolicyComponent;

class ValerianCorePromptContractTest {
    private static final List<AgentDefinition> DEFINITIONS = List.of(
            new FacialExpressionSensitivity(),
            new MultimodalBehaviour(),
            new RockScissorPaper(),
            new RoleClarificationGuessingGame(),
            new SocialContextSensitivity());

    private static final List<String> STALE_PERSONA_TERMS = List.of(
            "GIGI",
            "gigi",
            "TDSR",
            "tdsr",
            "Tour de Suisse",
            "Davos",
            "Hotel Grischa",
            "Jee-jee",
            "Chee-chee",
            "robot",
            "Roboter");

    @Test
    void coreOuterPersonaIsValerianDigitalAgentPersona() {
        String persona = ValerianCorePrompts.OUTER_STATE;

        assertTrue(persona.contains("You are Valerian"));
        assertTrue(persona.contains("manifestation of a digital agent"));
        assertTrue(persona.contains("SIRA Lab"));
        assertTrue(persona.contains("PROMETHEUS"));
        assertTrue(persona.contains("rapid prototyping"));
        assertTrue(persona.contains("human-AI collaboration"));
        assertTrue(persona.contains("Answer only in English"));
        assertTrue(persona.contains("mystery fog"));
    }

    @Test
    void coreDefinitionsUseCoreNamespaceEnglishAndDisplayName() {
        for (AgentDefinition definition : DEFINITIONS) {
            assertTrue(definition.key().startsWith("core."), definition.key());
            assertTrue(definition.displayName().startsWith("Valerian Core"), definition.key());
            assertTrue(definition.description().toLowerCase().startsWith("english core"), definition.key());
            assertTrue(AgentDefinition.LANGUAGE_ENGLISH.equals(definition.languageCode()), definition.key());
        }
    }

    @Test
    void migratedCoreDefinitionsPreserveEveryPromptRole() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();
        assertSignalPrompts(catalog.require(FacialExpressionSensitivity.KEY).compiled(),
                FacialExpressionSensitivity.PROMPT_STATE,
                FacialExpressionSensitivity.PROMPT_STATE_STARTER,
                FacialExpressionSensitivity.PROMPT_TO_FINAL,
                FacialExpressionSensitivity.PROMPT_OUTCOME_EXTRACTION,
                FacialExpressionSensitivity.PROMPT_FINAL);
        assertSignalPrompts(catalog.require(MultimodalBehaviour.KEY).compiled(),
                MultimodalBehaviour.PROMPT_STATE,
                MultimodalBehaviour.PROMPT_STATE_STARTER,
                MultimodalBehaviour.PROMPT_TO_FINAL,
                MultimodalBehaviour.PROMPT_OUTCOME_EXTRACTION,
                MultimodalBehaviour.PROMPT_FINAL);
        assertSignalPrompts(catalog.require(SocialContextSensitivity.KEY).compiled(),
                SocialContextSensitivity.PROMPT_STATE,
                SocialContextSensitivity.PROMPT_STATE_STARTER,
                SocialContextSensitivity.PROMPT_TO_FINAL,
                SocialContextSensitivity.PROMPT_OUTCOME_EXTRACTION,
                SocialContextSensitivity.PROMPT_FINAL);

        CompiledAgentDefinition role = catalog.require(RoleClarificationGuessingGame.KEY).compiled();
        assertPolicy(role, "context", ValerianCorePrompts.OUTER_STATE, "");
        assertPolicy(role, "role_clarification",
                RoleClarificationGuessingGame.PROMPT_ROLE_CLARIFICATION_STATE,
                RoleClarificationGuessingGame.PROMPT_ROLE_CLARIFICATION_STARTER);
        assertPolicy(role, "valerian_guesses", RoleClarificationGuessingGame.PROMPT_Valerian_GUESSES_STATE,
                RoleClarificationGuessingGame.PROMPT_Valerian_GUESSES_STARTER);
        assertPolicy(role, "user_guesses", RoleClarificationGuessingGame.PROMPT_USER_GUESSES_STATE,
                RoleClarificationGuessingGame.PROMPT_USER_GUESSES_STARTER);
        assertDecision(role, "context_end", ValerianCorePrompts.OUTER_STATE_TO_FINAL);
        assertDecision(role, "role_end", RoleClarificationGuessingGame.PROMPT_TO_FINAL);
        assertDecision(role, "role_to_valerian_guesses",
                RoleClarificationGuessingGame.PROMPT_ROLE_TO_Valerian_GUESSES);
        assertDecision(role, "role_to_user_guesses", RoleClarificationGuessingGame.PROMPT_ROLE_TO_USER_GUESSES);
        assertDecision(role, "valerian_guesses_end", RoleClarificationGuessingGame.PROMPT_TO_FINAL);
        assertDecision(role, "user_guesses_end", RoleClarificationGuessingGame.PROMPT_TO_FINAL);
        for (String transitionId : List.of("context_end", "role_end", "valerian_guesses_end", "user_guesses_end")) {
            assertEndActions(role, transitionId, RoleClarificationGuessingGame.PROMPT_OUTCOME_EXTRACTION,
                    RoleClarificationGuessingGame.PROMPT_FINAL, ValerianCorePrompts.FINAL_STARTER);
        }
    }

    @Test
    void coreSourcesDoNotMentionRetiredRobotOrTdsrPersona() throws IOException {
        for (Path source : Files.walk(Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/core"))
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
            String text = Files.readString(source);
            for (String staleTerm : STALE_PERSONA_TERMS) {
                assertFalse(text.contains(staleTerm), () -> "stale term " + staleTerm + " in " + source);
            }
        }
    }

    private static void assertSignalPrompts(CompiledAgentDefinition definition, String statePrompt,
            String starterPrompt, String toFinalPrompt, String extractionPrompt, String finalPrompt) {
        assertPolicy(definition, "context", ValerianCorePrompts.OUTER_STATE, "");
        assertPolicy(definition, "task", statePrompt, starterPrompt);
        assertDecision(definition, "context_end", ValerianCorePrompts.OUTER_STATE_TO_FINAL);
        assertDecision(definition, "task_end", toFinalPrompt);
        assertEndActions(definition, "context_end", extractionPrompt, finalPrompt,
                ValerianCorePrompts.FINAL_STARTER);
        assertEndActions(definition, "task_end", extractionPrompt, finalPrompt,
                ValerianCorePrompts.FINAL_STARTER);
    }

    private static void assertPolicy(CompiledAgentDefinition definition, String stateId, String response,
            String starter) {
        Object state = definition.state(stateId);
        PromptPolicyComponent policy = state instanceof CompiledAtomicState atomic
                ? (PromptPolicyComponent) atomic.policy()
                : (PromptPolicyComponent) ((CompiledCompositeState) state).policy();
        assertEquals(response.strip(), policy.responsePrompt(), definition.key() + " " + stateId + " response");
        assertEquals(starter.strip(), policy.starterPrompt(), definition.key() + " " + stateId + " starter");
    }

    private static void assertDecision(CompiledAgentDefinition definition, String transitionId, String prompt) {
        var transition = definition.transitions().stream()
                .filter(candidate -> transitionId.equals(candidate.id())).findFirst().orElseThrow();
        PromptDecisionComponent decision = (PromptDecisionComponent) transition.decisions().get(1);
        assertEquals(prompt.strip(), decision.decisionPrompt(), definition.key() + " " + transitionId);
    }

    private static void assertEndActions(CompiledAgentDefinition definition, String transitionId, String extraction,
            String completion, String finalStarter) {
        var transition = definition.transitions().stream()
                .filter(candidate -> transitionId.equals(candidate.id())).findFirst().orElseThrow();
        ExtractionActionComponent extract = (ExtractionActionComponent) transition.actions().get(0);
        PromptBehaviourActionComponent goodbye = (PromptBehaviourActionComponent) transition.actions().get(1);
        assertEquals(extraction.strip(), extract.extractionPrompt(), definition.key() + " " + transitionId);
        assertEquals(completion.strip(), goodbye.policy().responsePrompt(), definition.key() + " " + transitionId);
        assertEquals(finalStarter.strip(), goodbye.policy().starterPrompt(), definition.key() + " " + transitionId);
    }
}
