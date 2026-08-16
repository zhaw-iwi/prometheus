<?php
declare(strict_types=1);

putenv('PARTICIPATE_ENV_FILE=' . __DIR__ . '/../.env.test');
$_ENV['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';
$_SERVER['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';

require_once __DIR__ . '/../config/bootstrap.php';

const MIGRATION_SURVEY_URL = 'https://www.uzh.ch/zi/cl/surveys/index.php/922424?lang=de-easy';

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

function migration_insert_legacy_registration(PDO $pdo, int $registrationId): void
{
    $email = "participant{$registrationId}@example.test";
    $statement = $pdo->prepare(
        "INSERT INTO participation_registrations
          (id, public_token, full_name, date_of_birth, email, email_normalized, slot_id,
           slot_preference_key, slot_preference_label, status)
         VALUES
          (:id, :public_token, :full_name, '1990-01-01', :email, :email_normalized, 1,
           'test-slot', 'Test slot', 'received')"
    );
    $statement->execute([
        'id' => $registrationId,
        'public_token' => hash('sha256', "legacy-participant:{$registrationId}"),
        'full_name' => "Participant {$registrationId}",
        'email' => $email,
        'email_normalized' => $email,
    ]);
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

    $liveDumpPath = participate_base_path('database/e93ud_siralab.sql');
    $liveDumpSql = is_file($liveDumpPath) ? file_get_contents($liveDumpPath) : false;
    $usingLiveDump = is_string($liveDumpSql);
    $privateSeedSql = false;

    if ($usingLiveDump) {
        $pdo->exec($liveDumpSql);
    } else {
        $schema = migration_read_sql('database/schema.sql');
        $phaseSchemaPosition = strpos($schema, 'CREATE TABLE IF NOT EXISTS participation_phase_settings');
        migration_expect($phaseSchemaPosition !== false, 'Could not identify the pre-migration schema boundary.');
        $legacySchema = substr($schema, 0, $phaseSchemaPosition);
        $legacySchema = str_replace('full_name VARCHAR(255) NULL', 'full_name VARCHAR(255) NOT NULL', $legacySchema);
        $legacySchema = str_replace(
            'slot_preference_key VARCHAR(64) NULL',
            'slot_preference_key VARCHAR(64) NOT NULL',
            $legacySchema
        );
        $legacySchema = str_replace(
            'slot_preference_label VARCHAR(255) NULL',
            'slot_preference_label VARCHAR(255) NOT NULL',
            $legacySchema
        );
        $pdo->exec($legacySchema);
        $pdo->exec(
            "INSERT INTO participation_slots
              (id, slot_key, label, starts_at, ends_at, capacity, is_active, sort_order)
             VALUES (1, 'test-slot', 'Test slot', NULL, NULL, NULL, 1, 1)"
        );

        $privateSeedPath = participate_base_path('database/brainkick_seed.sql');
        $privateSeedSql = is_file($privateSeedPath) ? file_get_contents($privateSeedPath) : false;
        $registrationIds = [42];
        if (is_string($privateSeedSql)) {
            preg_match_all('/^\s*\((\d+),/m', $privateSeedSql, $matches);
            $registrationIds = array_map('intval', $matches[1] ?? []);
            migration_expect(count($registrationIds) === 57, 'The private Brainkick seed must contain 57 assignments.');
        }
        foreach (array_values(array_unique($registrationIds)) as $registrationId) {
            migration_insert_legacy_registration($pdo, $registrationId);
        }

        $legacyPhaseMigration = migration_read_sql('database/migrations/20260814_participation_phases.sql');
        $pdo->exec($legacyPhaseMigration);
        $pdo->exec($legacyPhaseMigration);

        if (is_string($privateSeedSql)) {
            $pdo->exec($privateSeedSql);
            $pdo->exec($privateSeedSql);
            migration_expect(
                (int) $pdo->query('SELECT COUNT(*) FROM participation_assignments')->fetchColumn() === 57,
                'The private Brainkick seed must upsert exactly 57 assignments.'
            );
        }
    }

    $defaultPhaseBeforeMigration = (int) $pdo->query(
        'SELECT default_phase FROM participation_phase_settings WHERE id = 1'
    )->fetchColumn();
    $migration = migration_read_sql('database/migrations/20260816_participant_admin_fields.sql');
    $pdo->exec($migration);
    $pdo->exec($migration);

    migration_expect(
        (int) $pdo->query('SELECT default_phase FROM participation_phase_settings WHERE id = 1')->fetchColumn()
            === $defaultPhaseBeforeMigration,
        'The migration must preserve the live default phase.'
    );
    migration_expect(
        $pdo->query('SELECT survey_url FROM participation_phase_settings WHERE id = 1')->fetchColumn()
            === MIGRATION_SURVEY_URL,
        'The migration must store the agreed singleton survey URL.'
    );

    $nullableColumns = $pdo->query(
        "SELECT COLUMN_NAME, IS_NULLABLE
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'participation_registrations'
           AND COLUMN_NAME IN ('full_name', 'slot_preference_key', 'slot_preference_label')"
    )->fetchAll(PDO::FETCH_KEY_PAIR);
    foreach (['full_name', 'slot_preference_key', 'slot_preference_label'] as $nullableColumn) {
        migration_expect(
            ($nullableColumns[$nullableColumn] ?? null) === 'YES',
            "{$nullableColumn} must be nullable after the migration."
        );
    }

    $foreignKeys = $pdo->query(
        "SELECT CONSTRAINT_NAME, CONCAT(DELETE_RULE, '/', UPDATE_RULE)
         FROM information_schema.REFERENTIAL_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME IN (
             'fk_participation_assignments_registration',
             'fk_participation_participant_state_registration'
           )"
    )->fetchAll(PDO::FETCH_KEY_PAIR);
    foreach (
        ['fk_participation_assignments_registration', 'fk_participation_participant_state_registration']
        as $foreignKey
    ) {
        migration_expect(
            ($foreignKeys[$foreignKey] ?? null) === 'CASCADE/CASCADE',
            "{$foreignKey} must cascade registration deletes and participant-ID updates."
        );
    }

    if (!$usingLiveDump) {
        $invalidPhaseRejected = false;
        try {
            $pdo->exec('UPDATE participation_phase_settings SET default_phase = 5 WHERE id = 1');
        } catch (PDOException $exception) {
            $invalidPhaseRejected = true;
        }
        migration_expect($invalidPhaseRejected, 'The canonical database must reject invalid phases.');
    }

    $maxRegistrationId = (int) $pdo->query('SELECT COALESCE(MAX(id), 0) FROM participation_registrations')->fetchColumn();
    $originalId = $maxRegistrationId + 100;
    $duplicateId = $originalId + 1;
    $movedId = $originalId + 2;
    $insertMinimalRegistration = $pdo->prepare(
        "INSERT INTO participation_registrations
          (id, public_token, date_of_birth, email, email_normalized, status)
         VALUES (:id, :public_token, '1990-01-01', :email, :email_normalized, 'received')"
    );
    foreach ([$originalId, $duplicateId] as $registrationId) {
        $email = "migration-smoke-{$registrationId}@example.test";
        $insertMinimalRegistration->execute([
            'id' => $registrationId,
            'public_token' => hash('sha256', "minimal-participant:{$registrationId}"),
            'email' => $email,
            'email_normalized' => $email,
        ]);
    }

    $nullFieldCount = $pdo->prepare(
        'SELECT COUNT(*) FROM participation_registrations
         WHERE id = :registration_id
           AND full_name IS NULL
           AND slot_preference_key IS NULL
           AND slot_preference_label IS NULL'
    );
    $nullFieldCount->execute(['registration_id' => $originalId]);
    migration_expect((int) $nullFieldCount->fetchColumn() === 1, 'A minimal admin participant must allow optional NULL fields.');

    $insertAssignment = $pdo->prepare(
        "INSERT INTO participation_assignments
          (registration_id, access_code, participant_role, team_id, half_day_slot, time_slot, room)
         VALUES (:registration_id, NULL, NULL, NULL, NULL, NULL, NULL)"
    );
    $insertAssignment->execute(['registration_id' => $originalId]);
    $insertState = $pdo->prepare(
        "INSERT INTO participation_participant_state
          (registration_id, phase_override, results_interest, results_interest_updated_at)
         VALUES (:registration_id, 1, NULL, NULL)"
    );
    $insertState->execute(['registration_id' => $originalId]);

    $updateRegistrationId = $pdo->prepare(
        'UPDATE participation_registrations SET id = :new_id WHERE id = :registration_id'
    );
    $updateRegistrationId->execute(['new_id' => $movedId, 'registration_id' => $originalId]);
    foreach (['participation_assignments', 'participation_participant_state'] as $childTable) {
        $childCount = $pdo->query(
            "SELECT COUNT(*) FROM {$childTable} WHERE registration_id = {$movedId}"
        )->fetchColumn();
        migration_expect((int) $childCount === 1, "{$childTable} must follow an edited participant ID.");
    }

    $duplicateIdRejected = false;
    try {
        $updateRegistrationId->execute(['new_id' => $movedId, 'registration_id' => $duplicateId]);
    } catch (PDOException $exception) {
        $duplicateIdRejected = true;
    }
    migration_expect($duplicateIdRejected, 'The database must reject a duplicate participant ID.');

    $deleteRegistration = $pdo->prepare('DELETE FROM participation_registrations WHERE id = :registration_id');
    $deleteRegistration->execute(['registration_id' => $movedId]);
    foreach (['participation_assignments', 'participation_participant_state'] as $childTable) {
        $childCount = $pdo->query(
            "SELECT COUNT(*) FROM {$childTable} WHERE registration_id = {$movedId}"
        )->fetchColumn();
        migration_expect((int) $childCount === 0, "{$childTable} must be deleted with its registration.");
    }

    echo $usingLiveDump
        ? "Participant admin-field migration passed against the private live database dump\n"
        : "Participant admin-field migration passed against the synthetic legacy schema\n";
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
