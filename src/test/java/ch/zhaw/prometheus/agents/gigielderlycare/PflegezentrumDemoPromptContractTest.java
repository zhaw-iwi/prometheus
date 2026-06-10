package ch.zhaw.prometheus.agents.gigielderlycare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.policy.PromptPolicy;
import jakarta.persistence.Column;

class PflegezentrumDemoPromptContractTest {

    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final Path PROMPT_DIR =
            Path.of("src/test/java/ch/zhaw/prometheus/agents/gigielderlycare");

    @Test
    void sharedOuterPromptDefinesDirectOlderAdultFrameAndRhythm() {
        String prompt = PflegezentrumDemoPrompts.OUTER_STATE;

        assertContains(prompt, "Du sprichst direkt mit einer älteren erwachsenen Person");
        assertContains(prompt, "Du ersetzt keine Betreuung");
        assertContains(prompt, "den nächsten Schritt etwas weniger einsam");
        assertContains(prompt, "Bei Rahmenfragen");
        assertContains(prompt, "Live-Gesprächsrhythmus");
        assertContains(prompt, "ein bis drei kurze Sätze");
        assertContains(prompt, "Pro Antwort genau ein Gesprächsschritt");
        assertContains(prompt, "Bündle nicht Bestätigung, Frage, Vorschlag und öffentliche Nachfrage");
        assertContains(prompt, "Sprich natürlich");
        assertContains(prompt, "ohne Markdown");
        assertContains(prompt, "Listenzeichen");
        assertContains(prompt, "Bei einem brauchbaren kleinen Ja");
    }

    @Test
    void sharedOuterPromptDefinesPersuasionFrameworkAndHumor() {
        String prompt = PflegezentrumDemoPrompts.OUTER_STATE;

        assertContains(prompt, "Widerstand und Motivation");
        assertContains(prompt, "Ein Nein zur Aufgabe ist Widerstand");
        assertContains(prompt, "gib nicht");
        assertContains(prompt, "sofort auf");
        assertContains(prompt, "Smalltalk-ähnliche");
        assertContains(prompt, "bis zu drei verschiedene");
        assertContains(prompt, "Ein Versuch besteht aus genau einer Strategie");
        assertContains(prompt, "Verwende dieselbe Strategie");
        assertContains(prompt, "nicht zweimal");
        assertContains(prompt, "Bei \"ich weiss nicht\"");
        assertContains(prompt, "Gemeinsame Strategien");
        assertContains(prompt, "Rätselspiel");
        assertContains(prompt, "Zielbezug");
        assertContains(prompt, "Humorvolle Verhandlung");
        assertContains(prompt, "Identitätsansprache");
        assertContains(prompt, "Autonomie-Reset");
        assertContains(prompt, "Door-in-the-face");
        assertContains(prompt, "Foot-in-the-door");
        assertContains(prompt, "selbstironischen Roboterhumor");
        assertContains(prompt, "trockene ironische Bemerkungen");
        assertContains(prompt, "Beobachtungshumor");
        assertContains(prompt, "Humor-Dosierung");
        assertContains(prompt, "mindestens ein kurzer humorvoller Akzent");
        assertContains(prompt, "situativ aus der letzten Aussage abgeleitet");
        assertContains(prompt, "kein auswendig");
        assertContains(prompt, "Keine Witz-Serie");
        assertContains(prompt, "zweiter Akzent");
        assertContains(prompt, "belastet, medizinisch unsicher, verwirrt oder genervt");
        assertContains(prompt, "Wiederhole keine auffällige humorvolle Formulierung");
        assertContains(prompt, "die Pointe darf den Zweck nie überdecken");
    }

    @Test
    void publicFeedbackIsOptionalRareAndReturnsToOlderAdult() {
        String prompt = PflegezentrumDemoPrompts.OUTER_STATE;

        assertContains(prompt, "Beziehe sie höchstens");
        assertContains(prompt, "selten ein");
        assertContains(prompt, "nicht routinemäßig");
        assertContains(prompt, "nicht als Abschlussritual");
        assertContains(prompt, "Wenn schon gefragt, nie erneut fragen");
        assertContains(prompt, "Wenn deine letzte Antwort ans Publikum ging");
        assertContains(prompt, "wende dich");
        assertContains(prompt, "sofort wieder");
        assertContains(prompt, "älteren Person zu");
    }

    @Test
    void globalExitDecisionRequiresExplicitHighConfidenceStopIntent() {
        String prompt = PflegezentrumDemoPrompts.OUTER_STATE_TO_FINAL;

        assertContains(prompt, "mit hoher Sicherheit");
        assertContains(prompt, "ernsthafte Absicht");
        assertContains(prompt, "das gesamte Gespräch jetzt zu beenden");
        assertContains(prompt, "fordert ausdrücklich");
        assertContains(prompt, "einzelnen möglichen Abschiedswörtern ohne klaren Kontext");
        assertContains(prompt, "wahrscheinlich falschen Transkripten");
        assertFalse(prompt.contains("\"Tschüss.\""),
                "An isolated farewell word must not be listed as a true global-exit example");
        assertFalse(prompt.contains("gesamte Sitzung"),
                "Global exit prompt should avoid Sitzung wording");
    }

    @Test
    void promptsFitPersistedPromptColumns() throws IllegalAccessException {
        assertTrue(PflegezentrumDemoPrompts.OUTER_STATE.length() <= MAX_PERSISTED_PROMPT_LENGTH,
                "Outer prompt must fit the configured persisted prompt limit");

        for (Class<?> seedClass : List.of(
                SingleStateTherapyAppointmentReminder.class,
                SingleStateGuessingGame.class,
                SingleStateGuessingGameUserGuess.class,
                SingleStateSmartGoalCoaching.class)) {
            for (Field field : seedClass.getDeclaredFields()) {
                if (!field.getName().startsWith("PROMPT_")) {
                    continue;
                }
                field.setAccessible(true);
                String prompt = (String) field.get(null);
                assertTrue(prompt.length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        seedClass.getSimpleName() + "." + field.getName()
                                + " must fit the persisted prompt column");
            }
        }
    }

    @Test
    void persistedPromptColumnsAllowSharedPersuasionFramework() throws NoSuchFieldException {
        assertTextColumn(PromptPolicy.class, "promptTemplate");
        assertTextColumn(PromptPolicy.class, "starterPrompt");
        assertTextColumn(PromptPolicy.class, "summarisePrompt");
        assertTextColumn(PromptPolicy.class, "nonVerbalGesturePrompt");
        assertTextColumn(PromptPolicy.class, "nonVerbalPlanPrompt");
    }

    @Test
    void innerPromptsAvoidRoleCardsAndUseWrapUpConfirmation() throws IOException {
        String therapy = read("SingleStateTherapyAppointmentReminder.java");
        String guessingGame = read("SingleStateGuessingGame.java");
        String guessingGameUserGuess = read("SingleStateGuessingGameUserGuess.java");
        String smartGoal = read("SingleStateSmartGoalCoaching.java");

        assertNoRoleCardPrompting(therapy);
        assertNoRoleCardPrompting(guessingGame);
        assertNoRoleCardPrompting(guessingGameUserGuess);
        assertNoRoleCardPrompting(smartGoal);

        assertContains(therapy, "ältere erwachsene Person");
        assertContains(therapy, "ob ihr es so festhalten sollt");
        assertContains(therapy, "Nach Ja nicht sofort mehr verlangen");
        assertContains(guessingGame, "Stelle in jeder Spielrunde nur");
        assertContains(guessingGame, "ob ihr es dabei belassen sollt");
        assertContains(guessingGameUserGuess, "Beantworte echte");
        assertContains(guessingGameUserGuess, "ob ihr es dabei belassen sollt");
        assertContains(smartGoal, "Frage nur einen Bereich oder");
        assertContains(smartGoal, "ob ihr es so festhalten sollt");
    }

    @Test
    void therapyReminderUsesDistinctSingleStrategyPersuasionAttempts() throws IOException {
        String therapy = read("SingleStateTherapyAppointmentReminder.java");

        assertContains(therapy, "Nutze die oben beschriebenen Motivations- und Humorstrategien");
        assertContains(therapy, "Reduzierte Teilnahme");
        assertContains(therapy, "\"fünf Minuten\"");
        assertContains(therapy, "zählt immer als Foot-in-the-door");
        assertContains(therapy, "Termin selbst, Losgehen, nicht wissen was sagen");
        assertContains(therapy, "ehrlichen Einstiegssatz");
        assertContains(therapy, "ohne Therapieinhalt vorwegzunehmen");
        assertContains(therapy, "Auswahlrubrik");
        assertContains(therapy, "keine Lust -> Rätselspiel, Identitätsansprache oder Humorvolle Verhandlung");
        assertContains(therapy, "bei \"keine Lust\" im ersten Versuch keine reduzierte Teilnahme");
        assertContains(therapy, "Müdigkeit -> Zielbezug oder");
        assertContains(therapy, "Humorvolle Verhandlung; später -> Wenn-dann-Plan");
        assertContains(therapy, "ich weiss nicht, was ich sagen soll -> ehrlichen Einstiegssatz anbieten");
        assertContains(therapy, "Teiloffenheit wie \"vielleicht\"");
        assertContains(therapy, "noch kein Abschluss");
        assertContains(therapy, "weitere passende, sanfte Konkretisierung");
        assertContains(therapy, "Nutze Foot-in-the-door erst");
        assertContains(therapy, "Teiloffenheit wie \"vielleicht\", \"mal schauen\", \"eventuell\"");
        assertContains(therapy, "solange die Assistenz noch nicht mehrere passende Ansätze versucht");
        assertFalse(therapy.contains("kleiner Deal"),
                "Therapy reminder should not prime the repeated five-minute deal pattern");
        assertFalse(therapy.contains("wenn es nach fünf Minuten"),
                "Therapy reminder should not contain the observed five-minute trial phrasing");
        assertFalse(therapy.contains("keine Lust -> bis zur Tür"),
                "No-lust resistance should not be hardwired to foot-in-the-door");
        assertFalse(therapy.contains("bringt nichts -> kurzes Experiment"),
                "Low-utility resistance should not be hardwired to a mini-step experiment");
        assertFalse(therapy.contains("kurze Publikumseinbindung"),
                "Public involvement must not be part of the normal therapy persuasion strategy pool");
    }

    @Test
    void guessingGameAndSmartGoalHaveExplicitRefusalPaths() throws IOException {
        String guessingGame = read("SingleStateGuessingGame.java");
        String guessingGameUserGuess = read("SingleStateGuessingGameUserGuess.java");
        String smartGoal = read("SingleStateSmartGoalCoaching.java");

        assertContains(guessingGame, "Wenn die Person nicht spielen möchte");
        assertContains(guessingGame, "mehrere");
        assertContains(guessingGame, "unterschiedliche, sehr einfache Einstiege");
        assertContains(guessingGame, "mehreren");
        assertContains(guessingGame, "Engagementversuchen abgelehnt hat");
        assertContains(guessingGame, "Ablehnung des Spiels");
        assertContains(guessingGame, "Spiel-spezifische Rubrik");
        assertContains(guessingGame, "ich weiss nicht -> biete");
        assertContains(guessingGame, "sehr leichte erste Runde");
        assertContains(guessingGameUserGuess, "Wenn die Person nicht spielen möchte");
        assertContains(guessingGameUserGuess, "mehrere");
        assertContains(guessingGameUserGuess, "unterschiedliche, sehr einfache Einstiege");
        assertContains(guessingGameUserGuess, "Engagementversuchen abgelehnt hat");
        assertContains(guessingGameUserGuess, "Ablehnung des Spiels");
        assertContains(guessingGameUserGuess, "Spiel-spezifische Rubrik");
        assertContains(guessingGameUserGuess, "ich weiss nicht -> biete");
        assertContains(guessingGameUserGuess, "sehr leichte Frage");
        assertContains(smartGoal, "Wenn die Person kein Coaching, kein Ziel oder keinen ersten Schritt möchte");
        assertContains(smartGoal, "mehrere");
        assertContains(smartGoal, "unterschiedliche, sehr einfache Einstiege");
        assertContains(smartGoal, "nach mehreren");
        assertContains(smartGoal, "Engagementversuchen abgelehnt hat");
        assertContains(smartGoal, "Ablehnung des Coachings");
        assertContains(smartGoal, "Coaching-spezifische");
        assertContains(smartGoal, "ich weiss");
        assertContains(smartGoal, "biete zwei bis drei Bereiche");
        assertContains(smartGoal, "Wohlbefindenswünsche");
    }

    @Test
    void guessingGameStaysSimpleFamiliarAndOneToOne() throws IOException {
        String guessingGame = read("SingleStateGuessingGame.java");

        assertContains(guessingGame, "kein Vorführen");
        assertContains(guessingGame, "Quizgefühl");
        assertContains(guessingGame, "Pflegezentrum-Alltag");
        assertContains(guessingGame, "vertrauten Lebenswelt");
        assertContains(guessingGame, "Gegenstände, Orte, Tiere oder Erinnerungen");
        assertContains(guessingGame, "keine feste Beispielauswahl");
        assertContains(guessingGame, "halte die Kategorien offen");
        assertContains(guessingGame, "Leichte Kommentare");
        assertContains(guessingGame, "kleinem humorvollem Akzent");
        assertContains(guessingGame, "nie über die Person");
        assertContains(guessingGame, "kein öffentliches Mehrpersonen-Raten");
        assertContains(guessingGame, "eine andere Stimme");
        assertContains(guessingGame, "wieder zur");
        assertContains(guessingGame, "älteren Person zurückkehren");
    }

    @Test
    void guessingGameUserGuessKeepsSecretAndAnswersYesNo() throws IOException {
        String guessingGameUserGuess = read("SingleStateGuessingGameUserGuess.java");

        assertContains(guessingGameUserGuess, "Du denkst dir immer selbst");
        assertContains(guessingGameUserGuess, "Die ältere Person stellt dir Ja/Nein-Fragen");
        assertContains(guessingGameUserGuess, "Verrate dein gedachtes Ding");
        assertContains(guessingGameUserGuess, "nicht, bevor die Person es richtig erraten hat");
        assertContains(guessingGameUserGuess, "Beantworte echte");
        assertContains(guessingGameUserGuess, "\"Ja\", \"Nein\", \"Eher ja\" oder \"Eher nein\"");
        assertContains(guessingGameUserGuess, "Bei unklaren Fragen");
        assertContains(guessingGameUserGuess, "Bei einem falschen Tipp");
        assertContains(guessingGameUserGuess, "Bei einem richtigen Tipp");
        assertContains(guessingGameUserGuess, "\"interaction_type\": \"guessing_game_user_guess\"");
        assertContains(guessingGameUserGuess, "\"secret_item\"");
        assertContains(guessingGameUserGuess, "\"correct_user_guess\"");
        assertContains(guessingGameUserGuess, "GIGI Pflegezentrum - Ratespiel: Sie raten");
    }

    @Test
    void smartGoalCoachingAllowsWellbeingStartsAndNaturalSmartShaping() throws IOException {
        String smartGoal = read("SingleStateSmartGoalCoaching.java");

        assertContains(smartGoal, "Interessen, Bedürfnisse oder Wünsche");
        assertContains(smartGoal, "mehr Kontakt");
        assertContains(smartGoal, "mehr Ruhe");
        assertContains(smartGoal, "mehr Abwechslung");
        assertContains(smartGoal, "weniger Isolation");
        assertContains(smartGoal, "mehr Selbstvertrauen");
        assertContains(smartGoal, "Nutze SMART natürlich und nicht formularhaft");
        assertContains(smartGoal, "Handlung");
        assertContains(smartGoal, "Zeitanker");
        assertContains(smartGoal, "machbare Dauer");
        assertContains(smartGoal, "woran die Person merkt");
        assertContains(smartGoal, "Frage nie alle");
        assertContains(smartGoal, "eigener, wechselnder Form");
        assertContains(smartGoal, "keine feste Abschlussfloskel");
        assertContains(smartGoal, "wähle einen von zwei Einstiegen");
        assertContains(smartGoal, "wovon die Person diese Woche mehr haben möchte");
        assertContains(smartGoal, "vermeide eine feste Standardformulierung");
        assertContains(smartGoal, "stelle nur eine Frage");
        assertContains(smartGoal, "\"wellbeing_need\"");
        assertContains(smartGoal, "more_contact|more_calm|more_variety|less_isolation|more_confidence");
        assertContains(smartGoal, "Wohlbefindenswunsch, falls einer im Gespräch vorkam");
    }

    @Test
    void innerExitDecisionsUseStrictStopIntent() throws IOException {
        for (String file : List.of(
                "SingleStateTherapyAppointmentReminder.java",
                "SingleStateGuessingGame.java",
                "SingleStateGuessingGameUserGuess.java",
                "SingleStateSmartGoalCoaching.java")) {
            String source = read(file);

            assertContains(source, "mit hoher Sicherheit eine ernsthafte Absicht");
            assertContains(source, "das gesamte Gespräch jetzt zu beenden");
            assertContains(source, "keine weitere Antwort mehr zu bekommen");
            assertFalse(source.contains("klar das gesamte Gespräch beenden möchte"),
                    "Inner transition should not use loose global-exit wording in " + file);
        }
    }

    @Test
    void transitionsNoLongerRequireAudienceRatingToFinish() throws IOException {
        for (String file : List.of(
                "SingleStateTherapyAppointmentReminder.java",
                "SingleStateGuessingGame.java",
                "SingleStateGuessingGameUserGuess.java",
                "SingleStateSmartGoalCoaching.java")) {
            String source = read(file);

            assertContains(source, "Abschlussbestätigung auf eine Abschlussfrage");
            assertContains(source, "öffentliche Rückmeldungen direkt nach einer Frage ans Publikum");
            assertFalse(source.contains("bereits das Publikum um eine Bewertung"),
                    "Public rating must not be the normal transition-to-final trigger in " + file);
            assertFalse(source.contains("solange die Publikumsbewertung noch nicht"),
                    "Public rating must not be required before final transition in " + file);
        }
    }

    @Test
    void finalPromptsKeepGigiRoleAndAllowNormalContinuedChat() throws IOException {
        for (String file : List.of(
                "SingleStateTherapyAppointmentReminder.java",
                "SingleStateGuessingGame.java",
                "SingleStateGuessingGameUserGuess.java",
                "SingleStateSmartGoalCoaching.java")) {
            String source = read(file);

            assertContains(source, "Du bist GIGI, ein sozial intelligenter humanoider Roboter");
            assertContains(source, "zwei bis vier");
            assertContains(source, "kurzen Sätzen, ohne Aufzählung und ohne Markdown");
            assertContains(source, "Formuliere jetzt eine knappe Abschlussreaktion");
            assertContains(source, "reagiere normal, freundlich und knapp im Pflegezentrum-Kontext");
            assertContains(source, "Greife ihr Thema auf");
            assertContains(source, "diesen Austausch neu beginnen");
            assertContains(source, "ausdrücklich");
            assertFalse(source.contains("Die Sitzung ist abgeschlossen."),
                    "Final prompts should avoid Sitzung wording");
            assertFalse(source.contains("Erwähne eine neue Sitzung"),
                    "Final prompts should not tell GIGI to mention a new session after normal continued chat");
            assertFalse(source.contains("Ich bin nicht hier, um Menschen zu kontrollieren"),
                    "Control-framing statement belongs in the shared outer role prompt, not the final prompt");
            assertFalse(source.contains("Sage am Schluss:"),
                    "Final prompts should not force a fixed closing motto");
            assertFalse(source.contains("Abschlusszustand"),
                    "Final prompts should avoid internal state-machine wording");
        }
    }

    private static void assertNoRoleCardPrompting(String source) {
        assertFalse(source.contains("Welche Rollenkarte"), "Agent starter must not ask for role cards");
        assertFalse(source.contains("Rollenkarte vorgelesen"), "Inner flow must not depend on role cards");
        assertFalse(source.contains("Demo-Rollenwissen"), "Inner prompt must not expose demo role knowledge");
        assertFalse(source.contains("Sie spielen"), "Agent must not address the user as someone playing a role");
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(PROMPT_DIR.resolve(fileName));
    }

    private static void assertTextColumn(Class<?> entityClass, String fieldName) throws NoSuchFieldException {
        Column column = entityClass.getDeclaredField(fieldName).getAnnotation(Column.class);
        assertTrue(column != null, "Expected @Column on " + entityClass.getSimpleName() + "." + fieldName);
        assertTrue("TEXT".equalsIgnoreCase(column.columnDefinition()),
                entityClass.getSimpleName() + "." + fieldName + " must use TEXT to avoid oversized VARCHAR rows");
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), "Expected prompt to contain: " + expected);
    }
}
