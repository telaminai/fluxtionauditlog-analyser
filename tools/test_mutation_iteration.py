"""MG-1: regression witnesses for evidence scope and reduced baseline work."""
import tempfile
import subprocess
import json
import os
import sys
import time
import signal
import contextlib
import io
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import verify_project_chart_review as gate
import mutation_policy as policy
import mutation_shards as shards
import refresh_mutation_timings as timing
import mutation_run_report as run_report
import test_mutation_shards as fixtures


class IterationTest(unittest.TestCase):
    def test_baseline_runs_only_deduplicated_witnesses(self):
        # Stop at the baseline: this wrong-result witness cannot mutate a source file.
        class BaselineRecorded(Exception):
            pass
        engine = Mock()
        engine.run.return_value = {'exit': 0, 'suites': [], 'output': ''}
        cases = [('a', 'unused', 'a', 'b', 'ExampleTest#chosen'),
                 ('b', 'unused', 'a', 'b', 'ExampleTest#chosen'),
                 ('c', 'unused', 'a', 'b', 'ExampleTest#other')]
        def stop(_):
            raise BaselineRecorded()
        with self.assertRaises(BaselineRecorded):
            gate.run_gate(cases, engine, True, on_baseline=stop)
        self.assertEqual(['ExampleTest#chosen', 'ExampleTest#other'], engine.run.call_args.args[0],
                         'baseline must execute only the distinct named witnesses, never unrelated class methods')

    def test_shared_test_resource_change_forces_full(self):
        cases = [('a', 'src/main/java/Example.java', 'a', 'b', 'ExampleTest#chosen')]
        selected, skipped = gate.fast.select_subset(cases, ['src/test/resources/shared-fixture.xml'])
        self.assertEqual(cases, [c for c, _ in selected], 'shared fixture changes must not omit controls')
        self.assertEqual([], skipped, 'shared fixtures require full evidence')


class EvidenceScopeTest(fixtures.ShardingTest):
    def test_partial_scope_cannot_masquerade_as_full(self):
        self.docs[0]['scope'] = 'feedback'
        self.reject('scope')

    def test_unknown_schema_cannot_be_accepted(self):
        self.docs[0]['schema'] = -1
        self.reject('schema')

    def test_changed_plan_cannot_be_accepted(self):
        self.docs[0]['planDigest'] = 'different plan'
        self.reject('plan')


class RestoreTest(unittest.TestCase):
    def test_interrupted_control_restores_source_and_class_tree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / 'source.md'
            source.write_bytes(b'original source')
            classes = root / 'classes'
            classes.mkdir()
            existing = classes / 'Existing.class'
            existing.write_bytes(b'original class')
            engine = gate.fast.FastEngine()
            engine.snapshot = gate.fast.tree_snapshot((classes,))
            original_restore, original_drift = gate.fast.restore_trees, gate.fast.tree_drift
            def interrupted(_):
                existing.write_bytes(b'mutated class')
                (classes / 'Added.class').write_bytes(b'added')
                raise KeyboardInterrupt('interrupt after mutation')
            entry = {}
            with patch.object(engine, 'run', side_effect=interrupted), \
                 patch.object(gate.fast, 'restore_trees', side_effect=lambda snap: original_restore(snap, (classes,))), \
                 patch.object(gate.fast, 'tree_drift', side_effect=lambda snap: original_drift(snap, (classes,))):
                with self.assertRaisesRegex(KeyboardInterrupt, 'interrupt after mutation'):
                    engine.control(('restore', str(source), 'original', 'mutated', 'FixtureTest#chosen'), entry)
            self.assertEqual(b'original source', source.read_bytes(), 'interruption must restore original source bytes')
            self.assertEqual(b'original class', existing.read_bytes(), 'interruption must restore original class bytes')
            self.assertFalse((classes / 'Added.class').exists(), 'restoration must delete classes the mutant added')
            self.assertTrue(entry['restoredByteIdentical'] and entry['classesRestoredByteIdentical'],
                            'both restoration checks must be measured after interruption')


class TimingsTest(unittest.TestCase):
    def test_refresh_requires_distinct_training_and_held_out_runs(self):
        cases = [('a',), ('new',)]
        train = [({'run': str(i), 'revision': str(i)}, {'a': t}) for i, t in enumerate([2, 20, 3])]
        hold = ({'run': 'held', 'revision': 'held'}, {'a': 4, 'new': 5})
        # The production registry has enough work for eight; use extra unmeasured controls here too.
        cases += [(str(i),) for i in range(6)]
        r = timing.refresh(train, hold, cases)
        self.assertEqual(3, r['seconds']['a'], 'training uses the median, not the fastest sample')
        self.assertNotIn('new', r['seconds'], 'held-out observations must not leak into weights')
        self.assertGreaterEqual(r['defaultSeconds'], 4.2, 'unknown controls retain a conservative fallback')
        with self.assertRaisesRegex(ValueError, 'three distinct'):
            timing.refresh(train[:2], hold, cases)
        with self.assertRaisesRegex(ValueError, 'held-out'):
            timing.refresh(train, train[0], cases)


class RunReportTest(unittest.TestCase):
    def test_failed_attempts_remain_visible_and_unknown_queue_is_not_zero(self):
        run = {'status': 'completed', 'html_url': 'DEMO', 'head_sha': 'abc', 'event': 'pull_request',
               'conclusion': 'failure', 'run_started_at': '2026-10-05T10:00:00Z',
               'updated_at': '2026-10-05T10:02:00Z'}
        job = {'status': 'completed', 'name': 'mutation-shards', 'conclusion': 'failure',
               'started_at': '2026-10-05T10:00:10Z', 'completed_at': '2026-10-05T10:01:10Z'}
        r = run_report.summarise(run, [job])
        self.assertEqual('failure', r['jobs'][0]['conclusion'], 'failed attempts must remain in timing reports')
        self.assertEqual(1, r['jobExecutionMinutes'], 'runner time sums actual job intervals')
        self.assertIsNone(r['jobs'][0]['queueSeconds'], 'missing queue timestamps are unknown, not zero')
        with self.assertRaisesRegex(ValueError, 'complete run'):
            run_report.summarise({**run, 'status': 'in_progress'}, [job])


class ReviewCorrectionsTest(unittest.TestCase):
    def test_compare_sigterm_restores_the_real_mutated_source(self):
        script = r"""import os, signal, sys
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, 'tools')
import verify_project_chart_review as gate
import mutation_gate_fast as fast
source=Path(sys.argv[1]); output=sys.argv[2]
case=('probe',str(source),'original','mutant','FixtureTest#chosen')
class Engine(fast.FastEngine):
 def prepare(self): self.snapshot={}; self.calls=0
 def run(self, names):
  self.calls+=1
  if self.calls==2: os.kill(os.getpid(), signal.SIGTERM)
  return {'exit':0,'output':'','suites':[{'name':'FixtureTest','tests':1,'errors':0,'skipped':0,'failures':0,'testNames':['chosen'],'assertions':[]}]}
sys.argv=['gate','--mode','compare-baselines','--engine','fast','--case','probe','--output',output]
with patch.object(gate,'selected_cases',return_value=[case]),patch.object(gate,'PLANTED',[]),patch.object(fast,'FastEngine',Engine),patch.object(fast,'restore_trees',return_value=[]),patch.object(fast,'tree_drift',return_value=[]):
 gate.main()
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'source.md'
            source.write_text('original')
            result = subprocess.run([sys.executable, '-c', script, str(source), str(Path(tmp) / 'out.json')],
                                    capture_output=True, text=True, timeout=10)
            self.assertNotEqual(0, result.returncode, 'termination cannot report a successful comparison')
            self.assertEqual('original', source.read_text(), 'SIGTERM during comparison must restore the source')

    def test_timeout_descendant_cannot_overwrite_restored_class_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / 'source.md'; source.write_bytes(b'original source')
            classes = root / 'classes'; classes.mkdir()
            existing = classes / 'Existing.class'; existing.write_bytes(b'original class')
            ready = root / 'ready'
            child = ('import time; from pathlib import Path; '
                     'Path(' + repr(str(ready)) + ').write_text("ready"); time.sleep(.7); '
                     'Path(' + repr(str(existing)) + ').write_bytes(b"late mutant")')
            parent = ('import subprocess,sys,time; subprocess.Popen([sys.executable,"-c",' + repr(child) + '],'
                      'stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(10)')
            engine = gate.fast.FastEngine(); engine.snapshot = gate.fast.tree_snapshot((classes,))
            original_restore, original_drift = gate.fast.restore_trees, gate.fast.tree_drift
            calls = []
            def run(_):
                if not calls:
                    calls.append('mutated')
                    code, out = gate.fast.run_with_timeout([sys.executable, '-c', parent], .3)
                    return {'exit': code, 'output': out, 'suites': []}
                return {'exit': 0, 'output': '', 'suites': []}
            entry = {}
            with patch.object(engine, 'run', side_effect=run), \
                 patch.object(gate.fast, 'restore_trees', side_effect=lambda snap: original_restore(snap, (classes,))), \
                 patch.object(gate.fast, 'tree_drift', side_effect=lambda snap: original_drift(snap, (classes,))):
                engine.control(('restore', str(source), 'original', 'mutant', 'FixtureTest#chosen'), entry)
            self.assertTrue(ready.exists(), 'the descendant must have started before the timeout')
            self.assertTrue(entry['classesRestoredByteIdentical'], 'immediate restoration must be checked')
            time.sleep(.8)  # explicitly cross the descendant's scheduled late write, not an async success wait
            self.assertEqual(b'original class', existing.read_bytes(),
                             'a timed-out descendant must not overwrite restored class bytes')

    def assert_descendant_stopped(self, interrupt):
        # Real processes: the command waits for its descendant to start before exiting
        # or signalling the wrapper. DEVNULL prevents pipe lifetime hiding the defect.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ready, target = root / 'ready', root / 'class-bytes'
            target.write_text('restored')
            child = ("import time; from pathlib import Path; "
                     "Path(" + repr(str(ready)) + ").write_text('started'); time.sleep(.6); "
                     "Path(" + repr(str(target)) + ").write_text('late mutant')")
            parent = ("import os,signal,subprocess,sys,time; from pathlib import Path\n"
                      "subprocess.Popen([sys.executable,'-c'," + repr(child) + "], "
                      "stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)\n"
                      "deadline=time.monotonic()+5\n"
                      "while not Path(" + repr(str(ready)) + ").exists():\n"
                      " if time.monotonic()>deadline: raise RuntimeError('descendant never started')\n"
                      " time.sleep(.01)\n" +
                      ("os.kill(os.getppid(),signal.SIGTERM); time.sleep(10)\n" if interrupt else ""))
            wrapper = ("import signal,sys; sys.path.insert(0,'tools')\n"
                       "from mutation_gate_fast import run_with_timeout\n"
                       "def terminate(*_): raise KeyboardInterrupt('cancelled')\n"
                       "signal.signal(signal.SIGTERM,terminate)\n"
                       "try:\n"
                       " code, output = run_with_timeout([sys.executable,'-c'," + repr(parent) + "],10)\n"
                       " print('completed',code)\n"
                       "except KeyboardInterrupt: print('cancelled')\n")
            result = subprocess.run([sys.executable, '-c', wrapper], capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, 'the wrapper must complete its cleanup: ' + result.stderr)
            self.assertTrue(ready.exists(), 'the descendant must actually start before cleanup')
            self.assertIn('cancelled' if interrupt else 'completed 0', result.stdout,
                          'the requested cancellation or normal-exit path must run')
            time.sleep(.8)  # cross the scheduled write; absence is the wrong-result witness
            self.assertEqual('restored', target.read_text(),
                             'a descendant must not write after ' + ('SIGTERM cleanup' if interrupt else 'normal parent exit'))

    def test_sigterm_stops_descendants_before_returning_to_restoration(self):
        self.assert_descendant_stopped(True)

    def test_normal_parent_exit_does_not_leave_a_descendant_writer(self):
        self.assert_descendant_stopped(False)

    def test_unknown_registry_mutations_refuse_instead_of_silently_dropping_controls(self):
        base = "CONTROLS=[('a','site','old','new','Test#method')]\n"
        for suffix in ("if True: CONTROLS.append(('b','site','old','new','Test#other'))",
                       "for x in [1]: CONTROLS.clear()", "del CONTROLS[0]", "CONTROLS[0]=('b','s','o','n','T#m')",
                       "alias=CONTROLS\nalias.clear()"):
            with self.subTest(suffix=suffix), self.assertRaises(ValueError, msg='unsupported registry mutation must refuse'):
                policy.assignments(base + suffix, 'CONTROLS')

    def test_run_report_keeps_failed_jobs_from_earlier_attempts(self):
        run = {'status': 'completed', 'html_url': 'DEMO', 'head_sha': 'abc', 'event': 'pull_request',
               'conclusion': 'success', 'run_started_at': '2026-10-05T10:00:00Z',
               'updated_at': '2026-10-05T10:04:00Z', 'run_attempt': 2}
        job = {'status': 'completed', 'name': 'mutation-shards',
               'started_at': '2026-10-05T10:00:00Z', 'completed_at': '2026-10-05T10:01:00Z'}
        first = {**job, 'id': 1, 'run_attempt': 1, 'conclusion': 'failure'}
        second = {**job, 'id': 2, 'run_attempt': 2, 'conclusion': 'success'}
        def api(cmd, **kwargs):
            path = cmd[-1]
            return json.dumps({'jobs': [first, second] if 'filter=all' in path else [second]} if '/jobs?' in path else run)
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / 'report.json'
            with patch.object(sys, 'argv', ['report', '--run', '123', '--output', str(output)]), \
                 patch.object(run_report.subprocess, 'check_output', side_effect=api), contextlib.redirect_stdout(io.StringIO()):
                run_report.main()
            r = json.loads(output.read_text())
        self.assertEqual(2, r['jobExecutionMinutes'], 'failed earlier attempts must remain in total execution cost')
        self.assertEqual([1, 2], [j['attempt'] for j in r['jobs']], 'attempt identity must be retained')
        self.assertEqual(['failure', 'success'], [j['conclusion'] for j in r['jobs']], 'do not hide failed attempts')


class PolicyTest(unittest.TestCase):
    def setUp(self):
        self.cases = [('a', 'src/main/java/Example.java', 'old', 'new', 'ExampleTest#chosen'),
                      ('b', 'src/main/java/Other.java', 'old', 'new', 'OtherTest#chosen')]

    def select(self, old, changed):
        with patch.object(policy, 'registry_at', return_value=old), \
             patch.object(policy.fast, 'changed_files', return_value=changed), \
             patch.object(policy.subprocess, 'check_output', return_value='base'):
            return policy.selection(self.cases, 'base')

    def test_new_control_is_selected_even_when_site_unchanged(self):
        r = self.select(self.cases[:1], [])
        self.assertEqual(['b'], [c['name'] for c in r['selected']], 'new tuple must be selected without a changed site')
        self.assertEqual(['a'], [c['name'] for c in r['notRun']], 'unrun work stays explicit')

    def test_changed_witness_and_removed_control_are_reported(self):
        old = [('a', *self.cases[0][1:4], 'ExampleTest#earlier'),
               self.cases[1], ('removed', 'unused', 'o', 'n', 'Test#method')]
        r = self.select(old, [])
        self.assertEqual(['a'], r['changedMappings'], 'a changed witness changes the registry tuple')
        self.assertEqual(['removed'], r['removed'], 'retired controls must be visible for review')

    def test_test_only_changes_select_witness(self):
        r = self.select(self.cases, ['src/test/java/ExampleTest.java'])
        self.assertEqual(['a'], [c['name'] for c in r['selected']], 'test-only edits select their controls')

    def test_empty_selection_is_not_full(self):
        r = self.select(self.cases, [])
        self.assertFalse(r['full'], 'zero selected is feedback, not full evidence')
        self.assertEqual(2, len(r['notRun']), 'all omissions are named')

    def test_unknown_base_registry_and_files_force_full(self):
        with patch.object(policy, 'registry_at', side_effect=ValueError('unfamiliar syntax')):
            r = policy.selection(self.cases, 'HEAD')
        self.assertTrue(r['full'], 'an unreadable old registry must select every control')
        r = policy.selection(self.cases, 'missing-mg-test-ref')
        self.assertTrue(r['full'], 'a missing base must not yield empty success')
        r = self.select(self.cases, ['unknown/build.input'])
        self.assertTrue(r['full'], 'an unknown changed file requires full checks')

    def test_static_registry_parser_reproduces_live_registry(self):
        # Current registry source is unchanged by this feature; parse its historical form without executing it.
        session = policy.assignments(Path(policy.REGISTRY_FILES[1]).read_text(), 'CONTROLS')
        self.assertEqual(gate.CASES, policy.assignments(Path(policy.REGISTRY_FILES[0]).read_text(), 'CASES',
                                                     {'SESSION_CONTROLS': session}),
                         'historical tuple extraction must reproduce every actual control')
        with self.assertRaisesRegex(ValueError, 'cannot parse'):
            policy.assignments('CONTROLS = launch_a_process()', 'CONTROLS')

    def test_only_narrow_drafts_use_feedback(self):
        with patch.object(policy, 'selection', return_value={'full': False}) as select:
            self.assertEqual('feedback', policy.ci_plan(self.cases, 'pull_request', True, 'base')['mode'],
                             'a narrow draft must not start the full gate')
            self.assertEqual('full', policy.ci_plan(self.cases, 'pull_request', False, 'base')['mode'],
                             'ready always selects full')
            self.assertEqual('full', policy.ci_plan(self.cases, 'push', True, '')['mode'],
                             'main pushes never inherit PR draft state')
            self.assertEqual(1, select.call_count, 'only a draft needs subset selection')
        with patch.object(policy, 'selection', return_value={'full': True}):
            self.assertEqual('full', policy.ci_plan(self.cases, 'pull_request', True, 'base')['mode'],
                             'infrastructure changes force full even on drafts')

    def test_eight_workers_capped_only_by_registry_size(self):
        r = policy.ci_plan(gate.CASES, 'push', False, '')
        self.assertEqual(8, r['count'], 'the production plan uses eight isolated workers')
        self.assertEqual(list(range(8)), r['matrix'], 'matrix comes from the same count')
        self.assertEqual(2, policy.ci_plan(self.cases, 'push', False, '')['count'], 'no empty worker')
        parts, _ = shards.plan(gate.CASES, r['count'])
        self.assertEqual(sorted(c[0] for c in gate.CASES), sorted(c[0] for p in parts for c in p),
                         'eight workers still cover the full registry exactly once')

    def test_workflow_keeps_explicit_failure_and_pr_only_cancellation(self):
        text = Path('.github/workflows/ci.yml').read_text()
        self.assertIn('types: [opened, synchronize, reopened, ready_for_review, converted_to_draft]', text,
                      'draft state changes must schedule a run without a commit')
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", text,
                      'only a PR run may cancel older runs')
        self.assertIn("format('pr-{0}', github.event.pull_request.number)", text, 'cancellation is PR-specific')
        self.assertIn("format('run-{0}-{1}', github.run_id, github.run_attempt)", text, 'main runs have unique keys')
        deferred = text.split('- name: Full gate deferred while draft', 1)[1].split('- name:', 1)[0]
        self.assertIn('exit 1', deferred, 'a deferred gate must fail, not silently skip or conclude neutral')
        self.assertIn('fromJSON(needs.changes.outputs.shard_matrix)', text, 'matrix consumes the authoritative plan')
        self.assertNotIn('--count 4', text, 'collector must not keep the previous fixed count')
        self.assertNotIn('--shard-count 4', text, 'worker must not keep the previous fixed count')


if __name__ == '__main__':
    unittest.main()
