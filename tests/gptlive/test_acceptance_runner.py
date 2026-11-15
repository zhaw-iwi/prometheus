"""Boundary checks: reject unsafe/misconfigured paid runs before creating a database or calling a provider."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
from run_acceptance import escalation_cases

RUNNER = Path(__file__).with_name('run_acceptance.py')


class AcceptanceRunnerTest(unittest.TestCase):
    def test_integrated_study_selection_cannot_hide_cases_or_bill(self):
        for extra in (['--browser-grep','guess'],['--paid-core','coordination-text'],['--java-tests','none']):
            self.run_rejected(['--study-acceptance',*extra],{},'owns its complete offline selection')
    def test_study_rejects_empty_and_unrelated_paid_selections(self):
        self.run_rejected(['--study', '--java-tests', 'none'], {}, 'requires a test, browser, or provider selection')
        self.run_rejected(['--study', '--paid-core', 'live'], {}, 'requires --paid-core coordination-text')
    def test_study_remote_database_is_rejected_before_provisioning(self):
        self.run_rejected(['--study'], {'GPTLIVE_MYSQL_ADMIN_URL': 'jdbc:mysql://example.invalid/research'},
                          'no remote database is permitted')
    def test_study_scenario_cannot_be_ignored(self):
        self.run_rejected(['--study-scenario','guess'], {}, 'requires --study and --paid-core')
    def test_escalation_requires_real_live_transport(self):
        for scenario in ('escalation-recovery', 'escalation-dialogue', 'escalation-social'):
            self.run_rejected(['--paid-core', 'coordination-text', '--coordination-scenario', scenario], {},
                              'requires the Live Coordination journey')
    def test_classroom_capacity_must_select_its_dedicated_test(self):
        self.run_rejected(['--classroom-capacity', '--java-tests', 'all'], {}, 'ClassroomCapacityIntegrationTest alone')

    def test_study_capacity_owns_selection_and_has_bounded_duration(self):
        for extra in (['--paid-core','coordination-text'],['--browser'],['--study-acceptance'],['--java-tests','all']):
            self.run_rejected(['--study-capacity','1800',*extra],{},'owns its offline selection')
        self.run_rejected(['--study-capacity','1'],{},'owns its offline selection')
        self.run_rejected(['--study-wave','4'],{},'requires --study-capacity')
        self.run_rejected(['--study-capacity','1800','--study-wave','9'],{},'must be 3..8')

    def test_camera_selections_cannot_claim_legacy_capacity_or_skip_model_smoke(self):
        self.run_rejected(['--study-camera'],{},'requires --study')
        self.run_rejected(['--study-models','--study','--study-camera'],{},'requires --study --study-camera --browser')
        for selection in (['--study-capacity','1800'],['--study-acceptance']):
            self.run_rejected(['--study','--study-camera',*selection],{},'separately from the v2')

    def test_classroom_capacity_cannot_mix_provider_or_browser_traffic(self):
        self.run_rejected(['--classroom-capacity', '--java-tests', 'ClassroomCapacityIntegrationTest',
                           '--paid-core', 'coordination'], {}, 'requires a separate offline run')

    def test_synthetic_escalation_manifest_is_available_without_provisioning(self):
        fixture = escalation_cases(RUNNER.resolve().parents[2])
        self.assertFalse(fixture['baselineIsAcceptance'])
        self.assertEqual(6, len(fixture['cases']))
        self.assertIn('unaccepted-cue', {case['id'] for case in fixture['cases']})

    def run_rejected(self, arguments, environment, message):
        result = subprocess.run([sys.executable, '-B', str(RUNNER), *arguments],
                                env={**os.environ, **environment}, capture_output=True, text=True, timeout=15)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(message, result.stdout + result.stderr)
        self.assertNotIn('Disposable schema:', result.stdout)

    def test_remote_database_is_never_used(self):
        self.run_rejected(['--java-tests', 'none'],
                          {'GPTLIVE_MYSQL_ADMIN_URL': 'jdbc:mysql://example.invalid/production'},
                          'no remote database is permitted')

    def test_missing_model_fails_before_billing(self):
        self.run_rejected(['--paid-core', 'live'],
                          {'OPENAI_MODEL': 'invalid model', 'OPENAI_API_KEY': 'not-a-real-key'},
                          'requires an API key and configured openai.model')

    def test_paid_and_mocked_provider_runs_cannot_be_combined(self):
        self.run_rejected(['--paid-core', 'live', '--browser'], {}, 'must run separately')

    def test_coordination_scenario_cannot_silently_run_another_agent(self):
        self.run_rejected(['--paid-core', 'live', '--coordination-scenario', 'revision'], {},
                          'requires a paid Coordination journey')

    def test_coordination_requires_valid_provider_configuration_before_billing(self):
        self.run_rejected(['--paid-core', 'coordination'],
                          {'OPENAI_MODEL': 'invalid model', 'OPENAI_API_KEY': 'not-a-real-key'},
                          'requires an API key and configured openai.model')

    def test_browser_mode_cannot_be_ignored(self):
        self.run_rejected(['--browser-mode', 'enabled'], {}, 'requires --browser')

    def test_embodiment_cannot_silently_select_an_unrelated_journey(self):
        for args in (['--embodiment'], ['--embodiment','--study','--browser'], ['--embodiment','--paid-core','live']):
            with self.subTest(args=args):
                self.run_rejected(args, {}, '--embodiment requires')

    def test_interaction_cannot_silently_run_as_text(self):
        self.run_rejected(['--paid-core', 'coordination-text', '--coordination-scenario', 'interaction'], {},
                          'requires the Live Coordination journey')


if __name__ == '__main__':
    unittest.main()
