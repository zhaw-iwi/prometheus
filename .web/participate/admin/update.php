<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    participate_json([
        'ok' => false,
        'code' => 'method_not_allowed',
        'message' => 'Diese Methode wird nicht unterstützt.',
    ], 405);
}

$payload = participate_read_json();
$action = trim((string) ($payload['action'] ?? ''));
$pdo = participate_pdo();

if ($action === 'set_default_phase') {
    $phase = participate_phase_from_value($payload['defaultPhase'] ?? null);
    if ($phase === null) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_phase',
            'message' => 'Bitte wähle eine gültige Gesamtphase.',
        ], 422);
    }

    $statement = $pdo->prepare(
        'INSERT INTO participation_phase_settings (id, default_phase)
         VALUES (1, :default_phase)
         ON DUPLICATE KEY UPDATE default_phase = VALUES(default_phase)'
    );
    $statement->execute(['default_phase' => $phase]);
    participate_json([
        'ok' => true,
        'defaultPhase' => $phase,
        'signupOpen' => $phase === PARTICIPATE_PHASE_SIGNUP,
    ]);
}

if ($action !== 'save_participant') {
    participate_json([
        'ok' => false,
        'code' => 'unknown_action',
        'message' => 'Diese Admin-Aktion ist nicht bekannt.',
    ], 422);
}

$registrationId = filter_var(
    $payload['id'] ?? null,
    FILTER_VALIDATE_INT,
    ['options' => ['min_range' => 1]]
);
if ($registrationId === false || $registrationId === null) {
    participate_json([
        'ok' => false,
        'code' => 'invalid_registration',
        'message' => 'Die Anmeldung konnte nicht eindeutig bestimmt werden.',
    ], 422);
}

$rawOverride = $payload['phaseOverride'] ?? null;
$phaseOverride = $rawOverride === null || trim((string) $rawOverride) === ''
    ? null
    : participate_phase_from_value($rawOverride);
if ($rawOverride !== null && trim((string) $rawOverride) !== '' && $phaseOverride === null) {
    participate_json([
        'ok' => false,
        'code' => 'invalid_phase',
        'message' => 'Bitte wähle eine gültige individuelle Phase.',
    ], 422);
}

$assignment = [
    'access_code' => participate_nullable_text($payload['accessCode'] ?? null),
    'participant_role' => participate_nullable_text($payload['role'] ?? null),
    'team_id' => participate_nullable_text($payload['teamId'] ?? null),
    'half_day_slot' => participate_nullable_text($payload['halfDaySlot'] ?? null),
    'time_slot' => participate_nullable_text($payload['timeSlot'] ?? null),
    'room' => participate_nullable_text($payload['room'] ?? null),
];
$limits = [
    'access_code' => 64,
    'participant_role' => 32,
    'team_id' => 64,
    'half_day_slot' => 64,
    'time_slot' => 64,
    'room' => 64,
];
foreach ($limits as $field => $limit) {
    if ($assignment[$field] !== null && strlen($assignment[$field]) > $limit) {
        participate_json([
            'ok' => false,
            'code' => 'assignment_value_too_long',
            'message' => 'Ein Zuteilungswert ist länger als erlaubt.',
            'field' => $field,
        ], 422);
    }
}

try {
    $pdo->beginTransaction();

    $registrationStatement = $pdo->prepare(
        'SELECT id FROM participation_registrations WHERE id = :id LIMIT 1 FOR UPDATE'
    );
    $registrationStatement->execute(['id' => $registrationId]);
    if (!$registrationStatement->fetch()) {
        $pdo->rollBack();
        participate_json([
            'ok' => false,
            'code' => 'registration_not_found',
            'message' => 'Diese Anmeldung wurde nicht gefunden.',
        ], 404);
    }

    if ($assignment['access_code'] !== null) {
        $duplicateStatement = $pdo->prepare(
            'SELECT registration_id
             FROM participation_assignments
             WHERE access_code = :access_code AND registration_id <> :registration_id
             LIMIT 1'
        );
        $duplicateStatement->execute([
            'access_code' => $assignment['access_code'],
            'registration_id' => $registrationId,
        ]);
        if ($duplicateStatement->fetch()) {
            $pdo->rollBack();
            participate_json([
                'ok' => false,
                'code' => 'duplicate_access_code',
                'message' => 'Dieser Zugangscode ist bereits einer anderen Person zugeteilt.',
            ], 409);
        }
    }

    $existingAssignment = $pdo->prepare(
        'SELECT registration_id
         FROM participation_assignments
         WHERE registration_id = :registration_id
         LIMIT 1
         FOR UPDATE'
    );
    $existingAssignment->execute(['registration_id' => $registrationId]);
    $assignmentExists = (bool) $existingAssignment->fetch();
    $assignmentStatement = $assignmentExists
        ? $pdo->prepare(
            'UPDATE participation_assignments
             SET access_code = :access_code,
                 participant_role = :participant_role,
                 team_id = :team_id,
                 half_day_slot = :half_day_slot,
                 time_slot = :time_slot,
                 room = :room
             WHERE registration_id = :registration_id'
        )
        : $pdo->prepare(
            'INSERT INTO participation_assignments
              (registration_id, access_code, participant_role, team_id, half_day_slot, time_slot, room)
             VALUES
              (:registration_id, :access_code, :participant_role, :team_id, :half_day_slot, :time_slot, :room)'
        );
    $assignmentStatement->execute(['registration_id' => $registrationId] + $assignment);

    $stateStatement = $pdo->prepare(
        'INSERT INTO participation_participant_state (registration_id, phase_override)
         VALUES (:registration_id, :phase_override)
         ON DUPLICATE KEY UPDATE phase_override = VALUES(phase_override)'
    );
    $stateStatement->execute([
        'registration_id' => $registrationId,
        'phase_override' => $phaseOverride,
    ]);

    $defaultPhase = participate_default_phase($pdo);
    $context = participate_phase_context($defaultPhase, $phaseOverride, $assignment);
    $pdo->commit();
} catch (PDOException $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    if ($exception->getCode() === '23000') {
        participate_json([
            'ok' => false,
            'code' => 'assignment_conflict',
            'message' => 'Die Zuteilung steht im Konflikt mit einer bestehenden Zuteilung.',
        ], 409);
    }
    throw $exception;
}

participate_json([
    'ok' => true,
    'id' => $registrationId,
    'phaseOverride' => $phaseOverride,
    'assignment' => $assignment,
    'phase' => $context,
]);
