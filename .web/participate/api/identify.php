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
$email = trim((string) ($payload['email'] ?? ''));
$dateOfBirth = trim((string) ($payload['dateOfBirth'] ?? ''));

if (!filter_var($email, FILTER_VALIDATE_EMAIL) || preg_match('/^\d{4}-\d{2}-\d{2}$/', $dateOfBirth) !== 1) {
    participate_json([
        'ok' => false,
        'code' => 'invalid_identity',
        'message' => 'Bitte gib deine E-Mail-Adresse und dein gültiges Geburtsdatum ein.',
    ], 422);
}
[$year, $month, $day] = array_map('intval', explode('-', $dateOfBirth));
if (!checkdate($month, $day, $year)) {
    participate_json([
        'ok' => false,
        'code' => 'invalid_identity',
        'message' => 'Bitte gib deine E-Mail-Adresse und dein gültiges Geburtsdatum ein.',
    ], 422);
}

$pdo = participate_pdo();
$participant = participate_participant_by_identity($pdo, $email, $dateOfBirth);
if ($participant === null) {
    participate_json([
        'ok' => false,
        'code' => 'identity_not_found',
        'message' => 'Die Angaben konnten keiner aktiven Anmeldung zugeordnet werden. Bitte prüfe E-Mail-Adresse und Geburtsdatum.',
    ], 401);
}

participate_set_registration_cookie((string) $participant['public_token']);
participate_json([
    'ok' => true,
] + participate_public_participant_session($pdo, $participant));
