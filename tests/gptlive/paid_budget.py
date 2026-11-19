"""Local cumulative reservations for the explicitly authorized repair smoke tests."""
from pathlib import Path
import json
import os


def reserve(ledger: Path, plan: dict, mode: str, artifact: Path):
    ledger.parent.mkdir(parents=True, exist_ok=True)
    lock = ledger.with_suffix('.lock')
    # Fail rather than silently race another paid runner or erase a stale reservation.
    descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    try:
        history = json.loads(ledger.read_text(encoding='utf-8')) if ledger.exists() else {'attempts': []}
        attempts = history['attempts']
        reserved = sum(entry['reservedUsd'] for entry in attempts)
        amount = plan.get('reserveUsdByMode', {}).get(mode, plan['reserveUsdPerAttempt'])
        if (len(attempts) >= plan['maximumAttempts'] or reserved + amount > plan['maximumReservedUsd']
                or reserved + amount >= 50):
            raise ValueError('Paid repair budget exhausted; no provider call is permitted')
        attempts.append({'mode': mode, 'artifact': str(artifact), 'reservedUsd': amount})
        history['reservedUsd'] = reserved + amount
        temporary = ledger.with_suffix('.tmp')
        temporary.write_text(json.dumps(history, indent=2), encoding='utf-8')
        temporary.replace(ledger)
        return history
    finally:
        os.close(descriptor)
        lock.unlink()
