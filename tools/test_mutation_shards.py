"""Negative evidence controls: missing work must never make the required gate green."""
import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import mutation_shards as shards
import verify_project_chart_review as gate


class ShardingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name) / 'Fixture.java'
        self.source.write_text('fixture source')
        self.cases = [(f'control-{i}', str(self.source), 'fixture', 'mutation', 'FixtureTest#checksBehaviour')
                      for i in range(8)]
        self.revision = 'fixture-revision'
        self.docs = self.evidence()

    def result(self, failure=False):
        return {'completedNormally': True, 'exit': 1 if failure else 0, 'output': '', 'suites': [{
            'name': 'FixtureTest', 'tests': 1, 'failures': int(failure), 'errors': 0, 'skipped': 0,
            'testNames': ['checksBehaviour'],
            'assertions': [{'test': 'checksBehaviour', 'kind': 'failure'}] if failure else []}]}

    def evidence(self):
        plan, _ = shards.plan(self.cases, 4)
        return [{'schema': shards.SCHEMA, 'scope': 'full',
                 'planDigest': shards.plan_digest(self.cases, 4), 'mode': 'mutations', 'engine': 'fast', 'baseline': self.result(),
                 'shard': {'index': i, 'count': 4, 'revision': self.revision, 'cases': [c[0] for c in group]},
                 'runs': [{'name': c[0], 'site': c[1], 'sha256': hashlib.sha256(self.source.read_bytes()).hexdigest(),
                           'engine': 'fast', 'baselineGreen': True, 'verdict': 'caught',
                           'restoredByteIdentical': True, 'classesRestoredByteIdentical': True,
                           'mutated': self.result(True), 'restored': self.result()} for c in group]}
                for i, group in enumerate(plan)]

    def collect(self):
        return shards.collect(self.cases, self.docs, 4, self.revision, gate)

    def reject(self, message):
        with self.assertRaisesRegex(ValueError, message):
            self.collect()

    def test_complete_evidence_passes(self):
        result = self.collect()
        self.assertTrue(result['complete'], 'all shard evidence should establish completion')
        self.assertEqual(8, result['controls'], 'all controls must be counted')

    def test_missing_shard_fails(self):
        self.docs.pop()
        self.reject('expected 4 shard results')

    def test_duplicate_shard_fails(self):
        self.docs[3] = copy.deepcopy(self.docs[0])
        self.reject('duplicate shard')

    def test_missing_duplicate_extra_and_wrong_shard_controls_fail(self):
        for mode in ('missing', 'duplicate', 'extra', 'wrong-shard'):
            with self.subTest(mode=mode):
                self.docs = self.evidence()
                runs = self.docs[0]['runs']
                if mode == 'missing':
                    runs.pop()
                elif mode == 'duplicate':
                    runs[-1] = copy.deepcopy(runs[0])
                elif mode == 'extra':
                    runs.append(copy.deepcopy(runs[0]))
                else:
                    runs[0] = copy.deepcopy(self.docs[1]['runs'][0])
                self.reject('missing, duplicate, extra or out-of-order')

    def test_wrong_revision_fails(self):
        self.docs[0]['shard']['revision'] = 'earlier-run'
        self.reject('wrong revision')

    def test_changed_source_fails(self):
        self.source.write_text('different source')
        self.reject('wrong plan digest')

    def test_falsely_claimed_completion_fails(self):
        self.docs[0]['complete'] = True
        self.docs[0]['runs'].pop()
        self.reject('missing, duplicate, extra or out-of-order')

    def test_baseline_skip_error_empty_or_missing_named_test_fails(self):
        for key in ('skipped', 'errors', 'tests', 'testNames'):
            with self.subTest(key=key):
                self.docs = self.evidence()
                self.docs[0]['baseline']['suites'][0][key] = [] if key == 'testNames' else (0 if key == 'tests' else 1)
                self.reject('baseline')

    def test_named_assertion_is_required_even_with_caught_verdict(self):
        for mode in ('error', 'wrong-name', 'wrong-class', 'zero-exit'):
            with self.subTest(mode=mode):
                self.docs = self.evidence()
                mutated = self.docs[0]['runs'][0]['mutated']
                suite = mutated['suites'][0]
                if mode == 'error':
                    suite['assertions'][0]['kind'] = 'error'
                elif mode == 'wrong-name':
                    suite['assertions'][0]['test'] = 'someOtherTest'
                elif mode == 'wrong-class':
                    suite['name'] = 'OtherTest'
                else:
                    mutated['exit'] = 0
                self.reject('no named assertion failure')

    def test_survivor_and_compile_failure_fail(self):
        for verdict in ('survived', 'compile-error', 'not-restored'):
            with self.subTest(verdict=verdict):
                self.docs = self.evidence()
                self.docs[0]['runs'][0]['verdict'] = verdict
                self.reject('control not caught')

    def test_each_restore_flag_is_required(self):
        for field in ('restoredByteIdentical', 'classesRestoredByteIdentical'):
            with self.subTest(field=field):
                self.docs = self.evidence()
                self.docs[0]['runs'][0][field] = False
                self.reject('bytes not restored')

    def test_restored_named_test_must_pass_without_skips(self):
        for mode in ('skip', 'failure', 'wrong-name'):
            with self.subTest(mode=mode):
                self.docs = self.evidence()
                suite = self.docs[0]['runs'][0]['restored']['suites'][0]
                if mode == 'wrong-name':
                    suite['testNames'] = ['someOtherTest']
                else:
                    suite['skipped' if mode == 'skip' else 'failures'] = 1
                self.reject('named test not restored green')

    def test_subset_and_wrong_plan_fail(self):
        self.docs[0]['subset'] = {}
        self.reject('branch subset')
        self.docs = self.evidence()
        self.docs[0]['shard']['cases'].pop()
        self.reject('allocation differs')

    def test_allocation_is_complete_deterministic_and_balanced(self):
        cases = [(str(i),) for i in range(20)]
        weights = {str(i): float(i + 1) for i in range(20)}
        first, loads = shards.allocate(cases, 4, weights, 5)
        second, _ = shards.allocate(list(reversed(cases)), 4, weights, 5)
        self.assertEqual(first, second, 'input order must not change shard ownership')
        self.assertEqual(sorted(cases), sorted(c for group in first for c in group), 'every control exactly once')
        self.assertLessEqual(max(loads) - min(loads), 5, 'long controls must be spread across workers')

    def test_new_controls_are_included_without_timing_entries(self):
        cases = self.cases + [('brand-new-control',)]
        plan, _ = shards.allocate(cases, 4, {}, 5)
        self.assertEqual(sorted(cases), sorted(c for group in plan for c in group), 'no timing is not permission to skip')

    def test_priority_control_runs_once_and_first(self):
        cases = self.cases + [(shards.PRIORITY,)]
        plan, _ = shards.allocate(cases, 4, {shards.PRIORITY: 0.5}, 5)
        containing = [group for group in plan if any(c[0] == shards.PRIORITY for c in group)]
        self.assertEqual(1, len(containing), 'early control must not be duplicated')
        self.assertEqual(shards.PRIORITY, containing[0][0][0], 'early control must run first in its shard')

    def test_invalid_counts_duplicate_registry_and_invalid_timings_fail(self):
        for count in (0, 9):
            with self.assertRaisesRegex(ValueError, 'shard count'):
                shards.allocate(self.cases, count, {}, 5)
        with self.assertRaisesRegex(ValueError, 'duplicate names'):
            shards.allocate(self.cases + [self.cases[0]], 4, {}, 5)
        timing = Path(self.tmp.name) / 'timings.json'
        for value in (0, -1, float('inf'), 'slow', True):
            timing.write_text(json.dumps({'seconds': {'a': value}, 'defaultSeconds': 5}))
            with self.assertRaisesRegex(ValueError, 'invalid duration'):
                shards.durations(timing)

    def test_cli_refuses_subsets_or_wrong_mode_before_running_tests(self):
        base = [sys.executable, str(Path(gate.__file__)), '--output', str(Path(self.tmp.name) / 'out.json')]
        for extra in [
            ['--mode', 'display', '--shard-index', '0', '--shard-count', '4'],
            ['--mode', 'mutations', '--shard-index', '0', '--shard-count', '4'],
            ['--mode', 'mutations', '--engine', 'fast', '--shard-index', '0'],
            ['--mode', 'mutations', '--engine', 'fast', '--shard-index', '4', '--shard-count', '4'],
            ['--mode', 'mutations', '--engine', 'fast', '--shard-index', '0', '--shard-count', '4', '--case', 'any'],
            ['--mode', 'mutations', '--engine', 'fast', '--shard-index', '0', '--shard-count', '4', '--changed-since', 'HEAD']]:
            with self.subTest(extra=extra):
                r = subprocess.run(base + extra, capture_output=True, text=True)
                self.assertEqual(2, r.returncode, 'invalid shard command must refuse before Maven')
                self.assertIn('sharding requires', r.stderr, 'refusal must name the incompatible selection')

    def test_sharding_infrastructure_changes_select_the_full_branch_gate(self):
        for path in ('tools/mutation_shards.py', 'tools/mutation_timings.json'):
            selected, skipped = gate.fast.select_subset(self.cases, [path])
            self.assertEqual(self.cases, [c for c, _ in selected], 'infrastructure changes select all controls')
            self.assertEqual([], skipped, 'no control may be skipped for a sharding change')

    def test_collector_cli_rejects_a_missing_artifact(self):
        folder = Path(self.tmp.name) / 'artifacts'
        folder.mkdir()
        # No worker result: reject before trusting a claimed success or starting any engine.
        r = subprocess.run([sys.executable, str(Path(shards.__file__)), '--artifacts', str(folder),
                            '--count', '4', '--revision', self.revision,
                            '--output', str(folder / 'combined.json')], capture_output=True, text=True)
        self.assertEqual(1, r.returncode, 'absent results must fail the required check')
        self.assertIn('expected 4 shard results, got 0', r.stderr, 'failure must identify missing evidence')
        rejected = json.loads((folder / 'combined.json').read_text())
        self.assertFalse(rejected['complete'], 'rejection evidence must never claim completion')
        self.assertIn('expected 4 shard results', rejected['rejection'], 'retain the rejection reason')

    def test_worker_uses_full_preflight_then_only_its_assigned_controls(self):
        output = Path(self.tmp.name) / 'worker.json'
        args = ['gate', '--mode', 'mutations', '--engine', 'fast', '--shard-index', '1', '--shard-count', '4',
                '--output', str(output)]
        def execute(cases, engine, **kwargs):
            self.assertEqual(shards.plan(self.cases, 4)[0][1], cases, 'worker must execute its assigned partition')
            kwargs['on_baseline'](self.result())
            for entry in self.docs[1]['runs']:
                kwargs['on_entry'](entry)
        with patch.object(sys, 'argv', args), patch.object(gate, 'selected_cases', return_value=self.cases) as selected, \
                patch.object(gate.fast, 'FastEngine', return_value=type('Engine', (), {'phases': {}, 'fallbacks': 0, 'prepare': lambda self: None})()), patch.object(gate, 'run_gate', side_effect=execute), \
                patch.object(shards, 'revision', return_value=self.revision):
            gate.main()
        selected.assert_called_once_with(None)
        self.assertEqual(self.docs[1]['shard'], json.loads(output.read_text())['shard'], 'worker must publish its scope')


if __name__ == '__main__':
    unittest.main()
