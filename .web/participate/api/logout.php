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

participate_clear_registration_cookie();
$defaultPhase = participate_default_phase(participate_pdo());

participate_json([
    'ok' => true,
    'registered' => false,
    'signupOpen' => $defaultPhase === PARTICIPATE_PHASE_SIGNUP,
]);
