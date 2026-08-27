<?php
declare(strict_types=1);

require_once __DIR__ . '/../database/generate_brainkick_seed.php';

$tempDirectory = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'brainkick_seed_' . bin2hex(random_bytes(6));
if (!mkdir($tempDirectory, 0700, true) && !is_dir($tempDirectory)) {
    fwrite(STDERR, "Could not create test directory\n");
    exit(1);
}

$csvPath = $tempDirectory . DIRECTORY_SEPARATOR . 'participants.csv';
$sqlPath = $tempDirectory . DIRECTORY_SEPARATOR . 'brainkick_seed.sql';
$csv = implode("\n", [
    implode(',', BRAINKICK_CSV_HEADERS),
    '42,CODE42,A,7,Morgen,11:45 - 13:00 Uhr,A',
    '4,NULL,NULL,Reserve,Morgen,NULL,NULL',
]) . "\n";

try {
    file_put_contents($csvPath, $csv);
    $count = brainkick_generate_seed($csvPath, $sqlPath);
    $sql = file_get_contents($sqlPath);
    if ($count !== 2) {
        throw new RuntimeException('Expected two generated assignment rows.');
    }
    if (!is_string($sql) || !str_contains($sql, "(4, NULL, NULL, 'Reserve', 'Morgen', NULL, NULL)")) {
        throw new RuntimeException('Literal NULL values were not converted into SQL NULL values.');
    }
    if (strpos($sql, '(4, ') > strpos($sql, '(42, ')) {
        throw new RuntimeException('Generated assignments are not deterministically sorted by participant ID.');
    }
    if (!str_contains($sql, 'UPDATE participation_assignments a')
        || !str_contains($sql, 'WHERE a.registration_id IS NULL;')) {
        throw new RuntimeException('Generated seed is not repeatable.');
    }
} catch (Throwable $exception) {
    fwrite(STDERR, $exception->getMessage() . PHP_EOL);
    exit(1);
} finally {
    foreach ([$csvPath, $sqlPath] as $file) {
        if (is_file($file)) {
            unlink($file);
        }
    }
    if (is_dir($tempDirectory)) {
        rmdir($tempDirectory);
    }
}

echo "Brainkick seed generator tests passed\n";
