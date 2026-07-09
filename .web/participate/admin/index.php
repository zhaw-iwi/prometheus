<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

$pdo = participate_pdo();
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
        r.user_agent
     FROM participation_registrations r
     LEFT JOIN participation_slots s ON s.id = r.slot_id
     ORDER BY r.created_at DESC, r.id DESC"
);
$registrations = $statement->fetchAll();
$registrationCount = count($registrations);
$morningCount = 0;
$afternoonCount = 0;
$unavailableCount = 0;

foreach ($registrations as $registration) {
    if ($registration['slot_preference_key'] === '2026-08-17-morning') {
        $morningCount += 1;
    } elseif ($registration['slot_preference_key'] === '2026-08-17-afternoon') {
        $afternoonCount += 1;
    } elseif ($registration['slot_preference_key'] === 'unavailable') {
        $unavailableCount += 1;
    }
}
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
              <span class="metric-label">Vormittag</span>
              <strong data-metric="2026-08-17-morning"><?php echo htmlspecialchars((string) $morningCount, ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>17.08.2026, 09:00 bis 13:00</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Nachmittag</span>
              <strong data-metric="2026-08-17-afternoon"><?php echo htmlspecialchars((string) $afternoonCount, ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>17.08.2026, 13:00 bis 17:00</span>
            </div>
            <div class="fact-card">
              <span class="metric-label">Andere Termine</span>
              <strong data-metric="unavailable"><?php echo htmlspecialchars((string) $unavailableCount, ENT_QUOTES, 'UTF-8'); ?></strong>
              <span>interessiert, aber verhindert</span>
            </div>
          </div>
        </div>
      </section>

      <section class="content-band">
        <div class="section-inner">
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

  <script id="registration_data" type="application/json"><?php
    echo json_encode(
        $registrations,
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT
    );
  ?></script>
  <script src="admin.js"></script>
</body>

</html>
