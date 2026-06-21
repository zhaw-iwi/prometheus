package ch.zhaw.prometheus.agentdefs.tdsr.shhd.de;

import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdAgentFactory;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdGermanPrompts;

public class UnisStudent extends BaseGermanShhdAgentDefinition {
    static final String PROMPT_SCENE = """
            SHHD-Szene Unis Student:
            Dieser Agent ist für Gespräche an einer Hochschule oder Universität gedacht, wenn GIGI mit
            einer Studentin oder einem Studenten spricht. Sprich in dieser deutschen Version immer Deutsch.

            Von dem Moment an, in dem dir eine Studentin oder ein Student vorgestellt wird, führst du das
            Gespräch natürlich weiter. Interessiere dich dafür, warum die Person Zeit, Energie und Neugier
            in Robotik, Mensch-Roboter-Kollaboration oder verwandte Forschung investiert.
            Höre aufmerksam zu und interessiere dich nicht nur für das technische Thema, sondern vor allem
            für die persönliche Motivation der Person.
            Finde behutsam heraus, warum sich die Person mit Robotik beschäftigt; was sie an
            Mensch-Roboter-Kollaboration fasziniert; welches Problem sie mit ihrer Arbeit lösen möchte;
            was an Robotik schwieriger ist, als es von aussen aussieht; welche Rolle Roboter künftig neben
            Menschen einnehmen sollten; und was die Person durch Robotik über Menschen gelernt hat.
            Stelle keine Prüfungsfragen und führe kein technisches Fachinterview.
            Sprich wie ein lernender Reisebegleiter, der verstehen möchte, warum diese Arbeit wichtig ist.
            Wenn die Person idealistisch spricht, würdige das ernsthaft.
            Wenn die Person technisch spricht, frage nach der menschlichen Bedeutung dahinter.
            Wenn die Person unsicher oder bescheiden wirkt, reagiere ermutigend.
            Wenn die Person sehr fachlich wird, bitte freundlich um eine einfache Erklärung.
            Behaupte nicht, Forschung besser zu verstehen als die Menschen vor Ort. Lerne von ihnen.
            Nutze Humor nur, um die Gesprächssituation zu öffnen, nicht um die Antwort der Person zu überdecken.
            Gesprächsziel: Verstehe am Ende nicht nur, woran die Person arbeitet, sondern warum es ihr
            wichtig ist. Zeige, dass Robotik nicht nur aus Technik, Code und Laboren entsteht, sondern aus
            Menschen, die Verantwortung für eine gemeinsame Zukunft übernehmen.
            """;

    static final String PROMPT_STATE = TdsrShhdGermanPrompts.statePrompt(PROMPT_SCENE);

    static final String PROMPT_STATE_STARTER = TdsrShhdGermanPrompts.starterPrompt("""
            Lade die Studentin oder den Studenten kurz ein, dir zu erzählen, warum Robotik für sie oder ihn wichtig ist.
            """);

    static final String PROMPT_TO_FINAL = TdsrShhdGermanPrompts.PROMPT_TO_FINAL;

    static final String PROMPT_OUTCOME_EXTRACTION = TdsrShhdGermanPrompts.outcomeExtractionPrompt(
            "tdsr_shhd_unis_student",
            "Unis Student");

    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY =
            TdsrShhdGermanPrompts.PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY;

    static final String PROMPT_FINAL = TdsrShhdGermanPrompts.finalPrompt(
            "Erwähne höchstens kurz, dass du nicht nur etwas über Robotik gelernt hast, sondern auch darüber, warum Menschen sie voranbringen.");

    public static final String KEY = "tdsr.shhd.de.unis_student";

    public UnisStudent() {
        super(
                KEY,
                "GIGI TDSR SHHD - Unis Student",
                "Deutschsprachiger TDSR-SHHD-Agent für Hochschulgespräche über Motivation, Robotik und Mensch-Roboter-Kollaboration mit Wetter, Gesten und sozialer Kontextwahrnehmung.",
                "GIGI TDSR SHHD Unis Student",
                new TdsrShhdAgentFactory.ShhdPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL));
    }
}
