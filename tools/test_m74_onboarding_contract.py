"""Current-Java contract tests. Run AFTER mvn test-compile/verify; absence of compiled classes is an error, not a skip."""
import importlib.util
import copy
import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('m74_contract_runner',HERE/'m74_onboarding_runner.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)


class CurrentJavaContractTest(unittest.TestCase):
    def test_current_person_controls_require_ordered_matching_operator_decisions(self):
        classes=HERE.parent/'target/classes'
        result=subprocess.run(['java','-cp',str(classes),str(HERE/'m74_record_pack_crosscheck/RecordPersonFacts.java')],
                              capture_output=True,text=True,check=True)
        records=r.parse_session_record(result.stdout)
        self.assertEqual({rec['event'] for rec in records}, set(r.EVENT_TO_ACT),
                         'every current Java onboarding control must have an explicit operator contract')
        self.assertEqual(set(r.PERSON_ACT_EVENTS), set(r.ACT_FIELDS))
        acts=[]
        for rec in records:
            kind=r.EVENT_TO_ACT[rec['event']]
            expected={key:rec['fields'][key] for key in r.ACT_FIELDS[kind]}
            act={'kind':kind,'fact':expected}
            acts.append(act)
            with self.subTest(event=rec['event']):
                self.assertTrue(r.boundary_check([act],[rec])['ok'], 'a matching current control must be accepted')
                self.assertFalse(r.boundary_check([],[rec])['ok'], 'no person decision may be ignored')
                self.assertFalse(r.boundary_check([act],[])['ok'], 'a claimed press requires its fact')
            for field,value in expected.items():
                with self.subTest(event=rec['event'],field=field):
                    missing=copy.deepcopy(act);del missing['fact'][field]
                    self.assertFalse(r.boundary_check([missing],[rec])['ok'], 'missing references/choices must refuse')
                    wrong=copy.deepcopy(act)
                    wrong['fact'][field]=not value if type(value) is bool else 'different-DEMO'
                    self.assertFalse(r.boundary_check([wrong],[rec])['ok'], 'wrong references/choices must refuse')
        self.assertTrue(r.boundary_check(acts,records)['ok'])
        self.assertFalse(r.boundary_check(list(reversed(acts)),records)['ok'], 'correct totals in wrong order refuse')
        ask=copy.deepcopy(next(rec for rec in records if rec['event']=='LocalAgentAskAgainRequested'))
        ask['fields']['draftBindings']=[1]
        self.assertFalse(r.boundary_check([{'kind':'askAgain','fact':{'request':7,'draftBindings':[True]}}],[ask])['ok'])

    def test_actual_permission_answers_are_classified_by_allow_and_delivery(self):
        classes=HERE.parent/'target/classes'
        self.assertTrue((classes/'telamin/fluxtion/audit/analyser/analyser/session/SessionEvents.class').is_file(),
                        'compile the reviewed checkout before running the current-Java contract tests')
        result=subprocess.run(['java','-cp',str(classes),str(HERE/'m74_record_pack_crosscheck/RecordPermissionFacts.java')],
                              capture_output=True,text=True,check=True)
        got=r.approvals(r.parse_session_record(result.stdout))
        self.assertEqual(got['asked'],4)
        self.assertEqual(got['granted'],1,'the actual allow=true PERSON answer must not be called a denial')
        self.assertEqual(got['deniedBy'],{'PERSON':1},'pre-allows and undelivered answers are not denials')
        self.assertEqual(got['allowedBy'],{'PERSON':1,'PRE_ALLOWED':1})
        self.assertEqual(got['undelivered'],1)

    def test_current_parser_check_detects_drift_without_rewriting_expected_verdicts(self):
        spec=importlib.util.spec_from_file_location('m74_contract_recorder',HERE/'m74_record_pack_crosscheck.py')
        recorder=importlib.util.module_from_spec(spec);spec.loader.exec_module(recorder)
        with tempfile.TemporaryDirectory(prefix='DEMO-m74-contract-') as t:
            fixture=Path(t)/'crosscheck.json'
            fixture.write_bytes(recorder.FIXTURE.read_bytes());recorder.FIXTURE=fixture
            original=fixture.read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(recorder.main([str(HERE.parent),'--check']),0)
            self.assertEqual(original,fixture.read_bytes(),'a successful check cannot rewrite expected data')
            doc=json.loads(original)
            name='example-nbsp-suffix'
            doc['cases'][name]['java']={'entries':1}
            fixture.write_text(json.dumps(doc));wrong=fixture.read_bytes()
            err=io.StringIO()
            with contextlib.redirect_stderr(err):
                self.assertEqual(recorder.main([str(HERE.parent),'--check']),1,'a stale verdict must fail the CI check')
            self.assertIn(name,err.getvalue(),'the exact drifting input must be named')
            self.assertEqual(wrong,fixture.read_bytes(),'a failing check must not repair its own expectation')


if __name__ == '__main__':unittest.main()
