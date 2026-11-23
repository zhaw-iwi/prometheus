# Prometheus/Valerian – MSc-Kurstag

19. November 2026 · Alex · «Collaborate with AI Systems» · Abschnitt «Zusammenarbeit zwischen Mensch und Roboter koordinieren».

**Status: Zustandsaufnahme fertig; Cockpitaufnahme vorläufig ohne Kamerabild.** Alex hat ausschließlich die Nutzung einer vorhandenen Kamera-/Videofixture gewählt. Die Webcam bleibt aus. Eine geeignete eingebundene Fixture wurde nicht gefunden; der Wahrnehmungsteil des Auftrags ist noch nicht erfüllt.

## Bildunterschriften und didaktische Aussage

**01-valerian-cockpit-koordination.png:** «Eine Aufgabe gemeinsam präzisieren: Nach ausdrücklicher Startbestätigung soll der Coordination Agent auf ein frisches Scherenzeichen genau einmal eine Kategorie zur Wortfindung nennen. Der Vorschlag wartet noch auf Zustimmung. Rechts visualisiert der echte Valerian-Avatar den aktuellen Verhaltensplan; die Kamera ist in diesem Vorabzug aus.»

**02-coordination-agent-zustandsmaschine.png:** «Expliziter Zustand hält den Aufgabenvorschlag fest: Die tatsächliche Zustandsmaschine desselben Coordination Agent wartet in ‹Coordination awaiting acceptance› auf Zustimmung, bevor die vereinbarte Ausführung beginnen kann.»

Fähigkeiten klären, Aufgaben begrenzen und Zustimmung einholen sind unterschiedliche Schritte. Gespeicherter Zustand macht den Vorschlag über das Gespräch hinaus steuerbar. Die Demo belegt weder physische Roboteraktionen noch den Nutzen eines evaluierten Team-Unterstützungssystems. Auch Mehrpersonenerkennung allein würde kein gemeinsames Verständnis belegen.

## Agent und Zustand

- Typ: `core.coordination` / **Valerian Core - Coordination Agent**.
- Neue Demo-Instanz: `efc34183-8f75-4bc9-9e18-fd0e163b75f4`; UI-Kurzkennung `efc34183`.
- Beide Bilder: **Coordination awaiting acceptance**, aktive Zustands-ID `32795551-8d55-4705-9775-9354b6389244`.
- Gespeicherter Vorschlag: `obs.hand.sign`, `sign == scissor`, eine Kategorie zur Wortfindung, `maxActions: 1`, `firstAction: ON_CUE`, Revision 1. Nicht gestartet; keine Sensoraktion ausgeführt.
- Drei synthetische menschliche Texte wurden über das Cockpit gesendet. Antworten entstanden durch die tatsächliche Anwendung mit dem konfigurierten Modell `gpt-5.6-luna` beziehungsweise ihrer festen Bestätigungsvorlage; kein Antwort-Replay.
- Rechts steht die spätere deutsche Zusammenfassung, im Chat ein früherer zusammenhängender Ausschnitt. Begrüßung und feste Bestätigung sind teilweise Englisch und wurden nicht umgeschrieben.

## Wahrnehmung und Einschränkungen

- **Detektoren eingeschaltet/geladen:** Face emotion, Social context, Hand sign; Schalter sichtbar aktiv.
- **Kamerabild und Erkennung:** keine. Webcam nicht verwendet; geeignete vorhandene Kamera-/Videofixture nicht gefunden. Kein Replay. Eingeschaltete Detektoren oder initiale Nullwerte belegen keine Wahrnehmung.
- **Übermittlung:** nur drei `obs.user_utterance`; keine Kamera-Beobachtungen. «Emit camera observations» blieb aus.
- **Offen:** Pfad zu einer geeigneten vorhandenen Kamera-/Videofixture und eine vorhandene Zuführung als Kameraquelle. Danach echte Detektorausgaben und Übermittlung prüfen, Bild 1 ersetzen und die Replay-Herkunft dokumentieren. Aufgabe nicht starten. Eine Gesichtsausdrucksschätzung wäre kein gesichertes Wissen über innere Gefühle.
- Mikrofon, Transkription und Audioausgabe blieben aus. Avatar aus dem vorhandenen 3D-Renderer, keine physische Ausführung nachgewiesen.

## Aufnahme und Herkunft

Helles Theme, Ausgangsviewport **1920 × 1080**. Bild 1: **1920 × 1030**; nur die oberen 50 Pixel mit der Zugangscode-Kopfzeile beschnitten. Keine Montage, Retusche oder Layoutänderung.

Bild 2: **1920 × 1080**, separate Zustandsansicht im Vollbild, 125 % Browserzoom und «Fit graph». Vollständige flache Maschine mit **10 Zuständen und 47 Übergängen**; keine Hierarchie ausgeblendet. Blau = aktueller Zustand, ×N = gruppierte Übergänge. Pfeile zeigen Definitionen, keine geprüfte aktuelle Ausführbarkeit.

Repository `D:\VSCWorkSpace\prometheus`, Branch **main**, Commit **84f90dc921f33d51af476d9c522e30da3d798756**; anfangs sauberer Arbeitsbaum. Projektanweisungen, aktuelle README-/PROJECT-Abschnitte und Poster samt Transkript geprüft; keine anwendbare `AGENTS.md` gefunden.

Aufnahmequellstand erfolgreich mit Maven gebaut (`-DskipTests package`, separater Buildordner). Eigene lokale Anwendung auf `127.0.0.1:8086`, eigene MySQL-Datenbank mit beschränktem Benutzer. Bestehende Sitzungen und Anwendungscode unverändert. Zustand, Vorschlag, Ereignisse und Graph über reguläre scoped Endpunkte geprüft. Beide gespeicherten PNGs visuell geprüft.

**Nachprüfung der Fixtures:** In `tests/`, `src/test/`, `.doc/` und den lokalen `target/`-Artefakten wurden auch ignorierte Mediendateien geprüft. `installMockFaceCamera` und `installMockSocialCameraWithThrowingFace` in `tests/playwright/valerian-column-expansion.spec.mjs` liefern fest vorgegebene Modellwerte mit leerem `MediaStream`, keine aufgenommenen Kamerabilder. `src/test/resources/coordination/scissors-interaction.json` enthält einen synthetischen Dialog-/Aufgabentest, kein Video. `target/robot-reference/lumi.mp4` ist ein vorhandenes Roboter-Referenzvideo, keine eingebundene Wahrnehmungsfixture; ausgewählte vorhandene Frames wurden visuell geprüft. Keines dieser Artefakte wurde als Live-Erkennung ausgegeben. Das aktuelle Cockpit bietet Kamerageräte, aber keinen Video-Dateiimport. Ein zusätzlicher Fixture-Adapter wurde entsprechend dem Auftrag nicht implementiert.

Die Demo bleibt für eine mögliche Fortsetzung mit vorhandener Fixture geöffnet. Laufzeitnachweise unter `target/course-demo/`; dortige Betriebsdateien enthalten lokale Zugangsdaten und gehören nicht in Kursmaterial.
