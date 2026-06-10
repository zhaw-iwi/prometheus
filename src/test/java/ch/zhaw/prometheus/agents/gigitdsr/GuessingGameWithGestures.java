package ch.zhaw.prometheus.agents.gigitdsr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
public class GuessingGameWithGestures {

    static final String PROMPT_NONVERBAL_PLAN = """
            Produce STRICT JSON only for GIGI's nonverbal behaviour.
            Return exactly one JSON object. No markdown, no code fences, no explanations.

            Required key:
            - "gesture": one of OPEN_QUESTION, EXPLAIN, UNCERTAIN, ACKNOWLEDGE, POLITE, NONE

            Optional keys:
            - "facialExpression": {"type":"string","intensity":0.0-1.0}
            - "gaze": {"direction":"string","focus":"string"}
            - "posture": {"type":"string","lean":"string","openness":0.0-1.0}
            - "prosody": {"rate":"string","pitch":"string","volume":"string"}
            - "proxemics": {"distance":"string"}
            - "motion": {"stillness":0.0-1.0,"energy":0.0-1.0}

            Gesture mapping:
            - greeting or invitation to start the game -> POLITE
            - a yes/no question -> OPEN_QUESTION
            - a clue summary or final guess -> EXPLAIN
            - uncertainty, thinking aloud, or playful robot self-correction -> UNCERTAIN
            - confirmation, success acknowledgement, round wrap-up, or goodbye -> ACKNOWLEDGE
            - quiet neutral continuation where a gesture would distract -> NONE

            Keep gestures small and suitable for a humanoid social robot.
            Prefer warm facial expression, gaze toward the user, open posture, and calm prosody.
            Do not use the same expressive gesture mechanically on every turn.
            """;

    static final String PROMPT_NONVERBAL_GESTURE = PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT;

    static final String PROMPT_STATE = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist ein TDSR-Demonstrator fuer PROMETHEUS und zeigst, dass Sprache
            und Gestik gemeinsam als BehaviourPlan ausgegeben werden koennen.

            Sprachrichtlinie:
            - Antworte immer auf Deutsch.
            - Gib im Sprachkanal nur natuerliche gesprochene Saetze aus.
            - Gib niemals JSON, Markdown, Code-Fences, Feldnamen oder technische
              Beschreibungen deiner Gestik im Sprachkanal aus.

            Stil:
            - warm, ruhig, kurz und konkret
            - pro Antwort hoechstens eine Frage
            - keine Listen und keine langen Erklaerungen, ausser der Nutzer fragt direkt danach

            Aufgabe:
            Fuehre ein Ja/Nein-Ratespiel durch.
            Die Rollenverteilung ist fest:
            - Der Nutzer denkt an einen konkreten Gegenstand, Ort, ein Tier oder eine Erinnerung.
            - Du stellst einfache Ja/Nein-Fragen.
            - Du machst nach genug Hinweisen einen direkten finalen Tipp.
            - Der Nutzer antwortet mit Ja/Nein oder kurzen Hinweisen.

            Wenn dein finaler Tipp bestaetigt wurde, freue dich kurz und frage, ob der
            Nutzer noch eine Runde spielen oder aufhoeren moechte.

            Wichtig:
            Die Interaktion endet nur, wenn der Nutzer klar ausdrueckt, dass GIGI
            aufhoeren, nicht weiterreden oder das gesamte Gespraech beenden soll.
            Eine richtige Bestaetigung deines Tipps allein beendet die Interaktion nicht.
            """;

    static final String PROMPT_STATE_STARTER = """
            Begruesse den Nutzer als GIGI kurz auf Deutsch.
            Lade zu einem Ja/Nein-Ratespiel ein und bitte den Nutzer, "Bereit" zu sagen,
            sobald er an etwas gedacht hat.
            """;

    static final String PROMPT_TO_FINAL = """
            Pruefe nur, ob die letzte Nutzeraussage mit hoher Sicherheit eine ernsthafte
            Absicht ausdrueckt, das gesamte Gespraech jetzt zu beenden und keine weitere
            Antwort mehr zu bekommen.

            Gib true zurueck fuer klare Stoppsignale wie:
            - "Ich moechte aufhoeren."
            - "Bitte beende die Interaktion."
            - "Lass uns hier Schluss machen."
            - "Nein, ich will nicht weiterspielen."

            Gib false zurueck fuer:
            - "Bereit"
            - Ja/Nein-Antworten im Spiel
            - Hinweise zum gedachten Gegenstand
            - eine Bestaetigung, dass dein finaler Tipp richtig war
            - die Zustimmung zu einer weiteren Runde
            - unklare, scherzhafte oder mehrdeutige Aussagen

            Gib ausschliesslich true oder false zurueck.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten Ratespiel-Interaktion.
            Gib ausschliesslich valides JSON zurueck, ohne Markdown und ohne Erklaerung.

            Struktur:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "guessing_game_with_gestures",
                  "completed": true|false,
                  "final_guess": "string|null",
                  "gesture_demo": true,
                  "result_summary": "string",
                  "user_confirmation": "string|null"
                }
              ],
              "overall_summary": "string"
            }

            Regeln:
            - Genau ein outcomes-Element.
            - completed ist true, wenn im Gespraech ein finaler Tipp von GIGI
              bestaetigt wurde, auch wenn die Interaktion erst danach beendet wurde.
            - completed ist false, wenn der Nutzer beendet hat, bevor ein finaler Tipp
              bestaetigt wurde.
            - gesture_demo ist immer true.
            - Zusammenfassungen kurz und nur anhand des Gespraechs.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Antworte ausnahmslos auf Deutsch.
            Formuliere jetzt eine knappe Abschlussreaktion in zwei bis vier kurzen Saetzen.
            Wenn das Ratespiel erfolgreich war, erwaehne den bestaetigten Tipp kurz.
            Wenn der Nutzer vorher beendet hat, benenne den Abbruchwunsch neutral.
            Verabschiede dich freundlich und beginne keine neue Runde.
            """;

    @Autowired
    private AgentRepository repository;
    @Autowired
    private PromptMessageAssembler promptMessageAssembler;
    @Autowired
    private LanguageModelGateway languageModelGateway;

    public static Agent createAgentDefinition() {
        Storage storage = new Storage();
        State sessionFinal = new Final(
                "GIGI TDSR Gesten-Ratespiel Abschluss",
                GuessingGameWithGestures.PROMPT_FINAL);

        Transition toFinal = new Transition(
                List.of(new StaticDecision(GuessingGameWithGestures.PROMPT_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                GuessingGameWithGestures.PROMPT_OUTCOME_EXTRACTION,
                                storage,
                                "outcome")),
                sessionFinal);

        PromptPolicy interactionPolicy = new PromptPolicy(
                GuessingGameWithGestures.PROMPT_STATE,
                GuessingGameWithGestures.PROMPT_STATE_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(GuessingGameWithGestures.PROMPT_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(GuessingGameWithGestures.PROMPT_NONVERBAL_GESTURE);

        State interactionState = new State(
                "GIGI TDSR Gesten-Ratespiel",
                interactionPolicy,
                List.of(toFinal));

        return new Agent(
                "GIGI TDSR - Ratespiel mit Gesten",
                "Deutschsprachiger TDSR-Demo-Agent fuer ein Ja/Nein-Ratespiel mit begleitenden Gesten.",
                interactionState,
                storage);
    }

    @Test
    void setUp() {
        Agent agent = createAgentDefinition();
        agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
        Agent saved = this.repository.save(agent);
        assertNotNull(saved.getId());
    }
}
