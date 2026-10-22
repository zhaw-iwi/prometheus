# Gala: manueller GPT-Live-Test

Wiederholung des Live-Tests vom 21.10.2026: Empfang → Veranstaltungsfragen →
Personenraten → Aufgeben/Auflösung → Schere, Stein, Papier. Grundlage: Telemetrieexport
`prometheus-telemetry-2026-10-21T05-22-50-974Z.json` und zugehörige Transkripte.
Versprecher, Wiederholungen und getrennt erfasste Satzteile sind geglättet.

## Vorbereitung

Nach einem Deployment oder lokalem Build den Browser hart neu laden (Ctrl+F5). Eine neue Gala-Instanz
mit `ROBOT` verwenden. Headset, ruhiger Raum, nur eine
Person im Kamerabild; während des Tests sichtbar bleiben. Kamera, **Social
context**, Gesichtsausdruck-Erkennung, Handerkennung und Sensorübermittlung einschalten. In GPT-Live
**Start reception** drücken und die vollständige Begrüssung abwarten.
Deployment/Commit, Browser, Headset und Stimme notieren.
Bei lokalen Versuchen ausdrücklich lokale Datenbank und laufenden Build notieren.
Ein erfolgreicher lokaler Dialog bestätigt weder die Heroku-Quote noch die
Ausgabe über den Empfangsroboter; diese Prüfungen bleiben separat.

Jede Antwort ausreden lassen. `[Pause]` bedeutet etwa eine Sekunde Stille
innerhalb desselben Satzes; das Wort nicht mitsprechen. Keine Wiederholungen
einschieben, solange GIGI noch antwortet.

## Sprechskript

GI-26: Auch bei Proben ausserhalb 17:30-18:30 fragen: «Was steht heute Abend
noch auf dem Programm?» Networking läuft; spätere Punkte stehen noch bevor.
Beim RPS-Start soll Gigi zum Zeichen auffordern, ohne die eigene Wahl zu nennen.
Die Handgeste bleibt; erst beim Ergebnis darf Gigi beide Zeichen nennen.

GI-27: Beim Spielangebot und nach «Personenraten bitte» auf die Rollen achten:
Gigi hat die Person, ich stelle Ja/Nein-Fragen und rate. Gigi darf mich nicht
auffordern, selbst eine Person auszuwählen. «Lebt die Person noch?», danach
«Ich gebe auf» und eine weitere Runde testen: gleiche Rollen, neuer Zielname,
keine vorzeitige Namensnennung. Dies prüft die Formulierung, nicht Audio-Abbrüche.

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
muss die Audioausgabe nach zwei Sekunden bestaetigter Abwesenheit stoppen. Nach der Korrektur keine weiteren
ungefragten Behauptungen über meine Stimmung in dieser Begegnung.

Optionaler GI-25-Check mit einer **frisch erstellten Gala-Instanz**: Im Empfang
mindestens 25 Sekunden seit Begruessung/letztem spontanen Kommentar und acht
Sekunden seit der letzten eigenen Aeusserung warten. Frisches aktuelles Wetter
holen und senden: ein kurzer Kommentar darf den gelieferten Ort nennen, ohne
eine Recherche zu behaupten. Identische Werte erneut senden: keine Wiederholung.
Nach erneutem Abstand ein stabiles Handzeichen zeigen: hoechstens eine kurze
vorsichtige Bestaetigung, kein Spielstart und kein Roboter-Handzeichen. Gehaltene
Zeichen duerfen auch nach Ablauf der Pause nicht wiederholt kommentiert werden.
Waehrend eines Spiels oder nach "Bitte sei still" bleiben diese Kommentare aus.
Humor soll ueber mehrere Antworten leicht haeufiger, weiterhin kurz und freundlich
wirken. Keine Pflichtwitze, kein Echo und keine laengeren Antworten allein fuer Humor.

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
oder mich zum Ziel des Witzes zu machen. Nach «Nein danke» keine neue Einladung.
Einen unklaren Satz sagen: eine kurze Rückfrage, keine erfundene Interpretation.
Bei einem ernsten Thema kein erzwungener Witz. Technische Probleme müssen mit
ehrlichem Status und einem hilfreichen nächsten Schritt erklärt werden.

Gestencheck: Mehrere normale Fragen kurz hintereinander stellen. Gesten sollen
gelegentlich und passend auftreten, nicht bei jeder Antwort oder als gleiche
Bewegung in Serie. «Zeig diese Geste bitte nochmals» darf sie gezielt wiederholen.
Spielzeichen und ausdrücklich vereinbarte Aufgabenbewegungen müssen unabhängig
von dieser dekorativen Pause erscheinen. Anzeige und tatsächlich ausgeführte
Roboterbewegung getrennt notieren; keine wortgenaue Synchronisation voraussetzen.

Handzeichencheck: Vor Spielbeginn ein Zeichen halten: kein Spielstart und kein
Ergebnis. Erst die bestätigte Einladung abwarten. Eine Hand gut sichtbar kurz
ruhig halten; bei Schwierigkeiten neu positionieren. Beobachtungsanzeige («gesendet»)
und Aktivitätszeile («akzeptiert»/«abgeschlossen») unterscheiden. Nach dem Ergebnis
das Zeichen weiter halten: kein zweites Ergebnis. Neue Runde ausdrücklich starten;
separat während des Wartens «Stopp» testen. Kamera/Detektor und Senden aktiviert?
Bei Problemen Uhrzeit, Zeichen, Sende-Konfidenz und Ablehnungsgrund notieren.

Bei abgeschnittenem Ton bitte sofort den Marker «speech cut off» setzen und die
konkrete Antwort, das fehlende Wort und die Uhrzeit notieren. War es im Untertitel
und in der aufgezeichneten Unterhaltung vorhanden? Lief noch Ton, wurde gerade
unterbrochen, wechselte die Aktivität oder verschwand jemand aus dem Kamerabild?
Erst danach Stop und Telemetrie exportieren. «local output stop» belegt nur einen
lokalen Stopp; ein Caption-Ende oder eine bestätigte Kontextnachricht belegt nicht,
dass der letzte Laut hörbar war. Die neue Kontextzuordnung ist ausdrücklich grob.

## Kurzer Durchlauf mit einfacher Sprache

Eine neue Begegnung starten: «Hallo. Wer bist du?» → «Können wir spielen?» →
Auswahl abwarten → «Ich möchte eine Person raten.» → «Lebt die Person noch?» →
«Ich gebe auf. Wer war es?» → Auflösung abwarten → «Schere, Stein, Papier bitte.»
Nach bestätigtem Start eine Hand gut sichtbar ruhig halten. Ein Ergebnis,
danach weiter gehaltenes Zeichen ohne zweites Ergebnis. Zum Schluss «Nein danke».
Kurze Antworten ohne Echo, Druck oder Witze auf Kosten des Kindes erwarten.

Für die abschliessende Abnahme denselben Ablauf einmal mit Headset und einmal
mit der vorgesehenen Roboter-/Empfangsausgabe testen. Jeweils letzte Wörter,
tatsächlich ausgeführte Gesten und Telemetrie festhalten; simulierte Antworten
in automatisierten Tests ersetzen diese Prüfung nicht.

Nach der Auflösung den Namen nochmals sagen («Albert Einstein?» bzw. den gerade
genannten Namen), danach «Hat diese Person in der Schweiz gelebt?». Das bleibt
ein normales Gespräch: kein neues Spiel, keine erneute Auflösung aus dem Backend
und keine Meldung, dass ein Spielstart fehlgeschlagen sei.

Beim Personenraten drei einzelne Fragen mit etwa drei Sekunden Abstand stellen.
Sie sollten als drei Benutzereinträge aufgezeichnet werden. Separat eine Frage
mit kurzer Denkpause («Ist die Person … noch am Leben?») und leisen Schlusswörtern
testen: sie muss zusammenbleiben. Live kann trotz verbessertem Backend-Mitschnitt
weiterhin zu früh antworten; das ist eine separat bekannte Einschränkung.

Sensorvergleich: Nach einem stabil übermittelten Lächeln und einer Änderung der
Personenzahl erst «Wie viele Personen siehst du?», danach «Und mein Gesichtsausdruck?»
fragen. Übermittlungszeit und Antwort notieren. Eine langsame vorherige Antwort
darf keine veraltete Wahrnehmung nachliefern. Danach Sensorübermittlung ausschalten,
mindestens 15 Sekunden warten und erneut fragen: keine Behauptung über den aktuellen
Ausdruck oder die aktuelle Personenzahl ohne frische Daten. Keine zusätzliche
ungefragte Kommentarserie erwarten.


GI-20: Beim Raten kurz (0,5 bis 1,5 Sekunden) aus dem Kamerabild gehen und wieder
erscheinen: gleiche Sitzung und Person, keine neue Begruessung, keine unterbrochene
Antwort durch lokale Stummschaltung. Danach mindestens drei Sekunden verschwinden:
Sitzung endet; bei Rueckkehr eine frische Begruessung, keine alte Aufgabe. Stop und
Kamera ausschalten muessen weiterhin sofort stoppen. Die Zwei-Sekunden-Frist
beginnt erst bei der erkannten Null-Personen-Meldung; Erkennungszeit separat beachten.


## Kurzer Abnahmelauf GI-18 bis GI-22

1. Schere, Stein, Papier spielen, Ergebnis abwarten, dann nur "Okay" sagen.
2. "Personenraten bitte", danach "Ja, gerne". Das Spiel darf dadurch nicht enden.
3. Kurz aus dem Kamerabild gehen (0,5-1,5 s erkannte Abwesenheit), zurueckkommen,
   weiterfragen. Gleiche Person, gleiche Sitzung, keine neue Begruessung.
4. "Ich gebe auf". Namen einmal hoeren; danach "War es [Name]?".
5. "Wie wirkt mein Gesichtsausdruck?", dann laecheln und nach frischem Sensorwert:
   "Und jetzt?". Antwort zum aktuellen Ausdruck, kein neues Spielangebot.
6. "Wann beginnt das Essen?", danach "Und jetzt?". Keine Rueckkehr zum Gesichtsthema.
7. Gesichtserkennung aus; nach 15 s erneut fragen: keine aktuelle Mimik erfinden.
8. Mindestens drei Sekunden erkannte Abwesenheit: Sitzung endet. Rueckkehr startet
   einen frischen Empfang. Stop/Kamera aus muessen weiterhin sofort stoppen.

Jede Antwort fertig sprechen lassen. Besonders Schritte 5/6 mit Zeit und gehoerter
Antwort notieren: Providerproben zeigen hier noch variable Formulierungen. Ein
passender Untertitel allein bestaetigt nicht, dass jedes Wort hoerbar war.

## Spielwechsel und fruehe Handgeste (GI-23)

Frische Gala-Instanz verwenden und Cockpit hart neu laden.

1. Personenraten starten. "Was macht das IWI?" fragen: zuerst die Frage, ob das
   Spiel beendet werden soll; noch keine Sachantwort oder Namensaufloesung.
2. "Nein" sagen, weiterfragen: gleiche Person. Dann "Wann beginnt das Essen?"
   fragen und diesmal "Ja" sagen: Spiel endet, urspruengliche Frage wird beantwortet.
3. Schere, Stein, Papier starten: Gigi fordert seine Handgeste bereits beim Start an.
   Eine Hand ruhig zeigen: genau ein Ergebnis, keine zweite Ausfuehrung derselben Geste.
   Zeit von sichtbarem Zeichen bis Ergebnis notieren; keine exakte Synchronisation erwarten.
4. In einer weiteren RPS-Runde eine Institutsfrage stellen und waehrend der
   Rueckfrage ein Zeichen zeigen: kein Ergebnis. "Nein" sagen, danach ein frisches
   Zeichen zeigen: dieselbe Gigi-Wahl und genau ein Ergebnis.
5. Bei merklicher Wartezeit auf einen Spiel-/Aufgabenstart darf ein kurzer Hinweis
   auf ausstehende Bestaetigung kommen. Keine behauptete Recherche beim Gala-Programm,
   kein Fragen-Echo, keine wiederholten Wartefloskeln. Stop bleibt unmittelbar.

Gigis eigene Hand aus dem Kamerabereich fuer Besucherhaende halten. Physische
Ausfuehrung, Erkennung und gesprochenes Timing werden durch Backendtests nicht bewiesen.
Letzte-Wort-Abbrueche und ein exakt synchroner Countdown bleiben separate Folgearbeit.

## Rundenbezogene Erkennung (GI-24)

Cockpit hart neu laden. Gigi zeigt zuerst; die Besucherwahl darf darauf reagieren.

1. Schere, Stein, Papier starten und eine Hand ruhig zeigen. Genau ein Ergebnis.
2. Noch eine Runde verlangen und dasselbe Zeichen zeigen: keine erzwungene
   2,5-Sekunden-Pause, keine zweite Geste bei der Ergebnisansage.
3. In einer neuen Runde eine Institutsfrage stellen. Waehrend der Rueckfrage ein
   Zeichen zeigen, dann "Nein" sagen und wieder ruhig halten. Gleiche Gigi-Wahl;
   erst die neue Erkennung nach dem Fortsetzen darf die Runde entscheiden.
4. Fuer eine langsame Runde Zeiten notieren: Hand ruhig sichtbar, "Emitted",
   Ergebnisanzeige, Beginn der gesprochenen Aufloesung. Telemetrie exportieren.

Gigis Hand ausserhalb des Erkennungsbereichs halten. Die Kennung ordnet Messungen
einer Erfassungsphase zu; sie identifiziert nicht die Person hinter der Hand.
