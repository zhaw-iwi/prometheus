<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

$pdo = participate_pdo();
$defaultPhase = participate_default_phase($pdo);
$statement = $pdo->query(
    "SELECT
        r.id,
        r.created_at,
        r.updated_at,
        r.full_name,
        r.date_of_birth,
        r.email,
        r.slot_preference_key,
        r.slot_preference_label,
        s.starts_at AS slot_starts_at,
        s.ends_at AS slot_ends_at,
        s.capacity AS slot_capacity,
        r.status,
        r.ip_address,
        r.user_agent,
        a.access_code,
        a.participant_role,
        a.team_id,
        a.half_day_slot,
        a.time_slot,
        a.room,
        a.updated_at AS assignment_updated_at,
        ps.phase_override,
        ps.results_interest,
        ps.results_interest_updated_at
     FROM participation_registrations r
     LEFT JOIN participation_slots s ON s.id = r.slot_id
     LEFT JOIN participation_assignments a ON a.registration_id = r.id
     LEFT JOIN participation_participant_state ps ON ps.registration_id = r.id
     ORDER BY r.created_at DESC, r.id DESC"
);
$registrations = $statement->fetchAll();
$registrationCount = count($registrations);
$phaseCounts = [1 => 0, 2 => 0, 3 => 0, 4 => 0];
$assignmentFieldLabels = participate_assignment_field_labels();

foreach ($registrations as &$registration) {
    $assignment = [
        'access_code' => $registration['access_code'],
        'participant_role' => $registration['participant_role'],
        'team_id' => $registration['team_id'],
        'half_day_slot' => $registration['half_day_slot'],
        'time_slot' => $registration['time_slot'],
        'room' => $registration['room'],
    ];
    $phaseOverride = participate_phase_from_value($registration['phase_override']);
    $context = participate_phase_context($defaultPhase, $phaseOverride, $assignment);
    $effectivePhase = $context['effectivePhase'];
    $phaseCounts[$effectivePhase] += 1;
    $registration['phase_override'] = $phaseOverride;
    $registration['results_interest'] = $registration['results_interest'] === null
        ? null
        : (bool) $registration['results_interest'];
    $registration['requested_phase'] = $context['requestedPhase'];
    $registration['effective_phase'] = $effectivePhase;
    $registration['data_phase_ceiling'] = $context['dataPhaseCeiling'];
    $registration['limited_by_missing_data'] = $context['limitedByMissingData'];
    $registration['phase_summary'] = 'Phase ' . $effectivePhase . ' · ' . participate_phase_label($effectivePhase);
    if ($context['limitedByMissingData']) {
        $registration['phase_summary'] .= ' (angefordert: Phase ' . $context['requestedPhase'] . ')';
    }
    $missingKeys = $context['missingPhase2Fields'] !== []
        ? $context['missingPhase2Fields']
        : $context['missingPhase3Fields'];
    $registration['missing_assignment_data'] = implode(', ', array_map(
        static fn(string $field): string => $assignmentFieldLabels[$field] ?? $field,
        $missingKeys
    ));
    $registration['results_interest_label'] = $registration['results_interest'] === null
        ? 'Nicht beantwortet'
        : ($registration['results_interest'] ? 'Ja' : 'Nein');
}
unset($registration);
?>
<!doctype html>
<html lang="de" data-theme="light">

<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <meta name="description" content="Administrative Übersicht der SIRA-Lab-Teilnahmeanfragen.">
  <title>Admin | Teilnahme Studie | ZHAW SIRA Lab</title>
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
  <link rel="stylesheet" href="../assets/styles.css">
  <link rel="stylesheet" href="admin.css">
</head>

<body>
  <div class="page-shell admin-shell">
    <header class="site-header">
      <div class="header-inner">
        <a class="brand-lockup" href="../" aria-label="Zur Teilnahmeseite">
          <span class="brand-mark">S</span>
          <span class="brand-text">
            <span class="brand-title">ZHAW SIRA Lab</span>
            <span class="brand-subtitle">Admin Teilnahmeanfragen</span>
          </span>
        </a>
        <button class="icon-button" type="button" title="In den dunklen Modus wechseln"
          aria-label="In den dunklen Modus wechseln" aria-pressed="false" data-theme-toggle>
          <span class="theme-symbol" aria-hidden="true">●</span>
        </button>
      </div>
    </header>

    <main>
      <section class="admin-hero">
        <div class="section-inner admin-hero-inner">
          <div>
            <span class="metric-label">Teilnahme Studie</span>
            <h1>Admin Übersicht</h1>
            <p>
              Eingegangene Teilnahmeanfragen für die Studie zur Zusammenarbeit zwischen Menschen und KI.
            </p>
          </div>
          <div class="admin-metrics" aria-label="Kurzübersicht">
            <div class="fact-card">
              <span class="metric-label">Anmeldungen</span>
              <strong data-metric="total"><?php echo htmlspecialchars((string) $registrationCount, ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>gesamt erfasst</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Phase 1</span>
              <strong data-metric="phase-1"><?php echo htmlspecialchars((string) $phaseCounts[1], ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>Anmeldung</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Phase 2</span>
              <strong data-metric="phase-2"><?php echo htmlspecialchars((string) $phaseCounts[2], ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>Termin</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Phase 3</span>
              <strong data-metric="phase-3"><?php echo htmlspecialchars((string) $phaseCounts[3], ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>Zuteilung</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Phase 4</span>
              <strong data-metric="phase-4"><?php echo htmlspecialchars((string) $phaseCounts[4], ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>Abschluss</span>
            </div>
          </div>
        </div>
      </section>

      <section class="content-band">
        <div class="section-inner">
          <div class="phase-control panel">
            <div>
              <span class="metric-label">Standardphase</span>
              <h2>Gesamtphase steuern</h2>
              <p>
                Individuelle Überschreibungen bleiben erhalten. Fehlende Zuteilungsdaten begrenzen die tatsächlich
                sichtbare Phase automatisch. Beim Wechsel aus Phase 1 wird die Neuanmeldung geschlossen.
              </p>
            </div>
            <form class="phase-control-form" data-default-phase-form>
              <label class="field-label" for="default_phase">
                <span>Gesamtphase</span>
                <select id="default_phase" name="defaultPhase">
                  <?php foreach (participate_phase_labels() as $phase => $label): ?>
                    <option value="<?php echo $phase; ?>" <?php echo $phase === $defaultPhase ? 'selected' : ''; ?>>
                      Phase <?php echo $phase; ?> · <?php echo htmlspecialchars($label, ENT_QUOTES, 'UTF-8'); ?>
                    </option>
                  <?php endforeach; ?>
                </select>
              </label>
              <button class="button primary" type="submit">Gesamtphase speichern</button>
            </form>
          </div>

          <div class="admin-toolbar panel">
            <label class="field-label admin-search" for="registration_search">
              <span>Suche</span>
              <input id="registration_search" type="search" autocomplete="off" placeholder="Nach beliebigem Tabellenwert filtern"
                data-search>
            </label>
            <div class="admin-actions">
              <span class="metric-label" data-row-count>
                <?php echo htmlspecialchars((string) $registrationCount, ENT_QUOTES, 'UTF-8'); ?> Einträge
              </span>
              <button class="button primary" type="button" data-export-csv>
                <span class="button-icon" aria-hidden="true">↓</span>
                CSV exportieren
              </button>
            </div>
          </div>

          <div class="admin-table-wrap panel">
            <table class="admin-table">
              <thead>
                <tr>
                  <th><button type="button" data-sort="id">ID</button></th>
                  <th><button type="button" data-sort="effective_phase">Phase</button></th>
                  <th><button type="button" data-sort="missing_assignment_data">Fehlende Daten</button></th>
                  <th><button type="button" data-sort="created_at">Eingegangen</button></th>
                  <th><button type="button" data-sort="updated_at">Geändert</button></th>
                  <th><button type="button" data-sort="full_name">Name</button></th>
                  <th><button type="button" data-sort="date_of_birth">Geburtsdatum</button></th>
                  <th><button type="button" data-sort="email">E-Mail</button></th>
                  <th><button type="button" data-sort="slot_preference_label">Terminpräferenz</button></th>
                  <th><button type="button" data-sort="slot_starts_at">Slot Start</button></th>
                  <th><button type="button" data-sort="slot_ends_at">Slot Ende</button></th>
                  <th><button type="button" data-sort="slot_capacity">Kapazität</button></th>
                  <th><button type="button" data-sort="status">Status</button></th>
                  <th><button type="button" data-sort="half_day_slot">Halbtag</button></th>
                  <th><button type="button" data-sort="time_slot">Zeitfenster</button></th>
                  <th><button type="button" data-sort="access_code">Zugangscode</button></th>
                  <th><button type="button" data-sort="participant_role">Rolle</button></th>
                  <th><button type="button" data-sort="team_id">Team-ID</button></th>
                  <th><button type="button" data-sort="room">Raum</button></th>
                  <th><button type="button" data-sort="results_interest_label">Ergebnisinfo</button></th>
                  <th><button type="button" data-sort="results_interest_updated_at">Ergebnisinfo geändert</button></th>
                  <th><button type="button" data-sort="ip_address">IP</button></th>
                  <th><button type="button" data-sort="user_agent">User-Agent</button></th>
                  <th>Aktion</th>
                </tr>
              </thead>
              <tbody data-table-body></tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  </div>

  <dialog class="admin-edit-dialog" data-participant-dialog aria-labelledby="participant_editor_title">
    <form class="admin-edit-modal" data-participant-form>
      <div class="modal-header">
        <div>
          <span class="metric-label">Teilnehmenden-ID <span data-editor-participant-id></span></span>
          <h2 id="participant_editor_title">Phase und Zuteilung bearbeiten</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Dialog schliessen" data-close-participant-dialog>
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <input type="hidden" name="id">
      <div class="form-alert" data-participant-alert role="alert" hidden></div>
      <div class="form-grid admin-edit-grid">
        <label class="field-label field-wide" for="phase_override">
          <span>Individuelle Phase</span>
          <select id="phase_override" name="phaseOverride">
            <option value="">Standardphase übernehmen</option>
            <?php foreach (participate_phase_labels() as $phase => $label): ?>
              <option value="<?php echo $phase; ?>">
                Phase <?php echo $phase; ?> · <?php echo htmlspecialchars($label, ENT_QUOTES, 'UTF-8'); ?>
              </option>
            <?php endforeach; ?>
          </select>
        </label>
        <label class="field-label" for="half_day_slot">
          <span>Halbtag</span>
          <input id="half_day_slot" name="halfDaySlot" maxlength="64" placeholder="Morgen oder Nachmittag">
        </label>
        <label class="field-label" for="time_slot">
          <span>Zeitfenster</span>
          <input id="time_slot" name="timeSlot" maxlength="64" placeholder="09:45 - 11:00 Uhr">
        </label>
        <label class="field-label" for="access_code">
          <span>Zugangscode</span>
          <input id="access_code" name="accessCode" maxlength="64" autocomplete="off">
        </label>
        <label class="field-label" for="participant_role">
          <span>Rolle</span>
          <input id="participant_role" name="role" maxlength="32">
        </label>
        <label class="field-label" for="team_id">
          <span>Team-ID</span>
          <input id="team_id" name="teamId" maxlength="64">
        </label>
        <label class="field-label" for="room">
          <span>Raum</span>
          <input id="room" name="room" maxlength="64">
        </label>
      </div>
      <p class="summary-note">Leere Felder werden als NULL gespeichert und können die sichtbare Phase begrenzen.</p>
      <div class="modal-actions">
        <button class="button" type="button" data-close-participant-dialog>Abbrechen</button>
        <button class="button primary" type="submit">Änderungen speichern</button>
      </div>
    </form>
  </dialog>

  <script id="registration_data" type="application/json"><?php
    echo json_encode(
        $registrations,
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT
    );
  ?></script>
  <script id="phase_settings_data" type="application/json"><?php
    echo json_encode(
        ['defaultPhase' => $defaultPhase, 'phaseLabels' => participate_phase_labels()],
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT
    );
  ?></script>
  <script src="admin.js"></script>
</body>

</html>
