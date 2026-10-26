"""Boundary checks: reject unsafe/misconfigured paid runs before creating a database or calling a provider."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

RUNNER = Path(__file__).with_name('run_acceptance.py')


class AcceptanceRunnerTest(unittest.TestCase):
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


if __name__ == '__main__':
    unittest.main()
