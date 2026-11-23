import json
from pathlib import Path
import tempfile
import unittest
from paid_budget import reserve


class PaidBudgetTest(unittest.TestCase):
    def test_failures_keep_reservations_and_followups_cannot_exceed_the_plan(self):
        plan = json.loads(Path(__file__).with_name('gptlivefix-paid-plan.json').read_text(encoding='utf-8'))
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / 'budget.json'
            for attempt in range(9):
                result = reserve(ledger, plan, 'repair-continuous', Path(directory) / str(attempt))
                self.assertEqual((attempt + 1) * 5, result['reservedUsd'])
            with self.assertRaisesRegex(ValueError, 'budget exhausted'):
                reserve(ledger, plan, 'repair-continuous', Path(directory) / 'over-budget')
            self.assertEqual(45, json.loads(ledger.read_text())['reservedUsd'])
            result = reserve(ledger, plan, 'repair-text', Path(directory) / 'bounded-text')
            self.assertEqual(46, result['reservedUsd'])
            self.assertEqual([5] * 9 + [1], [v['reservedUsd'] for v in result['attempts']])
            previous_plan = dict(plan, maximumAttempts=11, maximumReservedUsd=48,
                                 reserveUsdByMode={'repair-text': 1, 'repair-live': 2})
            result = reserve(ledger, previous_plan, 'repair-live', Path(directory) / 'transport-native')
            self.assertEqual(48, result['reservedUsd'])
            result = reserve(ledger, plan, 'repair-live', Path(directory) / 'role-native')
            self.assertEqual(49.5, result['reservedUsd'])
            self.assertEqual([5] * 9 + [1, 2, 1.5], [v['reservedUsd'] for v in result['attempts']])
            with self.assertRaisesRegex(ValueError, 'budget exhausted'):
                reserve(ledger, plan, 'repair-text', Path(directory) / 'thirteenth')

    def test_concurrent_or_stale_lock_cannot_reset_reservations(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / 'budget.json'
            ledger.with_suffix('.lock').touch()
            with self.assertRaises(FileExistsError):
                reserve(ledger, {}, 'live', Path(directory))


if __name__ == '__main__':
    unittest.main()
