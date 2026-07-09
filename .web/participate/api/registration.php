<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    participate_json([
        'ok' => false,
        'message' => 'Diese Methode wird nicht unterstützt.',
    ], 405);
}

$token = participate_get_registration_token();
if ($token === null) {
    participate_json([
        'ok' => true,
        'registered' => false,
    ]);
}

$pdo = participate_pdo();
$statement = $pdo->prepare(
    "SELECT full_name, date_of_birth, email, slot_preference_key, slot_preference_label, created_at
     FROM participation_registrations
     WHERE public_token = :token AND status = 'received'
     LIMIT 1"
);
$statement->execute(['token' => $token]);
$registration = $statement->fetch();

if (!$registration) {
    participate_json([
        'ok' => true,
        'registered' => false,
    ]);
}

participate_json([
    'ok' => true,
    'registered' => true,
    'registration' => participate_public_registration($registration),
]);
