#!/usr/bin/env python3
"""The witness must reject an unrelated failure even when its code frame quotes the right label."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / 'docs/handoff/evidence/project-starter-comment-contract-2026-09-20/mutate-browser.py'
spec = importlib.util.spec_from_file_location('mutation', path)
mutation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mutation)

class WitnessTest(unittest.TestCase):
    def report(self):
        return {'numFailedTests':1, 'numPassedTests':2, 'numPendingTests':0, 'testResults':[
            {'assertionResults':[{'status':'failed', 'title':mutation.PARITY_TEST,
                'failureMessages':["AssertionError: browser handler: expected [ ...(10) ] to include 'text'\ncode frame: browser reference"]}]}]}
    def test_exact_assertion_and_counts(self):
        mutation.verify_witness(self.report())
    def test_wrong_assertion_with_expected_text_in_code_frame_is_refused(self):
        report = self.report()
        report['testResults'][0]['assertionResults'][0]['failureMessages'] = [
            'AssertionError: unrelated: expected 1 to be 0\ncode frame: browser handler']
        with self.assertRaises(AssertionError): mutation.verify_witness(report)
    def test_old_reference_label_is_refused(self):
        with self.assertRaises(AssertionError): mutation.verify_witness(self.report(), 'browser reference')
    def test_wrong_test_and_skipped_parity_are_refused(self):
        report = self.report()
        report['testResults'][0]['assertionResults'][0]['title'] = 'another test'
        with self.assertRaises(AssertionError): mutation.verify_witness(report)
        report = self.report(); report['numPendingTests'] = 1
        with self.assertRaises(AssertionError): mutation.verify_witness(report)

if __name__ == '__main__': unittest.main()
