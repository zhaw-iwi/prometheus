import copy
import json
import subprocess
import sys
import unittest
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from hand_interaction import ROOT, JOURNEYS, manifest, validate_contracts


class HandInteractionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads((ROOT / 'src/main/resources/hand/interaction-v1.schema.json').read_text())
        cls.examples = json.loads((ROOT / 'tests/hand-interaction/examples.json').read_text())

    def validate(self, name, value):
        Draft202012Validator({**self.schema, '$ref': '#/$defs/' + name}, format_checker=FormatChecker()).validate(value)

    def test_all_example_boundaries_round_trip_without_loss(self):
        self.assertEqual(9, validate_contracts())
        for name, value in self.examples.items():
            with self.subTest(name=name):
                self.validate(name, json.loads(json.dumps(value)))
                invalid = {**value, 'untrustedInstruction': 'execute this'}
                with self.assertRaises(ValidationError): self.validate(name, invalid)

    def test_observations_do_not_accept_extra_hands_guessed_states_or_invalid_time(self):
        for name in ('signObservation', 'poseObservation'):
            for key, value in [('ts', 'yesterday'), ('observationId', '../other-agent'), ('schemaVersion', 99)]:
                with self.subTest(name=name, key=key):
                    invalid = {**self.examples[name], key: value}
                    with self.assertRaises(ValidationError): self.validate(name, invalid)
            invalid = copy.deepcopy(self.examples[name]); invalid['hands'] *= 3
            with self.assertRaises(ValidationError): self.validate(name, invalid)
        invalid = copy.deepcopy(self.examples['poseObservation'])
        invalid['hands'][0]['fingerStates']['index'] = 'unknown'
        with self.assertRaises(ValidationError): self.validate('poseObservation', invalid)
        invalid['hands'][0]['fingerStates']['index'] = None
        invalid['hands'][0]['fingers']['index'] = None
        self.validate('poseObservation', invalid)

    def test_states_and_numeric_thresholds_have_distinct_types(self):
        for operator, value in [('gt', 'straight'), ('eq', '0.5'), ('lt', -0.1), ('eq', True)]:
            invalid = copy.deepcopy(self.examples['poseCondition'])
            invalid['hands'][0]['all'][0].update(operator=operator, value=value)
            with self.subTest(operator=operator, value=value):
                with self.assertRaises(ValidationError): self.validate('poseCondition', invalid)

    def test_directions_and_target_resolution_do_not_invent_a_frame_or_depth(self):
        self.validate('motion', {'schemaVersion': 1, 'action': {'kind': 'point', 'effector': 'left_hand',
            'direction': 'left', 'frame': self.examples['signObservation']['frame']}})
        for resolution in ({'status': 'resolved'}, {'status': 'ambiguous', 'candidates': []},
                           {'status': 'unresolved', 'target': {'id': 'guess'}}):
            invalid = {**self.examples['pointObservation'], 'resolution': resolution}
            with self.assertRaises(ValidationError): self.validate('pointObservation', invalid)
        invalid = copy.deepcopy(self.examples['pointObservation']); invalid['frame']['mirrored'] = True
        with self.assertRaises(ValidationError): self.validate('pointObservation', invalid)
        invalid['frame']['mirrored'] = False; invalid['direction'] = 'forward'
        with self.assertRaises(ValidationError): self.validate('pointObservation', invalid)

    def test_sequences_bound_order_length_dwell_and_output_duration(self):
        valid = self.examples['inputSequence']
        for key, value in [('steps', []), ('steps', valid['steps'] * 2), ('dwellMs', 0),
                           ('maximumDurationMs', 120001), ('maximumGapMs', 30001)]:
            with self.assertRaises(ValidationError): self.validate('inputSequence', {**valid, key: value})
        step = {'action': self.examples['motion']['action'], 'holdMs': 5000}
        sequence = {'schemaVersion': 1, 'action': {'kind': 'sequence', 'steps': [step] * 12}}
        self.validate('motion', sequence)
        sequence['action']['steps'] += [step]
        with self.assertRaises(ValidationError): self.validate('motion', sequence)
        nested = {'kind': 'sequence', 'steps': [step]}
        sequence['action']['steps'] = [{'action': nested, 'holdMs': 500}]
        with self.assertRaises(ValidationError): self.validate('motion', sequence)

    def test_input_and_output_capabilities_are_separate_and_no_legacy_sign_is_renamed(self):
        capabilities = copy.deepcopy(self.examples['capabilities'])
        capabilities['output']['signs'] = []
        self.validate('capabilities', capabilities)
        for legacy in ('rock', 'scissor', 'paper'):
            invalid = copy.deepcopy(self.examples['motion']); invalid['action']['sign'] = legacy
            with self.assertRaises(ValidationError): self.validate('motion', invalid)
        self.assertEqual(50, manifest()['budget']['maximumReservedUsd'])

    def test_runner_rejects_paid_journeys_and_mixed_selections_before_database_access(self):
        runner = ROOT / 'tests/gptlive/run_acceptance.py'
        for args in ([ '--hand-interaction', name] for name in JOURNEYS):
            result = subprocess.run([sys.executable, '-B', str(runner), *args], capture_output=True, text=True, timeout=15)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('not implemented; no provider access', result.stdout + result.stderr)
            self.assertNotIn('Disposable schema:', result.stdout)
        result = subprocess.run([sys.executable, '-B', str(runner), '--hand-interaction', 'baseline',
            '--paid-core', 'commanding-hp1'], capture_output=True, text=True, timeout=15)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('cannot borrow', result.stdout + result.stderr)


if __name__ == '__main__': unittest.main()
