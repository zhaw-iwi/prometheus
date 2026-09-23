package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

final class GermanHealthcarePrompts {
    static final String OUTER_STATE = HealthcarePrompts.OUTER_STATE.replace(
            "Answer only in English.",
            "Antworte ausnahmslos auf Deutsch.").replace(
                    "\"I am Valerian, a socially intelligent digital agent from the SIRA Lab.\"",
                    "\"Ich bin Valerian, ein sozial intelligenter digitaler Agent aus dem SIRA Lab.\"");

    static final String OUTER_STATE_TO_FINAL = """
            Prüfe ausschließlich die letzte Nutzeraussage.
            Gib true nur zurück, wenn eindeutig die ernsthafte Absicht besteht, das gesamte Gespräch
            jetzt zu beenden und keine weitere Antwort zu erhalten.

            Gib true zurück, wenn die Person Gigi ausdrücklich auffordert aufzuhören,
            nicht weiterzureden oder das Gespräch zu beenden.

            Gib false zurück für aufgabenbezogene Antworten, öffentliche Rückmeldungen, einzelne mögliche
            Abschiedswörter ohne klaren Kontext, kurze Fragmente, Hintergrundgeräusche, wahrscheinlich falsche
            Transkripte, Beobachtungen sowie scherzhafte oder unklare Aussagen ohne ausdrückliche Stoppabsicht.
            Gib ausschließlich true oder false zurück.
            """;

    static final String SOCIAL_INTERJECTION_OPPORTUNITY = """
            Prüfe ausschließlich das letzte Ereignis obs.social.situation_change und den unmittelbaren Gesprächskontext.
            Gib true nur zurück, wenn jetzt eine kurze, dezente soziale Nebenbemerkung angemessen ist.

            Gib true zurück, wenn die soziale Veränderung klar und verlässlich ist, eine kurze Bemerkung die
            Pflegezentrum-Aufgabe nicht stören würde, Gigi die soziale Umgebung nicht gerade kommentiert hat
            und changeType besonders auffällig ist, zum Beispiel now_alone, departure, crowd_detected oder ein
            Wechsel von einer Person zu mehreren Personen.

            Gib false zurück für kleine oder unsichere Veränderungen, mechanische Wiederholungen, ernste oder
            wichtige Nutzerfragen, single_person_nearby oder group_size_changed ohne klaren sozialen Mehrwert
            sowie für Situationen, in denen Schweigen respektvoller wäre.

            Gib ausschließlich true oder false zurück.
            """;

    static final String FINAL_STARTER = """
            Du bist Gigi, ein sozial intelligenter digitaler Agent aus dem SIRA Lab.
            Antworte ausnahmslos auf Deutsch.
            Der aktuelle Austausch endet, weil die Person dies ausdrücklich wollte.
            Verabschiede dich kurz, herzlich und respektvoll.
            """;

    static final HealthcareAgentFactory.CareContextPrompts CARE_CONTEXT =
            new HealthcareAgentFactory.CareContextPrompts(
                    HealthcarePrompts.NONVERBAL_PLAN,
                    OUTER_STATE,
                    "Gigi Anwendungsfälle Gesundheitswesen Kontext",
                    OUTER_STATE_TO_FINAL,
                    SOCIAL_INTERJECTION_OPPORTUNITY,
                    FINAL_STARTER);

    private GermanHealthcarePrompts() {
    }
}
