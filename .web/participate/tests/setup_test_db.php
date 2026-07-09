<?php
declare(strict_types=1);

putenv('PARTICIPATE_ENV_FILE=' . __DIR__ . '/../.env.test');
$_ENV['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';
$_SERVER['PARTICIPATE_ENV_FILE'] = __DIR__ . '/../.env.test';

require_once __DIR__ . '/../config/bootstrap.php';

$database = participate_env('DB_DATABASE', 'sira_participate_test');
$pdo = participate_pdo('');
$pdo->exec('DROP DATABASE IF EXISTS `' . str_replace('`', '``', $database) . '`');
$pdo->exec('CREATE DATABASE `' . str_replace('`', '``', $database) . '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
$pdo->exec('USE `' . str_replace('`', '``', $database) . '`');

foreach (['database/schema.sql', 'database/seed.sql'] as $file) {
    $sql = file_get_contents(participate_base_path($file));
    if ($sql === false) {
        fwrite(STDERR, "Could not read {$file}\n");
        exit(1);
    }
    $pdo->exec($sql);
}

$mailDir = participate_resolve_path(participate_env('MAIL_LOG_PATH', '.tmp/mail') ?? '.tmp/mail');
if (is_dir($mailDir)) {
    foreach (glob($mailDir . DIRECTORY_SEPARATOR . '*.eml') ?: [] as $mailFile) {
        unlink($mailFile);
    }
}

echo "Prepared {$database}\n";
