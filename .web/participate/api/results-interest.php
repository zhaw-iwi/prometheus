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
if (!array_key_exists('interest', $payload) || !is_bool($payload['interest'])) {
    participate_json([
        'ok' => false,
        'code' => 'invalid_interest',
        'message' => 'Bitte übermittle eine eindeutige Auswahl.',
    ], 422);
}

$token = participate_get_registration_token();
if ($token === null) {
    participate_json([
        'ok' => false,
        'code' => 'identification_required',
        'message' => 'Bitte rufe zuerst deine Anmeldung auf.',
    ], 401);
}

$pdo = participate_pdo();
$participant = participate_participant_by_token($pdo, $token);
if ($participant === null) {
    participate_json([
        'ok' => false,
        'code' => 'identification_required',
        'message' => 'Bitte rufe zuerst deine Anmeldung auf.',
    ], 401);
}

$session = participate_public_participant_session($pdo, $participant);
if (($session['phase']['number'] ?? null) !== PARTICIPATE_PHASE_COMPLETE) {
    participate_json([
        'ok' => false,
        'code' => 'interest_not_available',
        'message' => 'Diese Auswahl ist in deiner aktuellen Phase nicht verfügbar.',
    ], 409);
}

$statement = $pdo->prepare(
    'INSERT INTO participation_participant_state
      (registration_id, results_interest, results_interest_updated_at)
     VALUES
      (:registration_id, :results_interest, CURRENT_TIMESTAMP)
     ON DUPLICATE KEY UPDATE
       results_interest = VALUES(results_interest),
       results_interest_updated_at = CURRENT_TIMESTAMP'
);
$statement->execute([
    'registration_id' => (int) $participant['registration_id'],
    'results_interest' => $payload['interest'] ? 1 : 0,
]);

$updatedParticipant = participate_participant_by_id($pdo, (int) $participant['registration_id']);
if ($updatedParticipant === null) {
    throw new RuntimeException('The updated participation state could not be reloaded.');
}
$updatedSession = participate_public_participant_session($pdo, $updatedParticipant);

participate_json([
    'ok' => true,
    'resultsInterest' => $updatedSession['resultsInterest'],
    'resultsInterestUpdatedAt' => $updatedSession['resultsInterestUpdatedAt'],
]);
