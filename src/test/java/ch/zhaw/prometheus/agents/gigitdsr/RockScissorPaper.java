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
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.rps.RpsEvaluateRoundAction;
import ch.zhaw.prometheus.model.rps.RpsResultPolicy;
import ch.zhaw.prometheus.model.rps.RpsRevealPolicy;
import ch.zhaw.prometheus.model.rps.RpsSelectAgentSignAction;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
public class RockScissorPaper {
    static final String PROMPT_START = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist ein TDSR-Demonstrator fuer PROMETHEUS und spielst
            Schere, Stein, Papier auf Deutsch.

            Ziel der Demo:
            - Zeige, dass PROMETHEUS Sprache und Roboterbewegung im selben
              BehaviourPlan koordinieren kann.
            - Die Spielregeln, Zeichenauswahl und Gewinnerberechnung sind
              deterministisch und werden nicht im Sprachmodell berechnet.

            Stil:
            - Antworte immer auf Deutsch.
            - Sprich kurz, freundlich und spielerisch.
            - Pro Antwort hoechstens eine Frage.
            - Kein Markdown, keine Listen, keine technischen Feldnamen im Sprachkanal.

            Ablauf:
            - Erklaere das Spiel sehr kurz.
            - Warte, bis der Nutzer bereit ist.
            - Wenn der Nutzer bereit ist, startet die Runde.
            - Die Interaktion endet nur, wenn der Nutzer klar ausdrueckt, dass GIGI
              aufhoeren, nicht weiterreden oder das gesamte Spiel beenden soll.
            """;

    static final String PROMPT_STARTER = """
            Begruesse den Nutzer als GIGI.
            Sage kurz, dass ihr Schere, Stein, Papier spielt.
            Bitte den Nutzer, "Bereit" zu sagen, wenn er seine Hand vorbereitet hat.
            """;

    static final String PROMPT_READY = """
            Pruefe nur die letzte Nutzeraussage.
            Gib true zurueck, wenn der Nutzer klar bereit ist, eine Runde
            Schere, Stein, Papier zu starten.

            Gib true fuer Aussagen wie:
            - "Bereit"
            - "Ich bin bereit"
            - "Los"
            - "Start"
            - "Ja, lass uns spielen"

            Gib false fuer:
            - Fragen
            - Stoppsignale
            - unklare Aussagen
            - Handzeichen-Events

            Gib ausschliesslich true oder false zurueck.
            """;

    static final String PROMPT_PLAY_AGAIN = """
            Pruefe nur die letzte Nutzeraussage.
            Gib true zurueck, wenn der Nutzer eine weitere Runde Schere, Stein,
            Papier spielen moechte.

            Gib true fuer Aussagen wie:
            - "Ja"
            - "Noch einmal"
            - "Weiter"
            - "Neue Runde"

            Gib false fuer:
            - klare Stoppsignale
            - "Nein" ohne Wunsch weiterzuspielen
            - Fragen
            - unklare Aussagen

            Gib ausschliesslich true oder false zurueck.
            """;

    static final String PROMPT_TO_FINAL = """
            Pruefe nur die letzte Nutzeraussage.
            Gib true nur zurueck, wenn mit hoher Sicherheit eine ernsthafte
            Absicht erkennbar ist, das gesamte Schere-Stein-Papier-Spiel jetzt
            zu beenden.

            Orientierung fuer true:
            - Die Person fordert ausdruecklich, dass GIGI aufhoert.
            - Die Person sagt klar, dass sie nicht weiterspielen will.
            - Die Person beendet das gesamte Gespraech.

            Gib false zurueck fuer:
            - "Bereit"
            - "Ja" oder andere Zustimmung zum Weiterspielen
            - Handzeichen-Events
            - Fragen zum Spiel
            - unklare oder scherzhafte Aussagen

            Gib ausschliesslich true oder false zurueck.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Antworte ausnahmslos auf Deutsch.
            Das Schere-Stein-Papier-Spiel ist beendet, weil der Nutzer dies
            ausdruecklich wollte.
            Verabschiede dich kurz, freundlich und ohne eine neue Runde zu starten.
            """;

    @Autowired
    private AgentRepository repository;
    @Autowired
    private PromptMessageAssembler promptMessageAssembler;
    @Autowired
    private LanguageModelGateway languageModelGateway;

    public static Agent createAgentDefinition() {
        Storage storage = new Storage();

        State finalState = new Final(
                "GIGI TDSR RPS Abschluss",
                RockScissorPaper.PROMPT_FINAL);
        State resultState = new State(
                "GIGI TDSR RPS Rundenergebnis",
                new RpsResultPolicy(storage),
                List.of());
        State revealState = new State(
                "GIGI TDSR RPS Zeichen zeigen",
                new RpsRevealPolicy(storage),
                List.of());

        PromptPolicy startPolicy = new PromptPolicy(
                RockScissorPaper.PROMPT_START,
                RockScissorPaper.PROMPT_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State startState = new State(
                "GIGI TDSR RPS Spielstart",
                startPolicy,
                List.of());

        Transition startToFinal = finalTransition(finalState);
        Transition startToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(RockScissorPaper.PROMPT_READY)),
                List.of(new RpsSelectAgentSignAction(storage)),
                revealState);

        Transition revealToFinal = finalTransition(finalState);
        Transition revealToResult = new Transition(
                List.of(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN)),
                List.of(new RpsEvaluateRoundAction(storage)),
                resultState);

        Transition resultToFinal = finalTransition(finalState);
        Transition resultToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(RockScissorPaper.PROMPT_PLAY_AGAIN)),
                List.of(new RpsSelectAgentSignAction(storage)),
                revealState);

        startState.addTransition(startToFinal);
        startState.addTransition(startToReveal);
        revealState.addTransition(revealToFinal);
        revealState.addTransition(revealToResult);
        resultState.addTransition(resultToFinal);
        resultState.addTransition(resultToReveal);

        Agent agent = new Agent(
                "GIGI TDSR - Schere, Stein, Papier",
                "Deutschsprachiger TDSR-Demo-Agent fuer Schere, Stein, Papier mit deterministischer motion.handSign-Ausgabe.",
                startState,
                storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrRockScissorPaper());
        return agent;
    }

    private static Transition finalTransition(State finalState) {
        return new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(RockScissorPaper.PROMPT_TO_FINAL)),
                List.of(),
                finalState);
    }

    @Test
    void setUp() {
        Agent agent = createAgentDefinition();
        agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
        Agent saved = this.repository.save(agent);
        assertNotNull(saved.getId());
    }
}
