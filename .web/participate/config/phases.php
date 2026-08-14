<?php
declare(strict_types=1);

const PARTICIPATE_PHASE_SIGNUP = 1;
const PARTICIPATE_PHASE_SCHEDULE = 2;
const PARTICIPATE_PHASE_ASSIGNMENT = 3;
const PARTICIPATE_PHASE_COMPLETE = 4;

function participate_phase_labels(): array
{
    return [
        PARTICIPATE_PHASE_SIGNUP => 'Anmeldung',
        PARTICIPATE_PHASE_SCHEDULE => 'Termin',
        PARTICIPATE_PHASE_ASSIGNMENT => 'Zuteilung',
        PARTICIPATE_PHASE_COMPLETE => 'Abschluss',
    ];
}

function participate_phase_label(int $phase): string
{
    return participate_phase_labels()[$phase] ?? participate_phase_labels()[PARTICIPATE_PHASE_SIGNUP];
}

function participate_assignment_field_labels(): array
{
    return [
        'access_code' => 'Zugangscode',
        'participant_role' => 'Rolle',
        'team_id' => 'Team-ID',
        'half_day_slot' => 'Halbtag',
        'time_slot' => 'Zeitfenster',
        'room' => 'Raum',
    ];
}

function participate_phase_from_value(mixed $value): ?int
{
    if (is_int($value)) {
        $phase = $value;
    } elseif (is_string($value) && preg_match('/^[1-4]$/', trim($value)) === 1) {
        $phase = (int) trim($value);
    } else {
        return null;
    }

    return $phase >= PARTICIPATE_PHASE_SIGNUP && $phase <= PARTICIPATE_PHASE_COMPLETE
        ? $phase
        : null;
}

function participate_default_phase(PDO $pdo): int
{
    $statement = $pdo->query(
        'SELECT default_phase FROM participation_phase_settings WHERE id = 1 LIMIT 1'
    );
    return participate_phase_from_value($statement->fetchColumn()) ?? PARTICIPATE_PHASE_SIGNUP;
}

function participate_nullable_text(mixed $value): ?string
{
    if ($value === null) {
        return null;
    }

    $text = trim((string) $value);
    if ($text === '' || strcasecmp($text, 'NULL') === 0) {
        return null;
    }
    return $text;
}

function participate_assignment_missing_fields(?array $assignment): array
{
    $assignment ??= [];
    $phase2Fields = ['half_day_slot', 'time_slot'];
    $phase3Fields = ['access_code', 'participant_role', 'team_id', 'room'];

    $missingPhase2 = array_values(array_filter(
        $phase2Fields,
        static fn(string $field): bool => participate_nullable_text($assignment[$field] ?? null) === null
    ));
    $missingPhase3 = array_values(array_filter(
        $phase3Fields,
        static fn(string $field): bool => participate_nullable_text($assignment[$field] ?? null) === null
    ));

    return [
        'phase2' => $missingPhase2,
        'phase3' => $missingPhase3,
    ];
}

function participate_assignment_phase_ceiling(?array $assignment): int
{
    $missing = participate_assignment_missing_fields($assignment);
    if ($missing['phase2'] !== []) {
        return PARTICIPATE_PHASE_SIGNUP;
    }
    if ($missing['phase3'] !== []) {
        return PARTICIPATE_PHASE_SCHEDULE;
    }
    return PARTICIPATE_PHASE_COMPLETE;
}

function participate_phase_context(int $defaultPhase, ?int $phaseOverride, ?array $assignment): array
{
    $default = participate_phase_from_value($defaultPhase) ?? PARTICIPATE_PHASE_SIGNUP;
    $override = $phaseOverride === null ? null : participate_phase_from_value($phaseOverride);
    $requested = $override ?? $default;
    $ceiling = participate_assignment_phase_ceiling($assignment);
    $effective = min($requested, $ceiling);
    $missing = participate_assignment_missing_fields($assignment);

    return [
        'defaultPhase' => $default,
        'phaseOverride' => $override,
        'requestedPhase' => $requested,
        'effectivePhase' => $effective,
        'dataPhaseCeiling' => $ceiling,
        'limitedByMissingData' => $effective < $requested,
        'missingPhase2Fields' => $missing['phase2'],
        'missingPhase3Fields' => $missing['phase3'],
    ];
}

function participate_visible_assignment(int $registrationId, ?array $assignment, int $effectivePhase): array
{
    if ($effectivePhase < PARTICIPATE_PHASE_SCHEDULE || $effectivePhase >= PARTICIPATE_PHASE_COMPLETE) {
        return [];
    }

    $assignment ??= [];
    $visible = [
        'participantId' => $registrationId,
        'halfDaySlot' => participate_nullable_text($assignment['half_day_slot'] ?? null),
        'timeSlot' => participate_nullable_text($assignment['time_slot'] ?? null),
    ];

    if ($effectivePhase >= PARTICIPATE_PHASE_ASSIGNMENT) {
        $visible += [
            'accessCode' => participate_nullable_text($assignment['access_code'] ?? null),
            'role' => participate_nullable_text($assignment['participant_role'] ?? null),
            'teamId' => participate_nullable_text($assignment['team_id'] ?? null),
            'room' => participate_nullable_text($assignment['room'] ?? null),
        ];
    }

    return $visible;
}
