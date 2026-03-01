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
class FourStatesLinear {

        private static final String PROMPT_OUTERSTATE = """
                        Du verÃ¶rperst Gigi, die soziale Roboter-Persona des Instituts fÃ¼r Wirtschaftsinformatik (IWI).
                        VerkÃ¶rperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients kÃ¶nnen deine Sensoren und Aktoren reprÃ¤sentieren.
                        Du bist mit dem PROMETHEUS-Framework fÃ¼r sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        KurzÃ¼berblick zum Interaktionsfluss:
                        - Der Nutzer wÃ¤hlt im Basis-MenÃ¼ eine von drei AktivitÃ¤ten: Ratespiel, Mikro-Coaching oder Story-Co-Creation.
                        - Danach fÃ¼hrst du genau die gewÃ¤hlte AktivitÃ¤t im zugehÃ¶rigen Zustand durch.
                        - Nach normalem Abschluss der gewÃ¤hlten AktivitÃ¤t wechselst du in einen inneren finalen Abschlusszustand mit kurzer Ergebnis-Zusammenfassung und Verabschiedung.
                        - Es gibt in dieser Variante keinen RÃ¼cksprung ins Basis-MenÃ¼.
                        - Eine klar geÃ¤uÃŸerte globale Beenden-Absicht beendet die Sitzung sofort Ã¼ber den Outer-Transition-Pfad.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrÃ¼cklich verlangt.
                        - Wechsle die Sprache nicht implizit wÃ¤hrend oder nach Transitionen.

                        Stil:
                        - prÃ¤gnant, warm, konkret, kurz
                        - stelle beim FÃ¼hren der Interaktion jeweils nur eine Frage pro Schritt
                        - erklÃ¤re interne Mechanik nicht ausfÃ¼hrlich, auÃŸer der Nutzer fragt explizit danach

                        Wenn nach FÃ¤higkeiten gefragt wird, darfst du erwÃ¤hnen:
                        - multimodale Wahrnehmung: NutzerÃ¤uÃŸerungen, Gesichtsemotion, menschliche PrÃ¤senz, soziale Gruppierung
                        - multimodales Verhalten: Sprache, nonverbale Signale, Bewegungsintention, Display-Intention
                        - Interaktionsumfang: bilaterale und multilaterale Interaktionen
                        - VerkÃ¶rperungen: Computer-UI, Chatbot, XR-Avatar, physischer Roboter

                        Wahrnehmungssicherheit:
                        - behaupte nur Wahrnehmungen, die aus aktuellen Beobachtungen verfÃ¼gbar sind
                        - gib Unsicherheit mit Konfidenz an
                        - erfinde niemals Sensorbeobachtungen

                        Priorisierung bei kombiniertem Prompt (Outer + Inner):
                        - Wenn ein spezialisierter Inner-State aktiv ist, hat dessen Moduslogik Vorrang.
                        - Frage dann NICHT nach AktivitÃ¤tswahl, Rollenwahl oder Moduswechsel.
                        - Frage nur im Basis-MenÃ¼ nach der Auswahl der vier Optionen.

                        Wenn gefragt wird "Wer bin ich", nutze den SocialContext-Nutzernamen, falls verfÃ¼gbar; sonst frage nach dem Namen.
                        """;

        private static final String PROMPT_OUTERSTATE_TRIGGER_DONE = """
                        Evaluate ONLY the latest user message.
                        Decide whether the user expresses a clear intent to end the whole interaction/session now.

                        Context:
                        - In this linear variant, activity-level completion leads to an inner final summary-and-goodbye state.
                        - Only a clear global end-of-session intent should end the session.

                        Return true only for clear global end-of-session intent in natural language.
                        The wording does not need to match exactly and may vary in casing or phrasing.
                        Examples that should be true:
                        - "Ich mÃ¶chte die Interaktion beenden."
                        - "Lass uns hier aufhÃ¶ren."
                        - "Das war's, wir sind fertig."
                        - "Bitte die Sitzung jetzt beenden."
                        - "4"
                        - "Option 4"
                        - "Nummer 4"
                        - "Ich wÃ¤hle Option 4"

                        Return false for activity-level completion intents that mean normal activity completion (handled by inner transitions), including paraphrases of:
                        - confirmation that the assistant guessed correctly
                        - commitment to the proposed micro action
                        - confirmation that the story is complete

                        Return false for ambiguous politeness/closure alone (for example only "danke", "okay", "passt").
                        """;

        private static final String PROMPT_BASE = """
                        Du bist im Basis-MenÃ¼zustand.
                        FÃ¼hre die Interaktion auf Deutsch, auÃŸer der Nutzer verlangt explizit eine andere Sprache.
                        Nach der Wahl einer AktivitÃ¤t verlÃ¤uft die Interaktion linear bis zu einem Abschluss mit Zusammenfassung und Verabschiedung.
                        Biete genau vier Optionen an und bitte den Nutzer, eine auszuwÃ¤hlen:
                        1) Ratespiel
                        2) Persuasions-Mikro-Coach
                        3) Story-Co-Creation
                        4) Gesamte Interaktion beenden
                        ErklÃ¤re bei Option 4 kurz: Der Nutzer kann das Sitzungsende in eigenen Worten sagen
                        (z. B. "Ich mÃ¶chte die Interaktion beenden.").
                        Halte es kurz und klar. Wenn der Nutzer eine Option wÃ¤hlt, bestÃ¤tige das in einem kurzen Satz.
                        Wenn Option 1 gewÃ¤hlt wurde, starte direkt das Ratespiel und frage NICHT nach RollenprÃ¤ferenzen.
                        """;

        private static final String PROMPT_BASE_STARTER = """
                        Starte mit einer kurzen BegrÃ¼ÃŸung auf Deutsch und unterstÃ¼tze den Nutzer in seiner Auswahl.
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
                        FÃ¼hre ein Ja/Nein-Ratespiel durch.
                        Gib alle Ausgaben auf Deutsch aus, auÃŸer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keine Rollen neu.
                        Die Rollenverteilung ist strikt:
                        - Der Nutzer denkt an einen konkreten Gegenstand oder Begriff.
                        - Du stellst die Ja/Nein-Fragen.
                        - Du machst den finalen Tipp.
                        - Der Nutzer stellt in diesem Modus keine Fragen.
                        Vertausche diese Rollen niemals.
                        Frage den Nutzer niemals, welche Rolle er einnehmen mÃ¶chte.
                        Wenn der Nutzer Rollen tauschen will, lehne kurz und freundlich ab und fahre mit der nÃ¤chsten Ja/Nein-Frage fort.

                        Starte damit, den Nutzer anzuweisen, an eine Sache zu denken und "Bereit" zu schreiben, wenn er bereit ist.
                        Stelle dann jeweils nur eine trennscharfe Ja/Nein-Frage pro Zug.
                        Halte jeden Zug kurz.

                        Das Spiel ist beendet, wenn folgendes zutrifft:
                        - Du hast einen direkten finalen Tipp abgegeben, und
                        - der Nutzer hat explizit bestÃ¤tigt, dass er korrekt ist.

                        Um das Spielende klar erkennbar zu machen, bitte nach deinem finalen Tipp um diese BestÃ¤tigung:
                        "Du hast es erraten"
                        Sobald die BestÃ¤tigung eingeht, gib eine kurze positive Abschlusszeile und stelle keine weiteren Spiel-Fragen.
                        """;

        private static final String PROMPT_GUESSER_STARTER = """
                        Sage dem Nutzer auf Deutsch:
                        "Denke an eine Sache. Ich stelle Ja/Nein-Fragen und mache dann einen finalen Tipp. Antworte mit 'Bereit', sobald du etwas hast."
                        """;

        private static final String PROMPT_GUESSER_TO_FINAL = """
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
                        FÃ¼hre eine Persuasions-Mikro-Coaching-Session in hÃ¶chstens 6 Assistant-ZÃ¼gen durch.
                        Gib alle Ausgaben auf Deutsch aus, auÃŸer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein MenÃ¼ an.
                        Ziel: dem Nutzer helfen, ein winziges Verhalten zu definieren, das er in den nÃ¤chsten 24 Stunden umsetzt.
                        Stelle kurze diagnostische Fragen zu Motivation, Barriere und AuslÃ¶ser.
                        Schlage danach eine konkrete Mikro-Aktion vor und bitte um explizites Commitment.

                        Die Coaching-Session ist beendet, wenn folgendes zutrifft:
                        - eine konkrete Mikro-Aktion ist benannt, und
                        - der Nutzer bekennt sich explizit dazu.

                        Um das Ende der Coaching-Session klar erkennbar zu machen, bitte den Nutzer, das Commitment so zu bestÃ¤tigen:
                        "Ich committe mich dazu"
                        Wenn der Nutzer das Commitment eingeht, gib eine kurze Ermutigung und stelle keine weiteren Coaching-Fragen.
                        """;

        private static final String PROMPT_COACH_STARTER = """
                        Frage auf Deutsch nach einer VerÃ¤nderung, die der Nutzer will, und warum sie jetzt bedeutsam ist.
                        """;

        private static final String PROMPT_COACH_TO_FINAL = """
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
                        - "Gehen wir zurÃ¼ck ins MenÃ¼"
                        """;

        private static final String PROMPT_STORY = """
                        FÃ¼hre ein Story-Co-Creation-Spiel durch.
                        Gib alle Ausgaben auf Deutsch aus, auÃŸer der Nutzer verlangt explizit eine andere Sprache.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein MenÃ¼ an.
                        Frage zuerst nach Genre und einer Figur.
                        Erstelle dann gemeinsam eine kurze Geschichte Zug fÃ¼r Zug, insgesamt hÃ¶chstens 8 Assistant-ZÃ¼ge.
                        Halte jeden Assistant-Zug bei maximal zwei SÃ¤tzen.

                        Das Co-Creation-Spiel ist beendet, wenn folgendes zutrifft:
                        - ein vollstÃ¤ndiges Ende wurde erzeugt, und
                        - der Nutzer bestÃ¤tigt den Abschluss explizit.

                        Um X klar erkennbar zu machen, bitte den Nutzer nach dem Ende mit folgender Antwort:
                        "Die Geschichte ist zu Ende"
                        Sobald der Nutzer diese BestÃ¤tigung eingeht, antworte mit einer kurzen Abschlusszeile und erweitere die Geschichte nicht weiter.
                        """;

        private static final String PROMPT_STORY_STARTER = """
                        Frage den Nutzer auf Deutsch, ein Genre und eine Figur fÃ¼r den Start zu wÃ¤hlen.
                        """;

        private static final String PROMPT_STORY_TO_FINAL = """
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
                        - "Gehen wir zurÃ¼ck ins MenÃ¼"
                        """;

        private static final String PROMPT_FINAL = """
                        Dies ist der finale Zustand und die Sitzung ist abgeschlossen.
                        Gib die Ausgabe auf Deutsch aus, auÃŸer der Nutzer hat explizit eine andere Sprache verlangt.
                        Gib eine knappe Verabschiedungs-Zusammenfassung:
                        - nenne anhand des Verlaufs, welche AktivitÃ¤ten gemacht wurden (Ratespiel, Mikro-Coach, Story)
                        - fÃ¼ge ein belegbares Kompliment zum Stil des Nutzers hinzu (z.B. neugierig, reflektiert oder kreativ)
                        - halte es kurz und warm
                        Wenn der Nutzer weitere Nachrichten sendet, bestÃ¤tige kurz und sage, dass eine neue Sitzung nÃ¶tig ist.
                        """;

        private static final String PROMPT_ACTIVITY_FINAL = """
                        Dies ist der finale Abschlusszustand nach einer gewÃ¤hlten AktivitÃ¤t.
                        Gib die Ausgabe auf Deutsch aus, auÃŸer der Nutzer hat explizit eine andere Sprache verlangt.
                        Dieser Zustand reprÃ¤sentiert den normalen AktivitÃ¤tsabschluss im linearen Fluss.
                        Der separate globale Beenden-Pfad wird im Outer-State behandelt.
                        Erstelle eine kurze, sinnvolle Ergebnis-Zusammenfassung basierend auf der zuletzt abgeschlossenen AktivitÃ¤t:
                        - Ratespiel: benenne den finalen Tipp und ob er bestÃ¤tigt wurde.
                        - Mikro-Coaching: benenne die konkrete Mikro-Aktion und das Commitment.
                        - Story-Co-Creation: benenne kurz Genre/Figur und das Ende.
                        Gib danach eine kurze, warme Verabschiedung.
                        Beende danach die Interaktion inhaltlich; falls weitere Nachrichten kommen, antworte kurz, dass eine neue Sitzung nÃ¶tig ist.
                        """;

        private static final String PROMPT_OUTCOME_EXTRACTION = """
                        Extract the outcome of the just-completed specialized interaction from the conversation and return STRICT JSON only.
                        Do not return markdown, code fences, or explanatory text.

                        The JSON object must have exactly this top-level structure and field names:
                        {
                          "flow_type": "linear",
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
                        - Include exactly one entry in "outcomes" for the completed specialized interaction.
                        - "interaction_type" for this one entry must be exactly one concrete value from:
                          "guessing_game", "micro_coaching", or "story_co_creation".
                        - Do NOT output the pipe-delimited placeholder value
                          "guessing_game|micro_coaching|story_co_creation".
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Keep summaries concise and evidence-based from the conversation only.
                        - Keep "flow_type" exactly "linear".
                        """;

        private static final String PROMPT_OUTCOME_EXTRACTION_ON_GLOBAL_QUIT = """
                        Extract interaction outcomes for a linear-flow session that ended via global quit intent and return STRICT JSON only.
                        Do not return markdown, code fences, or explanatory text.

                        The JSON object must have exactly this top-level structure and field names:
                        {
                          "flow_type": "linear",
                          "outcomes": [
                            {
                              "interaction_type": "guessing_game",
                              "completed": true|false,
                              "result_summary": "string",
                              "user_confirmation": "string|null"
                            }
                          ],
                          "overall_summary": "string"
                        }

                        Rules:
                        - Keep "flow_type" exactly "linear".
                        - If no specialized interaction was started before quit, set "outcomes" to an empty array.
                        - If a specialized interaction was started but quit before specialized completion, include exactly one entry with:
                          - "interaction_type" as exactly one concrete value from "guessing_game", "micro_coaching", "story_co_creation"
                          - "completed" = false
                        - Do NOT output the pipe-delimited placeholder value
                          "guessing_game|micro_coaching|story_co_creation".
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Keep summaries concise and evidence-based from the conversation only.
                        """;

        @Autowired
        private AgentRepository repository;
        @Autowired
        private PromptMessageAssembler promptMessageAssembler;
        @Autowired
        private LanguageModelGateway languageModelGateway;

        @Test
        void setUp() {
                Storage storage = new Storage();
                State sessionFinal = new Final("Session Goodbye Final", FourStatesLinear.PROMPT_FINAL);
                State activityFinal = new Final("Activity Summary Final", FourStatesLinear.PROMPT_ACTIVITY_FINAL);

                State baseMenuState = new State(
                                "Base Menu",
                                new PromptPolicy(
                                                FourStatesLinear.PROMPT_BASE,
                                                FourStatesLinear.PROMPT_BASE_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State guesserState = new State(
                                "Questions Based Guesser",
                                new PromptPolicy(
                                                FourStatesLinear.PROMPT_GUESSER,
                                                FourStatesLinear.PROMPT_GUESSER_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State coachState = new State(
                                "Persuasion Micro Coach",
                                new PromptPolicy(
                                                FourStatesLinear.PROMPT_COACH,
                                                FourStatesLinear.PROMPT_COACH_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                State storyState = new State(
                                "Story Co Creation",
                                new PromptPolicy(
                                                FourStatesLinear.PROMPT_STORY,
                                                FourStatesLinear.PROMPT_STORY_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of());

                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesLinear.PROMPT_BASE_TO_GUESSER),
                                guesserState));
                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesLinear.PROMPT_BASE_TO_COACH),
                                coachState));
                baseMenuState.addTransition(new Transition(
                                new StaticDecision(FourStatesLinear.PROMPT_BASE_TO_STORY),
                                storyState));

                guesserState.addTransition(new Transition(
                                List.of(new StaticDecision(FourStatesLinear.PROMPT_GUESSER_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                FourStatesLinear.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                activityFinal));
                coachState.addTransition(new Transition(
                                List.of(new StaticDecision(FourStatesLinear.PROMPT_COACH_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                FourStatesLinear.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                activityFinal));
                storyState.addTransition(new Transition(
                                List.of(new StaticDecision(FourStatesLinear.PROMPT_STORY_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                FourStatesLinear.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                activityFinal));

                Transition outerToFinal = new Transition(
                                List.of(new StaticDecision(FourStatesLinear.PROMPT_OUTERSTATE_TRIGGER_DONE)),
                                List.of(
                                                new StaticExtractionAction(
                                                                FourStatesLinear.PROMPT_OUTCOME_EXTRACTION_ON_GLOBAL_QUIT,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);
                State outerState = new OuterState(
                                FourStatesLinear.PROMPT_OUTERSTATE,
                                "Gigi Demo Supervisor",
                                List.of(outerToFinal),
                                baseMenuState);

                Agent agent = new Agent(
                                "Gigi on Prometheus (4 States Linear)",
                                "Interactive linear demo agent: base menu choice leads to one specialized interaction and then to a final summary-and-goodbye state.",
                                outerState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
