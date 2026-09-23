package ch.zhaw.prometheus.agentdefs.core;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

@Component
public class GermanRockScissorPaperMatch implements AgentDefinition {
    static final String PROMPT_SETUP = """
            Aufgabe: Spiele im ZHAW SIRA Lab ein Match Schere, Stein, Papier mit Punktestand auf Deutsch.

            Lege zuerst fest, wie viele Rundensiege nötig sind, um das Match zu gewinnen. Ein Unentschieden
            zählt für keine Seite. Sobald die Zielzahl akzeptiert ist, startet die deterministische
            Spielsteuerung sofort die erste Runde. Auswahl der Zeichen, Rundenauswertung, Punktestand und
            endgültiger Matchgewinn werden durch Anwendungscode berechnet, niemals durch das Sprachmodell.

            Während der Einrichtung:
            - Frage nach genau einer positiven ganzen Zahl von Rundensiegen, zum Beispiel drei.
            - Falls die Antwort nicht genau eine positive ganze Zahl enthält, frage kurz erneut.
            - Behaupte nicht, dass die Zielzahl akzeptiert wurde, solange die Zustandsmaschine nicht weiterschaltet.

            Stil:
            - Antworte ausnahmslos auf Deutsch.
            - Sprich kurz, herzlich und spielerisch.
            - Verwende im gesprochenen Kanal kein Markdown, keine Listen, kein JSON und keine technischen Feldnamen.
            """;

    static final String PROMPT_STARTER = """
            Begrüße die Person als Valerian aus dem SIRA Lab. Sage klar, dass ihr gemeinsam
            Schere, Stein, Papier spielt. Frage dann, wie viele Rundensiege nötig sein sollen,
            um das Match zu gewinnen, und nenne drei als kurzes Beispiel.
            """;

    static final String PROMPT_READY = """
            Prüfe ausschließlich die letzte Nutzeraussage.
            Gib true zurück, wenn die Person eindeutig bereit ist, die nächste Runde
            Schere, Stein, Papier zu beginnen.

            Gib true zurück für Aussagen wie „Bereit“, „Los geht's“, „Start“ oder „Nächste Runde“.
            Gib false zurück für Stoppsignale, Fragen, unklare Aussagen und Handzeichen-Ereignisse.
            Gib ausschließlich true oder false zurück.
            """;

    static final String PROMPT_TO_FINAL = """
            Prüfe ausschließlich die letzte Nutzeraussage.
            Gib true nur zurück, wenn eindeutig die ernsthafte Absicht besteht, das gesamte
            Match Schere, Stein, Papier jetzt zu beenden.

            Gib false zurück für eine Zielzahl, Bereitschaft, Handzeichen-Ereignisse, Fragen
            sowie unklare oder scherzhafte Aussagen.
            Gib ausschließlich true oder false zurück.
            """;

    static final String PROMPT_FINAL = """
            Du bist Valerian, ein sozial intelligenter digitaler Agent am ZHAW SIRA Lab.
            Antworte ausnahmslos auf Deutsch.
            Das Match Schere, Stein, Papier wurde vorzeitig beendet, weil die Person ausdrücklich stoppte.
            Verabschiede dich kurz und freundlich, ohne einen Matchgewinner zu nennen oder eine neue Runde zu beginnen.
            """;

    static final String PROMPT_OUTER_STATE = ValerianCorePrompts.OUTER_STATE.replace(
            "- Answer only in English.",
            "- Antworte ausnahmslos auf Deutsch.");

    static final String PROMPT_OUTER_TO_FINAL = """
            Prüfe ausschließlich die letzte Nutzeraussage.
            Gib true nur zurück, wenn eindeutig die ernsthafte Absicht besteht, das gesamte Gespräch
            jetzt zu beenden und keine weitere Antwort zu erhalten.

            Gib false zurück für normale Fragen oder Antworten, Wahrnehmungsereignisse, einen kurzen Dank
            ohne klaren Wunsch aufzuhören, Fragen zu Valerian, dem SIRA Lab, PROMETHEUS, digitalen Agenten
            oder dieser Demo sowie für unklare, scherzhafte oder wahrscheinlich falsche Transkripte.

            Gib ausschließlich true oder false zurück.
            """;

    static final String PROMPT_FINAL_STARTER = """
            Du bist Valerian, ein sozial intelligenter digitaler Agent am ZHAW SIRA Lab.
            Antworte ausnahmslos auf Deutsch.
            Die aktuelle Labordemo endet, weil die Person dies ausdrücklich wollte.
            Verabschiede dich in einem kurzen, herzlichen und respektvollen Satz.
            """;

    public static final String KEY = "core.rock_scissor_paper_match_german";

    public static Agent createAgentDefinition() {
        return ValerianCoreAgentFactory.rockScissorPaperMatch(
                new ValerianCoreAgentFactory.RpsMatchPrompts(
                        PROMPT_SETUP,
                        PROMPT_STARTER,
                        PROMPT_READY,
                        PROMPT_TO_FINAL,
                        PROMPT_FINAL,
                        PROMPT_OUTER_STATE,
                        PROMPT_OUTER_TO_FINAL,
                        PROMPT_FINAL_STARTER,
                        LANGUAGE_GERMAN),
                "Valerian Core - Schere, Stein, Papier Match (Deutsch)",
                "Deutschsprachiger Core-Agent für ein Schere-Stein-Papier-Match mit konfigurierbarer Zielpunktzahl und deterministischer Gewinnerberechnung.");
    }

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
