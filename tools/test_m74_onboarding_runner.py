"""Tests for the M74 onboarding runner's offline half (tools/m74_onboarding_runner.py).

Nothing here starts `claude`, reads a key or a token, touches the network or needs M74 code: the pack grammar is pinned
to verdicts recorded from the analyser's own Java parser (fixtures/m74-onboarding/pack-crosscheck.json), and the
scorecard is computed over a synthetic recorded run (fixtures/m74-onboarding/run-t1-pass). Every check that decides a
verdict has a wrong-result control: the fixture is mutated the way a real failure would look, and the verdict must move.
"""
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).parent
spec = importlib.util.spec_from_file_location('m74_onboarding_runner', HERE / 'm74_onboarding_runner.py')
r = importlib.util.module_from_spec(spec)
sys.modules['m74_onboarding_runner'] = r
spec.loader.exec_module(r)

FIX = HERE / 'fixtures/m74-onboarding'
PACKS = HERE.parent / 'docs/proposals/beta-testing/m74-onboarding'


class PackGrammarTest(unittest.TestCase):
    def test_mirror_agrees_with_the_java_parser(self):
        cases = json.loads((FIX / 'pack-crosscheck.json').read_text())['cases']
        self.assertGreaterEqual(len(cases), 89)
        for name, c in cases.items():
            with self.subTest(name):
                got = r.lint_pack(c['input'])
                if 'cause' in c['java']:
                    self.assertEqual(got.get('refusal'), c['java'])
                else:
                    self.assertIn('pack', got, got.get('refusal'))
                    pack, java = got['pack'], c['java']
                    self.assertEqual(len(pack['entries']), java['entries'])
                    third = any(e.get('example', {}).get('thirdParty') for e in pack['entries'])
                    self.assertEqual(third, java['thirdParty'])
                    # the forgiving reader (69eb2fac): what it read, and each fix it made, kind by kind and line by line
                    self.assertEqual(pack['title'], java['title'])
                    self.assertEqual(pack['description'] or '', java['description'])
                    self.assertEqual([e['label'] for e in pack['entries']], java['labels'])
                    self.assertEqual([e.get('text') for e in pack['entries']], java['texts'])
                    self.assertEqual(pack['normalised'], java['normalised'])

    def test_the_crosscheck_covers_every_fix_and_every_refusal_that_stays(self):
        """The forgiving reader (69eb2fac): each kind of fix, and each refusal it keeps, is in the Java-recorded set."""
        cases = json.loads((FIX / 'pack-crosscheck.json').read_text())['cases']
        fixes = {f for c in cases.values() for _, f in c['java'].get('normalised', [])}
        self.assertEqual(fixes, set(r.FIXES))
        causes = {c['java']['cause'] for c in cases.values() if 'cause' in c['java']}
        for kept in ('HTML', 'IMAGE', 'HIDDEN_CHARACTER', 'TOO_MANY', 'LABEL_TOO_LONG', 'TEXT_TOO_LONG', 'TITLE_TOO_LONG',
                     'DESCRIPTION_TOO_LONG', 'PLACEHOLDER', 'LINK', 'MISSING_LABEL', 'EMPTY_TEXT', 'EXAMPLE_MALFORMED'):
            self.assertIn(kept, causes)
        for name in ('fix-dash-hyphen', 'fix-dash-double-hyphen', 'fix-dash-en', 'fix-dash-colon', 'fix-colon-in-bold',
                     'fix-plain-label-em', 'fix-bare-first-sentence', 'fix-bare-first-words-capped', 'fix-bullet-hyphen',
                     'fix-bullet-star', 'fix-bullet-plus', 'fix-paren-number', 'fix-blank-lines',
                     'fix-trailing-whitespace', 'fix-no-description', 'refuse-unclosed-bold', 'refuse-example-bullet',
                     'refuse-example-hyphen-dash', 'refuse-example-plain-label', 'refuse-bullet-link'):
            self.assertIn(name, cases)

    def test_a_variation_reads_to_the_canonical_pack(self):
        """Wrong-result control: the strict reader refused these as MISSING_LABEL / NOT_AN_ENTRY; now each reads to the
        same entries as the canonical file and only the note differs."""
        canonical = ('# Triage\nQuestions.\n\n1. **First breach** — Where first?\n'
                     '2. **Quiet nodes** — Which never logged?\n')
        want = r.lint_pack(canonical)['pack']
        self.assertEqual(want['normalised'], [])
        for variant, fix in ((canonical.replace('** — ', '** - '), 'DASH'),
                             (canonical.replace('1. ', '- ').replace('2. ', '* '), 'BULLET'),
                             (canonical.replace('2. **Quiet nodes** —', '2. Quiet nodes —'), 'LABEL_FROM_TEXT'),
                             (canonical.replace('?\n', '?  \n'), 'TRAILING_WHITESPACE')):
            with self.subTest(fix):
                got = r.lint_pack(variant)['pack']
                self.assertEqual(got['entries'], want['entries'])
                self.assertEqual({f for _, f in got['normalised']}, {fix})
        self.assertEqual(r.lint_pack('# T\n- **A — b\n')['refusal']['cause'], 'MISSING_LABEL', 'an unclosed ** is refused')
        self.assertEqual(r._first_words('w' * 70, 60), 'w' * 60)
        self.assertEqual(r.lint_pack('# T\n1. **a** — b\u00a0\n')['pack']['entries'][0]['text'], 'b\u00a0',
                         "Java's strip keeps a no-break space; str.strip would not")

    def test_every_committed_pack_parses(self):
        packs = [PACKS / 'virgin-pack.md', *sorted((PACKS / 'packs').glob('*.md'))]
        self.assertGreaterEqual(len(packs), 6)
        for p in packs:
            with self.subTest(p.name):
                got = r.lint_pack(p.read_bytes())
                self.assertIn('pack', got, got.get('refusal'))
                self.assertLessEqual(len(got['pack']['entries']), 9)

    def test_the_placeholder_example_is_marked_and_third_party(self):
        got = r.lint_pack((PACKS / 'virgin-pack.md').read_bytes())['pack']
        self.assertIn('PLACEHOLDER', got['description'])
        examples = [e['example'] for e in got['entries'] if 'example' in e]
        self.assertEqual(len(examples), 1)
        self.assertEqual(examples[0]['commit'], '0' * 40)
        self.assertTrue(examples[0]['thirdParty'])

    def test_virgin_prompts_name_no_verb_and_no_record(self):
        """Prompt leakage (PROTOCOL §4.2): a virgin prompt says what the person wants, never how the tool does it."""
        for p in [PACKS / 'virgin-pack.md', *sorted((PACKS / 'packs').glob('*.md'))]:
            for e in r.lint_pack(p.read_bytes())['pack']['entries']:
                text = e.get('text', '')
                with self.subTest(p.name, label=e['label']):
                    self.assertNotRegex(text, r'analyser_|\bopen \{|\brecord #|#\d|\.yaml|\.graphml|spotlight')

    def test_every_published_pack_lints(self):
        """The teaching packs under docs/proposals/packs/ are dropped on the start page as they are: each must parse."""
        packs = sorted((HERE.parent / 'docs/proposals/packs').glob('*/[0-9]*.md'))
        self.assertGreaterEqual(len(packs), 8)
        for p in packs:
            with self.subTest(p.name):
                self.assertIn('pack', r.lint_pack(p.read_bytes()), p.name)

    def test_a_literal_angle_placeholder_is_refused_as_java_refuses_it(self):
        """Control for the cross-check finding: urlsplit alone accepted `<40-hex>` and called it a moving ref."""
        got = r.lint_pack('# T\n1. **x** — example: https://github.com/example/demo/archive/<40-hex>.zip\n')
        self.assertEqual(got['refusal']['cause'], 'EXAMPLE_NOT_ARCHIVE')


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.m2 = Path(self.tmp.name) / 'seed'

    def tearDown(self):
        self.tmp.cleanup()

    def artefact(self, group, name, version, remote='central', files=('jar', 'pom')):
        d = self.m2 / group / name / version
        d.mkdir(parents=True, exist_ok=True)
        lines = ['#NOTE: This is a Maven Resolver internal implementation file']
        for ext in files:
            f = f'{name}-{version}.{ext}'
            (d / f).write_bytes(f'{name} {ext}'.encode())
            if remote is not None:
                lines.append(f'{f}>{remote}=')
        (d / '_remote.repositories').write_text('\n'.join(lines) + '\n')
        return d

    def test_clean_seed(self):
        self.artefact('org/acme', 'core', '1.0.0')
        self.artefact('com/acme', 'starter', '2.0.0', remote='repsy-fluxtion-public')
        v = r.m2_provenance(self.m2)
        self.assertTrue(v['clean'], v)
        self.assertEqual(v['artefactFilesByRemote'], {'central': 2, 'repsy-fluxtion-public': 2})

    def test_each_kind_of_dirty_artefact_is_refused(self):
        for label, kwargs, problem in (('snapshot', {'version': '1.0-SNAPSHOT'}, 'snapshot'),
                                       ('local', {'remote': ''}, 'locally-installed'),
                                       ('none', {'remote': None}, 'no-provenance'),
                                       ('other', {'remote': 'mirror-corp'}, 'other-remote:mirror-corp')):
            with self.subTest(label):
                shutil.rmtree(self.m2, ignore_errors=True)
                self.artefact('org/acme', 'core', '1.0.0')
                self.artefact('org/acme', 'bad', kwargs.pop('version', '1.0.0'), **kwargs)
                v = r.m2_provenance(self.m2)
                self.assertFalse(v['clean'])
                self.assertEqual(v['problems'][0][0], problem)

    def test_an_empty_repository_is_not_a_clean_seed(self):
        self.m2.mkdir(parents=True)
        self.assertFalse(r.m2_provenance(self.m2)['clean'])

    def test_seed_records_the_tree_digest_and_refuses_reuse(self):
        self.artefact('org/acme', 'core', '1.0.0')
        dest = Path(self.tmp.name) / 'run/tmp/m2'
        rec = r.seed_m2(self.m2, dest)
        self.assertEqual(rec['seedTreeSha256'], r.tree_sha256(dest))
        with self.assertRaisesRegex(ValueError, 'not empty'):
            r.seed_m2(self.m2, dest)

    def test_a_dirty_seed_is_never_copied(self):
        self.artefact('org/acme', 'core', '1.0.0', remote='')
        dest = Path(self.tmp.name) / 'run/tmp/m2'
        with self.assertRaisesRegex(ValueError, 'not provenance-clean'):
            r.seed_m2(self.m2, dest)
        self.assertFalse(dest.exists())

    def test_tree_digest_moves_with_content(self):
        self.artefact('org/acme', 'core', '1.0.0')
        before = r.tree_sha256(self.m2)
        (self.m2 / 'org/acme/core/1.0.0/core-1.0.0.jar').write_bytes(b'changed')
        self.assertNotEqual(before, r.tree_sha256(self.m2))


class SealTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cycle = Path(self.tmp.name) / 'cycle'
        self.cycle.mkdir()
        (self.cycle / 'predictions.md').write_text('1. T1 haiku passes 2 of 3\n')
        (self.cycle / 'oracle.json').write_text('{"primary": []}\n')
        (self.cycle / 'virgin-pack.md').write_text('# DEMO\n1. **Q** — Where?\n')
        (self.cycle / 'operator.json').write_text('{}\n')
        self.pins = {'analyserJarSha256': 'a', 'claudeBinSha256': 'b', 'claudeVersion': '2.x', 'model': 'm'}

    def tearDown(self):
        self.tmp.cleanup()

    def test_seal_is_one_shot_and_verifies(self):
        s = r.seal(self.cycle)
        self.assertIn('oracle.json', s['pinned'])
        self.assertEqual(r.verify_seal(self.cycle)['predictionsSha256'], s['predictionsSha256'])
        with self.assertRaisesRegex(ValueError, 'written once'):
            r.seal(self.cycle)

    def test_an_edited_prediction_or_oracle_is_refused(self):
        r.seal(self.cycle)
        (self.cycle / 'oracle.json').write_text('{"primary": [1]}\n')
        with self.assertRaisesRegex(ValueError, 'oracle.json changed'):
            r.verify_seal(self.cycle)
        (self.cycle / 'oracle.json').write_text('{"primary": []}\n')
        (self.cycle / 'predictions.md').write_text('1. T1 haiku passes 3 of 3\n')
        with self.assertRaisesRegex(ValueError, 'predictions.md changed'):
            r.verify_seal(self.cycle)

    def test_init_run_refuses_unsealed_unpinned_and_used(self):
        run = self.cycle / 'runs/t1-h-1'
        with self.assertRaisesRegex(ValueError, 'not sealed'):
            r.init_run(self.cycle, run, 'T1', self.pins)
        r.seal(self.cycle)
        with self.assertRaisesRegex(ValueError, 'unpinned: model'):
            r.init_run(self.cycle, run, 'T1', {**self.pins, 'model': ''})
        cfg = r.init_run(self.cycle, run, 'T1', self.pins)
        self.assertEqual(cfg['needs'], 'P2')
        with self.assertRaisesRegex(ValueError, 'never overwritten'):
            r.init_run(self.cycle, run, 'T1', self.pins)


class IsolationTest(unittest.TestCase):
    def test_env_scrubs_inherited_identity_and_isolates_both_homes(self):
        inherited = {'PATH': '/usr/bin:/bin', 'HOME': '/home/owner', 'CLAUDECODE': '1',
                     'CLAUDE_CODE_SESSION_ID': 'x', 'CLAUDE_CONFIG_DIR': '/home/owner/.claude',
                     'ANTHROPIC_API_KEY': 'k', 'FLUXTION_API_KEY': 'f', 'MAVEN_OPTS': '-Dx', 'LANG': 'C'}
        env = r.analyser_env('/private/tmp/fx-m74-t1', inherited, 'TOKEN', '/private/tmp/fx-m74-t1/cli', '/jdk')
        self.assertEqual(env['HOME'], '/private/tmp/fx-m74-t1/home')
        self.assertEqual(env['CLAUDE_CONFIG_DIR'], '/private/tmp/fx-m74-t1/home/.claude-virgin')
        self.assertEqual(env['CLAUDE_CODE_OAUTH_TOKEN'], 'TOKEN')
        for gone in ('CLAUDECODE', 'CLAUDE_CODE_SESSION_ID', 'ANTHROPIC_API_KEY', 'FLUXTION_API_KEY', 'MAVEN_OPTS'):
            self.assertNotIn(gone, env)
        self.assertTrue(env['PATH'].startswith('/private/tmp/fx-m74-t1/cli:'))
        self.assertNotIn('/home/owner', json.dumps(env))
        self.assertEqual(env['LANG'], 'C')

    def test_maven_is_redirected_only_when_asked(self):
        env = r.analyser_env('/b', {'PATH': '/bin'}, 't', '/w', '/jdk', maven=True)
        self.assertEqual(env['MAVEN_OPTS'], '-Dmaven.repo.local=/b/tmp/m2')

    @unittest.skipIf(sys.platform == 'win32', 'POSIX shell')
    def test_wrapper_adds_strict_mcp_config_once(self):
        with tempfile.TemporaryDirectory() as t:
            fake = Path(t) / 'real-claude'
            fake.write_text('#!/bin/sh\nfor a in "$@"; do echo "$a"; done\n')
            fake.chmod(0o755)
            info = r.write_wrapper(Path(t) / 'cli', fake)
            out = subprocess.run([info['wrapper'], '-p', '--model', 'm'], capture_output=True, text=True).stdout
            self.assertEqual(out.split(), ['--strict-mcp-config', '-p', '--model', 'm'])
            out = subprocess.run([info['wrapper'], '-p', '--strict-mcp-config'], capture_output=True, text=True).stdout
            self.assertEqual(out.split().count('--strict-mcp-config'), 1)

    def test_a_used_config_dir_is_not_virgin(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t) / '.claude-virgin'
            self.assertTrue(r.assert_virgin_config(d)['emptyAtStart'])
            (d / 'projects/x/memory').mkdir(parents=True)
            (d / 'projects/x/memory/MEMORY.md').write_text('a remembered answer')
            with self.assertRaisesRegex(ValueError, 'not empty'):
                r.assert_virgin_config(d)


class SessionRecordTest(unittest.TestCase):
    def test_parses_events_fields_and_node_logs(self):
        recs = r.parse_session_record((FIX / 'run-t1-pass/session-record.yaml').read_text())
        self.assertEqual(len(recs), 21)
        init = next(x for x in recs if x['event'] == 'LocalAgentInitObserved')
        self.assertEqual(init['fields']['servers'], ['fluxtion-analyser-hosted:connected'])
        self.assertIn('Read', init['fields']['tools'])
        sent = next(x for x in recs if x['event'] == 'StartPagePromptSent')
        self.assertEqual(sent['fields']['consent']['@type'], 'LocalAgentConsent')
        self.assertEqual(r.node_values(recs, 'localAgentTurnEnded')[0][1], 'COMPLETED')

    def test_nested_record_strings(self):
        name, f = r.parse_event_to_string('A[x=1, y=[p, q], z=B[w=true, v=null], s=two words]')
        self.assertEqual((name, f['x'], f['y'], f['z']['w'], f['z']['v'], f['s']),
                         ('A', 1, ['p', 'q'], True, None, 'two words'))


class ScorecardTest(unittest.TestCase):
    """The recorded fixture scores PASS; each control mutates it the way a real failure looks."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.run = Path(self.tmp.name) / 'run'
        shutil.copytree(FIX / 'run-t1-pass', self.run)
        self.oracle = FIX / 'oracle-t1.json'

    def tearDown(self):
        self.tmp.cleanup()

    def score(self):
        return r.score_run(self.run, self.oracle)

    def edit_jsonl(self, name, fn):
        p = self.run / name
        rows = [json.loads(x) for x in p.read_text().splitlines() if x.strip()]
        p.write_text(''.join(json.dumps(x) + '\n' for x in fn(rows)))

    def test_fixture_scores_pass_with_every_field_sourced(self):
        c = self.score()
        self.assertEqual(c['verdict'], 'PASS', c['void'])
        self.assertEqual(c['timeToFirstCorrectSeconds'], 41.3)
        self.assertEqual((c['sendCount'], c['sendsToFirstCorrect'], c['startPageSends'], c['starts']), (2, 2, 1, 1))
        self.assertEqual(c['refusalsByReason'], {'hostedCall:REFUSED': 1, 'note:PERMISSION_DENIED': 1})
        self.assertEqual(c['hostedCalls']['admitted'], 3)
        self.assertEqual(c['cost']['totalUsd'], 0.093)
        self.assertTrue(c['cost']['basis'].startswith('notional'))
        self.assertEqual([d['type'] for d in c['deadEnds']], ['ANSWERED_WITHOUT_LOOKING'])
        self.assertEqual(c['operator']['nudges'], 1)
        self.assertTrue(c['hostedCalls']['byVerb'].startswith('unavailable: R1'))

    def test_control_no_pointer_at_the_oracle_record_fails(self):
        self.edit_jsonl('snapshots.jsonl', lambda rows: [{**x, 'pointers': []} for x in rows])
        c = self.score()
        self.assertEqual(c['verdict'], 'FAIL')
        self.assertIsNone(c['timeToFirstCorrectSeconds'])
        self.assertIn('NO_CORRECT_ANSWER', [d['type'] for d in c['deadEnds']])

    def test_control_pointer_at_the_wrong_record_fails(self):
        self.edit_jsonl('snapshots.jsonl', lambda rows: [
            {**x, 'pointers': [{**p, 'logTime': p['logTime'] + 1} for p in x['pointers']]} for x in rows])
        self.assertEqual(self.score()['verdict'], 'FAIL')

    def test_control_a_send_the_operator_did_not_press_voids_the_run(self):
        self.edit_jsonl('operator.jsonl', lambda rows: [x for x in rows if not x.get('nudge')])
        c = self.score()
        self.assertEqual(c['verdict'], 'VOID')
        self.assertEqual(c['boundary']['factsWithoutAnAct'], {'send': 1})

    def test_control_a_press_with_no_fact_voids_the_run(self):
        self.edit_jsonl('operator.jsonl', lambda rows: rows + [{'t': 50, 'kind': 'start', 'source': 'script'}])
        c = self.score()
        self.assertIn('BOUNDARY', c['void'])
        self.assertEqual(c['boundary']['actsWithoutAFact'], {'start': 1})

    def test_control_a_second_mcp_server_is_contamination(self):
        p = self.run / 'session-record.yaml'
        p.write_text(p.read_text().replace('servers=[fluxtion-analyser-hosted:connected]',
                                           'servers=[fluxtion-analyser-hosted:connected, owner-db:connected]', 1))
        c = self.score()
        self.assertIn('POSTURE_OR_CONTAMINATION', c['void'])

    def test_control_a_foreign_mcp_tool_is_contamination(self):
        p = self.run / 'session-record.yaml'
        p.write_text(p.read_text().replace('tools=[Glob,', 'tools=[mcp__owner-db__query, Glob,', 1))
        self.assertIn('POSTURE_OR_CONTAMINATION', self.score()['void'])

    def test_control_a_dropped_record_is_incomplete(self):
        (self.run / 'session-record.meta.json').write_text('{"total": 2100, "dropped": 100, "sinkFailures": 0}')
        self.assertIn('INCOMPLETE_RECORD', self.score()['void'])

    def test_control_a_contradicting_answer_fails(self):
        self.edit_jsonl('answers.jsonl', lambda rows: rows + [{'ticket': 2, 'text': 'There was no breach at all.'}])
        c = self.score()
        self.assertEqual(c['verdict'], 'FAIL')
        self.assertEqual(c['correctness']['claims']['contradictions'], 1)

    def test_control_reading_the_answer_key_is_an_oracle_leak(self):
        with (self.run / 'cli-session.jsonl').open('w') as f:
            f.write(json.dumps({'tool': 'WebFetch', 'url': 'https://raw.githubusercontent.com/telaminai/'
                                'fluxtionauditlog-analyser/main/docs/proposals/beta-testing/m74-onboarding/'
                                'SCORECARD.md'}) + '\n')
        self.assertIn('ORACLE_LEAK', self.score()['void'])

    def test_control_a_turn_that_did_not_complete_is_a_dead_end(self):
        p = self.run / 'session-record.yaml'
        p.write_text(p.read_text().replace('localAgentTurnEnded: COMPLETED, ticket: 1',
                                           'localAgentTurnEnded: PROCESS_EXIT, ticket: 1'))
        types = [d['type'] for d in self.score()['deadEnds']]
        self.assertIn('TURN_NOT_COMPLETED', types)

    def test_cli_score_writes_score_json(self):
        import contextlib
        import io
        with contextlib.redirect_stdout(io.StringIO()):
            rc = r.main(['score', str(self.run), str(FIX / 'oracle-t1.json')])
        self.assertEqual(rc, 0)
        card = json.loads((self.run / 'score.json').read_text())
        self.assertEqual(card['correctness']['oracleSha256'], r.sha256_file(FIX / 'oracle-t1.json'))


class ProposalAndApprovalTest(unittest.TestCase):
    RECORD = '\n'.join([
        '---', 'eventLogRecord: ', '    event: ProposalReceived', '    eventToString: ProposalReceived[request=1, '
        'generation=1, summary=s, items=[]]', '    nodeLogs: ', '        - onboardingProposal: { proposal: p-1, '
        'items: 3, previewing: 3}',
        '---', 'eventLogRecord: ', '    event: ProposalApplyRequested', '    eventToString: ProposalApplyRequested['
        'proposalId=p-1, yes=[p-1.1, p-1.2], createProject=true]', '    nodeLogs: ', '        - onboardingProposal: '
        '{ proposalApply: p-1, queued: 3, create: true}',
        '---', 'eventLogRecord: ', '    event: ProposalItemApplied', '    eventToString: x', '    nodeLogs: ',
        '        - onboardingProposal: { proposalItem: p-1.create, outcome: APPLIED}',
        '---', 'eventLogRecord: ', '    event: ProposalItemApplied', '    eventToString: x', '    nodeLogs: ',
        '        - onboardingProposal: { proposalItem: p-1.1, outcome: APPLIED}',
        '---', 'eventLogRecord: ', '    event: ProposalItemApplied', '    eventToString: x', '    nodeLogs: ',
        '        - onboardingProposal: { proposalItem: p-1.2, outcome: APPLIED}',
        '---', 'eventLogRecord: ', '    event: ProposalChecked', '    eventToString: x', '    nodeLogs: ',
        '        - onboardingProposal: { proposalChecked: p-1, pairing: PAIRED, fit: FITS}', '---', ''])
    CONTEXT = {'proposals': [{'id': 'p-1.create', 'kind': 'createProject', 'state': 'applied'},
                             {'id': 'p-1.1', 'kind': 'graphml', 'state': 'applied'},
                             {'id': 'p-1.2', 'kind': 'processor', 'state': 'applied'},
                             {'id': 'p-1.3', 'kind': 'runbook', 'state': 'declined'},
                             {'id': 'p-1.4', 'kind': 'sourceRoot', 'state': 'outside'}]}

    def test_every_shown_item_must_have_a_recorded_review(self):
        recs = r.parse_session_record(self.RECORD)
        acts = [{'kind': 'review', 'item': i} for i in ('p-1.1', 'p-1.2', 'p-1.3')]
        p = r.proposals(recs, self.CONTEXT, acts)
        self.assertEqual((p['offered'], p['items'], p['applied'], p['createApplied'], p['pairing']),
                         (1, 3, 2, True, 'PAIRED'))
        self.assertEqual(p['finalStates'], {'applied': 2, 'declined': 1, 'outside': 1})
        self.assertTrue(p['everyItemReviewed'])
        # control: an item the operator never looked at
        self.assertFalse(r.proposals(recs, self.CONTEXT, acts[:2])['everyItemReviewed'])

    def test_proposal_oracle_needs_the_analysers_own_pairing(self):
        recs = r.parse_session_record(self.RECORD)
        oracle = {'primary': [{'kind': 'proposal', 'appliedKinds': ['graphml', 'processor'], 'pairing': 'PAIRED'}]}
        snap = {'recordSeq': len(recs), 'context': self.CONTEXT}
        self.assertTrue(all(r.oracle_status(oracle, snap, recs).values()))
        # control: the right kinds applied but the analyser could not pair them
        bad = r.parse_session_record(self.RECORD.replace('pairing: PAIRED', 'pairing: UNKNOWN'))
        self.assertFalse(all(r.oracle_status(oracle, {**snap, 'recordSeq': len(bad)}, bad).values()))

    def test_p3_approval_facts_are_counted_by_class_and_by_whom(self):
        rec = '\n'.join([
            '---', 'eventLogRecord: ', '    event: LocalAgentPermissionRequested', '    eventToString: '
            'LocalAgentPermissionRequested[generation=1, ticket=2, requestRef=9, toolClass=BUILT_IN, '
            'name=Read, inputDigest=d, toolUseIdDigest=e, showable=true, kind=CHANGES]', '    nodeLogs: ',
            '---', 'eventLogRecord: ', '    event: LocalAgentPermissionAnswered', '    eventToString: '
            'LocalAgentPermissionAnswered[opId=1, generation=1, ticket=2, requestRef=9, toolClass=BUILT_IN, '
            'name=Read, allow=true, by=PERSON, written=true]',
            '    nodeLogs: ',
            '---', 'eventLogRecord: ', '    event: LocalAgentPermissionRequested', '    eventToString: '
            'LocalAgentPermissionRequested[generation=1, ticket=2, requestRef=11, '
            'toolClass=HOSTED_DESTRUCTIVE, name=open, inputDigest=d, toolUseIdDigest=e, showable=true, kind=CHANGES]',
            '    nodeLogs: ',
            '---', 'eventLogRecord: ', '    event: LocalAgentPermissionAnswered', '    eventToString: '
            'LocalAgentPermissionAnswered[opId=2, generation=1, ticket=2, requestRef=11, toolClass=HOSTED_DESTRUCTIVE, '
            'name=open, allow=false, by=TIMEOUT, written=true]',
            '    nodeLogs: ', '---', ''])
        a = r.approvals(r.parse_session_record(rec))
        self.assertEqual((a['asked'], a['granted']), (2, 1))
        self.assertEqual(a['byClass'], {'BUILT_IN': 1, 'HOSTED_DESTRUCTIVE': 1})
        self.assertEqual(a['deniedBy'], {'TIMEOUT': 1})


class AuthoringOracleTest(unittest.TestCase):
    def test_new_node_logged_once_and_pointed_at(self):
        oracle = {'primary': [{'kind': 'node-logged-once', 'node': '@new'}]}
        snap = {'newNodes': ['firstBreachLatch'], 'nodeLogCounts': {'firstBreachLatch': 1},
                'pointers': [{'via': 'spotlight', 'nodes': ['breachHandler', 'firstBreachLatch']}]}
        self.assertTrue(all(r.oracle_status(oracle, snap, []).values()))
        # controls: it logged twice; the pointer is elsewhere
        self.assertFalse(all(r.oracle_status(oracle, {**snap, 'nodeLogCounts': {'firstBreachLatch': 2}}, []).values()))
        self.assertFalse(all(r.oracle_status(oracle, {**snap, 'pointers': [{'nodes': ['breachHandler']}]},
                                             []).values()))


class LaunchTest(unittest.TestCase):
    def test_launch_refuses_until_the_operator_harness_exists(self):
        out = subprocess.run([sys.executable, str(HERE / 'm74_onboarding_runner.py'), 'launch'],
                             capture_output=True, text=True)
        self.assertEqual(out.returncode, 2)
        self.assertIn('operator harness', out.stderr)

    def test_refusals_hold_under_python_O(self):
        out = subprocess.run([sys.executable, '-O', str(HERE / 'm74_onboarding_runner.py'), 'verify-seal',
                              tempfile.mkdtemp()], capture_output=True, text=True)
        self.assertEqual(out.returncode, 2)
        self.assertIn('not sealed', out.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=1)
