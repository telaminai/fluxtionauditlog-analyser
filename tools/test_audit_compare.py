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
        self.assertIn('verdict: business behaviour did not change', out)

    def test_framework_only_differences_are_reported_and_never_decide_the_verdict(self):
        code, out = self.run_compare(BEFORE, framework_only_changes(BEFORE))
        self.assertEqual(0, code, out)
        self.assertIn('business: same', out)
        self.assertIn('framework (reported, does not decide the verdict): DIFFERENT', out)
        self.assertIn('framework-only records: 0 before, 1 after', out)
        self.assertIn('verdict: business behaviour did not change', out)

    def test_WRONG_RESULT_CONTROL_a_business_difference_hidden_among_framework_differences_is_reported(self):
        after = framework_only_changes(BEFORE).replace('spread: 0.75, decision: publish',
                                                        'spread: 0.75, decision: hold')
        code, out = self.run_compare(BEFORE, after)
        self.assertEqual(1, code, out)
        self.assertIn('business: DIFFERENT (1 change(s))', out)
        self.assertIn('- PriceEvent [PriceEvent[symbol=DEMO, price=102.0]]', out)
        self.assertIn('quoteDEMO {spread: 0.75, decision: hold}', out, 'the changed record is named, with its values')
        self.assertIn('framework (reported, does not decide the verdict): DIFFERENT', out)
        self.assertIn('verdict: business behaviour CHANGED', out)

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


if __name__ == '__main__':
    unittest.main()
