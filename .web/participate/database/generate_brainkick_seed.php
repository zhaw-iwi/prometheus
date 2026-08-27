<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/phases.php';

const BRAINKICK_CSV_HEADERS = [
    'Participant ID',
    'Access Code',
    'Role',
    'Team ID',
    'Half-day slot',
    'Time slot',
    'Room',
];

function brainkick_sql_value(?string $value): string
{
    if ($value === null) {
        return 'NULL';
    }
    return "'" . str_replace("'", "''", $value) . "'";
}

function brainkick_read_csv(string $csvPath): array
{
    $handle = fopen($csvPath, 'rb');
    if ($handle === false) {
        throw new RuntimeException('Could not open the Brainkick participant CSV.');
    }

    try {
        $headers = fgetcsv($handle, null, ',', '"', '\\');
        if (isset($headers[0])) {
            $headers[0] = preg_replace('/^\xEF\xBB\xBF/', '', (string) $headers[0]);
        }
        if ($headers !== BRAINKICK_CSV_HEADERS) {
            throw new RuntimeException('The Brainkick participant CSV headers do not match the expected format.');
        }

        $rows = [];
        $participantIds = [];
        $accessCodes = [];
        while (($values = fgetcsv($handle, null, ',', '"', '\\')) !== false) {
            if ($values === [null] || $values === []) {
                continue;
            }
            if (count($values) !== count(BRAINKICK_CSV_HEADERS)) {
                throw new RuntimeException('A Brainkick participant row has the wrong number of columns.');
            }

            $row = array_combine(BRAINKICK_CSV_HEADERS, $values);
            $participantIdText = trim((string) $row['Participant ID']);
            if (preg_match('/^[1-9][0-9]*$/', $participantIdText) !== 1) {
                throw new RuntimeException('Every Brainkick participant ID must be a positive integer.');
            }
            $participantId = (int) $participantIdText;
            if (isset($participantIds[$participantId])) {
                throw new RuntimeException("Duplicate Brainkick participant ID {$participantId}.");
            }
            $participantIds[$participantId] = true;

            $accessCode = participate_nullable_text($row['Access Code']);
            if ($accessCode !== null && isset($accessCodes[$accessCode])) {
                throw new RuntimeException("Duplicate Brainkick access code for participant {$participantId}.");
            }
            if ($accessCode !== null) {
                $accessCodes[$accessCode] = true;
            }

            $normalized = [
                'registration_id' => $participantId,
                'access_code' => $accessCode,
                'participant_role' => participate_nullable_text($row['Role']),
                'team_id' => participate_nullable_text($row['Team ID']),
                'half_day_slot' => participate_nullable_text($row['Half-day slot']),
                'time_slot' => participate_nullable_text($row['Time slot']),
                'room' => participate_nullable_text($row['Room']),
            ];
            $fieldLimits = [
                'access_code' => 64,
                'participant_role' => 32,
                'team_id' => 64,
                'half_day_slot' => 64,
                'time_slot' => 64,
                'room' => 64,
            ];
            foreach ($fieldLimits as $field => $limit) {
                $value = $normalized[$field];
                if ($value !== null && strlen($value) > $limit) {
                    throw new RuntimeException("Brainkick field {$field} is too long for participant {$participantId}.");
                }
            }
            $rows[] = $normalized;
        }
    } finally {
        fclose($handle);
    }

    if ($rows === []) {
        throw new RuntimeException('The Brainkick participant CSV contains no data rows.');
    }
    usort($rows, static fn(array $left, array $right): int => $left['registration_id'] <=> $right['registration_id']);
    return $rows;
}

function brainkick_seed_sql(array $rows): string
{
    $values = array_map(static function (array $row): string {
        return sprintf(
            '  (%d, %s, %s, %s, %s, %s, %s)',
            $row['registration_id'],
            brainkick_sql_value($row['access_code']),
            brainkick_sql_value($row['participant_role']),
            brainkick_sql_value($row['team_id']),
            brainkick_sql_value($row['half_day_slot']),
            brainkick_sql_value($row['time_slot']),
            brainkick_sql_value($row['room'])
        );
    }, $rows);

    return implode(PHP_EOL, [
        '-- Generated from the private Brainkick participant CSV.',
        '-- Contains live access codes. Keep this file out of version control.',
        'START TRANSACTION;',
        '',
        'CREATE TEMPORARY TABLE brainkick_assignment_import (',
        '  registration_id BIGINT UNSIGNED NOT NULL,',
        '  access_code VARCHAR(64) NULL,',
        '  participant_role VARCHAR(32) NULL,',
        '  team_id VARCHAR(64) NULL,',
        '  half_day_slot VARCHAR(64) NULL,',
        '  time_slot VARCHAR(64) NULL,',
        '  room VARCHAR(64) NULL,',
        '  PRIMARY KEY (registration_id),',
        '  UNIQUE KEY uq_brainkick_assignment_import_access_code (access_code)',
        ') ENGINE=InnoDB;',
        '',
        'INSERT INTO brainkick_assignment_import',
        '  (registration_id, access_code, participant_role, team_id, half_day_slot, time_slot, room)',
        'VALUES',
        implode(',' . PHP_EOL, $values) . ';',
        '',
        'UPDATE participation_assignments a',
        'JOIN brainkick_assignment_import i ON i.registration_id = a.registration_id',
        'SET a.access_code = i.access_code,',
        '    a.participant_role = i.participant_role,',
        '    a.team_id = i.team_id,',
        '    a.half_day_slot = i.half_day_slot,',
        '    a.time_slot = i.time_slot,',
        '    a.room = i.room;',
        '',
        'INSERT INTO participation_assignments',
        '  (registration_id, access_code, participant_role, team_id, half_day_slot, time_slot, room)',
        'SELECT',
        '  i.registration_id, i.access_code, i.participant_role, i.team_id,',
        '  i.half_day_slot, i.time_slot, i.room',
        'FROM brainkick_assignment_import i',
        'LEFT JOIN participation_assignments a ON a.registration_id = i.registration_id',
        'WHERE a.registration_id IS NULL;',
        '',
        'DROP TEMPORARY TABLE brainkick_assignment_import;',
        '',
        'COMMIT;',
        '',
    ]);
}

function brainkick_generate_seed(string $csvPath, string $outputPath): int
{
    $rows = brainkick_read_csv($csvPath);
    if (file_put_contents($outputPath, brainkick_seed_sql($rows)) === false) {
        throw new RuntimeException('Could not write the Brainkick seed SQL file.');
    }
    return count($rows);
}

if (realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    if ($argc !== 3) {
        fwrite(STDERR, "Usage: php generate_brainkick_seed.php INPUT.csv OUTPUT.sql\n");
        exit(1);
    }
    try {
        $count = brainkick_generate_seed($argv[1], $argv[2]);
        echo "Generated {$count} Brainkick assignments\n";
    } catch (Throwable $exception) {
        fwrite(STDERR, $exception->getMessage() . PHP_EOL);
        exit(1);
    }
}
