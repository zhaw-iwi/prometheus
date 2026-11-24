import copy
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
from hand_paid import PLAN, validate, reserve, history, available_budget


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
        self.assertLess(validate(plan, 'gpt-5.6-luna'), 2)
        for revision in (None, 1, 3):
            changed = copy.deepcopy(plan); changed['acceptanceRevision'] = revision
            with self.assertRaisesRegex(ValueError, 'acceptance revision 2'): validate(changed, 'gpt-5.6-luna')
        for section, key, value in [('limits', 'sessions', 2), ('limits', 'outputSpeechCalls', 1),
                                    ('limits', 'sessionSeconds', 600), ('pricing', 'liveUsdPerMinute', .01),
                                    ('models', 'backend', 'unpriced')]:
            changed = copy.deepcopy(plan); changed[section][key] = value
            with self.assertRaises(ValueError): validate(changed, 'gpt-5.6-luna')
        with self.assertRaises(ValueError): validate(plan, 'unpriced')

    def test_failed_legacy_reservations_survive_and_revised_allocation_stops_at_50(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / 'hand.json'
            old={'authorization':'hand-interaction-2026-11-23-usd50','reservedUsd':12,
                 'attempts':[{'mode':'L-01','artifact':str(Path(directory)/str(i)),'reservedUsd':4} for i in range(3)]}
            ledger.write_text(json.dumps(old),encoding='utf-8')
            for i in range(19):
                self.assertEqual(12+(i + 1) * 2, reserve('L-02', Path(directory) / str(i+3), ledger)['reservedUsd'])
            with self.assertRaisesRegex(ValueError, 'exhausted'): reserve('L-02', directory, ledger)
            self.assertEqual(50, history(ledger)['reservedUsd'])
            self.assertEqual(old['attempts'],history(ledger)['attempts'][:3])

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
