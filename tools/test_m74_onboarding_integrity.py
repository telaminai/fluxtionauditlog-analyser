"""PR #155's wrong-result regressions. Offline, synthetic evidence only; no token or model required."""
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('m74_integrity_runner', HERE / 'm74_onboarding_runner.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
FIX = HERE / 'fixtures/m74-onboarding'


class RunIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='DEMO-m74-integrity-')
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.cycle = self.base / 'cycle'
        self.cycle.mkdir()
        self.oracle = self.cycle / 'oracle.json'
        shutil.copy2(FIX / 'oracle-t1.json', self.oracle)
        for name, text in [('predictions.md', 'DEMO prediction'), ('operator.json', '{}'),
                           ('virgin-pack.md', '# DEMO\n1. **Q** — Where?\n')]:
            (self.cycle / name).write_text(text)
        r.seal(self.cycle)
        self.pins = dict(analyserJarSha256='a'*64, claudeBinSha256='b'*64, claudeVersion='DEMO', model='DEMO')
        self.run = self.cycle / 'runs/DEMO'
        r.init_run(self.cycle, self.run, 'T1', self.pins)
        for p in (FIX / 'run-t1-pass').iterdir():
            if p.name != 'config.json':
                shutil.copy2(p, self.run / p.name)

    def score(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = r.main(['score', str(self.run), str(self.oracle)])
        return rc, json.loads(out.getvalue()) if out.getvalue() else None, err.getvalue()

    def edit(self, name, edit):
        p = self.run / name
        doc = json.loads(p.read_text())
        edit(doc)
        p.write_text(json.dumps(doc))

    def test_the_sealed_control_still_passes(self):
        rc, card, _ = self.score()
        self.assertEqual((rc, card['verdict']), (0, 'PASS'))

    def test_changed_sealed_oracle_is_refused(self):
        snaps = r.load_jsonl(self.run / 'snapshots.jsonl')
        for s in snaps:
            for p in s.get('pointers', []):
                p['logTime'] = 999
        (self.run / 'snapshots.jsonl').write_text(''.join(json.dumps(s)+'\n' for s in snaps))
        self.assertEqual(self.score()[1]['verdict'], 'FAIL', 'the original oracle rejects the wrong pointer')
        score_before = (self.run / 'score.json').read_bytes()
        doc = json.loads(self.oracle.read_text())
        doc['primary'][0]['match']['logTime'] = 999
        self.oracle.write_text(json.dumps(doc))
        rc, _, _ = self.score()
        self.assertEqual(rc, 2, 'a changed-after-seal oracle must not turn FAIL into PASS')
        self.assertEqual(score_before, (self.run / 'score.json').read_bytes(), 'refusal preserves the previous verdict')

    def test_missing_oracle_pin_is_refused(self):
        self.edit('config.json', lambda d: d['pinned'].pop('oracle.json'))
        self.assertEqual(self.score()[0], 2, 'a run without its oracle pin cannot certify a verdict')

    def test_export_count_must_match_sink_total(self):
        self.edit('session-record.meta.json', lambda d: d.update(total=21000))
        _, card, _ = self.score()
        self.assertEqual(card['verdict'], 'VOID', '21 records are not the declared 21000-record export')
        self.assertFalse(card['complete'])

    def test_missing_or_invalid_completeness_fields_are_not_zero(self):
        p = self.run / 'session-record.meta.json'
        original = json.loads(p.read_text())
        for key in ('total', 'dropped', 'sinkFailures'):
            for value in (None, True, -1):
                with self.subTest(key=key, value=value):
                    p.write_text(json.dumps({**original, key: value}))
                    card = self.score()[1]
                    self.assertFalse(card['complete'])
                    self.assertEqual(card['verdict'], 'VOID')

    def test_missing_answers_are_not_proof_of_no_contradiction(self):
        (self.run / 'answers.jsonl').unlink()
        card = self.score()[1]
        self.assertEqual(card['verdict'], 'VOID', 'a missing capture is not an empty answer')
        self.assertIsNone(card['checks']['noContradiction'], 'missing evidence cannot establish a negative')

    def test_an_explicit_empty_answer_stream_is_distinct_from_a_missing_capture(self):
        (self.run / 'answers.jsonl').write_text('')
        self.assertEqual(self.score()[1]['verdict'], 'PASS', 'required phrases are intentionally informational')

    def test_nonempty_answer_rows_require_text_not_a_silent_empty_default(self):
        self.assertEqual(self.score()[1]['verdict'], 'PASS')
        previous = (self.run / 'score.json').read_bytes()
        for row in ({'ticket': 1, 'message': 'never breached'}, {},
                    *({'ticket': 1, 'text': value} for value in (None, False, 1, [], {}))):
            with self.subTest(row=row):
                (self.run / 'answers.jsonl').write_text(json.dumps(row)+'\n')
                rc, card, err = self.score()
                self.assertEqual(rc, 2, 'unsupported answer rows cannot certify no contradiction')
                self.assertIsNone(card)
                self.assertIn('answers.jsonl', err)
                self.assertEqual(previous, (self.run / 'score.json').read_bytes())
        (self.run / 'answers.jsonl').write_text('{"ticket":1,"text":"never breached"}\n')
        self.assertEqual(self.score()[1]['verdict'], 'FAIL', 'valid captured contradictions must still be scored')

    def test_current_person_decisions_without_operator_acts_void_the_run(self):
        record = self.run / 'session-record.yaml'
        original = record.read_text()
        operator = self.run / 'operator.jsonl'
        original_acts = operator.read_text()
        meta = json.loads((self.run / 'session-record.meta.json').read_text())
        for event, fields, kind in (
                ('ProposalItemAnswered', 'proposalId=p-DEMO, itemId=DEMO-1, yes=true', 'review'),
                ('LocalAgentRestartAnswered', 'request=99, offer=1, restart=true, consent=null', 'restart'),
                ('StartPageNoLogAnswered', 'request=99, choice=ASK_ANYWAY', 'noLog'),
                ('LocalAgentNewChatRequested', 'origin=New chat', 'newChat'),
                ('LocalAgentRestartOfferRequested', 'origin=restart chip', 'restartOffer')):
            with self.subTest(event=event):
                operator.write_text(original_acts)
                record.write_text(original+'\n---\neventLogRecord:\n event: '+event+
                                  '\n eventToString: '+event+'['+fields+']\n nodeLogs:\n---\n')
                self.edit('session-record.meta.json', lambda d: d.update(total=meta['total']+1))
                rc, card, _ = self.score()
                self.assertEqual(rc, 0)
                self.assertEqual(card['verdict'], 'VOID', 'a current person decision cannot bypass correlation')
                self.assertEqual(card['boundary']['factsWithoutAnAct'], {kind: 1})
                fact = {key: value for key, value in r.parse_fields(fields).items() if key != 'consent'}
                act = {'t': 50, 'kind': kind, 'source': 'script', 'fact': fact}
                operator.write_text(original_acts+json.dumps(act)+'\n')
                self.assertEqual(self.score()[1]['verdict'], 'PASS', 'matching current decisions remain valid')
                key = next(iter(fact))
                act['fact'][key] = 'different-DEMO'
                operator.write_text(original_acts+json.dumps(act)+'\n')
                self.assertEqual(self.score()[1]['verdict'], 'VOID', 'another decision is not the missing act')

    def test_unknown_operator_kinds_expecting_a_fact_are_not_silently_ignored(self):
        rows = r.load_jsonl(self.run / 'operator.jsonl')
        rows.append({'t': 50, 'kind': 'unrecognised-DEMO', 'source': 'script'})
        (self.run / 'operator.jsonl').write_text(''.join(json.dumps(row)+'\n' for row in rows))
        card = self.score()[1]
        self.assertEqual(card['verdict'], 'VOID', 'unknown fact-bearing operator kinds must fail closed')
        self.assertEqual(card['boundary']['unsupportedActs'], ['unrecognised-DEMO'])

    def test_missing_reaping_evidence_is_not_safe(self):
        self.edit('meta.json', lambda d: d.pop('reaped'))
        self.assertEqual(self.score()[1]['verdict'], 'VOID', 'unknown process reaping cannot be certified safe')

    def test_snapshot_record_sequences_are_bounded(self):
        original = (self.run / 'snapshots.jsonl').read_text()
        for seq in (-1, 21000, None):
            with self.subTest(seq=seq):
                rows = [json.loads(s) for s in original.splitlines()]
                for row in rows:
                    if seq is None:
                        row.pop('recordSeq')
                    else:
                        row['recordSeq'] = seq
                (self.run / 'snapshots.jsonl').write_text(''.join(json.dumps(s)+'\n' for s in rows))
                self.assertEqual(self.score()[1]['verdict'], 'VOID', 'a snapshot cannot borrow absent records')

    def test_observer_times_cannot_precede_or_reverse_the_run(self):
        original = r.load_jsonl(self.run / 'snapshots.jsonl')
        for times in ((-1, 41.3), (42, 18), (float('inf'), 41.3)):
            with self.subTest(times=times):
                rows = copy.deepcopy(original)
                for row, stamp in zip(rows, times):row['t'] = stamp
                (self.run / 'snapshots.jsonl').write_text(''.join(json.dumps(s)+'\n' for s in rows))
                self.assertEqual(self.score()[1]['verdict'], 'VOID')

    def test_person_action_order_must_match(self):
        rows = r.load_jsonl(self.run / 'operator.jsonl')
        rows[3]['kind'], rows[4]['kind'] = rows[4]['kind'], rows[3]['kind']
        (self.run / 'operator.jsonl').write_text(''.join(json.dumps(s)+'\n' for s in rows))
        card = self.score()[1]
        self.assertFalse(card['boundary']['ok'], 'equal counts cannot approve Send-before-Start against Start-before-Send')
        self.assertEqual(card['verdict'], 'VOID')

    def test_a_wrong_decision_identity_is_not_the_matching_person_act(self):
        records = r.parse_session_record('---\neventLogRecord:\n event: LocalAgentPermissionChosen\n'
            ' eventToString: LocalAgentPermissionChosen[requestRef=9, allow=true]\n nodeLogs:\n---\n')
        good = [{'kind': 'permission', 'fact': {'requestRef': 9, 'allow': True}}]
        self.assertTrue(r.boundary_check(good, records)['ok'])
        for fact in ({'requestRef': 10, 'allow': True}, {'requestRef': 9, 'allow': False}, {}):
            with self.subTest(fact=fact):
                self.assertFalse(r.boundary_check([{'kind': 'permission', 'fact': fact}], records)['ok'],
                                 'same-kind counts cannot substitute another request or answer')

    def test_seal_requires_its_inputs(self):
        for name in ('oracle.json', 'operator.json', 'virgin-pack.md'):
            with self.subTest(name=name):
                cycle = self.base / name.replace('.', '-')
                cycle.mkdir()
                for src in self.cycle.iterdir():
                    if src.is_file() and src.name not in ('seal.json', name):
                        shutil.copy2(src, cycle / src.name)
                with self.assertRaises(ValueError, msg='missing seal inputs must not be silently omitted'):
                    r.seal(cycle)

    def test_pins_cannot_overwrite_cycle_ownership(self):
        for key, value in [('track', 'T5'), ('posture', 'external'), ('pinned', {}), ('predictionsSha256', 'changed')]:
            with self.subTest(key=key), self.assertRaises(ValueError, msg='pins must not replace seal-owned metadata'):
                r.init_run(self.cycle, self.base / ('run-'+key), 'T1', {**self.pins, key: value})


class SeedAndGrammarIntegrityTest(unittest.TestCase):
    def test_obsolete_permission_fields_do_not_become_denials(self):
        records = r.parse_session_record('---\neventLogRecord:\n event: LocalAgentPermissionAnswered\n'
            ' eventToString: LocalAgentPermissionAnswered[decision=ALLOW, by=PERSON]\n nodeLogs:\n---\n')
        with self.assertRaises(ValueError, msg='obsolete input is unavailable, not a real denial'):
            r.approvals(records)

    def test_destination_alias_is_refused_before_copying(self):
        with tempfile.TemporaryDirectory(prefix='DEMO-m74-dest-') as t:
            base=Path(t);seed=base/'seed';seed.mkdir()
            (seed/'core.jar').write_bytes(b'DEMO')
            (seed/'_remote.repositories').write_text('core.jar>central=\n')
            outside=base/'outside';outside.mkdir()
            dest=base/'copy';dest.symlink_to(outside)
            with self.assertRaises(ValueError):r.seed_m2(seed,dest)
            self.assertEqual(list(outside.iterdir()),[],'a rejected alias must not receive partial writes')

    def test_external_file_and_directory_links_are_not_frozen_seeds(self):
        for directory in (False, True):
            with self.subTest(directory=directory), tempfile.TemporaryDirectory(prefix='DEMO-m74-seed-') as t:
                base = Path(t)
                seed = base / 'seed';seed.mkdir()
                (seed / 'core.jar').write_bytes(b'DEMO regular')
                (seed / '_remote.repositories').write_text('core.jar>central=\nlinked.jar>central=\n')
                outside = base / 'outside';outside.mkdir()
                (outside / 'DEMO.jar').write_bytes(b'DEMO external')
                (seed / ('linked' if directory else 'linked.jar')).symlink_to(outside if directory else outside/'DEMO.jar')
                with self.assertRaises(ValueError, msg='a clean-looking seed must not retain live external aliases'):
                    r.seed_m2(seed, base/'copy')
                self.assertFalse((base/'copy').exists())

    def test_a_seed_root_alias_is_not_a_frozen_seed(self):
        with tempfile.TemporaryDirectory(prefix='DEMO-m74-seed-') as t:
            base=Path(t);seed=base/'seed';seed.mkdir()
            (seed/'core.jar').write_bytes(b'DEMO')
            (seed/'_remote.repositories').write_text('core.jar>central=\n')
            alias=base/'alias';alias.symlink_to(seed)
            with self.assertRaises(ValueError):r.seed_m2(alias, base/'copy')

    def test_example_nbsp_is_not_normalised_away(self):
        data = '# T\n1. **X** — example: https://github.com/telaminai/diamond/archive/'+'a'*40+'.zip\u00a0\n'
        self.assertEqual(r.lint_pack(data).get('refusal'), {'line': 2, 'cause': 'EXAMPLE_NOT_ARCHIVE'},
                         'the analyser refuses this exact URL; the offline mirror must do so too')

    def test_ci_executes_current_java_after_compilation(self):
        ci=(HERE.parent/'.github/workflows/ci.yml').read_text()
        check='python3 tools/m74_record_pack_crosscheck.py . --check'
        self.assertIn(check, ci, 'saved Java verdicts alone are not a current-parser check')
        self.assertLess(ci.index('run: mvn -B verify'), ci.index(check))


if __name__ == '__main__':
    unittest.main()
