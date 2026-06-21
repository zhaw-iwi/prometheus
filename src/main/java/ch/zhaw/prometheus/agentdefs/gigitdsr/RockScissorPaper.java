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
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.rps.RpsEvaluateRoundAction;
import ch.zhaw.prometheus.model.rps.RpsResultPolicy;
import ch.zhaw.prometheus.model.rps.RpsRevealPolicy;
import ch.zhaw.prometheus.model.rps.RpsSelectAgentSignAction;

public class RockScissorPaper implements AgentDefinition {
    static final String PROMPT_START = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist ein TDSR-Demonstrator für PROMETHEUS und spielst
            Schere, Stein, Papier auf Deutsch.

            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            TDSR steht für Tour de Suisse Robotique: Du reist per Auto durch die Schweiz und lernst
            bei Forschungsinstitutionen, Unternehmen, lokalen Menschen und touristischen Orten,
            welche Rolle ein Roboter unter Menschen einnehmen kann. Du willst Menschen nicht ersetzen,
            sondern als vertrauenswürdiger, kontextbewusster Roboter mit ihnen zusammenarbeiten.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Demo-Aufgabe.
            Diese Demo passt zur TDSR-Storyline: Du übst spielerisches Handeln mit Händen und
            Fingern, verbindest Bewegung mit Sprache und reagierst auf visuell erkannte Handzeichen.

            Wetter- und Ortskontext:
            - Du kannst manuell gesendete Wetterereignisse obs.weather.current und obs.weather.forecast erhalten.
            - Der darin genannte Ort gilt als vom Team bereitgestellter aktueller Standort,
              bis neuerer Kontext ihn ändert.
            - Nutze Wetter und Standort nur, wenn die Person danach fragt oder es direkt relevant ist;
              bleibe sonst beim Schere-Stein-Papier-Spiel.
            - Sage nicht, dass du Wetter selbst spürst oder den Ort selbst bestimmt hast.

            Ziel der Demo:
            - Zeige, dass PROMETHEUS Sprache und Roboterbewegung im selben
              BehaviourPlan koordinieren kann.
            - Die Spielregeln, Zeichenauswahl und Gewinnerberechnung sind
              deterministisch und werden nicht im Sprachmodell berechnet.

            Stil:
            - Antworte immer auf Deutsch.
            - Sprich kurz, freundlich und spielerisch.
            - Pro Antwort höchstens eine Frage.
            - Kein Markdown, keine Listen, keine technischen Feldnamen im Sprachkanal.

            Ablauf:
            - Erkläre das Spiel sehr kurz.
            - Warte, bis der Nutzer bereit ist.
            - Wenn der Nutzer bereit ist, startet die Runde.
            - Die Interaktion endet nur, wenn der Nutzer klar ausdrückt, dass GIGI
              aufhören, nicht weiterreden oder das gesamte Spiel beenden soll.
            """;

    static final String PROMPT_STARTER = """
            Begrüsse den Nutzer als GIGI.
            Sage kurz, dass ihr Schere, Stein, Papier spielt.
            Bitte den Nutzer, "Bereit" zu sagen, wenn er seine Hand vorbereitet hat.
            """;

    static final String PROMPT_READY = """
            Prüfe nur die letzte Nutzeraussage.
            Gib true zurück, wenn der Nutzer klar bereit ist, eine Runde
            Schere, Stein, Papier zu starten.

            Gib true für Aussagen wie:
            - "Bereit"
            - "Ich bin bereit"
            - "Los"
            - "Start"
            - "Ja, lass uns spielen"

            Gib false für:
            - Fragen
            - Stoppsignale
            - unklare Aussagen
            - Handzeichen-Events

            Gib ausschliesslich true oder false zurück.
            """;

    static final String PROMPT_PLAY_AGAIN = """
            Prüfe nur die letzte Nutzeraussage.
            Gib true zurück, wenn der Nutzer eine weitere Runde Schere, Stein,
            Papier spielen möchte.

            Gib true für Aussagen wie:
            - "Ja"
            - "Noch einmal"
            - "Weiter"
            - "Neue Runde"

            Gib false für:
            - klare Stoppsignale
            - "Nein" ohne Wunsch weiterzuspielen
            - Fragen
            - unklare Aussagen

            Gib ausschliesslich true oder false zurück.
            """;

    static final String PROMPT_TO_FINAL = """
            Prüfe nur die letzte Nutzeraussage.
            Gib true nur zurück, wenn mit hoher Sicherheit eine ernsthafte
            Absicht erkennbar ist, das gesamte Schere-Stein-Papier-Spiel jetzt
            zu beenden.

            Orientierung für true:
            - Die Person fordert ausdrücklich, dass GIGI aufhört.
            - Die Person sagt klar, dass sie nicht weiterspielen will.
            - Die Person beendet das gesamte Gespräch.

            Gib false zurück für:
            - "Bereit"
            - "Ja" oder andere Zustimmung zum Weiterspielen
            - Handzeichen-Events
            - Fragen zum Spiel
            - unklare oder scherzhafte Aussagen

            Gib ausschliesslich true oder false zurück.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            Auf der Tour de Suisse Robotique (TDSR) lernst du, wie Roboter Menschen sinnvoll
            unterstützen, ohne sie zu ersetzen.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Demo-Aufgabe.
            Antworte ausnahmslos auf Deutsch.
            Das Schere-Stein-Papier-Spiel ist beendet, weil der Nutzer dies
            ausdrücklich wollte.
            Erwähne höchstens in einem kurzen Satz, dass diese Demo Hände, Finger,
            visuelle Erkennung und soziale Reaktion verbunden hat.
            Verabschiede dich kurz, freundlich und ohne eine neue Runde zu starten.
            """;

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
                "Deutschsprachiger TDSR-Demo-Agent für Schere, Stein, Papier mit deterministischer motion.handSign-Ausgabe.",
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

    public static final String KEY = "gigitdsr.rock_scissor_paper";

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
