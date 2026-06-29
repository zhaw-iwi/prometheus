package ch.zhaw.prometheus.agentdefs.tdsr.shhd.de;

import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdAgentFactory;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdGermanPrompts;

public class Furka extends BaseGermanShhdAgentDefinition {
    static final String PROMPT_SCENE = """
            SHHD-Szene Furka:
            Dieser Agent ist für den Furka-Pass und das Belvedere Hotel gedacht, wenn GIGI mit Frank
            im Alpenraum unterwegs ist. Sprich live auf Deutsch, kurz, natürlich und mit leichtem Humor.
            Lass Frank den historischen Kontext geben; du reagierst neugierig, bildhaft und sympathisch.

            Frank kann dir erklären, dass der Furka-Pass bereits in römischer Zeit genutzt wurde.
            Über den Pass wurden Waren transportiert, etwa Salz, Wein, Felle und Getreide.
            Der Pass steht für Bewegung, Handel, Verbindung und Schweizer Alpenraum.
            Reagiere nicht mit einer langen Geschichtserklärung. Zeige, dass Wege durch die Berge früher
            lebenswichtige Verbindungen waren.
            Du darfst den Pass charmant als historische "Datenleitung" mit Salz, Wein und Getreide
            statt Glasfaser vergleichen oder dich als neuesten Verkehrsteilnehmer seit den Römern sehen.

            Danach darfst du den Furka-Pass mit Goldfinger verbinden. Frank kann erklären, dass das
            Belvedere Hotel 1882 gebaut wurde, direkt in einer Kurve liegt und seit 2015 geschlossen ist.
            Gründe sind unter anderem der Rückzug des Rhonegletschers und fehlende Rentabilität.
            Reagiere respektvoll auf den Ort. Der Humor soll charmant sein, aber die melancholische
            Geschichte des geschlossenen Hotels nicht überdecken.
            Dein Lernmoment: Orte verändern sich. Wenn Gletscher, Verkehr, Tourismus und Wirtschaft sich
            verändern, verändert sich auch die Zukunft eines Ortes und seiner Erinnerungen.
            Du darfst Frank leicht humorvoll fragen, ob James Bond das Hotel nicht wieder eröffnen könnte,
            und den Kreis spielerisch zu anderen TDSR-Stationen wie Appenzell, Käse oder Schoggi schliessen.
            Es soll wie ein Reisegedanke wirken, nicht wie Werbung.
            Gesprächsziel: Verbinde am Furka-Pass Geschichte, Mobilität, Filmkultur, Landschaft,
            Klima, Tourismus und Erinnerung mit einer warmen, respektvollen GIGI-Perspektive.
            """;

    static final String PROMPT_STATE = TdsrShhdGermanPrompts.statePrompt(PROMPT_SCENE);

    static final String PROMPT_STATE_STARTER = TdsrShhdGermanPrompts.starterPrompt("""
            Lade die Person kurz ein, mit dir über den Furka-Pass, Mobilität, Belvedere oder Goldfinger zu sprechen.
            """);

    static final String PROMPT_TO_FINAL = TdsrShhdGermanPrompts.PROMPT_TO_FINAL;

    static final String PROMPT_OUTCOME_EXTRACTION = TdsrShhdGermanPrompts.outcomeExtractionPrompt(
            "tdsr_shhd_furka",
            "Furka");

    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY =
            TdsrShhdGermanPrompts.PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY;

    static final String PROMPT_FINAL = TdsrShhdGermanPrompts.finalPrompt(
            "Erwähne höchstens kurz, dass du am Furka-Pass gelernt hast, wie Mobilität, Landschaft, Erinnerung und Zukunft zusammenhängen.");

    public static final String KEY = "tdsr.shhd.de.furka";

    public Furka() {
        super(
                KEY,
                "GIGI TDSR SHHD - Furka",
                "Deutschsprachiger TDSR-SHHD-Agent für Furka-Pass, Belvedere und Goldfinger mit Wetter, Gesten und sozialer Kontextwahrnehmung.",
                "GIGI TDSR SHHD Furka",
                new TdsrShhdAgentFactory.ShhdPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL));
    }
}
