"""Tests for tools/token_split.py (M72 L0, rule-8 check).

Every test runs on a small synthetic stream-json transcript built here; nothing reads a real transcript, a key or
the network. The classifier is pinned with one call per kind; a request over 100k pins the tier; a design / run /
diagnosis sequence pins the phases. The sum assertions are the spec's regression check: deleting a category rule
moves its bytes to `unclassified` (and fails `test_one_call_per_kind`), and deleting a phase rule moves calls to
another phase (and fails `test_phases`).

    python3 tools/test_token_split.py
"""
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('token_split', Path(__file__).parent / 'token_split.py')
ts = importlib.util.module_from_spec(spec)
sys.modules['token_split'] = ts
spec.loader.exec_module(ts)

MODEL = 'claude-haiku-5-5'
SENTINEL = 'SENTINEL-PATH-NEVER-WRITTEN'


class Transcript:
    """Builds stream-json lines the way Claude Code writes them (one assistant event per content block)."""

    def __init__(self, model=MODEL):
        self.model, self.lines, self.n, self.req = model, [], 0, 0
        self.usage = {'input': 0, 'cacheRead': 0, 'cacheWrite': 0}
        self.parent = None          # parent_tool_use_id: the delegating call's id while a subagent is speaking
        self.models = {}            # model -> usage, for modelUsage when a subagent runs on another model

    def _emit(self, event):
        event['parent_tool_use_id'] = self.parent    # every assistant/user message carries it; null in main
        self.lines.append(json.dumps(event))

    def _usage(self, prompt):
        # a cache-read-heavy prompt, as real runs are: 2 input, 500 written, the rest read
        u = {'input_tokens': 2, 'cache_creation_input_tokens': 500, 'cache_read_input_tokens': prompt - 502,
             'output_tokens': 3, 'cache_creation': {'ephemeral_1h_input_tokens': 500, 'ephemeral_5m_input_tokens': 0}}
        self.usage['input'] += 2
        self.usage['cacheWrite'] += 500
        self.usage['cacheRead'] += prompt - 502
        return u

    def call(self, name, inp, result, prompt=30_000, is_error=False):
        """One request that emits one tool call, then the call's result."""
        self.req += 1
        mid, tid = f'msg_{self.req}', f'toolu_{self.n}'
        self.n += 1
        u = self._usage(prompt)
        self.lines.append(json.dumps({'type': 'system', 'subtype': 'thinking_tokens', 'estimated_tokens_delta': 40}))
        self._emit({'type': 'assistant', 'message': {
            'id': mid, 'model': self.model, 'usage': u, 'content': [{'type': 'text', 'text': 'checking'}]}})
        self._emit({'type': 'assistant', 'message': {   # same id, same usage: de-duplicated
            'id': mid, 'model': self.model, 'usage': u,
            'content': [{'type': 'tool_use', 'id': tid, 'name': name, 'input': inp}]}})
        if result is not None:      # None: the result comes later (a delegation's reply), via result()
            self.result(tid, result, is_error)
        return tid

    def result(self, tid, body, is_error=False):
        self._emit({'type': 'user', 'message': {'role': 'user', 'content': [
            {'type': 'tool_result', 'tool_use_id': tid, 'content': body, 'is_error': is_error}]}})

    def answer(self, prompt=30_000, text='done'):
        self.req += 1
        self._emit({'type': 'assistant', 'message': {
            'id': f'msg_{self.req}', 'model': self.model, 'usage': self._usage(prompt),
            'content': [{'type': 'text', 'text': text}]}})

    def finish(self, output_tokens=1000, cost=None, side=None):
        mu = {self.model: {'inputTokens': self.usage['input'], 'outputTokens': output_tokens,
                           'cacheReadInputTokens': self.usage['cacheRead'],
                           'cacheCreationInputTokens': self.usage['cacheWrite'], 'costUSD': cost}}
        if side:
            mu[self.model]['inputTokens'] += side['input']
            mu[self.model]['outputTokens'] += side['output']
        self.lines.append(json.dumps({'type': 'result', 'subtype': 'success', 'total_cost_usd': cost,
                                      'usage': {'output_tokens': output_tokens}, 'modelUsage': mu}))
        return self.lines


# One call per kind, each with a distinct result size so a misfiled call is visible in the byte counts.
PER_KIND = [
    ('auditLog', 'Bash', {'command': 'cat logs/audit-marketProcessor.yaml'}, 'a' * 101),
    ('generated', 'Read', {'file_path': '/w/p/src/main/java/com/x/generated/MarketProcessor.java'}, 'g' * 102),
    ('server', 'Bash', {'command': './run-server.sh > logs/server.log 2>&1'}, 's' * 103),
    ('build', 'Bash', {'command': './mvnw -q package'}, 'b' * 104),
    ('design', 'Read', {'file_path': '/w/p/src/main/fluxtion/designer/application-context.xml'}, 'x' * 105),
    ('library', 'Bash', {'command': 'javap -p com.acme.Thing'}, 'l' * 106),
    ('docs', 'Read', {'file_path': '/w/p/RUNBOOK.md'}, 'd' * 107),
    ('source', 'Read', {'file_path': '/w/p/src/main/java/com/x/node/RootNode.java'}, 'j' * 108),
    ('analyser', 'mcp__fluxtion-analyser__analyser_context', {'sections': ['log']}, 'n' * 109),
    ('other', 'Edit', {'file_path': '/w/p/README.md'}, 'e' * 110),
]


class ClassifierTest(unittest.TestCase):
    def test_one_call_per_kind(self):
        t = Transcript()
        for _, name, inp, result in PER_KIND:
            t.call(name, inp, result)
        t.answer()
        _, cat, _ = ts.split(t.finish())
        for kind, _, _, result in PER_KIND:
            self.assertEqual(cat['kinds'][kind]['bytes'], len(result), kind)
            self.assertEqual(cat['kinds'][kind]['calls'], 1, kind)
        self.assertEqual(cat['kinds']['unclassified']['bytes'], 0)
        self.assertEqual(sum(k['bytes'] for k in cat['kinds'].values()), cat['toolResultBytes'])
        self.assertEqual(cat['toolResultBytes'], sum(len(r) for *_, r in PER_KIND))

    def test_unmatched_call_is_unclassified_not_other(self):
        t = Transcript()
        t.call('Bash', {'command': 'grep -rl "shadowing" .'}, 'z' * 50)
        _, cat, _ = ts.split(t.finish())
        self.assertEqual(cat['kinds']['unclassified']['bytes'], 50)
        self.assertEqual(cat['kinds']['other']['bytes'], 0)

    def test_one_path_is_one_kind(self):
        # `.yaml` would also match a project file and `.java` Java source: the token is ONE thing
        self.assertEqual(ts.classify('Bash', {'command': 'cat logs/audit-x.yaml'})[1], ['auditLog'])
        self.assertEqual(ts.classify('Read', {'file_path': '/p/src/generated/P.java'})[1], ['generated'])
        # two different files of different kinds: multi-kind, primary by priority
        kind, kinds, _ = ts.classify('Bash', {'command': 'cat README.md && cat src/main/java/A.java'})
        self.assertEqual((kind, kinds), ('docs', ['docs', 'source']))

    def test_reading_a_script_is_not_running_it(self):
        self.assertEqual(ts.classify('Bash', {'command': 'cat validate.sh generate.sh run-server.sh'})[0], 'source')
        self.assertEqual(ts.classify('Bash', {'command': 'cat src/a/RootNode.java src/b/Child.java'})[0], 'source')
        self.assertEqual(ts.classify('Bash', {'command': './run-and-export.sh'})[0], 'server')
        self.assertEqual(ts.classify('Bash', {'command': 'java -cp target/classes Scenario'})[0], 'server')

    def test_heredoc_body_is_not_the_subject(self):
        cmd = "cat > /tmp/x.py <<'EOF'\nprint(open('logs/audit-x.yaml').read())\nEOF\npython3 /tmp/x.py"
        self.assertNotEqual(ts.classify('Bash', {'command': cmd})[0], 'auditLog')

    def test_a_file_opened_in_the_analyser_is_an_audit_log(self):
        t = Transcript()
        t.call('mcp__fluxtion-analyser__analyser_open', {'log': '/p/target/run1.log'}, 'ok')
        t.call('Bash', {'command': 'sed -n 1,40p target/run1.log'}, 'r' * 70)
        _, cat, _ = ts.split(t.finish())
        self.assertEqual(cat['byRule']['openedLog']['bytes'], 70)

    def test_build_or_server_output_echoing_audit_records_is_counted(self):
        t = Transcript()
        t.call('Bash', {'command': './run.sh'}, 'eventToString: PriceEvent nodeLogs: []' + 'x' * 20)
        t.call('Bash', {'command': './mvnw -q package'}, 'BUILD SUCCESS')
        _, cat, _ = ts.split(t.finish())
        self.assertEqual(cat['auditEchoBytes'], {'server': 58, 'build': 0})
        self.assertEqual([c['auditEcho'] for c in cat['calls']], [True, False])

    def test_tables_hold_no_command_text_or_path(self):
        t = Transcript()
        t.call('Bash', {'command': f'cat /{SENTINEL}/logs/audit-x.yaml'}, f'{SENTINEL} body')
        t.answer(text=SENTINEL)
        for table in ts.split(t.finish(), run='r'):
            self.assertNotIn(SENTINEL, json.dumps(table))


class BucketsTest(unittest.TestCase):
    def _priced(self, prompt):
        """Billed cost for one call request + one answer request at `prompt`, from the price table."""
        p = ts.PRICES[MODEL]
        mult = p['over100k'] if prompt > ts.TIER_THRESHOLD else 1.0
        per = (2 * p['input'] + (prompt - 502) * p['cacheRead'] + 500 * p['cacheWrite1h']) * mult
        return (2 * per + 1000 * p['output'] * mult) / 1e6

    def test_request_over_100k(self):
        t = Transcript()
        t.call('Read', {'file_path': '/p/RUNBOOK.md'}, 'd' * 10, prompt=120_000)
        t.answer(prompt=120_000)
        b, _, _ = ts.split(t.finish(cost=self._priced(120_000)))
        self.assertEqual(b['totals']['requests'], 2)                  # de-duplicated by message id
        self.assertTrue(all(r['over100k'] for r in b['requests']))
        self.assertEqual(b['totals']['over100k'], 2)
        self.assertEqual(b['totals']['gt100k']['requests'], 2)
        self.assertEqual(b['totals']['le100k']['requests'], 0)
        self.assertAlmostEqual(b['totals']['costRecomputedRatio'], 1.0, places=4)
        self.assertAlmostEqual(b['totals']['gt100kCostShare'], 1.0, places=4)
        self.assertEqual(round(sum(r['outputAllocated'] for r in b['requests'])), 1000)

    def test_under_the_tier_is_priced_at_base(self):
        t = Transcript()
        t.call('Read', {'file_path': '/p/RUNBOOK.md'}, 'd' * 10, prompt=60_000)
        t.answer(prompt=60_000)
        b, _, _ = ts.split(t.finish(cost=self._priced(60_000)))
        self.assertFalse(any(r['over100k'] for r in b['requests']))
        self.assertAlmostEqual(b['totals']['costRecomputedRatio'], 1.0, places=4)
        self.assertEqual(b['totals']['gt100kCostShare'], 0.0)

    def test_tier_prices_are_pinned_by_literal(self):
        # Independent of PRICES: hand-computed from the fitted Haiku 5.5 rates ($/M: input 0.10, cache read 0.01,
        # 1-hour cache write 0.20, output 0.50; x5 above 100k). A price-table edit must change these numbers too.
        for prompt, expected in ((120_000, 5 * (2 * 0.10 + 119_498 * 0.01 + 500 * 0.20 + 1000 * 0.50) / 1e6),
                                 (60_000, (2 * 0.10 + 59_498 * 0.01 + 500 * 0.20 + 1000 * 0.50) / 1e6)):
            t = Transcript()
            t.answer(prompt=prompt)
            b, _, _ = ts.split(t.finish(output_tokens=1000))
            self.assertAlmostEqual(b['requests'][0]['costUsd'], expected, places=9)

    def test_streamed_output_is_kept_but_not_billed(self):
        t = Transcript()
        t.answer()
        b, _, _ = ts.split(t.finish(output_tokens=777))
        self.assertEqual(b['requests'][0]['outputStreamed'], 3)
        self.assertEqual(b['requests'][0]['outputAllocated'], 777)

    def test_side_calls_from_model_usage(self):
        t = Transcript()
        t.call('WebFetch', {'url': 'https://example.invalid/doc'}, 'w' * 10)
        b, cat, _ = ts.split(t.finish(side={'input': 4000, 'output': 300}))
        self.assertEqual(b['sideCalls'][MODEL]['input'], 4000)
        self.assertEqual(b['sideCalls'][MODEL]['output'], 300)
        self.assertEqual(cat['kinds']['docs']['bytes'], 10)


class PhasesTest(unittest.TestCase):
    def test_phases(self):
        t = Transcript()
        t.call('Read', {'file_path': '/p/RUNBOOK.md'}, 'd' * 10)                        # design
        t.call('Edit', {'file_path': '/p/src/main/fluxtion/designer/application-context.xml'}, 'ok')
        t.call('Bash', {'command': './generate.sh; echo "exit=$?"'}, 'exit=0')         # last good build
        t.call('Bash', {'command': './mvnw -q package; echo "exit=$?"'}, 'BUILD FAILURE exit=1')  # failed: not B
        t.call('Bash', {'command': './run-and-export.sh'}, 'exported')                # run, E
        t.call('Bash', {'command': 'ls logs'}, 'audit.yaml')                          # run
        t.call('mcp__fluxtion-analyser__analyser_open', {'log': '/p/logs/audit.yaml'}, 'opened')  # D
        t.call('mcp__fluxtion-analyser__analyser_read', {'recordIndex': 0}, 'rows')
        t.answer()
        b, _, ph = ts.split(t.finish())
        self.assertEqual(ph['boundaries'], {'exportCall': 4, 'lastBuildCall': 2, 'diagnosisStartCall': 6,
                                            'designEndCall': 2})
        self.assertEqual([c['phase'] for c in ph['calls']],
                         ['design'] * 3 + ['run'] * 3 + ['diagnosis'] * 2)
        self.assertEqual(sum(p['toolCalls'] for p in ph['phases'].values()), len(ph['calls']))
        self.assertEqual(sum(p['requests'] for p in ph['phases'].values()), b['totals']['requests'])
        self.assertEqual(ph['phases']['diagnosis']['analyserCalls'], 2)
        self.assertEqual(b['requests'][-1]['phase'], 'diagnosis')   # the answer follows the last call

    def test_no_analyser_call_means_no_diagnosis(self):
        t = Transcript()
        t.call('Bash', {'command': './generate.sh'}, 'ok')
        t.call('Bash', {'command': './run.sh'}, 'ran')
        _, _, ph = ts.split(t.finish())
        self.assertIsNone(ph['boundaries']['diagnosisStartCall'])
        self.assertEqual([c['phase'] for c in ph['calls']], ['design', 'run'])


class SummaryTest(unittest.TestCase):
    def test_summary_from_committed_tables(self):
        with tempfile.TemporaryDirectory() as tmp:
            results = Path(tmp) / 'evidence' / 'results'
            for name in ('r-1', 'r-2-VOID-x'):
                t = Transcript()
                t.call('Bash', {'command': 'cat logs/audit-x.yaml'}, 'a' * 40)
                raw = Path(tmp) / f'{name}.jsonl'
                raw.write_text('\n'.join(t.finish(cost=0.001)) + '\n')
                ts.write_tables(raw, results / name, run=name)
                (results / name / 'score.json').write_text(json.dumps({'reads': {'toolResultBytes': 40}}))
            text = ts.summary([results])
            self.assertIn('### evidence', text)
            import os
            cwd = os.getcwd()
            try:                                           # a relative results dir names its evidence folder too
                os.chdir(results)
                self.assertIn('### evidence', ts.summary(['.']))
            finally:
                os.chdir(cwd)
            self.assertIn('| r-1 | 40 = 40 yes', text)
            self.assertNotIn('r-2-VOID-x', text)                      # VOID runs are skipped


class DelegationTest(unittest.TestCase):
    """M72 L4 (iii): diagnosis delegated to a subagent runs in its own context, and the tables say so."""

    BRIEF = {'subagent_type': 'general-purpose', 'description': 'diagnose run', 'prompt': f'project: /{SENTINEL}'}

    def _delegated(self, sub_model=MODEL, visible=True):
        t = Transcript()
        t.call('Read', {'file_path': '/p/src/main/fluxtion/designer/application-context.xml'}, 'x' * 1000,
               prompt=40_000)                                                            # design (call 0)
        t.call('Bash', {'command': './generate.sh; echo "exit=$?"'}, 'exit=0', prompt=60_000)    # B (call 1)
        t.call('Bash', {'command': './run-and-export.sh'}, 'exported', prompt=90_000)             # E (call 2)
        tid = t.call('Agent', self.BRIEF, None, prompt=95_000)                                    # D (call 3)
        if visible:
            t.parent, t.model = tid, sub_model
            t.call('mcp__fluxtion-analyser__analyser_open', {'log': '/p/logs/audit-p.yaml'}, 'opened', prompt=21_000)
            t.call('mcp__fluxtion-analyser__analyser_context', {'sections': ['log', 'pairing']}, 'c' * 300,
                   prompt=22_000)
            t.answer(prompt=23_000, text='reply')
            t.parent, t.model = None, MODEL
        t.result(tid, 'r' * 200)
        t.answer(prompt=96_000)
        return t

    def test_the_subagent_is_its_own_context(self):
        b, cat, ph = ts.split(self._delegated().finish())
        ctx = {c['context']: c for c in b['contexts']}
        self.assertEqual(set(ctx), {'main', 'subagent-1'})
        self.assertEqual((ctx['subagent-1']['requests'], ctx['subagent-1']['firstPrompt'],
                          ctx['subagent-1']['maxPrompt'], ctx['subagent-1']['delegatedBy']), (3, 21_000, 23_000, 3))
        self.assertEqual((ctx['main']['requests'], ctx['main']['maxPrompt']), (5, 96_000))
        self.assertEqual(ph['boundaries']['diagnosisStartCall'], 3, 'the delegation starts the diagnosis')
        self.assertEqual([c['phase'] for c in ph['calls']], ['design'] * 2 + ['run'] + ['diagnosis'] * 3)
        d = ph['delegation']
        self.assertEqual((d['delegations'], d['subagentRequests'], d['subagentMaxPrompt'], d['delegateResultBytes'],
                          d['mainDiagnosisRequests'], d['warnings']), (1, 3, 23_000, 200, 2, []))
        self.assertEqual(d['delegateInputChars'], len(json.dumps(self.BRIEF)))
        self.assertEqual(sum(c['requests'] for c in b['contexts']), b['totals']['requests'])
        self.assertAlmostEqual(sum(c['costUsd'] for c in b['contexts']),
                               sum(r['costUsd'] for r in b['requests']), places=9)
        self.assertEqual(cat['byRule']['delegate'], {'kind': 'other', 'calls': 1, 'bytes': 200})

    def test_a_result_is_carried_only_by_its_own_context(self):
        # The design read (call 0, request 0) is re-read by the 4 later MAIN requests, not by the subagent's 3.
        # With one global context it would be 7: that is the regression this pins.
        _, cat, _ = ts.split(self._delegated().finish())
        self.assertEqual(cat['kinds']['design']['carried'], 1000 * 4)
        # the subagent's context reply (request 5) is carried by the subagent's answer only
        self.assertEqual(cat['kinds']['analyser']['carried'], len('opened') * 2 + 300 * 1)

    def test_a_delegation_with_no_visible_subagent_is_flagged(self):
        _, _, ph = ts.split(self._delegated(visible=False).finish())
        self.assertEqual(ph['delegation']['subagentRequests'], 0)
        self.assertIn('no subagent request is visible', ph['delegation']['warnings'][0])

    def test_a_subagent_on_another_model_is_flagged(self):
        _, _, ph = ts.split(self._delegated(sub_model='claude-sonnet-5').finish())
        self.assertIn('claude-sonnet-5', ph['delegation']['warnings'][0])

    def test_a_single_context_run_writes_no_delegation_tables(self):
        t = Transcript()
        t.call('Bash', {'command': './generate.sh'}, 'ok')
        t.call('Bash', {'command': './run.sh'}, 'ran')
        t.call('mcp__fluxtion-analyser__analyser_open', {'log': '/p/logs/audit-p.yaml'}, 'opened')
        b, _, ph = ts.split(t.finish())
        self.assertNotIn('contexts', b)
        self.assertNotIn('delegation', ph)

    def test_tables_hold_no_brief_text(self):
        for table in ts.split(self._delegated().finish(), run='r'):
            self.assertNotIn(SENTINEL, json.dumps(table))

    def test_summary_reports_diagnosis_contexts(self):
        with tempfile.TemporaryDirectory() as tmp:
            results = Path(tmp) / 'evidence' / 'results'
            for name, t in (('treat-1', self._delegated()), ('ctl-1', Transcript())):
                if name == 'ctl-1':
                    t.call('Bash', {'command': './run-and-export.sh'}, 'exported')
                    t.call('mcp__fluxtion-analyser__analyser_open', {'log': '/p/logs/audit-p.yaml'}, 'opened')
                raw = Path(tmp) / f'{name}.jsonl'
                raw.write_text('\n'.join(t.finish(cost=0.001)) + '\n')
                ts.write_tables(raw, results / name, run=name)
            text = ts.summary([results])
            self.assertIn('**Diagnosis contexts**', text)
            self.assertIn('| treat-1 | 1 | 3 | 0 | 21000 / 23000 | 2 | 0 | 96000 |', text)
            self.assertIn('| ctl-1 | 0 |', text)


if __name__ == '__main__':
    unittest.main()
