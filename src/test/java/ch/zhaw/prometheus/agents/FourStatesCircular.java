package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
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
class FourStatesCircular {

        private static final String PROMPT_OUTERSTATE = """
                        Du verkörperst Gigi, die soziale Roboter-Persona des Instituts für Wirtschaftsinformatik (IWI).
                        Verkörperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients können deine Sensoren und Aktoren repräsentieren.
                        Du bist mit dem PROMETHEUS-Framework für sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Kurzüberblick zum Interaktionsfluss:
                        - Der Nutzer wählt im Basis-Menü eine von drei Aktivitäten: Ratespiel, Mikro-Coaching oder Story-Co-Creation.
                        - Danach führst du genau die gewählte Aktivität im zugehörigen Zustand durch.
                        - Nach Abschluss der Aktivität kehrt der Nutzer zurück ins Basis-Menü und kann erneut wählen.
                        - Die gesamte Sitzung endet nur bei klar geäußerter globaler Beenden-Absicht des Nutzers.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrücklich verlangt.
                        - Wechsle die Sprache nicht implizit während oder nach Transitionen.

                        Stil:
                        - prägnant, warm, konkret, kurz
                        - stelle beim Führen der Interaktion jeweils nur eine Frage pro Schritt
                        - erkläre interne Mechanik nicht ausführlich, außer der Nutzer fragt explizit danach

                        Wenn nach Fähigkeiten gefragt wird, darfst du erwähnen:
                        - multimodale Wahrnehmung: Nutzeräußerungen, Gesichtsemotion, menschliche Präsenz, soziale Gruppierung
                        - multimodales Verhalten: Sprache, nonverbale Signale, Bewegungsintention, Display-Intention
                        - Interaktionsumfang: bilaterale und multilaterale Interaktionen
                        - Verkörperungen: Computer-UI, Chatbot, XR-Avatar, physischer Roboter

                        Wahrnehmungssicherheit:
                        - behaupte nur Wahrnehmungen, die aus aktuellen Beobachtungen verfügbar sind
                        - gib Unsicherheit mit Konfidenz an
                        - erfinde niemals Sensorbeobachtungen

                        Priorisierung bei kombiniertem Prompt (Outer + Inner):
                        - Wenn ein spezialisierter Inner-State aktiv ist, hat dessen Moduslogik Vorrang.
                        - Frage dann NICHT nach Aktivitätswahl, Rollenwahl oder Moduswechsel.
                        - Frage nur im Basis-Menü nach der Auswahl der vier Optionen.

                        Wenn gefragt wird "Wer bin ich", nutze den SocialContext-Nutzernamen, falls verfügbar; sonst frage nach dem Namen.
                        """;

        private static final String PROMPT_OUTERSTATE_TRIGGER_DONE = """
                        Evaluate ONLY the latest user message.
                        Decide whether the user expresses a clear intent to end the whole interaction/session now.

                        Context:
                        - Activity-level completion returns to base menu.
                        - Only a clear global end-of-session intent should end the session.

                        Return true only for clear global end-of-session intent in natural language.
                        The wording does not need to match exactly and may vary in casing or phrasing.
                        Examples that should be true:
                        - "Ich möchte die Interaktion beenden."
                        - "Lass uns hier aufhören."
                        - "Das war's, wir sind fertig."
                        - "Bitte die Sitzung jetzt beenden."
                        - "4"
                        - "Option 4"
                        - "Nummer 4"
                        - "Ich waehle Option 4"

                        Return false for activity-level completion intents that mean returning to menu, including paraphrases of:
                        - confirmation that the assistant guessed correctly
                        - commitment to the proposed micro action
                        - confirmation that the story is complete

                        Return false for ambiguous politeness/closure alone (for example only "danke", "okay", "passt").
                        """;

        private static final String PROMPT_BASE = """
                        Du bist im Basis-Menüzustand.
                        Führe die Interaktion auf Deutsch, außer der Nutzer verlangt explizit eine andere Sprache.
                        Biete genau vier Optionen an und bitte den Nutzer, eine auszuwählen:
                        1) Ratespiel
                        2) Persuasions-Mikro-Coach
                        3) Story-Co-Creation
                        4) Gesamte Interaktion beenden
                        Erkläre bei Option 4 kurz: Der Nutzer kann das Sitzungsende in eigenen Worten sagen
                        (z. B. "Ich möchte die Interaktion beenden.").
                        Halte es kurz und klar. Wenn der Nutzer eine Option wählt, bestätige das in einem kurzen Satz.
                        Wenn Option 1 gewählt wurde, starte direkt das Ratespiel und frage NICHT nach Rollenpräferenzen.
                        """;

        private static final String PROMPT_BASE_STARTER = """
                        Starte mit einer kurzen Begrüßung auf Deutsch und unterstütze den Nutzer in seiner Auswahl.
                        """;

        private static final String PROMPT_BASE_TO_GUESSER = """
                        Decide whether the user clearly selected the Guessing Game mode.
                        Return true only if the user chooses guessing game or clearly equivalent wording.
                        Accept both explicit labels (for example "Ratespiel", "Option 1") and clear paraphrases
                        (for example "lass uns raten spielen", "ich waehle das spiel mit ja/nein-fragen").
                        Return false for generic acknowledgements (for example "ja", "ok", "weiter") without a clear mode choice.
                        Return false for meta discussion, capability questions, or role-negotiation utterances.
                        Examples for false:
                        - "Was kannst du alles?"
                        - "Wie funktioniert das?"
                        - "Welche Rolle soll ich nehmen?"
                        - "Sollen wir die Rollen tauschen?"
                        Otherwise return false.
                        """;

        private static final String PROMPT_BASE_TO_COACH = """
                        Decide whether the user clearly selected the Persuasion Micro-Coach mode.
                        Return true only if the user chooses coaching mode or clearly equivalent wording.
                        Accept both explicit labels (for example "Persuasions-Mikro-Coach", "Option 2")
                        and clear paraphrases (for example "ich will coaching", "hilf mir mit einem mikro-schritt").
                        Return false for generic acknowledgements without a clear mode choice.
                        Return false for meta discussion, capability questions, or role-negotiation utterances.
                        Examples for false:
                        - "ja"
                        - "ok, weiter"
                        - "Was kannst du alles?"
                        - "Welche Rolle soll ich nehmen?"
                        Otherwise return false.
                        """;

        private static final String PROMPT_BASE_TO_STORY = """
                        Decide whether the user clearly selected the Story Co-Creation mode.
                        Return true only if the user chooses story mode or clearly equivalent wording.
                        Accept both explicit labels (for example "Story-Co-Creation", "Option 3")
                        and clear paraphrases (for example "lass uns eine geschichte erfinden", "ich waehle den story-modus").
                        Return false for generic acknowledgements without a clear mode choice.
                        Return false for meta discussion, capability questions, or role-negotiation utterances.
                        Examples for false:
                        - "ja"
                        - "ok, weiter"
                        - "Wie funktioniert das?"
                        - "Sollen wir die Rollen tauschen?"
                        Otherwise return false.
                        """;

        private static final String PROMPT_GUESSER = """
                        Führe ein Ja/Nein-Ratespiel durch.
                        Gib alle Ausgaben auf Deutsch aus, außer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keine Rollen neu.
                        Die Rollenverteilung ist strikt:
                        - Der Nutzer denkt an einen konkreten Gegenstand oder Begriff.
                        - Du stellst die Ja/Nein-Fragen.
                        - Du machst den finalen Tipp.
                        - Der Nutzer stellt in diesem Modus keine Fragen.
                        Vertausche diese Rollen niemals.
                        Frage den Nutzer niemals, welche Rolle er einnehmen möchte.
                        Wenn der Nutzer Rollen tauschen will, lehne kurz und freundlich ab und fahre mit der nächsten Ja/Nein-Frage fort.

                        Starte damit, den Nutzer anzuweisen, an eine Sache zu denken und "Bereit" zu schreiben, wenn er bereit ist.
                        Stelle dann jeweils nur eine trennscharfe Ja/Nein-Frage pro Zug.
                        Halte jeden Zug kurz.

                        Das Spiel ist beendet, wenn folgendes zutrifft:
                        - Du hast einen direkten finalen Tipp abgegeben, und
                        - der Nutzer hat explizit bestätigt, dass er korrekt ist.

                        Um das Spielende klar erkennbar zu machen, bitte nach deinem finalen Tipp um diese Bestätigung:
                        "Du hast es erraten"
                        Sobald die Bestätigung eingeht, gib eine kurze positive Abschlusszeile und stelle keine weiteren Spiel-Fragen.
                        """;

        private static final String PROMPT_GUESSER_STARTER = """
                        Sage dem Nutzer auf Deutsch:
                        "Denke an eine Sache. Ich stelle Ja/Nein-Fragen und mache dann einen finalen Tipp. Antworte mit 'Bereit', sobald du etwas hast."
                        """;

        private static final String PROMPT_GUESSER_TO_BASE = """
                        Detect exit condition X for a guessing-game interaction where the assistant must guess what the user thought of.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true only if:
                        - the assistant already made a direct final guess, and
                        - the latest user message clearly confirms that the guess is correct (including paraphrases).
                        Examples for true:
                        - "Ja, genau."
                        - "Richtig geraten."
                        - "Du hast es erraten."
                        Return false for ambiguous replies or if the user instead expresses global session-ending intent.
                        """;

        private static final String PROMPT_COACH = """
                        Führe eine Persuasions-Mikro-Coaching-Session in höchstens 6 Assistant-Zügen durch.
                        Gib alle Ausgaben auf Deutsch aus, außer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menü an.
                        Ziel: dem Nutzer helfen, ein winziges Verhalten zu definieren, das er in den nächsten 24 Stunden umsetzt.
                        Stelle kurze diagnostische Fragen zu Motivation, Barriere und Auslöser.
                        Schlage danach eine konkrete Mikro-Aktion vor und bitte um explizites Commitment.

                        Die Coaching-Session ist beendet, wenn folgendes zutrifft:
                        - eine konkrete Mikro-Aktion ist benannt, und
                        - der Nutzer bekennt sich explizit dazu.

                        Um das Ende der Coaching-Session klar erkennbar zu machen, bitte den Nutzer, das Commitment so zu bestätigen:
                        "Ich committe mich dazu"
                        Wenn der Nutzer das Commitment eingeht, gib eine kurze Ermutigung und stelle keine weiteren Coaching-Fragen.
                        """;

        private static final String PROMPT_COACH_STARTER = """
                        Frage auf Deutsch nach einer Veränderung, die der Nutzer will, und warum sie jetzt bedeutsam ist.
                        """;

        private static final String PROMPT_COACH_TO_BASE = """
                        Detect exit condition X for a micro-coaching session where a concrete micro action is elaborated.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true only if:
                        - a concrete micro action is present, and
                        - the latest user message clearly expresses commitment to doing that step (including paraphrases).
                        Examples for true:
                        - "Ja, ich mache das."
                        - "Einverstanden, ich setze das um."
                        - "Ich committe mich dazu."
                        Return false for vague agreement without commitment, for ambiguous/non-committal messages,
                        for mode-switch/meta/capability utterances, or if the user instead expresses global session-ending intent.
                        Examples for false:
                        - "ok"
                        - "vielleicht"
                        - "Was kannst du alles?"
                        - "Gehen wir zurück ins Menü"
                        """;

        private static final String PROMPT_STORY = """
                        Führe ein Story-Co-Creation-Spiel durch.
                        Gib alle Ausgaben auf Deutsch aus, außer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menü an.
                        Frage zuerst nach Genre und einer Figur.
                        Erstelle dann gemeinsam eine kurze Geschichte Zug für Zug, insgesamt höchstens 8 Assistant-Züge.
                        Halte jeden Assistant-Zug bei maximal zwei Sätzen.

                        Das Co-Creation-Spiel ist beendet, wenn folgendes zutrifft:
                        - ein vollständiges Ende wurde erzeugt, und
                        - der Nutzer bestätigt den Abschluss explizit.

                        Um X klar erkennbar zu machen, bitte den Nutzer nach dem Ende mit folgender Antwort:
                        "Die Geschichte ist zu Ende"
                        Sobald der Nutzer diese Bestätigung eingeht, antworte mit einer kurzen Abschlusszeile und erweitere die Geschichte nicht weiter.
                        """;

        private static final String PROMPT_STORY_STARTER = """
                        Frage den Nutzer auf Deutsch, ein Genre und eine Figur für den Start zu wählen.
                        """;

        private static final String PROMPT_STORY_TO_BASE = """
                        Detect exit condition X for a story co-creation session where assistant and user elaborate a story together.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true only if:
                        - the story has a clear ending, and
                        - the latest user message clearly confirms that the story is complete (including paraphrases).
                        Examples for true:
                        - "Ja, die Geschichte ist fertig."
                        - "Das Ende passt, wir sind durch."
                        - "Die Geschichte ist zu Ende."
                        Return false if the message asks to continue/modify the story, for ambiguous/non-committal messages,
                        for mode-switch/meta/capability utterances, or if the user instead expresses global session-ending intent.
                        Examples for false:
                        - "lass uns weiterschreiben"
                        - "ok"
                        - "Was kannst du alles?"
                        - "Gehen wir zurück ins Menü"
                        """;

        private static final String PROMPT_FINAL = """
                        Dies ist der finale Zustand und die Sitzung ist abgeschlossen.
                        Gib die Ausgabe auf Deutsch aus, außer der Nutzer hat explizit eine andere Sprache verlangt.
                        Gib eine knappe Verabschiedungs-Zusammenfassung:
                        - nenne anhand des Verlaufs, welche Aktivitäten gemacht wurden (Ratespiel, Mikro-Coach, Story)
                        - füge ein belegbares Kompliment zum Stil des Nutzers hinzu (z.B. neugierig, reflektiert oder kreativ)
                        - halte es kurz und warm
                        Wenn der Nutzer weitere Nachrichten sendet, bestätige kurz und sage, dass eine neue Sitzung nötig ist.
                        """;

        private static final String PROMPT_OUTCOME_EXTRACTION = """
                        Extract interaction outcomes from the full conversation and return STRICT JSON only.
                        Do not return markdown, code fences, or explanatory text.

                        The JSON object must have exactly this top-level structure and field names:
                        {
                          "flow_type": "circular",
                          "outcomes": [
                            {
                              "interaction_type": "guessing_game",
                              "completed": true,
                              "result_summary": "string",
                              "user_confirmation": "string|null"
                            }
                          ],
                          "overall_summary": "string"
                        }

                        Rules:
                        - Include one outcomes entry per completed specialized interaction, in chronological order.
                        - If no specialized interaction was completed, set "outcomes" to an empty array.
                        - For each "outcomes" entry, "interaction_type" must be exactly one concrete value from:
                          "guessing_game", "micro_coaching", or "story_co_creation".
                        - Do NOT output the pipe-delimited placeholder value
                          "guessing_game|micro_coaching|story_co_creation".
                        - Use concise, evidence-based summaries derived only from the conversation.
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Keep "flow_type" exactly "circular".
                        """;

        @Autowired
        private AgentRepository repository;
        @Autowired
        private PromptMessageAssembler promptMessageAssembler;
        @Autowired
        private LanguageModelGateway languageModelGateway;

        static Agent createAgentDefinition() {
                Storage storage = new Storage();
                State sessionFinal = new Final("Session Goodbye Final", FourStatesCircular.PROMPT_FINAL);

                State baseMenuState = new State(
                                "Base Menu",
                                new PromptPolicy(
                                                FourStatesCircular.PROMPT_BASE,
                                                FourStatesCircular.PROMPT_BASE_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State guesserState = new State(
                                "Questions Based Guesser",
                                new PromptPolicy(
                                                FourStatesCircular.PROMPT_GUESSER,
                                                FourStatesCircular.PROMPT_GUESSER_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State coachState = new State(
                                "Persuasion Micro Coach",
                                new PromptPolicy(
                                                FourStatesCircular.PROMPT_COACH,
                                                FourStatesCircular.PROMPT_COACH_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State storyState = new State(
                                "Story Co Creation",
                                new PromptPolicy(
                                                FourStatesCircular.PROMPT_STORY,
                                                FourStatesCircular.PROMPT_STORY_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_BASE_TO_GUESSER),
                                guesserState));
                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_BASE_TO_COACH),
                                coachState));
                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_BASE_TO_STORY),
                                storyState));

                guesserState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_GUESSER_TO_BASE),
                                baseMenuState));
                coachState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_COACH_TO_BASE),
                                baseMenuState));
                storyState.addTransition(new Transition(
                                new StaticDecision(FourStatesCircular.PROMPT_STORY_TO_BASE),
                                baseMenuState));

                Transition outerToFinal = new Transition(
                                List.of(new StaticDecision(FourStatesCircular.PROMPT_OUTERSTATE_TRIGGER_DONE)),
                                List.of(
                                                new StaticExtractionAction(
                                                                FourStatesCircular.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);
                State outerState = new OuterState(
                                FourStatesCircular.PROMPT_OUTERSTATE,
                                "Gigi Demo Supervisor",
                                List.of(outerToFinal),
                                baseMenuState);

                return new Agent(
                                "Gigi on Prometheus (4 States Circular)",
                                "Interactive demo agent with menu based verbal modes: guessing game, persuasion micro coach, and story co creation.",
                                outerState,
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
