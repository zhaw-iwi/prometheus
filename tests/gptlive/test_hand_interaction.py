import copy
import json
import subprocess
import sys
import unittest
from unittest.mock import patch
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from hand_interaction import ROOT, JOURNEYS, manifest, validate_contracts


class HandInteractionContractTest(unittest.TestCase):
    def test_mode_contract_preserves_native_ownership_and_backend_silence(self):
        data = manifest()
        self.assertEqual(2, data['acceptanceRevision'])
        cases = json.loads((ROOT / 'tests/gptlive/hand-cases.json').read_text(encoding='utf-8'))
        self.assertTrue(all('silent' not in text.lower() for text in cases.values()))
        for changed in (dict(data, acceptanceRevision=1), dict(data, modeChecks=[])):
            with patch('hand_interaction.MANIFEST') as source:
                source.read_text.return_value = json.dumps(changed)
                with self.assertRaisesRegex(ValueError, 'mode acceptance contract'): manifest()
        changed = copy.deepcopy(data); changed['modeContract']['live']['backendPlaybackApproval'] = True
        with patch('hand_interaction.MANIFEST') as source:
            source.read_text.return_value = json.dumps(changed)
            with self.assertRaisesRegex(ValueError, 'mode acceptance contract'): manifest()

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

    def test_relations_are_additive_and_relation_only_masks_are_explicit(self):
        relation = {'kind': 'tip_proximity', 'fingers': ['thumb', 'index'], 'operator': 'lt', 'value': .18}
        condition = {'schemaVersion': 1, 'hands': [{'handedness': 'any', 'all': [], 'relations': [relation]}]}
        self.validate('poseCondition', condition)
        for relations in ([], [{**relation, 'value': 3}], [{**relation, 'secret': True}]):
            invalid = copy.deepcopy(condition); invalid['hands'][0]['relations'] = relations
            with self.assertRaises(ValidationError): self.validate('poseCondition', invalid)
        pose = copy.deepcopy(self.examples['poseObservation'])
        fingers = ['thumb', 'index', 'middle', 'ring', 'little']
        pairs = {f'{a}_{b}': None for i, a in enumerate(fingers) for b in fingers[i+1:]}
        geometry = {'schemaVersion': 1, 'tipDistances': pairs, 'spreads': pairs.copy(),
                    'axes': {'palm_normal': None, 'index_axis': {'x': 0, 'y': -1}, 'thumb_axis': None}}
        pose['hands'][0]['geometry'] = geometry
        self.validate('poseObservation', pose)
        geometry['tipDistances']['thumb_index'] = 5
        with self.assertRaises(ValidationError): self.validate('poseObservation', pose)

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

    def test_registered_target_commands_and_runtime_resolution_are_bounded(self):
        ref = self.examples['pointObservation']['resolution']['target']
        action = {'kind': 'point', 'effector': 'left_hand', 'target': ref}
        self.validate('motion', {'schemaVersion': 1, 'action': action})
        action['resolved'] = {'label': 'Display', 'position': {'x': 3.2, 'y': 1.5, 'z': 0}, 'frame': ref['frame']}
        self.validate('motion', {'schemaVersion': 1, 'action': action})
        action['resolved']['execute'] = 'untrusted'
        with self.assertRaises(ValidationError): self.validate('motion', {'schemaVersion': 1, 'action': action})
        self.validate('pointObservation', {**self.examples['pointObservation'], 'resolution': {'status': 'unresolved'}})
        config = {'schemaVersion': 1, 'expectedRevision': 0, 'cameraFrame': None, 'transform': None, 'targets': []}
        self.validate('sceneConfiguration', config)
        for key, value in [('expectedRevision', -1), ('transform', [0]*5), ('targets', [{}]*17)]:
            with self.assertRaises(ValidationError): self.validate('sceneConfiguration', {**config, key: value})

    def test_detected_target_snapshot_and_projected_source_are_bounded(self):
        valid = self.examples['sceneTargets']
        for key in ('frame', 'observationId'):
            invalid = copy.deepcopy(valid); del invalid[key]
            with self.assertRaises(ValidationError): self.validate('sceneTargets', invalid)
        ref = self.examples['pointObservation']['resolution']['target']
        point = {**self.examples['pointObservation'], 'handOrigin': {'x': .4, 'y': .5},
                 'sourceAssociation': {'status': 'projected', 'person': ref}}
        self.validate('pointObservation', point)
        for key, value in [('handOrigin', {'x': -1, 'y': .5}),
                           ('sourceAssociation', {'status': 'identified', 'person': ref})]:
            with self.assertRaises(ValidationError): self.validate('pointObservation', {**point, key: value})

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

    def test_capture_ordinals_are_positive_bounded_integers_on_each_representation(self):
        for name in ('signObservation', 'poseObservation', 'pointObservation'):
            self.validate(name, {**self.examples[name], 'captureIndex': 1})
            for ordinal in (0, -1, 1.5, '1', True, 2147483648):
                with self.subTest(name=name, ordinal=ordinal):
                    with self.assertRaises(ValidationError):
                        self.validate(name, {**self.examples[name], 'captureIndex': ordinal})

    def test_execution_requests_cannot_claim_completion_or_choose_another_epoch(self):
        request = {'eventId': '11111111-1111-4111-8111-111111111111',
                   'owner': '22222222-2222-4222-8222-222222222222', 'operation': 'START', 'step': 0}
        self.validate('executionRequest', request)
        for key, value in [('epoch', 'invented'), ('operation', 'EXECUTED'), ('step', 12), ('eventId', 'guessed')]:
            with self.assertRaises(ValidationError): self.validate('executionRequest', {**request, key: value})
        state = copy.deepcopy(self.examples['executionReceipt'])
        state.update(status='RUNNING', step=0, current={'action': self.examples['motion']['action'], 'holdMs': 500})
        self.validate('executionReceipt', state)
        with self.assertRaises(ValidationError): self.validate('executionReceipt', {**state, 'physicalSuccess': True})

    def test_input_and_output_capabilities_are_separate_and_no_legacy_sign_is_renamed(self):
        capabilities = copy.deepcopy(self.examples['capabilities'])
        capabilities['output']['signs'] = []
        self.validate('capabilities', capabilities)
        for legacy in ('rock', 'scissor', 'paper'):
            invalid = copy.deepcopy(self.examples['motion']); invalid['action']['sign'] = legacy
            with self.assertRaises(ValidationError): self.validate('motion', invalid)
        self.assertEqual(236, manifest()['budget']['maximumReservedUsd'])

    def test_runner_rejects_paid_journeys_and_mixed_selections_before_database_access(self):
        runner = ROOT / 'tests/gptlive/run_acceptance.py'
        for args in ([ '--hand-interaction', name] for name in JOURNEYS):
            result = subprocess.run([sys.executable, '-B', str(runner), *args], capture_output=True, text=True, timeout=15)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('require --paid-core hand-live; no provider access', result.stdout + result.stderr)
            self.assertNotIn('Disposable schema:', result.stdout)
        result = subprocess.run([sys.executable, '-B', str(runner), '--hand-interaction', 'baseline',
            '--paid-core', 'commanding-hp1'], capture_output=True, text=True, timeout=15)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('cannot borrow', result.stdout + result.stderr)


if __name__ == '__main__': unittest.main()
