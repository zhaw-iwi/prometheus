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

function admin_valid_date_of_birth(string $value): bool
{
    if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $value) !== 1) {
        return false;
    }
    [$year, $month, $day] = array_map('intval', explode('-', $value));
    return checkdate($month, $day, $year);
}

function admin_assignment_input(array $payload): array
{
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
    return $assignment;
}

function admin_phase_override(array $payload, bool $creating): ?int
{
    $rawOverride = $payload['phaseOverride'] ?? null;
    if ($rawOverride === null || trim((string) $rawOverride) === '') {
        return $creating ? PARTICIPATE_PHASE_SIGNUP : null;
    }
    $phaseOverride = participate_phase_from_value($rawOverride);
    if ($phaseOverride === null) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_phase',
            'message' => 'Bitte wähle eine gültige individuelle Phase.',
        ], 422);
    }
    return $phaseOverride;
}

function admin_participant_id(mixed $value, bool $required): ?int
{
    $raw = trim((string) ($value ?? ''));
    if ($raw === '' && !$required) {
        return null;
    }
    $participantId = filter_var($raw, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    if ($participantId === false || $participantId === null) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_participant_id',
            'message' => 'Die Teilnehmenden-ID muss eine positive ganze Zahl sein.',
        ], 422);
    }
    return (int) $participantId;
}

function admin_slot_id(array $payload): array
{
    if (!array_key_exists('slotId', $payload)) {
        return ['provided' => false, 'id' => null];
    }
    $raw = trim((string) ($payload['slotId'] ?? ''));
    if ($raw === '') {
        return ['provided' => true, 'id' => null];
    }
    $slotId = filter_var($raw, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    if ($slotId === false || $slotId === null) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_slot',
            'message' => 'Bitte wähle eine gültige Terminpräferenz.',
        ], 422);
    }
    return ['provided' => true, 'id' => (int) $slotId];
}

function admin_validate_registration_values(?string $fullName, string $dateOfBirth, string $email): void
{
    if ($fullName !== null && strlen($fullName) > 255) {
        participate_json([
            'ok' => false,
            'code' => 'name_too_long',
            'message' => 'Der Name ist länger als erlaubt.',
        ], 422);
    }
    if (!admin_valid_date_of_birth($dateOfBirth)) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_date_of_birth',
            'message' => 'Bitte gib ein gültiges Geburtsdatum ein.',
        ], 422);
    }
    if (strlen($email) > 320 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        participate_json([
            'ok' => false,
            'code' => 'invalid_email',
            'message' => 'Bitte gib eine gültige E-Mail-Adresse ein.',
        ], 422);
    }
}

function admin_load_slot(PDO $pdo, ?int $slotId): ?array
{
    if ($slotId === null) {
        return null;
    }
    $statement = $pdo->prepare(
        'SELECT id, slot_key, label
         FROM participation_slots
         WHERE id = :slot_id
         LIMIT 1
         FOR UPDATE'
    );
    $statement->execute(['slot_id' => $slotId]);
    $slot = $statement->fetch();
    return is_array($slot) ? $slot : null;
}

function admin_assert_unique_email(PDO $pdo, string $emailNormalized, ?int $excludedId = null): void
{
    $sql = 'SELECT id FROM participation_registrations WHERE email_normalized = :email_normalized';
    $parameters = ['email_normalized' => $emailNormalized];
    if ($excludedId !== null) {
        $sql .= ' AND id <> :excluded_id';
        $parameters['excluded_id'] = $excludedId;
    }
    $statement = $pdo->prepare($sql . ' LIMIT 1');
    $statement->execute($parameters);
    if ($statement->fetch()) {
        participate_json([
            'ok' => false,
            'code' => 'duplicate_email',
            'message' => 'Für diese E-Mail-Adresse besteht bereits eine Anmeldung.',
        ], 409);
    }
}

function admin_assert_unique_access_code(PDO $pdo, ?string $accessCode, ?int $excludedId = null): void
{
    if ($accessCode === null) {
        return;
    }
    $sql = 'SELECT registration_id FROM participation_assignments WHERE access_code = :access_code';
    $parameters = ['access_code' => $accessCode];
    if ($excludedId !== null) {
        $sql .= ' AND registration_id <> :excluded_id';
        $parameters['excluded_id'] = $excludedId;
    }
    $statement = $pdo->prepare($sql . ' LIMIT 1');
    $statement->execute($parameters);
    if ($statement->fetch()) {
        participate_json([
            'ok' => false,
            'code' => 'duplicate_access_code',
            'message' => 'Dieser Zugangscode ist bereits einer anderen Person zugeteilt.',
        ], 409);
    }
}

function admin_save_assignment(PDO $pdo, int $registrationId, array $assignment): void
{
    $existingAssignment = $pdo->prepare(
        'SELECT registration_id
         FROM participation_assignments
         WHERE registration_id = :registration_id
         LIMIT 1
         FOR UPDATE'
    );
    $existingAssignment->execute(['registration_id' => $registrationId]);
    $assignmentExists = (bool) $existingAssignment->fetch();
    $statement = $assignmentExists
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
    $statement->execute(['registration_id' => $registrationId] + $assignment);
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
        'UPDATE participation_phase_settings SET default_phase = :default_phase WHERE id = 1'
    );
    $statement->execute(['default_phase' => $phase]);
    participate_json([
        'ok' => true,
        'defaultPhase' => $phase,
        'signupOpen' => $phase === PARTICIPATE_PHASE_SIGNUP,
    ]);
}

$creating = $action === 'create_participant';
if (!$creating && $action !== 'save_participant') {
    participate_json([
        'ok' => false,
        'code' => 'unknown_action',
        'message' => 'Diese Admin-Aktion ist nicht bekannt.',
    ], 422);
}

$originalId = $creating ? null : admin_participant_id($payload['id'] ?? null, true);
$participantId = admin_participant_id(
    $payload['participantId'] ?? ($creating ? null : $originalId),
    !$creating
);
$phaseOverride = admin_phase_override($payload, $creating);
$assignment = admin_assignment_input($payload);
$slotInput = admin_slot_id($payload);

$fullNameProvided = array_key_exists('fullName', $payload);
$dateOfBirthProvided = array_key_exists('dateOfBirth', $payload);
$emailProvided = array_key_exists('email', $payload);
$fullNameInput = $fullNameProvided ? participate_nullable_text($payload['fullName']) : null;
$dateOfBirthInput = $dateOfBirthProvided ? trim((string) $payload['dateOfBirth']) : '';
$emailInput = $emailProvided ? trim((string) $payload['email']) : '';

if ($creating) {
    admin_validate_registration_values($fullNameInput, $dateOfBirthInput, $emailInput);
}

try {
    $pdo->beginTransaction();

    if ($creating) {
        if ($participantId !== null) {
            $duplicateIdStatement = $pdo->prepare(
                'SELECT id FROM participation_registrations WHERE id = :participant_id LIMIT 1 FOR UPDATE'
            );
            $duplicateIdStatement->execute(['participant_id' => $participantId]);
            if ($duplicateIdStatement->fetch()) {
                $pdo->rollBack();
                participate_json([
                    'ok' => false,
                    'code' => 'duplicate_participant_id',
                    'message' => 'Diese Teilnehmenden-ID ist bereits vergeben.',
                ], 409);
            }
        }

        $emailNormalized = participate_normalize_email($emailInput);
        admin_assert_unique_email($pdo, $emailNormalized);
        admin_assert_unique_access_code($pdo, $assignment['access_code']);
        $slot = admin_load_slot($pdo, $slotInput['id']);
        if ($slotInput['id'] !== null && $slot === null) {
            $pdo->rollBack();
            participate_json([
                'ok' => false,
                'code' => 'invalid_slot',
                'message' => 'Die gewählte Terminpräferenz wurde nicht gefunden.',
            ], 422);
        }

        $columns = [
            'public_token', 'full_name', 'date_of_birth', 'email', 'email_normalized', 'slot_id',
            'slot_preference_key', 'slot_preference_label', 'status', 'ip_address', 'user_agent',
        ];
        $values = [
            ':public_token', ':full_name', ':date_of_birth', ':email', ':email_normalized', ':slot_id',
            ':slot_preference_key', ':slot_preference_label', "'received'", 'NULL', 'NULL',
        ];
        $parameters = [
            'public_token' => bin2hex(random_bytes(32)),
            'full_name' => $fullNameInput,
            'date_of_birth' => $dateOfBirthInput,
            'email' => $emailInput,
            'email_normalized' => $emailNormalized,
            'slot_id' => $slot['id'] ?? null,
            'slot_preference_key' => $slot['slot_key'] ?? null,
            'slot_preference_label' => $slot['label'] ?? null,
        ];
        if ($participantId !== null) {
            array_unshift($columns, 'id');
            array_unshift($values, ':participant_id');
            $parameters['participant_id'] = $participantId;
        }
        $insertRegistration = $pdo->prepare(
            'INSERT INTO participation_registrations (' . implode(', ', $columns) . ')
             VALUES (' . implode(', ', $values) . ')'
        );
        $insertRegistration->execute($parameters);
        $savedId = $participantId ?? (int) $pdo->lastInsertId();

        if (array_filter($assignment, static fn(?string $value): bool => $value !== null) !== []) {
            admin_save_assignment($pdo, $savedId, $assignment);
        }
        $stateStatement = $pdo->prepare(
            'INSERT INTO participation_participant_state (registration_id, phase_override)
             VALUES (:registration_id, :phase_override)'
        );
        $stateStatement->execute([
            'registration_id' => $savedId,
            'phase_override' => $phaseOverride,
        ]);
    } else {
        $registrationStatement = $pdo->prepare(
            'SELECT id, full_name, date_of_birth, email, slot_id, slot_preference_key, slot_preference_label
             FROM participation_registrations
             WHERE id = :id
             LIMIT 1
             FOR UPDATE'
        );
        $registrationStatement->execute(['id' => $originalId]);
        $registration = $registrationStatement->fetch();
        if (!$registration) {
            $pdo->rollBack();
            participate_json([
                'ok' => false,
                'code' => 'registration_not_found',
                'message' => 'Diese Anmeldung wurde nicht gefunden.',
            ], 404);
        }

        $savedId = $participantId ?? $originalId;
        if ($savedId !== $originalId) {
            $duplicateIdStatement = $pdo->prepare(
                'SELECT id FROM participation_registrations WHERE id = :participant_id LIMIT 1 FOR UPDATE'
            );
            $duplicateIdStatement->execute(['participant_id' => $savedId]);
            if ($duplicateIdStatement->fetch()) {
                $pdo->rollBack();
                participate_json([
                    'ok' => false,
                    'code' => 'duplicate_participant_id',
                    'message' => 'Diese Teilnehmenden-ID ist bereits vergeben.',
                ], 409);
            }
        }

        $fullName = $fullNameProvided ? $fullNameInput : participate_nullable_text($registration['full_name']);
        $dateOfBirth = $dateOfBirthProvided ? $dateOfBirthInput : (string) $registration['date_of_birth'];
        $email = $emailProvided ? $emailInput : (string) $registration['email'];
        admin_validate_registration_values($fullName, $dateOfBirth, $email);
        $emailNormalized = participate_normalize_email($email);
        admin_assert_unique_email($pdo, $emailNormalized, $originalId);
        admin_assert_unique_access_code($pdo, $assignment['access_code'], $originalId);

        if ($slotInput['provided']) {
            $slot = admin_load_slot($pdo, $slotInput['id']);
            if ($slotInput['id'] !== null && $slot === null) {
                $pdo->rollBack();
                participate_json([
                    'ok' => false,
                    'code' => 'invalid_slot',
                    'message' => 'Die gewählte Terminpräferenz wurde nicht gefunden.',
                ], 422);
            }
            $slotId = $slot['id'] ?? null;
            $slotPreferenceKey = $slot['slot_key'] ?? null;
            $slotPreferenceLabel = $slot['label'] ?? null;
        } else {
            $slotId = $registration['slot_id'];
            $slotPreferenceKey = $registration['slot_preference_key'];
            $slotPreferenceLabel = $registration['slot_preference_label'];
        }

        $updateRegistration = $pdo->prepare(
            'UPDATE participation_registrations
             SET id = :participant_id,
                 full_name = :full_name,
                 date_of_birth = :date_of_birth,
                 email = :email,
                 email_normalized = :email_normalized,
                 slot_id = :slot_id,
                 slot_preference_key = :slot_preference_key,
                 slot_preference_label = :slot_preference_label
             WHERE id = :registration_id'
        );
        $updateRegistration->execute([
            'participant_id' => $savedId,
            'full_name' => $fullName,
            'date_of_birth' => $dateOfBirth,
            'email' => $email,
            'email_normalized' => $emailNormalized,
            'slot_id' => $slotId,
            'slot_preference_key' => $slotPreferenceKey,
            'slot_preference_label' => $slotPreferenceLabel,
            'registration_id' => $originalId,
        ]);

        admin_save_assignment($pdo, $savedId, $assignment);
        $stateStatement = $pdo->prepare(
            'INSERT INTO participation_participant_state (registration_id, phase_override)
             VALUES (:registration_id, :phase_override)
             ON DUPLICATE KEY UPDATE phase_override = VALUES(phase_override)'
        );
        $stateStatement->execute([
            'registration_id' => $savedId,
            'phase_override' => $phaseOverride,
        ]);
    }

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
            'code' => 'participant_conflict',
            'message' => 'Teilnehmenden-ID, E-Mail-Adresse oder Zugangscode steht im Konflikt mit einem bestehenden Eintrag.',
        ], 409);
    }
    throw $exception;
} catch (Throwable $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    throw $exception;
}

participate_json([
    'ok' => true,
    'created' => $creating,
    'id' => $savedId,
    'mailSent' => false,
    'confirmationRequired' => false,
    'phaseOverride' => $phaseOverride,
    'assignment' => $assignment,
    'phase' => $context,
], $creating ? 201 : 200);
