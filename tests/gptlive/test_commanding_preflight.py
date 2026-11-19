import copy, io, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
from commanding_preflight import validate, validate_ledger, available
from paid_budget import reserve

class CommandingPreflightTest(unittest.TestCase):
    def setUp(self):
        self.plan=json.loads(Path(__file__).with_name('commanding-paid-plan.json').read_text())
    def test_complete_envelope_covers_the_worst_case_with_local_stimuli(self):
        self.assertLess(validate(self.plan,'gpt-5.6-luna'),1.11)
    def test_missing_authorization_model_bounds_or_prices_fail_closed(self):
        for field in ('authorized','models','limits','pricing'):
            p=copy.deepcopy(self.plan);p.pop(field)
            with self.assertRaises((ValueError,KeyError)):validate(p,'gpt-5.6-luna')
        with self.assertRaises(ValueError):validate(self.plan,'unpriced-model')
        for key in self.plan['limits']:
            p=copy.deepcopy(self.plan);p['limits'][key]+=1
            with self.assertRaises(ValueError):validate(p,'gpt-5.6-luna')
    def test_catalog_access_requires_both_models_without_inference(self):
        for names,valid in [(['gpt-live-1','gpt-5.6-luna'],True),(['gpt-live-1'],False)]:
            payload=io.BytesIO(json.dumps({'data':[{'id':n} for n in names]}).encode())
            with patch('commanding_preflight.urlopen',return_value=payload) as opened:
                if valid:available('synthetic-key',self.plan)
                else:
                    with self.assertRaises(ValueError):available('synthetic-key',self.plan)
                self.assertEqual(opened.call_args.args[0].full_url,'https://api.openai.com/v1/models')
    def test_separate_ledger_retains_failed_attempts_and_stops_at_eight(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'commanding.json'
            with self.assertRaises(FileNotFoundError):validate_ledger(ledger)
            ledger.write_text(json.dumps({'authorization':'commanding-2026-11-19-usd40','attempts':[]}))
            for index in range(8):
                validate_ledger(ledger);reserve(ledger,self.plan,'commanding-hp1',Path(directory)/str(index))
            with self.assertRaises(ValueError):validate_ledger(ledger)
            with self.assertRaises(ValueError):reserve(ledger,self.plan,'commanding-hp1',Path(directory)/'extra')
            self.assertEqual(json.loads(ledger.read_text())['reservedUsd'],40)
    def test_wrong_or_locked_ledger_cannot_be_reused(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'commanding.json';ledger.write_text('{"attempts":[]}')
            with self.assertRaises(ValueError):validate_ledger(ledger)
            ledger.with_suffix('.lock').touch()
            with self.assertRaises(FileExistsError):reserve(ledger,self.plan,'commanding-hp1',Path(directory))

    def test_retests_have_a_separate_authorization_and_one_attempt_per_journey(self):
        plan=json.loads(Path(__file__).with_name('commanding-retests-plan.json').read_text())
        self.assertLess(validate(plan,'gpt-5.6-luna'),1.11)
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'retests.json'
            ledger.write_text(json.dumps({'authorization':plan['authorization'],'attempts':[]}))
            with self.assertRaises(ValueError):validate_ledger(ledger,self.plan)
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp1')
            validate_ledger(ledger,plan,'commanding-hp2')
            reserve(ledger,plan,'commanding-hp2',Path(directory)/'task')
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp2')
            # The lock-protected reservation also rejects a duplicate if preflight raced.
            with self.assertRaises(ValueError):reserve(ledger,plan,'commanding-hp2',Path(directory)/'duplicate')
            reserve(ledger,plan,'commanding-hp3',Path(directory)/'live')
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp3')
            self.assertEqual(json.loads(ledger.read_text())['reservedUsd'],10)

    def test_retest_envelope_cannot_expand_or_use_an_exhausted_original_ledger(self):
        plan=json.loads(Path(__file__).with_name('commanding-retests-plan.json').read_text())
        for field,value in [('maximumAttempts',3),('maximumReservedUsd',15),('authorization','different'),
                            ('maximumAttemptsByMode',{'commanding-hp2':2,'commanding-hp3':1})]:
            changed=copy.deepcopy(plan);changed[field]=value
            with self.assertRaises(ValueError):validate(changed,'gpt-5.6-luna')
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'original.json'
            content=json.dumps({'authorization':'commanding-2026-11-19-usd40',
                                'attempts':[{'mode':'commanding-hp1','reservedUsd':5}]*8,'reservedUsd':40})
            ledger.write_text(content)
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp2')
            self.assertEqual(ledger.read_text(),content)

    def test_single_hp3_followup_cannot_borrow_or_repeat_an_authorization(self):
        plan=json.loads(Path(__file__).with_name('commanding-hp3-retest-plan.json').read_text())
        self.assertLess(validate(plan,'gpt-5.6-luna'),1.11)
        with tempfile.TemporaryDirectory() as directory:
            ledger=Path(directory)/'followup.json'
            ledger.write_text(json.dumps({'authorization':plan['authorization'],'attempts':[]}))
            prior=json.loads(Path(__file__).with_name('commanding-retests-plan.json').read_text())
            with self.assertRaises(ValueError):validate_ledger(ledger,prior,'commanding-hp3')
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp2')
            with self.assertRaises(ValueError):reserve(ledger,plan,'commanding-hp2',Path(directory)/'wrong')
            validate_ledger(ledger,plan,'commanding-hp3')
            reserve(ledger,plan,'commanding-hp3',Path(directory)/'live')
            with self.assertRaises(ValueError):validate_ledger(ledger,plan,'commanding-hp3')
            with self.assertRaises(ValueError):reserve(ledger,plan,'commanding-hp3',Path(directory)/'duplicate')
            self.assertEqual(json.loads(ledger.read_text())['reservedUsd'],5)

    def test_single_hp3_followup_cannot_expand_its_envelope(self):
        plan=json.loads(Path(__file__).with_name('commanding-hp3-retest-plan.json').read_text())
        for field,value in [('maximumAttempts',2),('maximumReservedUsd',10),('authorization','different'),
                            ('maximumAttemptsByMode',{'commanding-hp2':1,'commanding-hp3':1})]:
            changed=copy.deepcopy(plan);changed[field]=value
            with self.assertRaises(ValueError):validate(changed,'gpt-5.6-luna')
