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

$fullName = trim((string) ($payload['fullName'] ?? ''));
$dateOfBirth = trim((string) ($payload['dateOfBirth'] ?? ''));
$email = trim((string) ($payload['email'] ?? ''));
$slotPreference = trim((string) ($payload['slotPreference'] ?? ''));

if ($fullName === '') {
    participate_json(['ok' => false, 'message' => 'Bitte gib deinen vollständigen Namen ein.'], 422);
}
if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $dateOfBirth)) {
    participate_json(['ok' => false, 'message' => 'Bitte gib dein Geburtsdatum ein.'], 422);
}
[$year, $month, $day] = array_map('intval', explode('-', $dateOfBirth));
if (!checkdate($month, $day, $year)) {
    participate_json(['ok' => false, 'message' => 'Bitte gib ein gültiges Geburtsdatum ein.'], 422);
}
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    participate_json(['ok' => false, 'message' => 'Bitte gib eine gültige E-Mail-Adresse ein.'], 422);
}
if ($slotPreference === '') {
    participate_json(['ok' => false, 'message' => 'Bitte wähle eine Terminpräferenz aus.'], 422);
}

$pdo = participate_pdo();
$defaultPhase = participate_default_phase($pdo);
if ($defaultPhase !== PARTICIPATE_PHASE_SIGNUP) {
    participate_json([
        'ok' => false,
        'code' => 'signup_closed',
        'message' => 'Die Anmeldung für diese Studie ist geschlossen.',
    ], 409);
}
$emailNormalized = participate_normalize_email($email);

try {
    $pdo->beginTransaction();

    $slotStatement = $pdo->prepare(
        'SELECT id, slot_key, label, capacity
         FROM participation_slots
         WHERE slot_key = :slot_key AND is_active = 1
         LIMIT 1
         FOR UPDATE'
    );
    $slotStatement->execute(['slot_key' => $slotPreference]);
    $slot = $slotStatement->fetch();

    if (!$slot) {
        $pdo->rollBack();
        participate_json(['ok' => false, 'message' => 'Diese Terminoption ist nicht verfügbar.'], 422);
    }

    if ($slot['capacity'] !== null) {
        $countStatement = $pdo->prepare(
            "SELECT COUNT(*)
             FROM participation_registrations
             WHERE slot_id = :slot_id AND status = 'received'"
        );
        $countStatement->execute(['slot_id' => $slot['id']]);
        $count = (int) $countStatement->fetchColumn();
        if ($count >= (int) $slot['capacity']) {
            $pdo->rollBack();
            participate_json([
                'ok' => false,
                'message' => 'Diese Terminoption ist bereits vollständig belegt. Bitte wähle eine andere Option.',
            ], 409);
        }
    }

    $token = bin2hex(random_bytes(32));
    $insert = $pdo->prepare(
        'INSERT INTO participation_registrations
          (public_token, full_name, date_of_birth, email, email_normalized, slot_id,
           slot_preference_key, slot_preference_label, ip_address, user_agent)
         VALUES
          (:public_token, :full_name, :date_of_birth, :email, :email_normalized, :slot_id,
           :slot_preference_key, :slot_preference_label, :ip_address, :user_agent)'
    );
    $insert->execute([
        'public_token' => $token,
        'full_name' => $fullName,
        'date_of_birth' => $dateOfBirth,
        'email' => $email,
        'email_normalized' => $emailNormalized,
        'slot_id' => $slot['id'],
        'slot_preference_key' => $slot['slot_key'],
        'slot_preference_label' => $slot['label'],
        'ip_address' => $_SERVER['REMOTE_ADDR'] ?? null,
        'user_agent' => substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 2000),
    ]);

    $registrationId = (int) $pdo->lastInsertId();
    $pdo->commit();
} catch (PDOException $exception) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    if ($exception->getCode() === '23000') {
        participate_json([
            'ok' => false,
            'code' => 'duplicate_registration',
            'message' => 'Für diese E-Mail-Adresse liegt bereits eine Anmeldung vor. Wenn du glaubst, dass etwas schiefgelaufen ist, oder wenn du eine Frage hast, kontaktiere bitte alexandre.despindler@zhaw.ch.',
        ], 409);
    }
    throw $exception;
}

$participant = participate_participant_by_id($pdo, $registrationId);
if ($participant === null) {
    throw new RuntimeException('The saved participation registration could not be reloaded.');
}
$registration = participate_public_registration($participant);
$session = participate_public_participant_session($pdo, $participant, $defaultPhase);

participate_set_registration_cookie($token);
$mailSent = participate_send_confirmation_mail($registration);

participate_json([
    'ok' => true,
    'mailSent' => $mailSent,
] + $session, 201);
