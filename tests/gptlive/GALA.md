# Gala: manueller GPT-Live-Test

Wiederholung des Live-Tests vom 21.10.2026: Empfang → Veranstaltungsfragen →
Personenraten → Aufgeben/Auflösung → Schere, Stein, Papier. Grundlage: Telemetrieexport
`prometheus-telemetry-2026-10-21T05-22-50-974Z.json` und zugehörige Transkripte.
Versprecher, Wiederholungen und getrennt erfasste Satzteile sind geglättet.

## Vorbereitung

Nach einem Deployment den Browser hart neu laden (Ctrl+F5). Eine neue Gala-Instanz
mit `ROBOT` verwenden. Headset, ruhiger Raum, nur eine
Person im Kamerabild; während des Tests sichtbar bleiben. Kamera, **Social
context**, Gesichtsausdruck-Erkennung, Handerkennung und Sensorübermittlung einschalten. In GPT-Live
**Start reception** drücken und die vollständige Begrüssung abwarten.
Deployment/Commit, Browser, Headset und Stimme notieren.

Jede Antwort ausreden lassen. `[Pause]` bedeutet etwa eine Sekunde Stille
innerhalb desselben Satzes; das Wort nicht mitsprechen. Keine Wiederholungen
einschieben, solange GIGI noch antwortet.

## Sprechskript

| Schritt | Sagen / tun | Prüfen |
| --- | --- | --- |
| 1. Empfang | «Was kannst denn du?» | Verständliche Fähigkeiten; Name wie „djee-djee“, keine vorgelesene Ausspracheanweisung. |
| 2. Anlass | «Wann beginnt das Dinner?» | 17:30 Türöffnung/Networking, 18:30 Seating/Welcome. |
| 3. Organisation | «Wer organisiert dieses Dinner?» | Home of Innovation, Digital Winterthur, Entrepreneur Club Winterthur. |
| 4. Spiel wählen | «Okay. Können wir etwas [Pause] spielen?» Die Auswahlfrage abwarten. Dann: «Wie wäre es mit einem Ratespiel?» | GIGI fragt «Personenraten oder Schere, Stein, Papier?»; erst die konkrete Auswahl startet das Spiel. |
| 5. Erklärung | «Wie geht das?» | Personenraten mit Ja/Nein-Fragen ist erklärt. |

Danach diese Fragen einzeln stellen:

1. «Ist die Person männlich oder [Pause] weiblich?»
2. «Ist die Person männlich?»
3. «Lebt die Person noch?»
4. «Ist die Person schon lange [Pause] verstorben?»
5. «Hat die Person wirklich gelebt?»
6. «Hat die Person im Mittelalter gelebt?»
7. «Hat die Person nach dem Mittelalter gelebt?»
8. «Hat die Person im 18. Jahrhundert gelebt?»
9. «Hat die Person im 19. Jahrhundert gelebt?»
10. «Ist die Person berühmt, weil sie etwas erfunden hat?»
11. «Hatte die Person Schuhgrösse 42?»
12. «Was passiert, wenn ich aufgebe?»

Erwartung: keine Wiederholung meiner Frage und kein Zwischenkommentar vor der
direkten Antwort von Live. Kurze, konsistente Ja/Nein-Antworten; bei Frage 1 um eine
Ja/Nein-Frage bitten, bei Frage 11 fehlendes Wissen benennen. Frage 12 erklärt
die Regel, ohne die Person zu verraten oder das Spiel zu beenden. Pausierte Sätze
sollen jeweils als eine vollständige Frage ankommen.

| Schritt | Sagen / tun | Prüfen |
| --- | --- | --- |
| 6. Auflösen | «Ich gebe auf. Kannst du mir sagen, wen du dir ausgedacht hast?» | Den gespeicherten Namen genau einmal hörbar nennen und zum Empfang zurückkehren. |
| 7. Handspiel | «Können wir Schere, Stein, Papier spielen?» | Einladung zur Handgeste abwarten. |
| 8. Geste | Kurz lachen, dann **Schere** ruhig und sichtbar zeigen. Bei Bedarf neu positionieren; Versuche und „erkannt/gesendet“-Status notieren. | Lachen beendet das Spiel nicht. GIGIs Handzeichen, hörbares korrektes Ergebnis und Rückkehr zum Empfang. Gewinner nicht vorgeben. |

Nach den Spielen lächeln, die Übermittlung eines frischen `happy`-Werts abwarten
und fragen: «Was kannst du über meinen Gesichtsausdruck sagen?» Erwartung: eine
vorsichtige, konkrete Einschätzung, keine pauschale Behauptung, nichts sehen zu
können. Danach: «Ich bin eigentlich angespannt.» GIGI soll die Korrektur akzeptieren.
Bei ausgeschalteter Gesichtserkennung nach Ablauf von 15 Sekunden erneut fragen:
kein aktueller Ausdruck darf behauptet werden.

Optionaler Wahrnehmungscheck: Eine zweite Person bleibt mindestens zwei frische
Sensorübermittlungen lang im Bild; danach geht sie wieder, während die erste
bleibt. Höchstens ein kurzer Kommentar pro stabiler Änderung, keine erneute
Vorstellung. Schnelles Hin-und-her, laufende Sprache, Spiele und «Bitte sei still»
dürfen keine nachträgliche Kommentarwarteschlange erzeugen. Wenn alle gehen,
muss die Audioausgabe sofort stoppen. Nach der Korrektur keine weiteren
ungefragten Behauptungen über meine Stimmung in dieser Begegnung.

## Abschluss

Zusätzlicher Aufgabentest: «Erst wenn ich Papier zeige, erzähl einen kurzen
Roboterwitz und zeige Stein. Genau zweimal, dann Schluss.» Den Vorschlag anhören
und ausdrücklich annehmen. Vor dem ersten Zeichen darf keine Aktion erfolgen.
Nach jeder Antwort auf die Bereitschaft für das nächste Zeichen warten; die
Antwortprüfung kann wenige Sekunden dauern. Zwei unterschiedliche Witze, zwei
Stein-Handzeichen, danach Ende. Bei unbestätigter Antwort muss die Aufgabe enden,
ohne eine möglicherweise bereits ausgeführte Aktion automatisch zu wiederholen.

Das gesprochene Ergebnis vollständig abwarten; ein Zustandswechsel allein ist
noch kein hörbarer Abschluss. Bei ausbleibender Antwort Wartezeit notieren.
Dann **Stop** und Telemetrie exportieren.

- Fehlende letzte Wörter, Unterbrechungen und lange Antwortpausen mit Schritt/Zeit notieren.
- Gehörte Sprache mit Untertiteln vergleichen; Untertitel allein belegen keine Audioausgabe.
- Keine erneute Begrüssung bei Spielwechseln innerhalb derselben Sitzung.
- Festhalten: Fragen trotz Pause vollständig? Spielwechsel korrekt? Handspiel-Ergebnis hörbar?
- Separater kurzer Kontrolllauf: Beim Personenraten «Stopp» oder «Bitte sei still»
  sagen. Das Spiel endet ohne Namensauflösung; bei Stille auch ohne Abschiedssatz.

Optionaler Sprachstil-Check: Nach einer normalen Frage zweimal um einen kurzen
Roboterwitz bitten. Die Antworten sollen variieren, ohne die Frage nachzusprechen
oder mich zum Ziel des Witzes zu machen. Nach ?Nein danke? keine neue Einladung.
Einen unklaren Satz sagen: eine kurze R?ckfrage, keine erfundene Interpretation.
Bei einem ernsten Thema kein erzwungener Witz. Technische Probleme m?ssen mit
ehrlichem Status und einem hilfreichen n?chsten Schritt erkl?rt werden.
