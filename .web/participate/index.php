<!doctype html>
<html lang="de" data-theme="light">

<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description"
    content="Teilnahmeformular für eine Studie zur Zusammenarbeit zwischen Menschen und KI in Teams.">
  <title>Mitmachen | ZHAW SIRA Lab</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link
    href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=Spline+Sans+Mono:wght@400;500&display=swap"
    rel="stylesheet">
  <script>
    (() => {
      let theme = "light";
      try {
        theme = localStorage.getItem("sira.participate.theme") === "dark" ? "dark" : "light";
      } catch (error) {
        theme = "light";
      }
      document.documentElement.dataset.theme = theme;
    })();
  </script>
  <link rel="stylesheet" href="assets/styles.css">
</head>

<body>
  <div class="page-shell">
    <div id="status_alert" class="status-alert" role="status" aria-live="polite" hidden></div>

    <header class="site-header">
      <div class="header-inner">
        <a class="brand-lockup" href="#top" aria-label="Zur Startsektion">
          <span class="brand-mark">S</span>
          <span class="brand-text">
            <span class="brand-title">ZHAW SIRA Lab</span>
            <span class="brand-subtitle">Studie zur Mensch-KI-Zusammenarbeit</span>
          </span>
        </a>
        <div class="header-actions">
          <button class="icon-button" type="button" title="In den dunklen Modus wechseln"
            aria-label="In den dunklen Modus wechseln" aria-pressed="false" data-theme-toggle>
            <span class="theme-symbol" aria-hidden="true">◐</span>
          </button>
          <button class="icon-button session-button" type="button" title="Anmeldung aufrufen"
            aria-label="Anmeldung aufrufen" data-participant-session-action data-session-state="anonymous">
            <svg class="session-icon" data-session-entry-icon viewBox="0 0 24 24" aria-hidden="true">
              <path d="M10 17l5-5-5-5"></path>
              <path d="M15 12H3"></path>
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
            </svg>
            <svg class="session-icon" data-session-exit-icon viewBox="0 0 24 24" aria-hidden="true" hidden>
              <path d="M14 8l4 4-4 4"></path>
              <path d="M18 12H9"></path>
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
            </svg>
          </button>
        </div>
      </div>
    </header>

    <main id="top">
      <section class="hero" aria-label="Studie zur Zusammenarbeit zwischen Menschen und KI">
        <canvas class="hero-canvas" data-hero-canvas aria-hidden="true"></canvas>
        <div class="hero-content">
          <div class="status-row" aria-label="Studieneckdaten">
            <span class="status-pill accent">Deutsch</span>
            <span class="status-pill">Bern</span>
            <span class="status-pill">ca. 1 Stunde</span>
          </div>
          <h1>Gestalte die Zukunft der Zusammenarbeit zwischen Menschen und KI</h1>
          <p class="hero-copy">
            Künstliche Intelligenz wird zunehmend Teil unseres Arbeitsalltags. In dieser Studie untersuchen wir, ob
            KI-gestützte Kollaborationsagenten Teams dabei unterstützen können, während Besprechungen ein stärkeres
            gemeinsames Verständnis zu entwickeln und dadurch die Zusammenarbeit zu verbessern.
          </p>
          <div class="hero-actions">
            <button class="button primary" type="button" data-open-registration data-signup-action disabled>
              <span class="button-icon" aria-hidden="true" data-signup-icon>+</span>
              <span data-signup-label>Mitmachen</span>
            </button>
            <button class="button" type="button" data-open-recovery>
              <span class="button-icon" aria-hidden="true">↗</span>
              Bereits angemeldet?
            </button>
            <a class="button" href="#details">
              <span class="button-icon" aria-hidden="true">i</span>
              Details
            </a>
          </div>
          <p class="signup-status" data-signup-status hidden>
            Die Anmeldung ist geschlossen. Bereits angemeldete Personen können ihre Informationen weiterhin aufrufen.
          </p>
        </div>
      </section>

      <section class="content-band" id="details">
        <div class="section-inner">
          <div class="section-heading">
            <h2>Worum geht es?</h2>
          </div>
          <div class="panel-grid">
            <article class="panel">
              <div class="panel-header">
                <span>Forschungsfrage</span>
                <span class="metric-label">Studie</span>
              </div>
              <div class="panel-body">
                <p>
                  Wir suchen Teilnehmende aus unterschiedlichen beruflichen und persönlichen Hintergründen, die uns
                  dabei unterstützen möchten, neue Formen der Zusammenarbeit zwischen Menschen und KI zu erforschen.
                </p>
                <p>
                  Für die Teilnahme sind keine besonderen Vorkenntnisse erforderlich.
                </p>
              </div>
            </article>

            <article class="panel">
              <div class="panel-header">
                <span>Was erwartet dich?</span>
                <span class="metric-label">Ablauf</span>
              </div>
              <div class="panel-body">
                <p>
                  Während einer einstündigen Sitzung arbeitest du gemeinsam mit anderen Teilnehmenden an einer
                  kreativen Teamaufgabe. Dabei untersuchen wir, wie digitale Unterstützungssysteme die Zusammenarbeit in
                  Teams beeinflussen können.
                </p>
                <p>
                  Die Teilnahme ist einzeln möglich; die Teams werden zufällig zusammengestellt.
                </p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="content-band tight" aria-labelledby="facts-heading">
        <div class="section-inner">
          <div class="section-heading">
            <h2 id="facts-heading">Eckdaten</h2>
          </div>
          <div class="fact-grid">
            <div class="fact-card">
              <span class="metric-label">Datum</span>
              <strong>Montag, 17. August 2026</strong>
            </div>
            <div class="fact-card">
              <span class="metric-label">Ort</span>
              <strong>Bern</strong>
              <span>Eigerstrasse 64, 3007 Bern</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Zielgruppe</span>
              <strong>ca. 20 bis 60 Jahre</strong>
              <span>Personen mit bürobasierter Tätigkeit</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Dankeschön</span>
              <strong>Brain2Business™</strong>
              <span>Inspirationstool für deine Teilnahme</span>
            </div>
          </div>
        </div>
      </section>

      <section class="content-band tight" aria-labelledby="privacy-heading">
        <div class="section-inner">
          <div class="privacy-band">
            <div>
              <span class="metric-label">Datenschutz und Kontakt</span>
              <h2 id="privacy-heading">Was passiert mit deinen Daten?</h2>
              <p>
                Wir sind von der Hochschule der Angewandten Wissenschaften (ZHAW) und der Universität Zürich (UZH).
                Alles, was du mit uns tust, behandeln wir anonym und behalten es für uns.
              </p>
              <p>
                Kontaktangaben dienen nur dazu, deine Teilnahme zu koordinieren. Tool-Nutzungen und Meinungen werden
                anonym erfasst und nicht deinen Kontaktangaben zugeordnet.
              </p>
              <p>
                <a href="mailto:alexandre.despindler@zhaw.ch">Alexandre de Spindler</a> verantwortet diese
                Untersuchung. Bei Fragen, Unklarheiten oder Anmerkungen kannst du ihm eine E-Mail oder auf Teams
                schreiben.
              </p>
            </div>
            <button class="button" type="button" data-open-privacy>
              <span class="button-icon" aria-hidden="true">i</span>
              Vollständige Information
            </button>
          </div>
        </div>
      </section>

      <section class="content-band tight" aria-labelledby="summary-heading" data-local-summary-section
        data-participant-section hidden>
        <div class="section-inner">
          <div class="section-heading">
            <h2 id="summary-heading" data-participant-heading>Deine Anmeldung</h2>
          </div>
          <div class="panel participant-panel">
            <div class="panel-header">
              <span data-participant-panel-title>Gespeicherte Zusammenfassung</span>
              <span class="metric-label" data-participant-phase-label>Phase 1 · Anmeldung</span>
            </div>
            <div class="panel-body">
              <div class="summary-grid" data-local-summary></div>
              <form class="results-interest" data-results-interest-form hidden>
                <label class="interest-choice" for="results_interest">
                  <input id="results_interest" name="resultsInterest" type="checkbox">
                  <span>Ich möchte informiert werden, sobald Ergebnisse aus den nachfolgenden Analysen vorliegen.</span>
                </label>
                <div class="interest-actions">
                  <button class="button primary" type="submit">Auswahl speichern</button>
                  <span class="interest-status" data-interest-status aria-live="polite"></span>
                </div>
              </form>
              <p class="summary-note" data-participant-note>
                Du kannst deine Anmeldung auf diesem oder einem anderen Gerät mit deiner E-Mail-Adresse und deinem
                Geburtsdatum wieder aufrufen. Bei Fragen kontaktiere bitte
                <a href="mailto:alexandre.despindler@zhaw.ch">alexandre.despindler@zhaw.ch</a>.
              </p>
            </div>
          </div>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <div class="footer-inner">
        <span>ZHAW SIRA Lab · Teilnahme Studie</span>
        <span class="footer-links">
          <a href="mailto:alexandre.despindler@zhaw.ch">Kontakt</a>
          <button class="footer-button" type="button" data-open-privacy>Datenschutz</button>
        </span>
      </div>
    </footer>
  </div>

  <dialog class="registration-dialog" data-registration-dialog aria-labelledby="registration_title">
    <form class="registration-modal" data-registration-form method="dialog" novalidate>
      <div class="modal-header">
        <div>
          <span class="metric-label">Teilnahme anfragen</span>
          <h2 id="registration_title">Mitmachen</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Dialog schliessen" data-close-registration>
          <span aria-hidden="true">×</span>
        </button>
      </div>

      <ul class="wizard-progress" role="tablist" aria-label="Anmeldeschritte">
        <li class="nav-item">
          <button class="nav-link active" type="button" data-step-target aria-current="step">
            <span class="step-index">1</span>
            <span class="step-copy">
              <span class="step-title"><span class="step-icon" aria-hidden="true">1</span>Angaben</span>
              <span class="step-caption">Name, Geburtsdatum und E-Mail.</span>
            </span>
          </button>
        </li>
        <li class="nav-item">
          <button class="nav-link" type="button" data-step-target>
            <span class="step-index">2</span>
            <span class="step-copy">
              <span class="step-title"><span class="step-icon" aria-hidden="true">2</span>Termin</span>
              <span class="step-caption">Halbtagespräferenz wählen.</span>
            </span>
          </button>
        </li>
        <li class="nav-item">
          <button class="nav-link" type="button" data-step-target>
            <span class="step-index">3</span>
            <span class="step-copy">
              <span class="step-title"><span class="step-icon" aria-hidden="true">3</span>Prüfen</span>
              <span class="step-caption">Kontrollieren und absenden.</span>
            </span>
          </button>
        </li>
      </ul>

      <div class="form-alert" data-validation-alert role="alert" hidden></div>

      <section class="wizard-step active" data-step-panel>
        <div class="form-grid">
          <label class="field-label" for="full_name">
            <span>Vollständiger Name</span>
            <input id="full_name" name="fullName" autocomplete="name" required placeholder="Vorname Nachname">
          </label>
          <label class="field-label" for="date_of_birth">
            <span>Geburtsdatum</span>
            <input id="date_of_birth" name="dateOfBirth" type="date" required>
          </label>
          <label class="field-label field-wide" for="email">
            <span>E-Mail-Adresse</span>
            <input id="email" name="email" type="email" autocomplete="email" required
              placeholder="name@example.com">
          </label>
        </div>
        <div class="modal-actions">
          <button class="button primary" type="button" data-next>Weiter</button>
        </div>
      </section>

      <section class="wizard-step" data-step-panel>
        <div class="info-panel">
          <strong>Wichtig zur Terminwahl</strong>
          <p>
            Du gibst hier eine Halbtagespräferenz an. Sobald genügend Teilnehmende für einen Halbtag vorhanden sind,
            teilen wir dich einem konkreten einstündigen Slot zu. Du musst nur ungefähr eine Stunde vor Ort sein, nicht
            den ganzen Halbtag.
          </p>
        </div>
        <fieldset class="slot-fieldset" required>
          <legend>Welche Option passt für dich am besten?</legend>
          <label class="slot-card">
            <input type="radio" name="slotPreference" value="2026-08-17-morning" required>
            <span>
              <strong>Montag, 17. August 2026</strong>
              <small>Vormittag · 09:00 bis 13:00</small>
            </span>
          </label>
          <label class="slot-card">
            <input type="radio" name="slotPreference" value="2026-08-17-afternoon" required>
            <span>
              <strong>Montag, 17. August 2026</strong>
              <small>Nachmittag · 13:00 bis 17:00</small>
            </span>
          </label>
          <label class="slot-card">
            <input type="radio" name="slotPreference" value="unavailable" required>
            <span>
              <strong>Ich will gerne teilnehmen, aber diese Termine passen mir nicht</strong>
              <small>Wir melden uns, falls weitere Termine möglich werden.</small>
            </span>
          </label>
        </fieldset>
        <div class="modal-actions">
          <button class="button" type="button" data-prev>Zurück</button>
          <button class="button primary" type="button" data-next>Weiter zur Prüfung</button>
        </div>
      </section>

      <section class="wizard-step" data-step-panel>
        <div class="summary-grid" data-review-summary></div>
        <div class="info-panel">
          <strong>Was passiert nach dem Absenden?</strong>
          <p>
            Wir senden dir unmittelbar eine E-Mail zur Bestätigung deiner Anmeldung. Die Zuteilung des konkreten
            einstündigen Slots braucht etwas Zeit. Danach erhältst du die finale Einladung mit deinem genauen
            einstündigen Termin.
          </p>
          <button class="button" type="button" data-open-privacy>
            <span class="button-icon" aria-hidden="true">i</span>
            Datenschutzinformation lesen
          </button>
        </div>
        <div class="modal-actions">
          <button class="button" type="button" data-prev>Zurück</button>
          <button class="button primary" type="submit">Teilnahmeanfrage absenden</button>
        </div>
      </section>
    </form>
  </dialog>

  <dialog class="registration-dialog recovery-dialog" data-recovery-dialog aria-labelledby="recovery_title">
    <form class="registration-modal recovery-modal" data-recovery-form method="dialog" novalidate>
      <div class="modal-header">
        <div>
          <span class="metric-label">Bestehende Anmeldung</span>
          <h2 id="recovery_title">Anmeldung wieder aufrufen</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Dialog schliessen" data-close-recovery>
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <p>
        Gib dieselbe E-Mail-Adresse und dasselbe Geburtsdatum wie bei deiner Anmeldung ein. So kannst du deine
        Informationen auch auf einem anderen Gerät öffnen.
      </p>
      <div class="form-alert" data-recovery-alert role="alert" hidden></div>
      <div class="form-grid">
        <label class="field-label field-wide" for="recovery_email">
          <span>E-Mail-Adresse</span>
          <input id="recovery_email" name="email" type="email" autocomplete="email" required
            placeholder="name@example.com">
        </label>
        <label class="field-label field-wide" for="recovery_date_of_birth">
          <span>Geburtsdatum</span>
          <input id="recovery_date_of_birth" name="dateOfBirth" type="date" autocomplete="bday" required>
        </label>
      </div>
      <div class="modal-actions">
        <button class="button" type="button" data-close-recovery>Abbrechen</button>
        <button class="button primary" type="submit">Anmeldung aufrufen</button>
      </div>
    </form>
  </dialog>

  <dialog class="privacy-dialog" data-privacy-dialog aria-labelledby="privacy_dialog_title">
    <div class="privacy-modal">
      <div class="modal-header">
        <div>
          <span class="metric-label">Information</span>
          <h2 id="privacy_dialog_title">Datenschutz und Untersuchung</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Datenschutzdialog schliessen" data-close-privacy>
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <div class="privacy-copy">
        <p>
          Wir sind von der Hochschule der Angewandten Wissenschaften (ZHAW) und der Universität Zürich (UZH). Alles, was
          du mit uns tust, behandeln wir anonym und behalten es für uns.
        </p>
        <p>
          Im Rahmen deiner Teilnahme interessieren wir uns für die Nutzung eines Tools und für deine Meinung zum Tool.
          Wir brauchen nicht zu wissen, wer du bist. Wenn wir von dir Kontaktangaben haben, um deine Teilnahme mit dir
          zu koordinieren, werden wir trotzdem nicht wissen, welche Tool-Nutzungen und welche Meinungen zu deinen
          Angaben gehören.
        </p>
        <p>
          Alles, was das Tool aufnimmt, wird von der ZHAW/UZH anonym erfasst und bearbeitet. Alle Daten werden
          ausschliesslich von der ZHAW/UZH verwendet und niemals weitergegeben. Das Tool benutzt GPT bei OpenAI, um die
          Gespräche zu transkribieren und extrahieren.
        </p>
        <p>
          <a href="mailto:alexandre.despindler@zhaw.ch">Alexandre de Spindler</a> verantwortet diese Untersuchung. Bei
          Fragen, Unklarheiten oder Anmerkungen kannst du ihm eine E-Mail oder auf Teams schreiben.
        </p>
      </div>
      <div class="modal-actions">
        <button class="button primary" type="button" data-close-privacy>Verstanden</button>
      </div>
    </div>
  </dialog>

  <script src="assets/app.js"></script>
</body>

</html>
