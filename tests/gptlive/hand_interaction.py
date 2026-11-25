"""Hand-interaction acceptance selection. No provider or database access on import."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'tests/hand-interaction/manifest.json'
JOURNEYS = tuple(f'L-{number:02d}' for number in range(1, 10))
SELECTIONS = ('contracts', 'baseline', *JOURNEYS, 'L-06-visitor')
BASELINE_JAVA = ','.join(('CoordinationHandPoseUnitTest', 'CoordinationObservationTimingUnitTest',
    'TaskHandConfidenceUnitTest', 'HandPoseTaskUnitTest', 'CoordinationAgentUnitTest',
    'CoordinationAgentIntegrationTest', 'LiveQueryBudgetIntegrationTest'))
BASELINE_BROWSER = 'Coordination Text and Continuous preserve|native Coordination final contribution reaches Reflection'


def manifest():
    data = json.loads(MANIFEST.read_text(encoding='utf-8'))
    if data.get('schema') != 'hand-interaction-acceptance-v1' or data.get('baselineIsAcceptance') is not False:
        raise ValueError('Invalid hand interaction acceptance manifest')
    if [case['id'] for case in data['journeys']] != list(JOURNEYS):
        raise ValueError('Missing or reordered hand interaction journey')
    if (data.get('acceptanceRevision') != 2 or data.get('modeContract') != {
            'decisionDate': '2026-11-24',
            'live': {'speechOwner': 'gpt-live', 'strictSilence': 'unsupported', 'backendPlaybackApproval': False},
            'textContinuous': {'speechOwner': 'backend', 'strictSilence': 'required'}}
            or [case['id'] for case in data.get('modeChecks', [])] != ['M-01', 'M-02']):
        raise ValueError('Unreviewed hand mode acceptance contract')
    budget = data['budget']
    if (budget.get('authorization') != 'hand-interaction-2026-11-23-usd50'
            or budget.get('maximumReservedUsd') != 316 or budget.get('maximumAttempts') != 114
            or budget.get('extensionAuthorization') != 'hand-interaction-2026-11-25-l06-visitor-usd5'
            or budget.get('reserveUsdPerAttempt') != 5 or budget.get('allocationRevision') != 17):
        raise ValueError('Unreviewed hand interaction budget')
    if any(not case.get('milestones') or not case.get('expected') for case in data['journeys']):
        raise ValueError('Journey requires milestone and outcome evidence')
    return data


def validate_contracts():
    # Test-only dependency; ordinary/legacy acceptance does not require jsonschema.
    from jsonschema import Draft202012Validator, FormatChecker
    schema = json.loads((ROOT / 'src/main/resources/hand/interaction-v1.schema.json').read_text(encoding='utf-8'))
    examples = json.loads((ROOT / 'tests/hand-interaction/examples.json').read_text(encoding='utf-8'))
    Draft202012Validator.check_schema(schema)
    for name, value in examples.items():
        validator = Draft202012Validator({**schema, '$ref': '#/$defs/' + name}, format_checker=FormatChecker())
        validator.validate(value)
    manifest()
    return len(examples)


def configure(args):
    """Validate selection before config reads, database provisioning or paid calls."""
    if getattr(args, 'hand_comparison', False) and (args.hand_interaction != 'L-09' or args.paid_core != 'hand-live'):
        raise ValueError('Hand comparison requires L-09 with --paid-core hand-live')
    journey = 'L-06' if args.hand_interaction == 'L-06-visitor' else args.hand_interaction
    if args.paid_core == 'hand-live' and journey not in JOURNEYS:
        raise ValueError('Paid hand mode requires an explicit L-01..09 selection')
    if not args.hand_interaction:
        return False
    if (args.paid_core not in (None, 'hand-live') or args.classroom_capacity or args.embodiment or args.commanding_retests
            or args.commanding_hp3_retest or args.coordination_scenario != 'baseline'):
        raise ValueError('Hand interaction selection cannot borrow another acceptance or paid mode')
    manifest()
    if journey in JOURNEYS:
        if args.paid_core != 'hand-live':
            raise ValueError('Hand Live journeys require --paid-core hand-live; no provider access is permitted')
        if args.browser or args.browser_grep or args.browser_mode != 'both' or args.java_tests not in ('none','LiveCockpitSmokeIntegrationTest'):
            raise ValueError('Paid hand selection cannot mix mocked browser or Java runs')
        if not manifest()['budget']['pricingVerified'] or not next(c for c in manifest()['journeys'] if c['id']==journey)['providerReady']:
            raise ValueError('Hand journey preflight is not ready; no provider access is permitted')
        args.java_tests = 'none'
        return False
    if args.hand_interaction == 'contracts':
        if (args.browser or args.java_tests != 'LiveCockpitSmokeIntegrationTest' or args.live_only
                or args.browser_grep or args.browser_mode != 'both'):
            raise ValueError('Contracts-only selection cannot silently discard browser/Java checks')
        print(f'Hand interaction contracts: {validate_contracts()} examples validated; no provider/database access.')
        return True
    if args.java_tests != 'LiveCockpitSmokeIntegrationTest' or args.browser_grep or args.browser_mode != 'both':
        raise ValueError('Hand interaction baseline owns its Java/browser selection')
    args.java_tests = BASELINE_JAVA
    args.browser = True
    args.browser_mode = 'enabled'
    args.browser_grep = BASELINE_BROWSER
    args.live_only = True
    validate_contracts()
    return False
