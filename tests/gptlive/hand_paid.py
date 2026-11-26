"""Fail-closed $170 continuation from the immutable 88-attempt/$186 checkpoint. Importing never bills."""
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / 'tests/gptlive/hand-paid-plan.json'
LEDGER = ROOT / 'target/hand-interaction-budget.json'
CHECKPOINT = ROOT / '.agents/checkpoints/hand-interaction-2026-11-24'
MODES = tuple(f'L-{i:02d}' for i in range(1, 10)) + ('L-09-comparison', 'L-06-visitor')
AUTHORIZATION = 'hand-interaction-2026-11-23-usd50'
EXTENSION = 'hand-interaction-2026-11-24-usd20-extension'
SECOND_EXTENSION = 'hand-interaction-2026-11-24-usd30-extension'
THIRD_EXTENSION = 'hand-interaction-2026-11-24-usd100-extension'
CONTINUATION = 'hand-interaction-2026-11-24-live-continuation-usd50'
CONTINUATION_EXTENSION = 'hand-interaction-2026-11-24-live-continuation-plus-usd20'
SECOND_CONTINUATION_EXTENSION = 'hand-interaction-2026-11-24-live-continuation-second-usd20'
L07_EXTENSION = 'hand-interaction-2026-11-24-l07-usd5'
L07_VERIFICATION = 'hand-interaction-2026-11-24-l07-verification-usd5'
WORKFLOW_EXTENSION = 'hand-interaction-2026-11-24-workflow-usd20'
L06_VERIFICATION = 'hand-interaction-2026-11-25-l06-verification-usd5'
L06_VISITOR = 'hand-interaction-2026-11-25-l06-visitor-usd5'
ROADMAP = 'hand-interaction-2026-11-25-roadmap-usd40'
OPEN_JOURNEYS = ('L-04', 'L-05', 'L-06', 'L-07', 'L-08')
LIMITS = dict(backendRequests=10, requestBytes=80000, inputFramingTokenAllowance=2048,
              completionTokensPerRequest=2048, reportedTokens=250000, sessions=1,
              sessionSeconds=360, journeySeconds=390, visitorTurns=12,
              nativeInputAudioSeconds=360, nativeOutputAudioSeconds=360, nativeTextBytes=250000,
              stimulusCalls=0, outputSpeechCalls=0, gradingCalls=0)


def validate(plan, model):
    if plan.get('acceptanceRevision') != 2:
        raise ValueError('Use hand acceptance revision 2; preserve earlier failures')
    if (plan.get('schema') != 'hand-paid-plan-v1' or plan.get('authorized') is not True
            or plan.get('authorization') != AUTHORIZATION or plan.get('maximumAttempts') != 122
            or plan.get('maximumReservedUsd') != 356 or plan.get('reserveUsdPerAttempt') != 5
            or plan.get('allocationRevision') != 18 or plan.get('extensionAuthorization') != ROADMAP):
        raise ValueError('Unreviewed hand authorization')
    if plan.get('limits') != LIMITS:
        raise ValueError('Missing or unreviewed hand bounds')
    validate_model_pricing(plan, model)
    # One token per UTF-8 input byte plus framing; longest listed cache-write rate.
    # GPT-Live bills elapsed session seconds, covering concurrent input/output audio/text.
    worst = 10 * ((80000 + 2048) * 2 * 2.5 + 2048 * 10) / 1e6 + 7 * .05
    if worst >= 5:
        raise ValueError('Attempt exceeds reservation')
    return worst


def validate_model_pricing(plan, model):
    if plan.get('models') != {'backend': 'gpt-6.2-sol', 'live': 'gpt-live-1.1'} or model != 'gpt-6.2-sol' or plan.get('backendReasoningEffort') != 'low':
        raise ValueError('Unpriced hand model')
    if plan.get('pricingChecked') != '2026-11-24' or plan.get('pricing') != dict(
            backendInputUsdPerMillion=2, backendOutputUsdPerMillion=10, cacheWriteMultiplier=2.5,
            liveUsdPerMinute=.05, liveBilling='session_duration', liveCostedSeconds=420):
        raise ValueError('Unreviewed hand prices')


def _allocation_for(index):
    if index >= 114: return 18, ROADMAP
    if index == 113: return 17, L06_VISITOR
    if index == 112: return 16, L06_VERIFICATION
    if index >= 108: return 15, WORKFLOW_EXTENSION
    if index == 107: return 14, L07_VERIFICATION
    if index == 106: return 13, L07_EXTENSION
    if index >= 97: return 12, SECOND_CONTINUATION_EXTENSION
    if index >= 96: return 11, CONTINUATION_EXTENSION
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
    attempts = base['attempts'] + extra
    reserved = extension['cumulativeReservedUsd']
    for filename, count, base_usd, increment, cap in [
        ('live-continuation-extension.json', 96, 226, 20, 70),
        ('live-continuation-extension-2.json', 97, 231, 20, 90),
        ('live-continuation-l07.json', 106, 276, 5, 95),
        ('live-continuation-l07-verification.json', 107, 281, 5, 100),
        ('live-continuation-workflow.json', 108, 286, 20, 120),
        ('live-continuation-l06-verification.json', 112, 306, 5, 125),
        ('live-continuation-l06-visitor.json', 113, 311, 5, 130),
        ('live-continuation-roadmap.json', 114, 316, 40, 170),
    ]:
        topup = json.loads((CHECKPOINT / filename).read_text(encoding='utf-8'))
        added = topup['reservations']
        if (len(attempts) != count or topup['baseAttempts'] != count or reserved != base_usd
                or topup['baseReservedUsd'] != base_usd or topup['addedCapUsd'] != increment
                or topup['continuationCapUsd'] != cap
                or topup['cumulativeReservedUsd'] != reserved + sum(a['reservedUsd'] for a in added)
                or topup['cumulativeReservedUsd'] > base['reservedUsd'] + cap):
            raise ValueError('Invalid hand continuation extension checkpoint')
        attempts = attempts + added
        reserved = topup['cumulativeReservedUsd']
    return {**base, 'attempts': attempts, 'reservedUsd': reserved}


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
            or not 114 <= len(attempts) <= 122 or any(a.get('mode') not in MODES
                                       or 106 <= i < 108 and a.get('mode') != 'L-07'
                                       or 108 <= i < 112 and a.get('mode') not in OPEN_JOURNEYS
                                       or i == 112 and a.get('mode') != 'L-06'
                                       or i in (113,114) and a.get('mode') != 'L-06-visitor'
                                       or a.get('reservedUsd') != (4 if i<3 else 2 if i<28 else 1 if i<74 else 7 if i<78 else 5)
                                       or not a.get('artifact')
                                       or i >= 22 and (a.get('allocationRevision'), a.get('extensionAuthorization')) != _allocation_for(i)
                                       for i,a in enumerate(attempts))
            or data.get('reservedUsd') != sum(a['reservedUsd'] for a in attempts)
            or data.get('reservedUsd') > 356):
        raise ValueError('Invalid hand ledger; never replace or reset it')
    saved = checkpoint_history()['attempts']
    if attempts[:len(saved)] != saved or sum(a['reservedUsd'] for a in attempts[88:]) > 170:
        raise ValueError('Invalid hand ledger: checkpoint prefix or continuation cap')
    return data


def available_budget(mode, ledger=LEDGER):
    data = history(ledger)
    if (mode not in MODES or len(data['attempts']) == 114 and mode != 'L-06-visitor'
            or len(data['attempts']) >= 122 or data['reservedUsd'] + 5 > 356):
        raise ValueError('Hand budget exhausted or invalid selection; no provider call permitted')
    return data


def reserve(mode, artifact, ledger=LEDGER):
    ledger.parent.mkdir(parents=True, exist_ok=True)
    lock = ledger.with_suffix('.lock')
    descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    try:
        data = available_budget(mode, ledger)
        revision, authorization = _allocation_for(len(data['attempts']))
        data['attempts'].append({'mode': mode, 'artifact': str(Path(artifact).resolve()), 'reservedUsd': 5,
                                 'allocationRevision': revision, 'extensionAuthorization': authorization})
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


def validate_audio(root=ROOT, *, forum=False):
    directory = Path(root) / ('target/forum-audio' if forum else 'target/hand-interaction-audio')
    data = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    cases = 'forum-cases.json' if forum else 'hand-cases.json'
    texts = json.loads((Path(root) / 'tests/gptlive' / cases).read_text(encoding='utf-8'))
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
