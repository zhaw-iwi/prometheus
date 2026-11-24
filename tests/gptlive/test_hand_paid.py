import copy
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
from hand_paid import PLAN, EXTENSION, SECOND_EXTENSION, THIRD_EXTENSION, validate, reserve, history, available_budget


class HandPaidBudgetTest(unittest.TestCase):
    def test_controlled_sensor_inputs_match_the_committed_schema(self):
        from jsonschema import Draft202012Validator, FormatChecker
        root = PLAN.parents[2]
        schema = json.loads((root / 'src/main/resources/hand/interaction-v1.schema.json').read_text(encoding='utf-8'))
        script = "import * as f from './tests/gptlive/hand_fixtures.mjs';console.log(JSON.stringify({signObservation:[f.sign()],poseObservation:[f.custom(),f.ring(),f.count(3)],pointObservation:[f.point(),f.point(null)],sceneConfiguration:[f.sceneConfig(),f.sceneConfig(true)],sceneTargets:[f.targets({ambiguous:true})]}));"
        fixtures = json.loads(subprocess.check_output(['node', '--input-type=module', '-e', script], cwd=root, text=True, encoding='utf-8'))
        for name, values in fixtures.items():
            for value in values:
                Draft202012Validator({**schema, '$ref': '#/$defs/' + name}, format_checker=FormatChecker()).validate(value)

    def test_current_priced_routes_fit_the_reserve_and_changed_bounds_fail_closed(self):
        plan = json.loads(PLAN.read_text(encoding='utf-8'))
        self.assertAlmostEqual(validate(plan, 'gpt-6.2-sol'), 4.6572)
        for revision in (None, 1, 3):
            changed = copy.deepcopy(plan); changed['acceptanceRevision'] = revision
            with self.assertRaisesRegex(ValueError, 'acceptance revision 2'): validate(changed, 'gpt-6.2-sol')
        for section, key, value in [('limits', 'sessions', 2), ('limits', 'outputSpeechCalls', 1),
                                    ('limits', 'sessionSeconds', 600), ('limits', 'backendRequests', 36), ('pricing', 'liveUsdPerMinute', .01),
                                    ('models', 'backend', 'unpriced'), ('models', 'live', 'gpt-live-1')]:
            changed = copy.deepcopy(plan); changed[section][key] = value
            with self.assertRaises(ValueError): validate(changed, 'gpt-6.2-sol')
        with self.assertRaises(ValueError): validate(plan, 'unpriced')
        for key, value in [('maximumReservedUsd', 201), ('maximumAttempts', 91),
                           ('extensionAuthorization', None), ('allocationRevision', 3), ('backendReasoningEffort', 'none'), ('reserveUsdPerAttempt', 1)]:
            changed = copy.deepcopy(plan); changed[key] = value
            with self.assertRaises(ValueError): validate(changed, 'gpt-6.2-sol')

    def test_all_88_prior_reservations_survive_and_five_dollar_runs_stop_before_200(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / 'hand.json'
            old={'authorization':'hand-interaction-2026-11-23-usd50','reservedUsd':186,
                 'attempts':[{'mode':'L-01','artifact':str(Path(directory)/str(i)),
                              'reservedUsd':4 if i < 3 else 2} for i in range(22)]}
            old['attempts'].extend({'mode':'L-08','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':2,'allocationRevision':4,'extensionAuthorization':EXTENSION} for i in range(22,28))
            old['attempts'].extend({'mode':'L-07','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':1,'allocationRevision':5,'extensionAuthorization':EXTENSION} for i in range(28,36))
            old['attempts'].extend({'mode':'L-07','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':1,'allocationRevision':6,'extensionAuthorization':SECOND_EXTENSION} for i in range(36,66))
            old['attempts'].extend({'mode':'L-07','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':1,'allocationRevision':7,'extensionAuthorization':THIRD_EXTENSION} for i in range(66,74))
            old['attempts'].extend({'mode':'L-07','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':7,'allocationRevision':8,'extensionAuthorization':THIRD_EXTENSION} for i in range(74,78))
            old['attempts'].extend({'mode':'L-07','artifact':str(Path(directory)/str(i)),
                                    'reservedUsd':5,'allocationRevision':9,'extensionAuthorization':THIRD_EXTENSION} for i in range(78,88))
            shortened=copy.deepcopy(old)
            shortened['attempts'].pop(); shortened['reservedUsd']-=5
            ledger.write_text(json.dumps(shortened),encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Invalid hand ledger'): reserve('L-02', directory, ledger)
            self.assertEqual(shortened,json.loads(ledger.read_text(encoding='utf-8')))
            ledger.write_text(json.dumps(old),encoding='utf-8')
            for i in range(2):
                self.assertEqual(186+5*(i+1), reserve('L-02', Path(directory) / str(i+88), ledger)['reservedUsd'])
            with self.assertRaisesRegex(ValueError, 'exhausted'): reserve('L-02', directory, ledger)
            data=history(ledger)
            self.assertEqual(196, data['reservedUsd'])
            self.assertEqual(old['attempts'],data['attempts'][:88])
            self.assertTrue(all(a['extensionAuthorization']==EXTENSION for a in data['attempts'][22:36]))
            reduced=copy.deepcopy(data)
            reduced['attempts'][0]['reservedUsd']=2; reduced['reservedUsd']-=2
            ledger.write_text(json.dumps(reduced),encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Invalid hand ledger'): history(ledger)
            self.assertTrue(all(a['extensionAuthorization']==SECOND_EXTENSION for a in data['attempts'][36:66]))
            self.assertTrue(all(a['extensionAuthorization']==THIRD_EXTENSION for a in data['attempts'][66:]))
            del data['attempts'][36]['extensionAuthorization']
            ledger.write_text(json.dumps(data),encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Invalid hand ledger'): history(ledger)

    def test_missing_history_cannot_reopen_the_authorized_envelope(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'missing.json'
            with self.assertRaisesRegex(ValueError, 'ledger is missing'): reserve('L-01', directory, ledger)
            self.assertFalse(ledger.exists())
            ledger.write_text(json.dumps({'authorization':'hand-interaction-2026-11-23-usd50','attempts':[],'reservedUsd':0}),encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Invalid hand ledger'): reserve('L-01', directory, ledger)

    def test_foreign_corrupted_and_locked_ledgers_are_never_reset(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / 'hand.json'
            ledger.write_text('{"authorization":"old-repair","attempts":[],"reservedUsd":0}', encoding='utf-8')
            previous = ledger.read_bytes()
            with self.assertRaises(ValueError): reserve('L-01', directory, ledger)
            self.assertEqual(previous, ledger.read_bytes())
            ledger.unlink(); ledger.with_suffix('.lock').touch()
            with self.assertRaises(FileExistsError): reserve('L-01', directory, ledger)
            with self.assertRaises(ValueError): available_budget('commanding-hp1', ledger)


if __name__ == '__main__': unittest.main()
