<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    participate_json([
        'ok' => false,
        'message' => 'Diese Methode wird nicht unterstützt.',
    ], 405);
}

$pdo = participate_pdo();
$defaultPhase = participate_default_phase($pdo);
$token = participate_get_registration_token();
$participant = $token === null ? null : participate_participant_by_token($pdo, $token);

if ($participant === null) {
    participate_json([
        'ok' => true,
        'registered' => false,
        'signupOpen' => $defaultPhase === PARTICIPATE_PHASE_SIGNUP,
    ]);
}

participate_json(['ok' => true] + participate_public_participant_session($pdo, $participant, $defaultPhase));
