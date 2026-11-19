"""Fail-closed preflight for the separately authorized commanding envelope."""
import json
import hashlib
from pathlib import Path
from urllib.request import Request, urlopen

MODES = ('commanding-hp1', 'commanding-hp2', 'commanding-hp3', 'commanding-baseline', 'commanding-ordinary')
ENVELOPES = {
    'commanding-paid-plan-v1': (8, 40, 'commanding-2026-11-19-usd40', None),
    'commanding-retests-plan-v1': (2, 10, 'commanding-retests-2026-11-19-usd10', {'commanding-hp2':1, 'commanding-hp3':1}),
    'commanding-hp3-retest-plan-v1': (1, 5, 'commanding-hp3-retest-2026-11-19-usd5', {'commanding-hp3':1}),
}

def envelope(plan=None):
    schema = plan.get('schema') if plan is not None else 'commanding-paid-plan-v1'
    if schema not in ENVELOPES:
        raise ValueError('Missing commanding authorization')
    return ENVELOPES[schema]

def validate(plan, model):
    attempts, dollars, authorization, by_mode = envelope(plan)
    if plan.get('authorized') is not True:
        raise ValueError('Missing commanding authorization')
    if plan.get('maximumAttempts') != attempts or plan.get('maximumReservedUsd') != dollars or plan.get('reserveUsdPerAttempt') != 5:
        raise ValueError('Unreviewed commanding envelope')
    if plan.get('authorization', 'commanding-2026-11-19-usd40') != authorization or plan.get('maximumAttemptsByMode') != by_mode:
        raise ValueError('Unreviewed retest authorization')
    if plan.get('models') != {'backend': 'gpt-5.6-luna', 'live': 'gpt-live-1'} or model != plan['models']['backend']:
        raise ValueError('Unpriced commanding model')
    expected = {'backendRequests':36,'requestBytes':80000,'completionTokensPerRequest':2048,'reportedTokens':250000,
                'sessions':1,'sessionSeconds':360,'journeySeconds':390,'visitorTurns':12,'stimulusCalls':0,'outputSpeechCalls':0}
    if plan.get('limits') != expected:
        raise ValueError('Missing or unreviewed commanding bounds')
    prices=plan['pricing']
    if prices != {'backendInputUsdPerMillion':.2,'backendOutputUsdPerMillion':1.2,'cacheWriteMultiplier':1.25,'liveUsdPerMinute':.05}:
        raise ValueError('Unreviewed prices')
    worst = 36*(80000*.2*1.25+2048*1.2)/1e6+6*.05
    if worst >= plan['reserveUsdPerAttempt']:
        raise ValueError('Attempt exceeds reserve')
    return worst

def available(key, plan):
    request=Request('https://api.openai.com/v1/models',headers={'Authorization':'Bearer '+key.removeprefix('Bearer ')})
    with urlopen(request,timeout=20) as response:
        models={entry['id'] for entry in json.load(response)['data']}
    if not set(plan['models'].values()).issubset(models):
        raise ValueError('Configured commanding models are unavailable for this account')

def validate_audio(root):
    manifest=Path(root)/'target/commanding-audio/manifest.json'
    data=json.loads(manifest.read_text(encoding='utf-8-sig'))
    if data.get('providerCalls') != 0 or data.get('sampleRate') != 24000:
        raise ValueError('Unreviewed stimulus source')
    expected=json.loads((Path(root)/'tests/gptlive/commanding-cases.json').read_text(encoding='utf-8'))
    if {name:c['text'] for name,c in data['clips'].items()} != expected:
        raise ValueError('Stimulus text differs from reviewed cases')
    for clip in data['clips'].values():
        file=manifest.parent/clip['file']
        if file.resolve().parent != manifest.parent.resolve() or not file.is_file() or file.stat().st_size != clip['bytes'] or not 0 < clip['bytes'] <= 7200000:
            raise ValueError('Missing/bounded local PCM required')
        if hashlib.sha256(file.read_bytes()).hexdigest() != clip['sha256']:
            raise ValueError('Stimulus hash mismatch')
    return data

def validate_ledger(ledger, plan=None, mode=None):
    data=json.loads(Path(ledger).read_text(encoding='utf-8'))
    attempts, dollars, authorization, by_mode = envelope(plan)
    modes = by_mode if by_mode is not None else MODES
    if data.get('authorization') != authorization or not isinstance(data.get('attempts'),list):
        raise ValueError('Wrong commanding ledger')
    if len(data['attempts']) >= attempts or sum(a['reservedUsd'] for a in data['attempts'])+5 > dollars:
        raise ValueError('Commanding budget exhausted')
    if any(a['reservedUsd'] != 5 or a['mode'] not in modes for a in data['attempts']):
        raise ValueError('Invalid commanding reservation')
    if mode is not None and (mode not in modes or by_mode is not None and any(a['mode'] == mode for a in data['attempts'])):
        raise ValueError('Retest selection is not authorized or already reserved')
    return data
