INSERT INTO participation_phase_settings (id, default_phase)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO participation_slots
  (slot_key, label, starts_at, ends_at, capacity, is_active, sort_order)
VALUES
  (
    '2026-08-17-morning',
    'Montag, 17. August 2026, 09:00 bis 13:00',
    '2026-08-17 09:00:00',
    '2026-08-17 13:00:00',
    64,
    1,
    10
  ),
  (
    '2026-08-17-afternoon',
    'Montag, 17. August 2026, 13:00 bis 17:00',
    '2026-08-17 13:00:00',
    '2026-08-17 17:00:00',
    64,
    1,
    20
  ),
  (
    'unavailable',
    'Ich will gerne teilnehmen, aber diese Termine passen mir nicht',
    NULL,
    NULL,
    NULL,
    1,
    30
  )
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  starts_at = VALUES(starts_at),
  ends_at = VALUES(ends_at),
  capacity = VALUES(capacity),
  is_active = VALUES(is_active),
  sort_order = VALUES(sort_order);
