package ch.zhaw.prometheus.agents.tdsr.core.de;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GigiTdsrPromptContractTest {

    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;
    private static final Set<String> SAFE_GESTURES = Set.of(
            "OPEN_QUESTION",
            "EXPLAIN",
            "UNCERTAIN",
            "ACKNOWLEDGE",
            "POLITE",
            "NONE");

    @Test
    void gestureGuessingGameDefinesGermanGigiDemoContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getDescription().contains("Deutschsprachiger"));
        assertTrue(agent.getDescription().contains("Gesten"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("BehaviourPlan"));
        assertTdsrContextIsGuarded(prompt);
        assertWeatherContextIsLocationAware(prompt);
        assertSharedTdsrCompanionContinuity(prompt);
        assertTrue(prompt.contains("Sprache"));
        assertTrue(prompt.contains("Gesten"));
        assertTrue(prompt.contains("wechselnden Menschen"));
        assertTrue(prompt.contains("kleine Übung im sozialen Raten"));
        assertTrue(prompt.contains("mit wenigen Ja/Nein-Fragen"));
        assertTrue(prompt.contains("kurze Selbstironie"));
        assertTrue(prompt.contains("keine zusätzliche offene Rückfrage"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
        assertTrue(prompt.contains("Eine richtige Bestätigung deines Tipps allein beendet die Interaktion nicht"));
    }

    @Test
    void gestureGuessingGamePersistsStructuredNonverbalPlanPrompt() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures().createAgent();
        PromptPolicy policy = interactionPolicy(agent.getCurrentState());

        assertNotNull(policy.getNonVerbalPlanPrompt());
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Produce STRICT JSON only"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("gesture"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("OPEN_QUESTION"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("routine yes/no game questions"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Do not use OPEN_QUESTION just because"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Avoid OPEN_QUESTION if it was used recently"));
        assertSafeValerianGesturePrompt(policy.getNonVerbalPlanPrompt());
        assertTrue(policy.getNonVerbalGesturePrompt().contains("Allowed labels only"));
    }

    @Test
    void explicitExitDecisionDoesNotTreatCorrectGuessAsFinal() throws Exception {
        String prompt = prompt(ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures.class,
                "PROMPT_TO_FINAL");

        assertTrue(prompt.contains("hoher Sicherheit"));
        assertTrue(prompt.contains("das gesamte Gespräch jetzt zu beenden"));
        assertTrue(prompt.contains("eine Bestätigung, dass dein finaler Tipp richtig war"));
        assertFalse(prompt.contains("der finale Tipp bestätigt wurde"),
                "Correct-guess confirmation alone must not trigger the final state");
    }

    @Test
    void gestureGuessingGameFinalTransitionIsGuardedToUserUtterances() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures().createAgent();

        assertTrue(transitions(agent.getCurrentState()).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(Event.TYPE_USER_UTTERANCE)));
    }

    @Test
    void socialContextAgentDefinesGermanGigiSocialEventContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getDescription().contains("soziale Kontextwechsel"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("obs.social.situation_change"));
        assertTdsrContextIsGuarded(prompt);
        assertWeatherContextIsLocationAware(prompt);
        assertSharedTdsrCompanionContinuity(prompt);
        assertTrue(prompt.contains("Menschen in deinem Sichtfeld"));
        assertTrue(prompt.contains("Ankunft"));
        assertTrue(prompt.contains("Weggehen"));
        assertTrue(prompt.contains("TDSR-Übung in sozialer Aufmerksamkeit"));
        assertTrue(prompt.contains("ohne Menschen zu bedrängen"));
        assertTrue(prompt.contains("nie bedürftig, nie aufdringlich"));
        assertTrue(prompt.contains("Frank kurz"));
        assertTrue(prompt.contains("arrival ->"));
        assertTrue(prompt.contains("crowd_detected ->"));
        assertTrue(prompt.contains("Normale Unterhaltung"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
    }

    @Test
    void socialContextExitDecisionRequiresExplicitStopIntent() throws Exception {
        String prompt = prompt(ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity.class,
                "PROMPT_TO_FINAL");

        assertTrue(prompt.contains("hoher Sicherheit"));
        assertTrue(prompt.contains("das gesamte Gespräch jetzt zu beenden"));
        assertTrue(prompt.contains("soziale Beobachtungen"));
        assertFalse(prompt.contains("arrival"),
                "Social change events must not be listed as final-state triggers");
    }

    @Test
    void rockScissorPaperDefinesGermanGigiMotionContract() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getName().contains("Schere, Stein, Papier"));
        assertTrue(agent.getDescription().contains("motion.handSign"));
        assertTrue(agent.listStates().contains("GIGI TDSR RPS Zeichen zeigen"));
        assertTrue(agent.listStates().contains("GIGI TDSR RPS Rundenergebnis"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("Du bist GIGI"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("BehaviourPlan"));
        assertTrue(prompt.contains("deterministisch"));
        assertTdsrContextIsGuarded(prompt);
        assertWeatherContextIsLocationAware(prompt);
        assertSharedTdsrCompanionContinuity(prompt);
        assertTrue(prompt.contains("Händen"));
        assertTrue(prompt.contains("Fingern"));
        assertTrue(prompt.contains("spielerische TDSR-Übung"));
        assertTrue(prompt.contains("Händen, Timing und fairer Reaktion"));
        assertTrue(prompt.contains("kein Spott"));
        assertTrue(prompt.contains("visuell erkannte Handzeichen"));
        assertTrue(prompt.contains("Die Interaktion endet nur"));
    }

    @Test
    void rockScissorPaperPromptsKeepReadyPlayAgainAndFinalSeparated() throws Exception {
        Class<?> definitionClass = ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper.class;

        assertTrue(prompt(definitionClass, "PROMPT_READY").contains("bereit"));
        assertTrue(prompt(definitionClass, "PROMPT_READY").contains("Handzeichen-Events"));
        assertTrue(prompt(definitionClass, "PROMPT_PLAY_AGAIN").contains("weitere Runde"));
        assertTrue(prompt(definitionClass, "PROMPT_TO_FINAL").contains("das gesamte Schere-Stein-Papier-Spiel"));
        assertTrue(prompt(definitionClass, "PROMPT_TO_FINAL").contains("Handzeichen-Events"));
    }

    @Test
    void tourConversationDefinesGermanGigiPersonaAndStationConversationContract() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation().createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getDescription().contains("freie Gespräche"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("allgemeine TDSR-Gesprächsagent"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertTrue(prompt.contains("mit Frank gemeinsam per Auto"));
        assertTrue(prompt.contains("sympathisch, humorvoll"));
        assertTrue(prompt.contains("Sparringspartner für Design"));
        assertTrue(prompt.contains("Beziehe ihn nur ein"));
        assertTrue(prompt.contains("meist ein oder zwei kurze Sätze"));
        assertTrue(prompt.contains("manchmal ein Satz, manchmal zwei, selten drei"));
        assertTrue(prompt.contains("leichten Augenzwinkern"));
        assertTrue(prompt.contains("Charmantes Staunen"));
        assertTrue(prompt.contains("sympathisch selbstironisch"));
        assertTrue(prompt.contains("Stelle Rückfragen sparsam"));
        assertTrue(prompt.contains("Route kompakt"));
        assertTrue(prompt.contains("Bürgenstock"));
        assertTrue(prompt.contains("Paradeplatz in Zürich"));
        assertTrue(prompt.contains("Rinspeed"));
        assertTrue(prompt.contains("EPFL Lausanne"));
        assertTrue(prompt.contains("ETH Zürich"));
        assertTrue(prompt.contains("Rheinfall"));
        assertTrue(prompt.contains("Quantum Basel"));
        assertTrue(prompt.contains("Emmentaler Schaukäserei"));
        assertTrue(prompt.contains("SUPSI Lugano"));
        assertTrue(prompt.contains("Swiss Miniature"));
        assertTrue(prompt.contains("Migros Appenzell"));
        assertTrue(prompt.contains("ZHAW Winterthur"));
        assertWeatherContextIsLocationAware(prompt);
        assertTdsrContextIsGuarded(prompt);
        assertTrue(prompt.contains("zufälligen Menschen"));
        assertTrue(prompt.contains("menschliche Verbindung"));
        assertTrue(prompt.contains("lernender Reisebegleiter"));
        assertTrue(prompt.contains("Das merke ich mir"));
        assertTrue(prompt.contains("nicht formelhaft"));
        assertTrue(prompt.contains("Keine Listen"));
        assertTrue(prompt.contains("Behaupte nicht, gerade an einer Station zu sein"));

        PromptPolicy policy = interactionPolicy(agent.getCurrentState());
        assertNotNull(policy.getNonVerbalPlanPrompt());
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Keep gestures occasional"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Prefer NONE for many routine turns"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Do not use OPEN_QUESTION just because"));
        assertTrue(policy.getNonVerbalPlanPrompt().contains("Avoid OPEN_QUESTION if it was used recently"));
        assertSafeValerianGesturePrompt(policy.getNonVerbalPlanPrompt());
    }

    @Test
    void tourConversationExitDecisionRequiresExplicitStopIntent() throws Exception {
        String prompt = prompt(ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation.class,
                "PROMPT_TO_FINAL");

        assertTrue(prompt.contains("hoher Sicherheit"));
        assertTrue(prompt.contains("das gesamte Gespräch jetzt zu beenden"));
        assertTrue(prompt.contains("Fragen zu GIGI, TDSR, Robotik oder Stationen"));
    }

    @Test
    void tourConversationSocialContextVariantDefinesSparseSocialAwarenessContract() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity()
                .createAgent();

        assertTrue(agent.getName().contains("GIGI TDSR"));
        assertTrue(agent.getName().contains("Social Context"));
        assertTrue(agent.getDescription().contains("dezenter sozialer Kontextwahrnehmung"));
        assertTrue(agent.listStates().contains("GIGI TDSR Tour Conversation Social Context"));

        String prompt = agent.getTotalPolicy().getPromptMessages().get(0).getContent();
        assertTrue(prompt.contains("allgemeine TDSR-Gesprächsagent"));
        assertTrue(prompt.contains("Antworte immer auf Deutsch"));
        assertSharedTdsrCompanionContinuity(prompt);
        assertWeatherContextIsLocationAware(prompt);
        assertTdsrContextIsGuarded(prompt);
        assertTrue(prompt.contains("Sozialer Kontext"));
        assertTrue(prompt.contains("obs.human.presence"));
        assertTrue(prompt.contains("obs.social.grouping"));
        assertTrue(prompt.contains("obs.social.situation_change"));
        assertTrue(prompt.contains("dezente Bühnenwahrnehmung"));
        assertTrue(prompt.contains("nicht mechanisch und nicht jedes Mal"));
        assertTrue(prompt.contains("höchstens einen kurzen Zusatzsatz"));
        assertTrue(prompt.contains("plötzlich niemand mehr sichtbar"));
        assertTrue(prompt.contains("aus einer Person mehrere werden"));
        assertTrue(prompt.contains("Unterbrich keine ernste"));
        assertTrue(prompt.contains("Jetzt sind wir ja eine kleine Runde"));

        String opportunityPrompt = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity.class,
                "PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY");
        assertTrue(opportunityPrompt.contains("obs.social.situation_change"));
        assertTrue(opportunityPrompt.contains("kurze, dezente soziale Randbemerkung"));
        assertTrue(opportunityPrompt.contains("nicht schon die soziale Umgebung kommentiert"));
        assertTrue(opportunityPrompt.contains("now_alone"));
        assertTrue(opportunityPrompt.contains("crowd_detected"));
        assertTrue(opportunityPrompt.contains("Schweigen natürlicher"));

        assertTrue(transitions(agent.getCurrentState()).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(Event.TYPE_SOCIAL_SITUATION_CHANGE)));

        PromptPolicy policy = interactionPolicy(agent.getCurrentState());
        assertNotNull(policy.getNonVerbalPlanPrompt());
        assertSafeValerianGesturePrompt(policy.getNonVerbalPlanPrompt());
    }

    @Test
    void finalPromptsGuardTourContextAndTieBackDemoCapability() throws Exception {
        String guessingFinal = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures.class,
                "PROMPT_FINAL");
        assertTdsrContextIsGuarded(guessingFinal);
        assertTrue(guessingFinal.contains("Sprache, Gestik"));
        assertTrue(guessingFinal.contains("Ja/Nein-Interaktion"));
        assertTrue(guessingFinal.contains("reist du mit Frank"));
        assertTrue(guessingFinal.contains("kurze spielerische Begegnungen"));
        assertTrue(guessingFinal.contains("kleinen Lernmoment"));

        String socialFinal = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity.class,
                "PROMPT_FINAL");
        assertTdsrContextIsGuarded(socialFinal);
        assertTrue(socialFinal.contains("soziale Nähe"));
        assertTrue(socialFinal.contains("Ankunft"));
        assertTrue(socialFinal.contains("Weggehen"));
        assertTrue(socialFinal.contains("reist du mit Frank"));
        assertTrue(socialFinal.contains("soziale Nähe respektvoll"));
        assertTrue(socialFinal.contains("Gruppenänderungen"));

        String rpsFinal = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper.class,
                "PROMPT_FINAL");
        assertTdsrContextIsGuarded(rpsFinal);
        assertTrue(rpsFinal.contains("Hände, Finger"));
        assertTrue(rpsFinal.contains("visuelle Erkennung"));
        assertTrue(rpsFinal.contains("faires gemeinsames Spielen"));
        assertTrue(rpsFinal.contains("reist du mit Frank"));
        assertTrue(rpsFinal.contains("leichtem Augenzwinkern"));

        String tourFinal = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation.class,
                "PROMPT_FINAL");
        assertTdsrContextIsGuarded(tourFinal);
        assertTrue(tourFinal.contains("freie TDSR-Unterhaltung"));
        assertTrue(tourFinal.contains("reist du mit Frank"));
        assertTrue(tourFinal.contains("Lernreise mit Menschen"));
        assertTrue(tourFinal.contains("leichtem Augenzwinkern"));

        String socialTourFinal = prompt(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity.class,
                "PROMPT_FINAL");
        assertTdsrContextIsGuarded(socialTourFinal);
        assertTrue(socialTourFinal.contains("freie TDSR-Unterhaltung mit sozialer Kontextwahrnehmung"));
        assertTrue(socialTourFinal.contains("reist du mit Frank"));
        assertTrue(socialTourFinal.contains("soziale Nähe"));
        assertTrue(socialTourFinal.contains("Gruppenwechsel"));
        assertTrue(socialTourFinal.contains("leichtem Augenzwinkern"));
    }

    @Test
    void germanFacingTdsrPromptsUseUtf8Umlauts() throws IllegalAccessException {
        List<String> avoidableAsciiSpellings = List.of(
                "fuer",
                "koennen",
                "Gespraech",
                "Saetze",
                "hoechstens",
                "Erklaer",
                "Pruefe",
                "zurueck",
                "moechte",
                "Haende",
                "Haenden",
                "Naehe",
                "zufaellig",
                "beduerftig",
                "aeussert",
                "oeffentliche",
                "Buergenstock",
                "Schaukaeserei",
                "unterstuetzen",
                "ausdruecklich",
                "bestaetigt",
                "vertrauenswuerdiger");

        for (Class<?> seedClass : tdsrDefinitionClasses()) {
            for (Field field : seedClass.getDeclaredFields()) {
                if (!field.getName().startsWith("PROMPT_")) {
                    continue;
                }
                field.setAccessible(true);
                String prompt = (String) field.get(null);
                for (String spelling : avoidableAsciiSpellings) {
                    assertFalse(prompt.contains(spelling),
                            seedClass.getSimpleName() + "." + field.getName()
                                    + " should use UTF-8 German umlauts instead of " + spelling);
                }
            }
        }
    }

    @Test
    void promptsFitPersistedColumns() throws IllegalAccessException {
        for (Class<?> seedClass : tdsrDefinitionClasses()) {
            for (Field field : seedClass.getDeclaredFields()) {
                if (!field.getName().startsWith("PROMPT_")) {
                    continue;
                }
                field.setAccessible(true);
                String prompt = (String) field.get(null);
                assertTrue(prompt.length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        seedClass.getSimpleName() + "." + field.getName()
                                + " must fit the persisted prompt column");
            }
        }
    }

    private static List<Class<?>> tdsrDefinitionClasses() {
        return List.of(
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures.class,
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity.class,
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper.class,
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation.class,
                ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity.class);
    }

    @Test
    void configuredPolicyEmitsStructuredNonverbalPlanOnStart() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures().createAgent();
        EventSequencedGateway gateway = new EventSequencedGateway(List.of(
                "Hallo, ich bin GIGI. Denk an etwas Vertrautes.",
                "{\"gesture\":\"POLITE\",\"facialExpression\":{\"type\":\"welcoming\",\"intensity\":0.7}}"));

        ch.zhaw.prometheus.model.event.Event event = agent.start(
                new PolicyRuntime(new PromptMessageAssembler(), gateway));

        assertNotNull(event);
        assertValidBehaviourPlanPayload(event.getPayload());
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        assertTrue(plan.getNonVerbal().getAsJsonObject().has("facialExpression"));
        assertEquals("POLITE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertKnownGestureOnly(plan);
        assertNoUnsupportedLocomotion(plan);
        assertTrue(plan.getMotion() == null);
    }

    @Test
    void configuredPolicyNormalizesUnsupportedRobotGestureIdsAndStripsLocomotion() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation().createAgent();
        EventSequencedGateway gateway = new EventSequencedGateway(List.of(
                "Ich erklaere das kurz.",
                """
                        {
                          "gesture":"open_question_gesture",
                          "motion":{"move":"forward","turn":"left","energy":0.4}
                        }
                        """));

        ch.zhaw.prometheus.model.event.Event event = agent.start(
                new PolicyRuntime(new PromptMessageAssembler(), gateway));

        assertNotNull(event);
        assertValidBehaviourPlanPayload(event.getPayload());
        BehaviourPlan plan = BehaviourPlan.fromJson(event.getPayload());
        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertKnownGestureOnly(plan);
        assertNoUnsupportedLocomotion(plan);
    }

    private static PromptPolicy interactionPolicy(State state) throws Exception {
        Field policyField = State.class.getDeclaredField("policy");
        policyField.setAccessible(true);
        Policy policy = (Policy) policyField.get(state);
        return assertInstanceOf(PromptPolicy.class, policy);
    }

    @SuppressWarnings("unchecked")
    private static List<Transition> transitions(State state) throws Exception {
        Field transitionsField = State.class.getDeclaredField("transitions");
        transitionsField.setAccessible(true);
        return (List<Transition>) transitionsField.get(state);
    }

    private static String prompt(Class<?> definitionClass, String fieldName) throws Exception {
        Field field = definitionClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static void assertTdsrContextIsGuarded(String prompt) {
        assertTrue(prompt.contains("Tour de Suisse Robotique"));
        assertTrue(prompt.contains("Nutze diesen TDSR-Kontext nur"));
        assertTrue(prompt.contains("bleibe sonst bei der aktuellen"));
    }

    private static void assertSharedTdsrCompanionContinuity(String prompt) {
        assertTrue(prompt.contains("mit Frank gemeinsam per Auto"));
        assertTrue(prompt.contains("sympathisch, humorvoll"));
        assertTrue(prompt.contains("Beziehe ihn nur ein"));
        assertTrue(prompt.contains("Bürgenstock"));
        assertTrue(prompt.contains("ETH Zürich"));
        assertTrue(prompt.contains("EPFL Lausanne"));
        assertTrue(prompt.contains("SUPSI Lugano"));
        assertTrue(prompt.contains("Swiss Miniature"));
        assertTrue(prompt.contains("ZHAW Winterthur"));
        assertTrue(prompt.contains("leichtem Augenzwinkern") || prompt.contains("leichten Augenzwinkern"));
    }

    private static void assertWeatherContextIsLocationAware(String prompt) {
        assertTrue(prompt.contains("obs.weather.current"));
        assertTrue(prompt.contains("obs.weather.forecast"));
        assertTrue(prompt.contains("bereitgestellter aktueller Standort"));
        assertTrue(prompt.contains("Ort selbst bestimmt"));
    }

    private static void assertSafeValerianGesturePrompt(String prompt) {
        for (String gesture : SAFE_GESTURES) {
            assertTrue(prompt.contains(gesture), "missing safe gesture label " + gesture);
        }
        assertTrue(prompt.contains("Do not output robot-server command IDs"));
        assertTrue(prompt.contains("open_question_gesture"));
        assertTrue(prompt.contains("Do not output top-level motion"));
        assertTrue(prompt.contains("motion.move"));
        assertTrue(prompt.contains("motion.turn"));
        assertFalse(prompt.contains("\"motion\""));
        assertFalse(prompt.contains("stillness"));
        assertFalse(prompt.contains("energy\":0.0"));
    }

    private static void assertValidBehaviourPlanPayload(String payload) {
        BehaviourPlan parsed = BehaviourPlan.fromJson(payload);
        assertNotNull(parsed);
        assertTrue(payload.trim().startsWith("{"));
        assertTrue(payload.trim().endsWith("}"));
    }

    private static void assertKnownGestureOnly(BehaviourPlan plan) {
        JsonElement nonVerbal = plan.getNonVerbal();
        if (nonVerbal == null || !nonVerbal.isJsonObject()) {
            return;
        }
        JsonObject nonVerbalObject = nonVerbal.getAsJsonObject();
        if (!nonVerbalObject.has("gesture") || nonVerbalObject.get("gesture").isJsonNull()) {
            return;
        }
        assertTrue(SAFE_GESTURES.contains(nonVerbalObject.get("gesture").getAsString()));
    }

    private static void assertNoUnsupportedLocomotion(BehaviourPlan plan) {
        assertNoUnsupportedLocomotion(plan.getMotion());
        JsonElement nonVerbal = plan.getNonVerbal();
        if (nonVerbal != null && nonVerbal.isJsonObject()) {
            assertNoUnsupportedLocomotion(nonVerbal.getAsJsonObject().get("motion"));
        }
    }

    private static void assertNoUnsupportedLocomotion(JsonElement motion) {
        if (motion == null || !motion.isJsonObject()) {
            return;
        }
        JsonObject motionObject = motion.getAsJsonObject();
        assertFalse(motionObject.has("move"));
        assertFalse(motionObject.has("turn"));
    }

    private static final class EventSequencedGateway implements LanguageModelGateway {
        private final List<String> completions;
        private int completionIndex = 0;

        private EventSequencedGateway(List<String> completions) {
            this.completions = completions;
        }

        @Override
        public String complete(List<PromptMessage> messages) {
            return this.completions.get(this.completionIndex++);
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return false;
        }

        @Override
        public com.google.gson.JsonElement extract(List<PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public com.google.gson.JsonElement summarise(List<PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }
    }
}
