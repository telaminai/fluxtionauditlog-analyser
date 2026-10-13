"""Tests for the add-a-node skill's audit-compare.py (docs/skills/spring/add-a-node/).

The script's one job is a verdict a model will repeat: "business behaviour did / did not change". The
control that matters is therefore the wrong-result one - a business difference hidden among framework
differences must still be reported, and framework differences alone must never be.
"""
import importlib.util
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.dont_write_bytecode = True        # the script's directory is published skill content; leave no cache in it
SCRIPT = Path(__file__).resolve().parent.parent / 'docs/skills/spring/add-a-node/audit-compare.py'
spec = importlib.util.spec_from_file_location('audit_compare', SCRIPT)
audit_compare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_compare)

BEFORE = textwrap.dedent('''\
    ---
    #09:00:00.000 [main] INFO DEMO
    eventLogRecord:
      eventTime: 1000
      logTime: 1000
      event: PriceEvent
      eventToString: PriceEvent[symbol=DEMO, price=101.5]
      thread: main
      nodeLogs:
        - priceSource: { thread: main, method: onPrice, price: 101.5, book: [DEMO, bid=1, ask=2]}
        - quoteDEMO: { thread: main, method: onTrigger}
        - quoteDEMO: { spread: 0.5, decision: publish}
      endTime: 1001
    ---
    eventLogRecord:
      logTime: 2000
      event: PriceEvent
      eventToString: PriceEvent[symbol=DEMO, price=102.0]
      nodeLogs:
        - priceSource: { thread: main, method: onPrice, price: 102.0, book: [DEMO, bid=1, ask=2]}
        - quoteDEMO: { thread: main, method: onTrigger}
        - quoteDEMO: { spread: 0.75, decision: publish}
    ---
    eventLogRecord:
      logTime: 3000
      event: PriceEvent
      eventToString: PriceEvent[symbol=DEMO, price=99.0]
      nodeLogs:
        - priceSource: { thread: main, method: onPrice, price: 99.0, book: [DEMO, bid=1, ask=2]}
        - quoteDEMO: { thread: main, method: onTrigger}
        - quoteDEMO: { spread: 0.25, decision: hold}
    ---
    eventLogRecord:
      streamEnd: normal
      streamEndRecords: 3
    ---
    ''')


def framework_only_changes(text):
    """Every kind of framework difference a refactor produces, and no business one."""
    text = text.replace('thread: main', 'thread: worker-DEMO')                  # another thread
    text = text.replace('method: onTrigger}', 'method: onQuote, annotation: OnTrigger}')   # renamed, annotated
    text = text.replace('logTime: 2000', 'logTime: 2600')                       # times never compared
    text = text.replace('streamEndRecords: 3', 'streamEndRecords: 4')
    extra = textwrap.dedent('''\
        eventLogRecord:
          logTime: 1500
          event: ExportFunctionAuditEvent
          eventToString: @Override
          nodeLogs:
            - demoService: { thread: main, method: registerService, annotation: ServiceRegistered}
        ---
        ''')
    first_end = text.index('---\n', 4) + 4                                      # a trace-only record after record 1
    return text[:first_end] + extra + text[first_end:]


class AuditCompareTest(unittest.TestCase):

    def run_compare(self, before, after, *flags):
        return self._run(before, after, '--annotations-are-trace', *flags) if self.ANNOTATIONS_ARE_TRACE else self._run(before, after, *flags)

    ANNOTATIONS_ARE_TRACE = True   # most cases are about something else; the ambiguity has its own tests

    def _run(self, before, after, *flags):
        with tempfile.TemporaryDirectory() as tmp:
            b, a = Path(tmp, 'before.yaml'), Path(tmp, 'after.yaml')
            b.write_text(before, encoding='utf-8')
            a.write_text(after, encoding='utf-8')
            result = subprocess.run([sys.executable, str(SCRIPT), str(b), str(a), *flags],
                                    capture_output=True, text=True)
        return result.returncode, result.stdout

    def test_identical_logs_did_not_change(self):
        code, out = self.run_compare(BEFORE, BEFORE)
        self.assertEqual(0, code, out)
        self.assertIn('verdict: logged business entries MATCH for these captures', out)

    def test_framework_only_differences_are_reported_and_never_decide_the_verdict(self):
        code, out = self.run_compare(BEFORE, framework_only_changes(BEFORE))
        self.assertEqual(0, code, out)
        self.assertIn('business: same', out)
        self.assertIn('framework (reported, does not decide the verdict): DIFFERENT', out)
        self.assertIn('framework-only records: 0 before, 1 after', out)
        self.assertIn('verdict: logged business entries MATCH for these captures', out)

    def test_WRONG_RESULT_CONTROL_a_business_difference_hidden_among_framework_differences_is_reported(self):
        after = framework_only_changes(BEFORE).replace('spread: 0.75, decision: publish',
                                                        'spread: 0.75, decision: hold')
        code, out = self.run_compare(BEFORE, after)
        self.assertEqual(1, code, out)
        self.assertIn('business: DIFFERENT (1 change(s))', out)
        self.assertIn('- PriceEvent [PriceEvent[symbol=DEMO, price=102.0]]', out)
        self.assertIn('quoteDEMO {spread: 0.75, decision: hold}', out, 'the changed record is named, with its values')
        self.assertIn('framework (reported, does not decide the verdict): DIFFERENT', out)
        self.assertIn('verdict: logged business entries DIFFER for these captures', out)

    def test_a_dropped_business_record_is_a_change(self):
        records = BEFORE.split('---\n')
        after = '---\n'.join(records[:2] + records[3:])                          # record 2 never happened
        code, out = self.run_compare(BEFORE, after)
        self.assertEqual(1, code, out)
        self.assertIn('delete business record(s) before[1:2]', out)

    def test_a_lone_trace_spelled_key_is_the_nodes_own(self):
        # a node may log `thread` or `annotation` itself; without `method` beside it, that is business data
        before = BEFORE.replace('spread: 0.5, decision: publish', 'spread: 0.5, annotation: draft')
        after = BEFORE.replace('spread: 0.5, decision: publish', 'spread: 0.5, annotation: final')
        code, out = self.run_compare(before, after)
        self.assertEqual(1, code, out)

    def test_a_value_is_split_only_on_top_level_commas(self):
        entries = audit_compare.split_top_level(' a: [x, y], b: Q(p=1, q={2, 3}), c: "u, v", 4')
        self.assertEqual([('a', '[x, y]'), ('b', 'Q(p=1, q={2, 3})'), ('c', '"u, v"'), (None, '4')], entries)

    def test_identity_hashes_are_masked_unless_asked_to_keep_them(self):
        before = BEFORE.replace('decision: hold', 'decision: Order@1a2b3c4d')
        after = BEFORE.replace('decision: hold', 'decision: Order@5e6f7a8b')
        self.assertEqual(0, self.run_compare(before, after)[0])
        self.assertEqual(1, self.run_compare(before, after, '--keep-identity-hashes')[0])

    def test_an_empty_log_proves_nothing(self):
        code, out = self.run_compare('', BEFORE)
        self.assertEqual(2, code, out)
        self.assertIn('NOTHING TO COMPARE', out)

    def test_a_stream_end_marker_is_not_a_record_and_an_unparsed_document_is_business(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, 'log.yaml')
            path.write_text(BEFORE + 'not a record\n---\n', encoding='utf-8')
            records = audit_compare.parse(str(path))
        self.assertEqual(4, len(records), 'three records and one unparsed document; the marker is not counted')
        self.assertEqual({'raw': 'not a record'}, records[3])
        code, out = self.run_compare(BEFORE, BEFORE + 'not a record\n---\n')
        self.assertEqual(1, code, 'a document the script cannot read must not be silently treated as framework')
        self.assertIn('+ unparsed document: not a record', out)

    def test_the_analysers_own_real_export_reads_without_error(self):
        export = Path(__file__).resolve().parent.parent / 'src/test/resources/conformance/c21-real-export.yaml'
        code, out = self.run_compare(export.read_text(encoding='utf-8'), export.read_text(encoding='utf-8'))
        self.assertEqual(0, code, out)
        self.assertIn('before: 25 records', out)


    # --- review of PR 127: each of these once returned "did not change" (or misread the log) ---

    def test_a_business_key_spelled_like_a_trace_key_after_the_trace_run_is_the_nodes_own(self):
        before = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}',
                                '- quoteDEMO: { thread: main, method: onTrigger, annotation: OnTrigger, annotation: approved}')
        after = before.replace('annotation: approved', 'annotation: rejected')
        code, out = self.run_compare(before, after)
        self.assertEqual(1, code, out)
        for key in ('thread', 'method', 'asyncMethod'):
            b = BEFORE.replace('spread: 0.5, decision: publish', f'spread: 0.5, decision: publish, {key}: a')
            self.assertEqual(1, self.run_compare(b, b.replace(f'{key}: a', f'{key}: b'))[0], key)

    def test_an_annotation_whose_value_is_not_an_annotation_name_is_business_even_in_a_traced_entry(self):
        # a pre-1.1.1 node that logged its own `annotation` straight after tracing's method
        before = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}',
                                '- quoteDEMO: { thread: main, method: onTrigger, annotation: approved}')
        self.assertEqual(1, self.run_compare(before, before.replace('annotation: approved', 'annotation: rejected'))[0])

    def test_a_forked_entry_is_framework(self):
        forked = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}',
                                '- quoteDEMO: { thread: main, forkedExecution: true, asyncMethod: onTrigger, annotation: OnTrigger}')
        code, out = self.run_compare(BEFORE, forked)
        self.assertEqual(0, code, out)
        self.assertIn('framework (reported, does not decide the verdict): DIFFERENT', out)

    def test_records_run_together_without_a_separator_are_refused(self):
        run_together = BEFORE.replace('---\neventLogRecord:\n  logTime: 2000', 'eventLogRecord:\n  logTime: 2000', 1)
        self.assertNotEqual(BEFORE, run_together)
        code, out = self.run_compare(run_together, run_together)
        self.assertEqual(2, code, out)
        self.assertIn('NOTHING TO COMPARE', out)

    def test_a_list_shaped_log_is_refused_not_misread(self):
        listed = 'eventLogRecords:\n  - eventLogRecord:\n      event: PriceEvent\n'
        self.assertEqual(2, self.run_compare(listed, listed)[0])

    def test_only_the_class_name_at_hash_shape_is_masked(self):
        for before, after in (('ref: batch@20261013', 'ref: batch@20261014'), ('acct: ops@cafe01', 'acct: ops@beef02')):
            b = BEFORE.replace('decision: hold', 'decision: hold, ' + before)
            self.assertEqual(1, self.run_compare(b, BEFORE.replace('decision: hold', 'decision: hold, ' + after))[0], before)
        b = BEFORE.replace('decision: hold', 'decision: com.acme.Order$Line@1a2b3c4d')
        code, out = self.run_compare(b, BEFORE.replace('decision: hold', 'decision: com.acme.Order$Line@5e6f7a8b'))
        self.assertEqual(0, code, out)
        self.assertIn('masked: 2', out)

    def test_a_lambda_address_and_its_hash_are_masked(self):
        b = BEFORE.replace('decision: hold', 'sink: Svc$$Lambda/0x000000700136dfb0@7883e4ae')
        a = BEFORE.replace('decision: hold', 'sink: Svc$$Lambda/0x000000700137aa10@11aa22bb')
        self.assertEqual(0, self.run_compare(b, a)[0])
        self.assertEqual(1, self.run_compare(b, a, '--keep-identity-hashes')[0])

    def test_a_business_thread_after_the_method_is_the_nodes_own(self):
        # tracing writes thread BEFORE method; a `thread` after it (printThreadName off) is the node's own key
        b = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}', '- quoteDEMO: { method: onTrigger, thread: worker-1}')
        self.assertEqual(1, self.run_compare(b, b.replace('worker-1', 'worker-2'))[0])

    def test_hash_like_values_that_are_not_identity_hashes_are_compared(self):
        for before, after in (('ver: Release@2026', 'ver: Release@2027'), ('user: Bob@deadbeef.com', 'user: Bob@cafe00.com')):
            self.assertEqual(1, self.run_compare(BEFORE.replace('decision: hold', before),
                                                 BEFORE.replace('decision: hold', after))[0], before)

    def test_older_lambdas_and_arrays_are_masked(self):
        for before, after in (('s: Foo$$Lambda$14/0x0000000800c0b040@1b2c3d4e', 's: Foo$$Lambda$14/0x0000000800c0c999@5f6a7b8c'),
                              ('s: [Ljava.lang.String;@1b2c3d4e', 's: [Ljava.lang.String;@5f6a7b8c'),
                              ('s: [I@1b2c3d4e', 's: [I@5f6a7b8c')):
            self.assertEqual(0, self.run_compare(BEFORE.replace('decision: hold', before),
                                                 BEFORE.replace('decision: hold', after))[0], before)

    # --- second review of PR 127 ---

    def test_an_entry_that_never_closes_is_refused(self):
        broken = BEFORE.replace('- quoteDEMO: { spread: 0.25, decision: hold}', '- quoteDEMO: { spread: 0.25, note: "open')
        self.assertNotEqual(BEFORE, broken)
        self.assertEqual(2, self.run_compare(broken, broken)[0])

    def test_an_ambiguous_annotation_makes_a_match_inconclusive_unless_declared_trace(self):
        def entry(value):
            return BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}',
                                  f'- quoteDEMO: {{ thread: main, method: onTrigger, annotation: {value}, price: 100}}')
        code, out = self._run(entry('Approved'), entry('Rejected'))
        self.assertEqual(3, code, out)
        self.assertIn('INCONCLUSIVE', out)
        self.assertEqual(0, self._run(entry('OnTrigger'), entry('OnParentUpdate'), '--annotations-are-trace')[0])
        self.assertEqual(0, self._run(entry('Approved'), entry('Approved'))[0], 'the same value either way matches')

    def test_the_verdict_names_what_it_covers(self):
        out = self.run_compare(BEFORE, BEFORE)[1]
        self.assertIn('an output or side effect no node logged is not covered', out)

    # --- the analyser's record-structure rule (RecordBreak, C32), ported ---

    C32 = Path(__file__).resolve().parent.parent / 'src/test/resources/conformance/c32-record-structure.yaml'

    def c32_record(self, n):
        return '---' + self.C32.read_text(encoding='utf-8').split('\n---')[n].lstrip('-') + '\n---\n'

    def test_c32_a_deeper_payload_breaks_its_record_and_is_refused(self):
        code, out = self.run_compare(self.c32_record(0), self.c32_record(0))
        self.assertEqual(2, code, out)

    def test_c32_a_closed_quoted_node_value_is_whole_and_compared(self):
        record = self.c32_record(1)
        self.assertIn('last"}', record)
        self.assertEqual(0, self.run_compare(record, record)[0])
        code, out = self.run_compare(record, record.replace('last"}', 'final"}'))
        self.assertEqual(1, code, 'the trailing text of a quoted value is part of it: ' + out)
        self.assertEqual(1, self.run_compare(record, record.replace('event: Forged', 'event: Other'))[0])

    def test_c32_the_exported_service_signature_is_whole_and_compared(self):
        record = self.c32_record(2)
        self.assertEqual(0, self.run_compare(record, record)[0])
        self.assertEqual(1, self.run_compare(record, record.replace('suspendQuoting(String arg0)', 'resumeQuoting(String arg0)'))[0])

    def test_a_quoted_event_text_across_lines_is_one_value(self):
        # second independent review: the continuation became an unrelated `detail` field, never compared
        def log(detail):
            return ('---\neventLogRecord:\n  event: PriceEvent\n  eventToString: "first\n  detail: ' + detail
                    + '\n  last"\n  nodeLogs:\n    - priceSource: { price: 100}\n---\n')
        self.assertEqual(0, self.run_compare(log('before'), log('before'))[0])
        self.assertEqual(1, self.run_compare(log('before'), log('after'))[0])

    def test_an_unquoted_indented_continuation_breaks_the_record(self):
        broken = BEFORE.replace('eventToString: PriceEvent[symbol=DEMO, price=99.0]',
                                'eventToString: Order{\n      price=5}')
        self.assertEqual(2, self.run_compare(broken, broken)[0])

    def test_a_multiline_legacy_entry_is_read_whole(self):
        legacy = Path(__file__).resolve().parent.parent / 'src/test/resources/conformance/c17-legacy-quotes.yaml'
        text = legacy.read_text(encoding='utf-8')
        self.assertEqual(0, self.run_compare(text, text)[0], 'a quote mark mid-value is data in the legacy grammar')
        self.assertEqual(1, self.run_compare(text, text.replace('end, price: 77}', 'end, price: 78}'))[0])

if __name__ == '__main__':
    unittest.main()
