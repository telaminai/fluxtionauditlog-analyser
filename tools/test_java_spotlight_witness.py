#!/usr/bin/env python3
"""Exercise the witness runner with controlled Maven outcomes and real temporary source/report files."""
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('java_witness', Path(__file__).with_name('verify-java-source-spotlight.py'))
witness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(witness)


class WitnessTest(unittest.TestCase):
    def run_witness(self, baseline, mutated='failure', baseline_code=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root/'source.java'
            source.write_bytes(b'original\r\n')
            report = root/'target/surefire-reports/TEST-telamin.fluxtion.audit.analyser.analyser.ui.Suite.xml'
            report.parent.mkdir(parents=True)
            # Stale green output must not let a later missing report pass.
            report.write_text('<testsuite tests="1"><testcase name="method"/></testsuite>')
            observed = []

            def maven(*args, **kwargs):
                observed.append(source.read_bytes())
                outcome = baseline if len(observed) == 1 else mutated
                name = 'otherMethod' if outcome == 'wrong-name' else 'method'
                counts = {k: int(outcome == k.rstrip('s')) for k in ('failures', 'errors', 'skipped')}
                if outcome == 'skipped':
                    counts['skipped'] = 1
                child = {'failure': '<failure message="wrong value">assertion</failure>',
                         'error': '<error/>', 'skipped': '<skipped/>'}.get(outcome, '')
                if outcome != 'missing':
                    report.write_text(f'<testsuite tests="1" failures="{counts["failures"]}" errors="{counts["errors"]}" skipped="{counts["skipped"]}"><testcase name="{name}">{child}</testcase></testsuite>')
                code = (0 if outcome in ('green', 'skipped', 'wrong-name', 'missing') else 1)
                if len(observed) == 1 and baseline_code is not None:
                    code = baseline_code
                return SimpleNamespace(returncode=code)

            with patch.object(witness, 'ROOT', root), patch.object(witness, 'WITNESSES', [
                ('probe', 'Suite', 'method', [('source.java', 'original', 'mutated')])
            ]), patch.object(witness.subprocess, 'run', side_effect=maven), patch('sys.argv', ['witness']), patch('builtins.print'):
                code = witness.main()
            result = json.loads((root/'target/java-spotlight-mutations/summary.json').read_text())[0]
            self.assertEqual(b'original\r\n', source.read_bytes())
            return code, result, observed

    def test_green_then_named_assertion_proves_both_halves(self):
        code, result, observed = self.run_witness('green')
        self.assertEqual(0, code)
        self.assertTrue(result['baselineGreen'])
        self.assertTrue(result['seenRed'])
        self.assertTrue(result['restored'])
        self.assertEqual([b'original\r\n', b'mutated\n'], observed)

    def test_bad_baseline_never_mutates_or_attempts_red(self):
        for outcome in ('failure', 'error', 'skipped', 'wrong-name', 'missing'):
            with self.subTest(outcome=outcome):
                code, result, observed = self.run_witness(outcome)
                self.assertEqual(1, code)
                self.assertFalse(result['baselineGreen'])
                self.assertFalse(result['seenRed'])
                self.assertEqual([], result['sourceSites'])
                self.assertEqual([b'original\r\n'], observed)

    def test_green_xml_with_failed_process_is_not_baseline(self):
        code, result, observed = self.run_witness('green', baseline_code=1)
        self.assertEqual(1, code)
        self.assertFalse(result['baselineGreen'])
        self.assertEqual([b'original\r\n'], observed)

    def test_mutation_requires_named_assertion_not_error_skip_or_success(self):
        for outcome in ('green', 'error', 'skipped', 'wrong-name', 'missing'):
            with self.subTest(outcome=outcome):
                code, result, _ = self.run_witness('green', outcome)
                self.assertEqual(1, code)
                self.assertTrue(result['baselineGreen'])
                self.assertFalse(result['seenRed'])


if __name__ == '__main__':
    unittest.main()
