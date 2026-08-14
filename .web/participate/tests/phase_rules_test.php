<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/phases.php';

$failures = [];

function expect_same(mixed $expected, mixed $actual, string $message): void
{
    global $failures;
    if ($expected !== $actual) {
        $failures[] = $message . ': expected ' . var_export($expected, true) . ', got ' . var_export($actual, true);
    }
}

$complete = [
    'access_code' => 'CODE123',
    'participant_role' => 'A',
    'team_id' => '7',
    'half_day_slot' => 'Morgen',
    'time_slot' => '11:45 - 13:00 Uhr',
    'room' => 'A',
];

expect_same(1, participate_phase_from_value('1'), 'String phase 1 is accepted');
expect_same(4, participate_phase_from_value(4), 'Integer phase 4 is accepted');
expect_same(null, participate_phase_from_value('5'), 'Out-of-range phases are rejected');
expect_same(null, participate_nullable_text(' NULL '), 'Literal NULL becomes a database null');
expect_same('0', participate_nullable_text('0'), 'Zero remains a meaningful assignment value');

$unassigned = participate_phase_context(3, null, null);
expect_same(1, $unassigned['effectivePhase'], 'Unassigned participants stay in phase 1');
expect_same(true, $unassigned['limitedByMissingData'], 'Missing data limitation is reported');

$phase2Only = $complete;
$phase2Only['access_code'] = null;
$phase2Only['participant_role'] = null;
$phase2Only['team_id'] = null;
$phase2Only['room'] = null;
$scheduled = participate_phase_context(3, null, $phase2Only);
expect_same(2, $scheduled['effectivePhase'], 'Schedule-only data limits participants to phase 2');
expect_same(4, count($scheduled['missingPhase3Fields']), 'All phase 3 gaps are reported');

$reserve = $complete;
$reserve['access_code'] = null;
$reserve['participant_role'] = null;
$reserve['team_id'] = 'Reserve';
$reserve['time_slot'] = null;
$reserve['room'] = null;
expect_same(1, participate_assignment_phase_ceiling($reserve), 'Reserve without a time stays in phase 1');

$ready = participate_phase_context(4, null, $complete);
expect_same(4, $ready['effectivePhase'], 'Complete assignments can reach phase 4');
expect_same(false, $ready['limitedByMissingData'], 'Complete assignments are not data-limited');

$overridden = participate_phase_context(4, 2, $complete);
expect_same(2, $overridden['effectivePhase'], 'A participant override takes precedence over the default');

$phase2Payload = participate_visible_assignment(42, $complete, 2);
expect_same(
    ['participantId', 'halfDaySlot', 'timeSlot'],
    array_keys($phase2Payload),
    'Phase 2 exposes only schedule data'
);

$phase3Payload = participate_visible_assignment(42, $complete, 3);
expect_same('CODE123', $phase3Payload['accessCode'] ?? null, 'Phase 3 exposes the access code');
expect_same('A', $phase3Payload['room'] ?? null, 'Phase 3 exposes the room');
expect_same([], participate_visible_assignment(42, $complete, 4), 'Phase 4 replaces assignment data');

if ($failures !== []) {
    fwrite(STDERR, implode(PHP_EOL, $failures) . PHP_EOL);
    exit(1);
}

echo "Participation phase rule tests passed\n";
