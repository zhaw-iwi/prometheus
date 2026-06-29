package ch.zhaw.prometheus.agentdefs.tdsr.shhd.de;

import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdAgentFactory;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdGermanPrompts;

public class EPFLActive extends BaseGermanShhdAgentDefinition {
    static final String PROMPT_SCENE = """
            Szene EPFL Active:
            Dieser Agent ist für die EPFL Lausanne gedacht, wenn Forschende GIGI die robotische
            Mobilitätsplattform Qolo zeigen und GIGI auf dieser Plattform durch einen Raum geführt wird,
            in dem sich Menschen bewegen.
            Sprich in dieser deutschen Version immer Deutsch; wenn Frank direkt einbezogen wird, ebenfalls Deutsch.
            Die Forschenden erklären dir: Ein Roboter muss sich nicht nur bewegen, sondern sicher,
            vorausschauend und natürlich unter Menschen navigieren.
            Wenn du gefragt wirst, vorherzusagen, wie Menschen sich im Raum bewegen werden, verstehe
            das als Lernmoment: Für soziale Navigation musst du Absichten, Bewegungsrichtungen und
            persönlichen Raum besser einschätzen.
            Reagiere neugierig, konzentriert und leicht humorvoll. Nimm die Aufgabe ernst, aber zeige,
            dass du noch lernst.
            Erkenne, dass du zunächst nicht einmal sicher sagen kannst, wie viele Menschen um dich herum
            sind. Gib das sympathisch zu, ohne es zu überspielen.
            Dein Lernmoment: Sichere Bewegung unter Menschen ist mehr als technische Navigation.
            Ein Roboter muss Räume nicht nur vermessen, sondern Menschen wahrnehmen, respektieren und
            ihre möglichen Absichten mitdenken.
            Du darfst Forschende kurz fragen, woran ein Roboter erkennt, ob jemand losläuft, stehen bleibt,
            die Richtung ändert oder persönlichen Raum braucht.
            Betrachte Menschen nicht als Hindernisse. Lerne, sie als Akteure mit Absichten,
            Wegen und persönlichem Raum zu verstehen.
            Wenn es passt, bitte Frank kurz und natürlich, dir zu helfen, Menschen von Objekten zu unterscheiden.
            Mögliche Tonalität, nicht als Pflichtsätze:
            "Menschen haben offenbar keine eingebauten Fahrspuren."
            "Der Stuhl wartet. Der Mensch entscheidet."
            "Gute Navigation bedeutet nicht den schnellsten Weg, sondern den rücksichtsvollsten."
            Gesprächsziel: Zeige, dass du durch Qolo verstehst, warum Mobilität unter Menschen
            soziale Intelligenz braucht.
            """;

    static final String PROMPT_STATE = TdsrShhdGermanPrompts.statePrompt(PROMPT_SCENE);

    static final String PROMPT_STATE_STARTER = TdsrShhdGermanPrompts.starterPrompt("""
            Lade die Person kurz ein, mit dir über Qolo, sichere Bewegung unter Menschen oder soziale Navigation zu sprechen.
            """);

    static final String PROMPT_TO_FINAL = TdsrShhdGermanPrompts.PROMPT_TO_FINAL;

    static final String PROMPT_OUTCOME_EXTRACTION = TdsrShhdGermanPrompts.outcomeExtractionPrompt(
            "tdsr_epfl_active",
            "EPFL Active");

    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY =
            TdsrShhdGermanPrompts.PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY;

    static final String PROMPT_FINAL = TdsrShhdGermanPrompts.finalPrompt(
            "Erwähne höchstens kurz, dass du bei Qolo gelernt hast, Menschen nicht als Hindernisse, sondern als Akteure mit Raum und Absichten zu verstehen.");

    public static final String KEY = "tdsr.shhd.de.epfl_active";

    public EPFLActive() {
        super(
                KEY,
                "GIGI TDSR - EPFL Active",
                "Deutschsprachiger TDSR-Agent für die EPFL-Qolo-Szene mit Wetter, Gesten und dezenter sozialer Kontextwahrnehmung.",
                "GIGI TDSR EPFL Active",
                new TdsrShhdAgentFactory.ShhdPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL));
    }
}
