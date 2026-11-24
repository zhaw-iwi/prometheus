"""Fail-closed $50 continuation from the immutable 88-attempt/$186 checkpoint. Importing this module never bills."""
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / 'tests/gptlive/hand-paid-plan.json'
LEDGER = ROOT / 'target/hand-interaction-budget.json'
CHECKPOINT = ROOT / '.agents/checkpoints/hand-interaction-2026-11-24'
MODES = tuple(f'L-{i:02d}' for i in range(1, 10)) + ('L-09-comparison',)
AUTHORIZATION = 'hand-interaction-2026-11-23-usd50'
EXTENSION = 'hand-interaction-2026-11-24-usd20-extension'
SECOND_EXTENSION = 'hand-interaction-2026-11-24-usd30-extension'
THIRD_EXTENSION = 'hand-interaction-2026-11-24-usd100-extension'
CONTINUATION = 'hand-interaction-2026-11-24-live-continuation-usd50'
LIMITS = dict(backendRequests=10, requestBytes=80000, inputFramingTokenAllowance=2048,
              completionTokensPerRequest=2048, reportedTokens=250000, sessions=1,
              sessionSeconds=360, journeySeconds=390, visitorTurns=12,
              nativeInputAudioSeconds=360, nativeOutputAudioSeconds=360, nativeTextBytes=250000,
              stimulusCalls=0, outputSpeechCalls=0, gradingCalls=0)


def validate(plan, model):
    if plan.get('acceptanceRevision') != 2:
        raise ValueError('Use hand acceptance revision 2; preserve earlier failures')
    if (plan.get('schema') != 'hand-paid-plan-v1' or plan.get('authorized') is not True
            or plan.get('authorization') != AUTHORIZATION or plan.get('maximumAttempts') != 98
            or plan.get('maximumReservedUsd') != 236 or plan.get('reserveUsdPerAttempt') != 5
            or plan.get('allocationRevision') != 10 or plan.get('extensionAuthorization') != CONTINUATION):
        raise ValueError('Unreviewed hand authorization')
    if plan.get('models') != {'backend': 'gpt-6.2-sol', 'live': 'gpt-live-1.1'} or model != 'gpt-6.2-sol' or plan.get('backendReasoningEffort') != 'low':
        raise ValueError('Unpriced hand model')
    if plan.get('limits') != LIMITS:
        raise ValueError('Missing or unreviewed hand bounds')
    if plan.get('pricingChecked') != '2026-11-24' or plan.get('pricing') != dict(
            backendInputUsdPerMillion=2, backendOutputUsdPerMillion=10, cacheWriteMultiplier=2.5,
            liveUsdPerMinute=.05, liveBilling='session_duration', liveCostedSeconds=420):
        raise ValueError('Unreviewed hand prices')
    # One token per UTF-8 input byte plus framing; longest listed cache-write rate.
    # GPT-Live bills elapsed session seconds, covering concurrent input/output audio/text.
    worst = 10 * ((80000 + 2048) * 2 * 2.5 + 2048 * 10) / 1e6 + 7 * .05
    if worst >= 5:
        raise ValueError('Attempt exceeds reservation')
    return worst


def _allocation_for(index):
    if index >= 88: return 10, CONTINUATION
    if index < 28: return 4, EXTENSION
    if index < 36: return 5, EXTENSION
    if index < 66: return 6, SECOND_EXTENSION
    return (7 if index < 74 else 8 if index < 78 else 9), THIRD_EXTENSION


def checkpoint_history():
    base = json.loads((CHECKPOINT / 'ledger.json').read_text(encoding='utf-8'))
    evidence = json.loads((CHECKPOINT / 'evidence.json').read_text(encoding='utf-8'))
    canonical = json.dumps(base, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()
    if hashlib.sha256(canonical).hexdigest() != evidence['ledgerCanonicalSha256']:
        raise ValueError('Invalid original hand checkpoint')
    extension = json.loads((CHECKPOINT / 'live-continuation.json').read_text(encoding='utf-8'))
    extra = extension['reservations']
    if (extension['baseAttempts'] != len(base['attempts']) or extension['baseReservedUsd'] != base['reservedUsd']
            or extension['continuationCapUsd'] != 50 or len(extra) > 10
            or extension['cumulativeReservedUsd'] != base['reservedUsd'] + sum(a['reservedUsd'] for a in extra)):
        raise ValueError('Invalid hand continuation checkpoint')
    return {**base, 'attempts': base['attempts'] + extra, 'reservedUsd': extension['cumulativeReservedUsd']}


def restore_checkpoint(ledger=LEDGER):
    if not ledger.exists():
        saved = checkpoint_history()
        ledger.parent.mkdir(parents=True, exist_ok=True)
        with ledger.open('x', encoding='utf-8') as stream:
            json.dump(saved, stream, indent=2)
            stream.write('\n')
    return history(ledger)


def history(ledger=LEDGER):
    if not ledger.exists():
        raise ValueError('Required hand ledger is missing; never replace or reset prior reservations')
    data = json.loads(ledger.read_text(encoding='utf-8'))
    attempts = data.get('attempts')
    if (data.get('authorization') != AUTHORIZATION or not isinstance(attempts, list)
            or not 88 <= len(attempts) <= 98 or any(a.get('mode') not in MODES
                                       or a.get('reservedUsd') != (4 if i<3 else 2 if i<28 else 1 if i<74 else 7 if i<78 else 5)
                                       or not a.get('artifact')
                                       or i >= 22 and (a.get('allocationRevision'), a.get('extensionAuthorization')) != _allocation_for(i)
                                       for i,a in enumerate(attempts))
            or data.get('reservedUsd') != sum(a['reservedUsd'] for a in attempts)
            or data.get('reservedUsd') > 236):
        raise ValueError('Invalid hand ledger; never replace or reset it')
    saved = checkpoint_history()['attempts']
    if attempts[:len(saved)] != saved or sum(a['reservedUsd'] for a in attempts[88:]) > 50:
        raise ValueError('Invalid hand ledger: checkpoint prefix or continuation cap')
    return data


def available_budget(mode, ledger=LEDGER):
    data = history(ledger)
    if mode not in MODES or len(data['attempts']) >= 98 or data['reservedUsd'] + 5 > 236:
        raise ValueError('Hand budget exhausted or invalid selection; no provider call permitted')
    return data


def reserve(mode, artifact, ledger=LEDGER):
    ledger.parent.mkdir(parents=True, exist_ok=True)
    lock = ledger.with_suffix('.lock')
    descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    try:
        data = available_budget(mode, ledger)
        data['attempts'].append({'mode': mode, 'artifact': str(Path(artifact).resolve()), 'reservedUsd': 5, 'allocationRevision': 10, 'extensionAuthorization': CONTINUATION})
        data['reservedUsd'] += 5
        temporary = ledger.with_suffix('.tmp')
        temporary.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        temporary.replace(ledger)
        return data
    finally:
        os.close(descriptor)
        lock.unlink()


def available_models(key, plan):
    request = Request('https://api.openai.com/v1/models', headers={'Authorization': 'Bearer ' + key.removeprefix('Bearer ')})
    with urlopen(request, timeout=20) as response:
        models = {entry['id'] for entry in json.load(response)['data']}
    if not set(plan['models'].values()).issubset(models):
        raise ValueError('Configured hand models are unavailable for this account')


def validate_audio(root=ROOT):
    directory = Path(root) / 'target/hand-interaction-audio'
    data = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    texts = json.loads((Path(root) / 'tests/gptlive/hand-cases.json').read_text(encoding='utf-8'))
    if data.get('providerCalls') != 0 or data.get('sampleRate') != 24000 or {
            name: clip['text'] for name, clip in data['clips'].items()} != texts:
        raise ValueError('Unreviewed hand stimulus')
    for clip in data['clips'].values():
        file = directory / clip['file']
        if (file.resolve().parent != directory.resolve() or not file.is_file()
                or file.stat().st_size != clip['bytes'] or not 0 < clip['bytes'] <= 2160000
                or hashlib.sha256(file.read_bytes()).hexdigest() != clip['sha256']):
            raise ValueError('Missing, changed or oversized local hand audio')
    return data
