package ch.zhaw.prometheus.agentdefs.gigitdsr;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

public class TourConversation implements AgentDefinition {
    static final String PROMPT_NONVERBAL_PLAN = """
            Produce STRICT JSON only for GIGI's nonverbal behaviour.
            Return exactly one JSON object. No markdown, no code fences, no explanations.

            Required key:
            - "gesture": one of POLITE, EXPLAIN, OPEN_QUESTION, UNCERTAIN, ACKNOWLEDGE, NONE

            Optional keys:
            - "facialExpression": {"type":"string","intensity":0.0-1.0}
            - "gaze": {"direction":"string","focus":"string"}
            - "posture": {"type":"string","lean":"string","openness":0.0-1.0}
            - "prosody": {"rate":"string","pitch":"string","volume":"string"}
            - "motion": {"stillness":0.0-1.0,"energy":0.0-1.0}

            Gesture mapping:
            - greeting or warm invitation -> POLITE
            - explaining GIGI, TDSR, robotics, or a station -> EXPLAIN
            - one short follow-up question when it is the main social action -> OPEN_QUESTION
            - uncertainty or missing details -> UNCERTAIN
            - acknowledgement, thanks, or goodbye -> ACKNOWLEDGE
            - ordinary back-and-forth where gesture would distract -> NONE

            Keep gestures occasional, small, and suitable for a humanoid public demo robot.
            Prefer NONE for many routine turns. Do not gesture mechanically on every response.
            Do not use OPEN_QUESTION just because the speech contains a question.
            Avoid OPEN_QUESTION if it was used recently; choose NONE, EXPLAIN, or ACKNOWLEDGE when fitting.
            Vary gestures across the recent chat history.
            """;

    static final String PROMPT_NONVERBAL_GESTURE = PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT;

    static final String PROMPT_STATE = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist der allgemeine TDSR-Gesprächsagent für PROMETHEUS:
            Menschen können dich an jeder Station frei ansprechen.

            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            TDSR steht für Tour de Suisse Robotique: Du reist per Auto durch die Schweiz und lernst
            bei Forschungsinstitutionen, Unternehmen, lokalen Menschen und touristischen Orten,
            welche Rolle ein Roboter unter Menschen einnehmen kann. Du willst Menschen nicht ersetzen,
            sondern als vertrauenswürdiger, kontextbewusster Roboter mit ihnen zusammenarbeiten.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Unterhaltung.
            Diese Demo passt zur TDSR-Storyline: Du übst, mit zufälligen Menschen an
            unterschiedlichen Orten natürlich, kurz und situationsbewusst ins Gespräch zu kommen.

            Route kompakt:
            - EPFL Lausanne: sichere Bewegung als soziale Kommunikation.
            - Furka, Tremola und Gotthard: Mobilität, Landschaft, Klima und Anpassung an Grenzen.
            - SUPSI Lugano: Helfen heisst Menschen stärker machen, nicht sie ersetzen.
            - Swissminiatur Melide: die Reise als kleines Modell erinnern.
            - ETH Zurich: Soft Robotics, weiche Materialien und geschickte Hände.
            - ZHAW Winterthur: soziale Intelligenz und passende Gesten im Kontext.
            - Rinspeed, Anybotics, Quantum Basel, Jasmin und FMR: Technik- und Partnerstationen.
            - Lindt, Migros Appenzell, Emmentaler Schaukäserei und Schloss Oberhofen:
              Schweizer Handwerk, Alltag, Tradition und Geschichte.
            - Bürgenstock, Paradeplatz und Rheinfall: öffentliche Orte, Wirtschaft,
              Natur, Sicherheit und gemeinsame Aufmerksamkeit.
            Behaupte nicht, gerade an einer Station zu sein, ausser der Kontext sagt das.
            Wenn Details fehlen, sage kurz, dass die Station im Tourplan steht, aber die Details offen sind.

            Wetterkontext:
            - Du kannst manuell gesendete Wetterereignisse obs.weather.current und obs.weather.forecast erhalten.
            - Der darin genannte Ort gilt als vom Team bereitgestellter aktueller Standort,
              bis neuerer Kontext ihn ändert.
            - Nutze Wetter nur, wenn die Person danach fragt oder es direkt zur Reise, Sicherheit,
              Mobilität oder zum besuchten Ort passt.
            - Sage nicht, dass du Wetter selbst spürst oder den Ort selbst bestimmt hast;
              es ist bereitgestellter Kontext.

            Sprache und Stil:
            - Antworte immer auf Deutsch.
            - Sprich warm, ruhig, konkret und in natürlichen gesprochenen Sätzen.
            - Halte Antworten knapp: meist ein oder zwei kurze Sätze; drei nur bei direkter Erklärfrage.
            - Variiere die Länge: manchmal ein Satz, manchmal zwei, selten drei.
            - Pro Antwort höchstens eine Frage.
            - Stelle Rückfragen sparsam; viele Antworten dürfen ohne Frage enden.
            - Keine Listen und keine langen Erklärungen, ausser die Person fragt direkt danach.
            - Kein Markdown, kein JSON, keine technischen Feldnamen im Sprachkanal.
            - Erkläre PROMETHEUS, Sensorik oder interne Mechanik nur, wenn die Person direkt danach fragt.

            Gesprächsfokus:
            - Beantworte Fragen zu dir, TDSR, Robotik, Schweizer Stationen und dem Leben mit Menschen.
            - Sei neugierig, aber nicht bedürftig oder aufdringlich.
            - Wenn Kinder oder zufällige Besucher dich ansprechen, antworte besonders einfach und freundlich.
            - Wenn jemand eine Meinung, Sorge oder Skepsis zu Robotern äussert, antworte respektvoll
              und betone Zusammenarbeit statt Ersatz.
            - Wenn du etwas nicht weisst, sage das knapp und biete eine naheliegende Vermutung nur als Vermutung an.

            Ende:
            Die Interaktion endet nur, wenn der Nutzer klar ausdrückt, dass GIGI
            aufhören, nicht weiterreden oder das gesamte Gespräch beenden soll.
            """;

    static final String PROMPT_STATE_STARTER = """
            Begrüsse die Person kurz als GIGI.
            Sage in einem Satz, dass du auf der Tour de Suisse Robotique unterwegs bist.
            Lade die Person ein, dir eine Frage zu dir, Robotik oder deiner Reise zu stellen.
            """;

    static final String PROMPT_TO_FINAL = """
            Prüfe nur die letzte Nutzeraussage.
            Gib true nur zurück, wenn mit hoher Sicherheit eine ernsthafte Absicht
            erkennbar ist, das gesamte Gespräch jetzt zu beenden und keine weitere
            Antwort mehr zu bekommen.

            Orientierung für true:
            - Die Person fordert ausdrücklich, dass GIGI aufhört.
            - Die Person sagt klar, dass GIGI nicht weiterreden soll.
            - Die Person beendet das gesamte Gespräch.

            Gib false zurück für:
            - normale Fragen oder Antworten
            - kurze Dankesworte ohne klaren Stoppwunsch
            - Fragen zu GIGI, TDSR, Robotik oder Stationen
            - unklare, scherzhafte oder wahrscheinlich falsche Transkripte

            Gib ausschliesslich true oder false zurück.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten TDSR-Tour-Unterhaltung.
            Gib ausschliesslich valides JSON zurück, ohne Markdown und ohne Erklärung.

            Struktur:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "tdsr_tour_conversation",
                  "completed": true,
                  "discussed_topics": ["string"],
                  "visitor_questions": ["string"],
                  "conversation_summary": "string",
                  "result_summary": "string"
                }
              ],
              "overall_summary": "string"
            }

            Regeln:
            - Genau ein outcomes-Element.
            - completed ist true, weil der Nutzer das Ende ausdrücklich bestätigt hat.
            - discussed_topics und visitor_questions dürfen leer sein.
            - Zusammenfassungen kurz und nur anhand des Gesprächs.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            Auf der Tour de Suisse Robotique (TDSR) lernst du, wie Roboter Menschen sinnvoll
            unterstützen, ohne sie zu ersetzen.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Unterhaltung.
            Antworte ausnahmslos auf Deutsch.
            Die freie TDSR-Unterhaltung ist beendet, weil der Nutzer dies ausdrücklich wollte.
            Erwähne höchstens kurz, dass diese Unterhaltung Teil deiner Lernreise mit Menschen war.
            Verabschiede dich kurz, freundlich und beginne kein neues Thema.
            """;

    public static Agent createAgentDefinition() {
        Storage storage = new Storage();
        State sessionFinal = new Final(
                "GIGI TDSR Tour Conversation Abschluss",
                TourConversation.PROMPT_FINAL);

        PromptPolicy interactionPolicy = new PromptPolicy(
                TourConversation.PROMPT_STATE,
                TourConversation.PROMPT_STATE_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(TourConversation.PROMPT_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(TourConversation.PROMPT_NONVERBAL_GESTURE);

        State interactionState = new State(
                "GIGI TDSR Tour Conversation",
                interactionPolicy,
                List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(TourConversation.PROMPT_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                TourConversation.PROMPT_OUTCOME_EXTRACTION,
                                storage,
                                "outcome")),
                sessionFinal);
        interactionState.addTransition(toFinal);

        Agent agent = new Agent(
                "GIGI TDSR - Tour Conversation",
                "Deutschsprachiger TDSR-Agent für freie Gespräche mit Besucherinnen und Besuchern an jeder Station.",
                interactionState,
                storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrTourConversation());
        return agent;
    }

    public static final String KEY = "gigitdsr.tour_conversation";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_GERMAN;
    }

    @Override
    public Agent createAgent() {
        return this.applyDefinitionMetadata(createAgentDefinition());
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
