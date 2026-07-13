<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    participate_json([
        'ok' => false,
        'message' => 'Diese Methode wird nicht unterstützt.',
    ], 405);
}

$payload = participate_read_json();
$id = filter_var($payload['id'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);

if ($id === false || $id === null) {
    participate_json([
        'ok' => false,
        'message' => 'Die Anmeldung konnte nicht eindeutig bestimmt werden.',
    ], 422);
}

$pdo = participate_pdo();
$statement = $pdo->prepare('DELETE FROM participation_registrations WHERE id = :id');
$statement->execute(['id' => $id]);

if ($statement->rowCount() === 0) {
    participate_json([
        'ok' => false,
        'message' => 'Diese Anmeldung wurde nicht gefunden.',
    ], 404);
}

participate_json([
    'ok' => true,
    'deleted' => true,
    'id' => $id,
]);
