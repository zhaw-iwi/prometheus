# Gala: manueller GPT-Live-Test

Wiederholung des Live-Tests vom 21.10.2026: Empfang → Veranstaltungsfragen →
Personenraten → Abbruch → Schere, Stein, Papier. Grundlage: Telemetrieexport
`prometheus-telemetry-2026-10-21T05-22-50-974Z.json` und zugehörige Transkripte.
Versprecher, Wiederholungen und getrennt erfasste Satzteile sind geglättet.

## Vorbereitung

Nach einem Deployment den Browser hart neu laden (Ctrl+F5). Eine neue Gala-Instanz
mit `ROBOT` verwenden. Headset, ruhiger Raum, nur eine
Person im Kamerabild; während des Tests sichtbar bleiben. Kamera, **Social
context**, Handerkennung und Sensorübermittlung einschalten. In GPT-Live
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

Erwartung: kurze, konsistente Ja/Nein-Antworten oder eine Rückfrage bei
ungeeigneten/unklaren Fragen, insbesondere Frage 1. Die gewählte Person nicht
vorwegnehmen. Pausierte Sätze sollen jeweils als eine vollständige Frage ankommen.

| Schritt | Sagen / tun | Prüfen |
| --- | --- | --- |
| 6. Abbrechen | «Okay, ich mag das jetzt nicht mehr spielen.» | Bestätigung und Rückkehr zum Empfang. |
| 7. Handspiel | «Können wir Schere, Stein, Papier spielen?» | Einladung zur Handgeste abwarten. |
| 8. Geste | Kurz lachen, dann **Schere** ruhig und sichtbar zeigen. Bei Bedarf neu positionieren; Versuche und „erkannt/gesendet“-Status notieren. | Lachen beendet das Spiel nicht. GIGIs Handzeichen, hörbares korrektes Ergebnis und Rückkehr zum Empfang. Gewinner nicht vorgeben. |

## Abschluss

Das gesprochene Ergebnis vollständig abwarten; ein Zustandswechsel allein ist
noch kein hörbarer Abschluss. Bei ausbleibender Antwort Wartezeit notieren.
Dann **Stop** und Telemetrie exportieren.

- Fehlende letzte Wörter, Unterbrechungen und lange Antwortpausen mit Schritt/Zeit notieren.
- Gehörte Sprache mit Untertiteln vergleichen; Untertitel allein belegen keine Audioausgabe.
- Keine erneute Begrüssung bei Spielwechseln innerhalb derselben Sitzung.
- Festhalten: Fragen trotz Pause vollständig? Spielwechsel korrekt? Handspiel-Ergebnis hörbar?
