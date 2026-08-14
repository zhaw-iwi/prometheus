<?php
declare(strict_types=1);

putenv('PARTICIPATE_ENV_FILE=' . __DIR__ . '/../.env.test');
$_ENV['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';
$_SERVER['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';

require_once __DIR__ . '/../config/bootstrap.php';

function migration_expect(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

function migration_read_sql(string $relativePath): string
{
    $sql = file_get_contents(participate_base_path($relativePath));
    if ($sql === false) {
        throw new RuntimeException("Could not read {$relativePath}");
    }
    return $sql;
}

$configuredDatabase = participate_env('DB_DATABASE', 'sira_participate_test') ?? 'sira_participate_test';
if (preg_match('/^[A-Za-z0-9_]+$/', $configuredDatabase) !== 1) {
    fwrite(STDERR, "Unsafe test database name\n");
    exit(1);
}
$database = $configuredDatabase . '_migration_smoke';
$quotedDatabase = '`' . str_replace('`', '``', $database) . '`';
$pdo = participate_pdo('');

try {
    $pdo->exec("DROP DATABASE IF EXISTS {$quotedDatabase}");
    $pdo->exec("CREATE DATABASE {$quotedDatabase} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    $pdo->exec("USE {$quotedDatabase}");

    $schema = migration_read_sql('database/schema.sql');
    $phaseSchemaPosition = strpos($schema, 'CREATE TABLE IF NOT EXISTS participation_phase_settings');
    migration_expect($phaseSchemaPosition !== false, 'Could not identify the pre-migration schema boundary.');
    $pdo->exec(substr($schema, 0, $phaseSchemaPosition));
    $pdo->exec(
        "INSERT INTO participation_slots
          (id, slot_key, label, starts_at, ends_at, capacity, is_active, sort_order)
         VALUES (1, 'test-slot', 'Test slot', NULL, NULL, NULL, 1, 1)"
    );

    $privateSeed = participate_base_path('database/brainkick_seed.sql');
    $privateSeedSql = is_file($privateSeed) ? file_get_contents($privateSeed) : false;
    $registrationIds = [42];
    if (is_string($privateSeedSql)) {
        preg_match_all('/^\s*\((\d+),/m', $privateSeedSql, $matches);
        $registrationIds = array_map('intval', $matches[1] ?? []);
        migration_expect(count($registrationIds) === 57, 'The private Brainkick seed must contain 57 assignments.');
    }
    $cascadeRegistrationId = $registrationIds[0];
    $registrationIds[] = 1000;

    $insertRegistration = $pdo->prepare(
        "INSERT INTO participation_registrations
          (id, public_token, full_name, date_of_birth, email, email_normalized, slot_id,
           slot_preference_key, slot_preference_label, status)
         VALUES
          (:id, :public_token, :full_name, '1990-01-01', :email, :email_normalized, 1,
           'test-slot', 'Test slot', 'received')"
    );
    foreach (array_values(array_unique($registrationIds)) as $registrationId) {
        $email = "participant{$registrationId}@example.test";
        $insertRegistration->execute([
            'id' => $registrationId,
            'public_token' => str_pad(dechex($registrationId), 64, '0', STR_PAD_LEFT),
            'full_name' => "Participant {$registrationId}",
            'email' => $email,
            'email_normalized' => $email,
        ]);
    }

    $migration = migration_read_sql('database/migrations/20260814_participation_phases.sql');
    $pdo->exec($migration);
    $pdo->exec($migration);

    migration_expect(
        (int) $pdo->query('SELECT default_phase FROM participation_phase_settings WHERE id = 1')->fetchColumn() === 1,
        'The migration must initialize phase 1.'
    );

    $invalidPhaseRejected = false;
    try {
        $pdo->exec('UPDATE participation_phase_settings SET default_phase = 5 WHERE id = 1');
    } catch (PDOException $exception) {
        $invalidPhaseRejected = true;
    }
    migration_expect($invalidPhaseRejected, 'The database must reject invalid phases.');

    if (is_string($privateSeedSql)) {
        $pdo->exec($privateSeedSql);
        $pdo->exec($privateSeedSql);
        migration_expect(
            (int) $pdo->query('SELECT COUNT(*) FROM participation_assignments')->fetchColumn() === 57,
            'The private Brainkick seed must upsert exactly 57 assignments.'
        );
        $reserve = $pdo->query(
            'SELECT access_code, participant_role, team_id, half_day_slot, time_slot, room
             FROM participation_assignments WHERE registration_id = 4'
        )->fetch();
        migration_expect(is_array($reserve), 'Participant 4 must be present in the Brainkick seed.');
        migration_expect($reserve['access_code'] === null, 'Participant 4 access code must be SQL NULL.');
        migration_expect($reserve['participant_role'] === null, 'Participant 4 role must be SQL NULL.');
        migration_expect($reserve['team_id'] === 'Reserve', 'Participant 4 team must remain Reserve.');
        migration_expect($reserve['half_day_slot'] === 'Morgen', 'Participant 4 half-day must remain Morgen.');
        migration_expect($reserve['time_slot'] === null, 'Participant 4 time slot must be SQL NULL.');
        migration_expect($reserve['room'] === null, 'Participant 4 room must be SQL NULL.');
    } else {
        $insertAssignment = $pdo->prepare(
            "INSERT INTO participation_assignments
              (registration_id, access_code, participant_role, team_id, half_day_slot, time_slot, room)
             VALUES (:registration_id, 'TEST42', 'A', '1', 'Morgen', '09:00 - 10:00 Uhr', 'A')"
        );
        $insertAssignment->execute(['registration_id' => $cascadeRegistrationId]);
    }

    $insertState = $pdo->prepare(
        "INSERT INTO participation_participant_state
          (registration_id, phase_override, results_interest, results_interest_updated_at)
         VALUES (:registration_id, 2, 1, CURRENT_TIMESTAMP)
         ON DUPLICATE KEY UPDATE phase_override = VALUES(phase_override)"
    );
    $insertState->execute(['registration_id' => $cascadeRegistrationId]);
    $deleteRegistration = $pdo->prepare('DELETE FROM participation_registrations WHERE id = :registration_id');
    $deleteRegistration->execute(['registration_id' => $cascadeRegistrationId]);
    $stateCount = $pdo->prepare(
        'SELECT COUNT(*) FROM participation_participant_state WHERE registration_id = :registration_id'
    );
    $stateCount->execute(['registration_id' => $cascadeRegistrationId]);
    migration_expect(
        (int) $stateCount->fetchColumn() === 0,
        'Participant state must be deleted with its registration.'
    );
    $assignmentCount = $pdo->prepare(
        'SELECT COUNT(*) FROM participation_assignments WHERE registration_id = :registration_id'
    );
    $assignmentCount->execute(['registration_id' => $cascadeRegistrationId]);
    migration_expect(
        (int) $assignmentCount->fetchColumn() === 0,
        'Assignments must be deleted with their registration.'
    );

    echo is_string($privateSeedSql)
        ? "Participation migration and private Brainkick seed smoke tests passed\n"
        : "Participation migration smoke tests passed; private Brainkick seed was not present\n";
} catch (Throwable $exception) {
    fwrite(STDERR, $exception->getMessage() . PHP_EOL);
    exit(1);
} finally {
    try {
        $pdo->exec("DROP DATABASE IF EXISTS {$quotedDatabase}");
    } catch (Throwable $ignored) {
        // Preserve the original test result if cleanup itself fails.
    }
}
