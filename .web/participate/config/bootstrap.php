<?php
declare(strict_types=1);

require_once __DIR__ . '/phases.php';

const PARTICIPATE_COOKIE_NAME = 'sira_participate_registration';

function participate_base_path(string $path = ''): string
{
    $base = dirname(__DIR__);
    if ($path === '') {
        return $base;
    }
    return $base . DIRECTORY_SEPARATOR . ltrim($path, DIRECTORY_SEPARATOR . '/');
}

function participate_is_absolute_path(string $path): bool
{
    return $path !== '' && (
        $path[0] === '/' ||
        preg_match('/^[A-Za-z]:[\\\\\\/]/', $path) === 1
    );
}

function participate_resolve_path(string $path): string
{
    if (participate_is_absolute_path($path)) {
        return $path;
    }
    $cwdPath = getcwd() . DIRECTORY_SEPARATOR . $path;
    if (file_exists($cwdPath)) {
        return $cwdPath;
    }
    return participate_base_path($path);
}

function participate_load_env(?string $path = null): void
{
    static $loaded = [];

    $envPath = $path ?: (getenv('PARTICIPATE_ENV_FILE') ?: participate_base_path('.env'));
    $envPath = participate_resolve_path((string) $envPath);

    if (isset($loaded[$envPath])) {
        return;
    }
    $loaded[$envPath] = true;

    if (!is_file($envPath)) {
        return;
    }

    $lines = file($envPath, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    if ($lines === false) {
        return;
    }

    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) {
            continue;
        }
        if (str_starts_with($line, 'export ')) {
            $line = trim(substr($line, 7));
        }
        $separator = strpos($line, '=');
        if ($separator === false) {
            continue;
        }

        $key = trim(substr($line, 0, $separator));
        $value = trim(substr($line, $separator + 1));

        if ($key === '' || preg_match('/^[A-Z0-9_]+$/i', $key) !== 1) {
            continue;
        }

        if (
            (str_starts_with($value, '"') && str_ends_with($value, '"')) ||
            (str_starts_with($value, "'") && str_ends_with($value, "'"))
        ) {
            $value = substr($value, 1, -1);
        }

        putenv($key . '=' . $value);
        $_ENV[$key] = $value;
        $_SERVER[$key] = $value;
    }
}

function participate_env(string $key, ?string $default = null): ?string
{
    participate_load_env();

    $value = getenv($key);
    if ($value === false && array_key_exists($key, $_ENV)) {
        $value = $_ENV[$key];
    }
    if ($value === false && array_key_exists($key, $_SERVER)) {
        $value = $_SERVER[$key];
    }
    if ($value === false || $value === '') {
        return $default;
    }
    return (string) $value;
}

function participate_pdo(?string $database = null): PDO
{
    $host = participate_env('DB_HOST', '127.0.0.1');
    $port = participate_env('DB_PORT', '3306');
    $charset = participate_env('DB_CHARSET', 'utf8mb4');
    $dbName = $database ?? participate_env('DB_DATABASE', '');
    $dbPart = $dbName !== '' ? ';dbname=' . $dbName : '';

    return new PDO(
        "mysql:host={$host};port={$port}{$dbPart};charset={$charset}",
        participate_env('DB_USERNAME', 'root'),
        participate_env('DB_PASSWORD', ''),
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
}

function participate_json(array $payload, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=UTF-8');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function participate_read_json(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') {
        return [];
    }

    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        participate_json([
            'ok' => false,
            'message' => 'Die Anfrage konnte nicht gelesen werden.',
        ], 400);
    }

    return $decoded;
}

function participate_normalize_email(string $email): string
{
    return strtolower(trim($email));
}

function participate_public_registration(array $row): array
{
    return [
        'fullName' => $row['full_name'],
        'dateOfBirth' => $row['date_of_birth'],
        'email' => $row['email'],
        'slotPreference' => $row['slot_preference_key'],
        'slotPreferenceLabel' => $row['slot_preference_label'],
        'submittedAt' => $row['created_at'],
    ];
}

function participate_set_registration_cookie(string $token): void
{
    $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
    setcookie(PARTICIPATE_COOKIE_NAME, $token, [
        'expires' => time() + 31536000,
        'path' => '/',
        'secure' => $secure,
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
}

function participate_get_registration_token(): ?string
{
    $token = $_COOKIE[PARTICIPATE_COOKIE_NAME] ?? null;
    if (!is_string($token) || preg_match('/^[a-f0-9]{64}$/', $token) !== 1) {
        return null;
    }
    return $token;
}

function participate_mailbox_list(string $value): array
{
    $raw = preg_split('/[,;]+/', $value) ?: [];
    $emails = [];
    foreach ($raw as $email) {
        $email = trim($email);
        if ($email !== '' && filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $emails[] = $email;
        }
    }
    return array_values(array_unique($emails));
}

function participate_mime_header(string $value): string
{
    return '=?UTF-8?B?' . base64_encode($value) . '?=';
}

function participate_send_confirmation_mail(array $registration): bool
{
    $from = participate_env('MAIL_FROM', 'no-reply@example.com');
    $fromName = participate_env('MAIL_FROM_NAME', 'SIRA Lab Studie');
    $adminNotify = participate_mailbox_list(participate_env('ADMIN_NOTIFY_EMAIL', '') ?? '');
    $subject = 'DO NOT REPLY - Bestätigung deiner Teilnahmeanfrage';
    $transport = participate_env('MAIL_TRANSPORT', 'mail');

    $body = implode("\n", [
        'DO NOT REPLY TO THIS MAIL',
        '',
        'Bitte antworte nicht direkt auf diese E-Mail.',
        'Wenn du Fragen hast, leite diese E-Mail bitte an alexandre.despindler@zhaw.ch weiter und schreibe deine Frage dazu.',
        '',
        'Hallo ' . $registration['fullName'],
        '',
        'wir haben deine Teilnahmeanfrage für die Studie zur Zusammenarbeit zwischen Menschen und KI erhalten.',
        '',
        'Zusammenfassung deiner Anfrage:',
        'Name: ' . $registration['fullName'],
        'Geburtsdatum: ' . $registration['dateOfBirth'],
        'E-Mail: ' . $registration['email'],
        'Terminpräferenz: ' . $registration['slotPreferenceLabel'],
        '',
        'Wir melden uns mit der finalen Einladung, sobald wir die einstündigen Slots zugeteilt haben.',
        'Du musst nur für ungefähr eine Stunde vor Ort sein, nicht für den ganzen Halbtag.',
        '',
        'Kontakt bei Fragen: alexandre.despindler@zhaw.ch',
        '',
        'ZHAW SIRA Lab',
    ]);

    $headers = [
        'MIME-Version: 1.0',
        'Content-Type: text/plain; charset=UTF-8',
        'From: ' . participate_mime_header($fromName ?? 'SIRA Lab Studie') . ' <' . $from . '>',
        'Reply-To: ' . $from,
        'X-Auto-Response-Suppress: All',
    ];
    if ($adminNotify !== []) {
        $headers[] = 'Bcc: ' . implode(', ', $adminNotify);
    }

    if ($transport === 'log') {
        $logDir = participate_resolve_path(participate_env('MAIL_LOG_PATH', '.tmp/mail') ?? '.tmp/mail');
        if (!is_dir($logDir) && !mkdir($logDir, 0775, true) && !is_dir($logDir)) {
            return false;
        }
        $file = $logDir . DIRECTORY_SEPARATOR . date('Ymd_His') . '_' . bin2hex(random_bytes(4)) . '.eml';
        $mail = implode("\n", [
            'To: ' . $registration['email'],
            'Subject: ' . $subject,
            implode("\n", $headers),
            '',
            $body,
        ]);
        return file_put_contents($file, $mail) !== false;
    }

    return mail(
        $registration['email'],
        participate_mime_header($subject),
        $body,
        implode("\r\n", $headers)
    );
}
