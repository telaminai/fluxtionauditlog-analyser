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
        self.last_stderr = result.stderr
        return result.returncode, result.stdout

    # AG-5: the starter's generated callback audit (callback-audit.ts / Reconciler.callbackAudit) is trace.
    GENERATED = textwrap.dedent('''\
        ---
        eventLogRecord:
          logTime: 1000
          event: RentalEvent
          eventToString: RentalEvent{RETURN R3 DAMAGED}
          nodeLogs:
            - rentalDesk: { thread: main, method: onRentalEventFilter3, annotation: OnEventHandler, handledEvent: com.acme.RentalEvent, filterString: RETURN, charge: 30}
            - ledger: { thread: main, method: onTrigger, annotation: OnTrigger, triggerFired: true, total: 30}
        ---
        ''')
    HAND_WRITTEN = textwrap.dedent('''\
        ---
        eventLogRecord:
          logTime: 2000
          event: RentalEvent
          eventToString: RentalEvent{RETURN R3 DAMAGED}
          nodeLogs:
            - rentalDesk: { thread: main, method: onReturn, annotation: OnEventHandler, charge: 30}
            - ledger: { thread: main, method: onTrigger, annotation: OnTrigger, total: 30}
        ---
        ''')

    def test_generated_callback_audit_is_reported_with_the_framework(self):
        for flags in ((), ('--annotations-are-trace',)):
            code, out = self._run(self.GENERATED, self.HAND_WRITTEN, *flags)
            self.assertEqual(0, code, f"{flags}: a generated and a hand-written node logging the same values match\n{out}")
            self.assertIn('framework (reported, does not decide the verdict)', out)

    def test_generated_callback_audit_never_hides_a_business_change(self):
        code, out = self.run_compare(self.GENERATED, self.HAND_WRITTEN.replace('charge: 30', 'charge: 31'))
        self.assertEqual(1, code, out)

    def test_strict_trace_compares_the_callback_audit_as_business(self):
        code, out = self.run_compare(self.GENERATED, self.HAND_WRITTEN, '--strict-trace')
        self.assertEqual(1, code, out)

    def test_a_nodes_own_key_is_not_read_as_callback_audit_by_name_alone(self):
        own = self.HAND_WRITTEN.replace('charge: 30}', 'charge: 30, filterString: RETURN}')
        code, out = self.run_compare(self.HAND_WRITTEN, own)
        self.assertEqual(1, code, f"filterString after a business key is the node's own\n{out}")
        lifecycle = self.HAND_WRITTEN.replace('annotation: OnTrigger, total', 'annotation: OnTrigger, lifecycle: closing, total')
        code, out = self.run_compare(self.HAND_WRITTEN, lifecycle)
        self.assertEqual(1, code, f"a lifecycle value that is not a phase is the node's own\n{out}")

    # Reviews of PR #221: which nodes ran stays business, and a changed route is not a silent MATCH.
    STARTER = textwrap.dedent('''\
        ---
        eventLogRecord:
          logTime: 1000
          event: PriceEvent
          eventToString: PriceEvent[symbol=DEMO, price=905.6]
          nodeLogs:
            - rootNode: { thread: main, method: onPriceEvent, annotation: OnEventHandler, handledEvent: com.acme.PriceEvent, price: 905.6}
            - riskCheck: { thread: main, method: onRiskCheck, annotation: OnTrigger, triggerFired: true}
        ---
        ''')

    def test_a_trigger_that_logs_only_its_callback_audit_and_stops_running_is_a_difference(self):
        stopped = '\n'.join(l for l in self.STARTER.split('\n') if 'riskCheck' not in l) + '\n'
        code, out = self.run_compare(self.STARTER, stopped)
        self.assertEqual(1, code, f"riskCheck no longer ran: that must not read as MATCH\n{out}")

    def test_a_node_shown_by_tracing_alone_against_its_callback_audit_is_inconclusive(self):
        traced_only = self.STARTER.replace(', triggerFired: true}', '}')
        code, out = self.run_compare(self.STARTER, traced_only)
        self.assertEqual(3, code, out)
        self.assertIn('shown by tracing alone', out)

    def test_another_event_or_filter_reaching_a_node_is_inconclusive_not_a_match(self):
        rerouted = self.GENERATED.replace('handledEvent: com.acme.RentalEvent, filterString: RETURN',
                                          'handledEvent: com.acme.OtherEvent, filterString: RENT')
        code, out = self.run_compare(self.GENERATED, rerouted)
        self.assertEqual(3, code, out)
        self.assertIn('another event type or filter', out)

    def test_untraced_callback_audit_is_still_compared_for_presence_and_route(self):
        untraced = textwrap.dedent('''\
            ---
            eventLogRecord:
              logTime: 1000
              event: RentalEvent
              eventToString: RentalEvent{RETURN R3 OK}
              nodeLogs:
                - desk: { handledEvent: com.acme.RentalEvent, filterString: RETURN}
                - ledger: { total: 30}
            ---
            ''')
        gone = untraced.replace('    - desk: { handledEvent: com.acme.RentalEvent, filterString: RETURN}\n', '')
        code, out = self.run_compare(untraced, gone)
        self.assertEqual(1, code, f"desk stopped receiving the event\n{out}")
        lifecycle = untraced.replace('handledEvent: com.acme.RentalEvent, filterString: RETURN', 'lifecycle: start')
        code, out = self.run_compare(lifecycle, lifecycle.replace('lifecycle: start', 'lifecycle: stop'))
        self.assertEqual(3, code, f"a different lifecycle phase at position 0 is not a silent MATCH\n{out}")

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

    # --- third review of PR 127 ---

    # Every conformance fixture compared with itself: (file, exit, (records, business) or None, refused as broken).
    # Pinned, so a change to how any fixture reads is visible here (rule 8). A broken record is refused, never a match.
    # every conformance fixture (src/test/resources/conformance/*.yaml) needs a row here: the test below fails on a
    # fixture the table does not list
    FIXTURE_TABLE = [
        ('c01-minimal.yaml', 0, (1, 1), False),
        ('c02-unknown-fields.yaml', 0, (1, 1), False),
        ('c03-header.yaml', 0, (2, 2), False),
        ('c04-times.yaml', 0, (2, 2), False),
        ('c05-untimed.yaml', 0, (3, 3), False),
        ('c06-out-of-order.yaml', 2, (3, 0), False),
        ('c07-duplicate-instance.yaml', 0, (1, 1), False),
        ('c08-lenient-values.yaml', 0, (1, 1), False),
        ('c09-garbage.yaml', 0, (3, 3), False),
        ('c11-attribution.yaml', 0, (1, 1), False),
        ('c12-traced-regime.yaml', 0, (2, 2), False),
        ('c13-exported-call.yaml', 0, (1, 1), False),
        ('c16-quoted-scalars.yaml', 0, (1, 1), False),
        ('c17-legacy-quotes.yaml', 0, (3, 3), False),
        ('c18-stream-end.yaml', 0, (2, 2), False),
        ('c19-export-layout.yaml', 0, (2, 2), False),
        ('c20-marker-lookalike.yaml', 0, (2, 2), False),
        ('c21-real-export.yaml', 0, (25, 7), False),
        ('c22-marker-syntax.yaml', 0, (2, 2), False),
        ('c23-marker-values.yaml', 0, (9, 9), False),
        ('c24-unterminated-marker.yaml', 0, (2, 2), False),
        ('c25-marker-declaring-zero.yaml', 2, (0, 0), False),
        ('c26-two-empty-segments.yaml', 2, (0, 0), False),
        ('c27-whitespace-only.yaml', 2, (0, 0), False),
        ('c28-zero-bytes.yaml', 2, (0, 0), False),
        ('c29-no-record-key.yaml', 0, (3, 3), False),
        ('c30-per-node-level.yaml', 0, (2, 1), False),
        ('c31-broken-value.yaml', 2, None, True),
        ('c32-record-structure.yaml', 2, None, True),
        ('c33-trace-annotation.yaml', 0, (3, 3), False),
        ('c34-merged-entry.yaml', 0, (3, 3), False),
        ('c35-record-text-encoding.yaml', 0, (6, 6), False),
    ]

    def test_every_conformance_fixture_reads_as_pinned(self):
        root = Path(__file__).resolve().parent.parent / 'src/test/resources/conformance'
        self.assertEqual(sorted(p.name for p in root.glob('*.yaml')), [row[0] for row in self.FIXTURE_TABLE],
                         'a fixture was added or removed: pin it here')
        for name, code, counts, broken in self.FIXTURE_TABLE:
            text = (root / name).read_text(encoding='utf-8')
            got, out = self.run_compare(text, text)
            self.assertEqual(code, got, name + ': ' + out + self.last_stderr)
            if counts:
                self.assertIn(f'before: {counts[0]} records, {counts[1]} business', out, name)
            self.assertEqual(broken, 'record structure breaks' in self.last_stderr, name + ': ' + self.last_stderr)

    def test_a_block_scalar_event_text_is_compared(self):
        def log(price):
            return ('---\neventLogRecord:\n  event: PriceEvent\n  eventToString: |\n    Order{price=' + price
                    + '}\n  nodeLogs:\n    - n: { v: 1}\n---\n')
        self.assertEqual(0, self.run_compare(log('5'), log('5'))[0])
        self.assertEqual(1, self.run_compare(log('5'), log('9'))[0])

    def test_a_continuation_that_looks_like_a_field_is_still_compared(self):
        # the analyser cannot tell `detail:` from a field, so neither can the script - but it compares it
        def log(price):
            return ('---\neventLogRecord:\n  event: PriceEvent\n  eventToString: Order{id=1,\n  detail: price='
                    + price + '}\n  nodeLogs:\n    - n: { v: 1}\n---\n')
        self.assertEqual(1, self.run_compare(log('5'), log('9'))[0])
        times = log('5').replace('event: PriceEvent', 'event: PriceEvent\n  logTime: 1\n  thread: a')
        self.assertEqual(0, self.run_compare(times, times.replace('logTime: 1', 'logTime: 2').replace('thread: a', 'thread: b'))[0],
                         'times and the thread are never compared')

    def test_a_lone_carriage_return_does_not_split_a_line(self):
        # the analyser splits on \n only: `abc\r      deeper` is ONE line, not a deeper line that breaks the record
        a = '---\neventLogRecord:\n  event: E\n  eventToString: abc\r      deeper 1\n  nodeLogs:\n    - n: { v: 1}\n---\n'
        self.assertEqual(0, self.run_compare(a, a)[0], self.last_stderr)
        self.assertEqual(1, self.run_compare(a, a.replace('deeper 1', 'deeper 2'))[0])

    def test_entries_split_as_the_analyser_splits_a_text_export(self):
        # legacy grammar (NodeLogTokenizer.splitTopLevel): no escapes, single and double quotes, every part kept
        self.assertEqual([('v', 'a'), (None, ''), (None, 'b')], audit_compare.split_top_level(' v: a,,b'))
        self.assertEqual([('a', '1'), (None, '')], audit_compare.split_top_level('a: 1,'))
        self.assertEqual([('a', "'x, y'"), ('b', '2')], audit_compare.split_top_level("a: 'x, y', b: 2"))
        self.assertEqual(1, self.run_compare(BEFORE.replace('decision: hold', 'v: a,,b'),
                                             BEFORE.replace('decision: hold', 'v: a,b'))[0], 'an empty part is kept')
        # a backslash is text: a quoted value ending in one closes there, so the trace run after it is still framework
        a = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}', '- quoteDEMO: { thread: "a\\", method: onA, price: 5}')
        self.assertEqual(0, self.run_compare(a, a.replace('method: onA', 'method: onB'))[0])

    def test_port_quote_rules(self):
        q = audit_compare.quote_state_after
        self.assertEqual('"', q('  key: "open', ''))
        self.assertEqual('', q('  key: "a \\" b"', ''), 'a backslash escapes a double quote')
        self.assertEqual("'", q("  key: 'it''s", ''), "'' is an escaped single quote")
        self.assertEqual('', q("  key: it's plain", ''), 'a quote mid-scalar is text')
        self.assertEqual('', q('  # "comment', ''), 'a comment opens nothing')
        self.assertEqual('', q('more" tail', '"'), 'a continuation closes the open quote')
        lines = ['eventLogRecord:', '  event: E', '  eventToString: "never closes', '  nodeLogs:', '    - n: { v: 1}']
        roles, broken = audit_compare.analyse(lines)
        self.assertIsNone(broken)
        self.assertEqual('NODE_ITEM', roles[4], 'a quote that never closes is text, so it hides nothing (quotesClose)')

    def test_port_breaks(self):
        a = audit_compare.analyse
        self.assertIsNotNone(a(['eventLogRecord:', '  event: E', '  event: F', '  nodeLogs:'])[1], 'a repeated field')
        self.assertIsNone(a(['eventLogRecord:', '  event: E', '  extra:', '    nested: 1', '  nodeLogs:'])[1],
                          'nesting under an unknown key with no value is ignored')
        self.assertIsNotNone(a(['eventLogRecord:', '  event: ExportFunctionAuditEvent', '  eventToString: other',
                                'public void x()', '  nodeLogs:'])[1], 'the signature allowance needs @Override')
        self.assertIsNone(a(['eventLogRecord:', '  event: ExportFunctionAuditEvent', '  eventToString: @Override',
                             'public void x()', '  nodeLogs:'])[1])

    # --- fifth review of PR 127: text the analyser skips is still compared ---

    def assert_value_compared(self, make):
        self.assertEqual(0, self.run_compare(make('5'), make('5'))[0], self.last_stderr)
        self.assertEqual(1, self.run_compare(make('5'), make('9'))[0], make('5'))

    def record(self, header, items='    - n: { v: 1}'):
        return '---\neventLogRecord:\n  event: E\n' + header + '\n  nodeLogs:\n' + items + '\n---\n'

    def test_a_hash_line_in_unquoted_event_text_is_compared(self):
        self.assert_value_compared(lambda p: self.record('  eventToString: Order{id=1,\n#price=' + p + '}'))

    def test_a_hash_line_inside_a_block_scalar_is_compared(self):
        self.assert_value_compared(lambda p: self.record('  eventToString: |\n    Order\n    #price=' + p))

    def test_nesting_under_an_unknown_key_is_compared(self):
        self.assert_value_compared(lambda p: self.record('  eventToString: Order{id=1,\n  detail:\n    price=' + p + '}'))

    def test_a_hash_line_inside_a_node_value_is_compared(self):
        self.assert_value_compared(lambda p: self.record('  eventToString: x',
                                                         '    - n: { note: a,\n#price=' + p + '\n        v: 1}'))

    def test_a_record_behind_a_mid_file_byte_order_mark_is_read(self):
        a = BEFORE.replace('---\neventLogRecord:\n  logTime: 2000', '---\n\ufeffeventLogRecord:\n  logTime: 2000', 1)
        self.assertNotEqual(a, BEFORE)
        b = a.replace('method: onTrigger}', 'method: onQuote}')
        self.assertEqual(0, self.run_compare(a, b)[0], 'a framework-only change, so the record was parsed, not compared raw')

    def test_which_fields_are_compared(self):
        self.assert_value_compared(lambda p: self.record('  eventType: com.acme.E' + p))
        self.assert_value_compared(lambda p: self.record('  eventToString: x\n  venue: DEMO' + p))
        g = lambda p: self.record('  eventToString: x\n  groupingId: ' + p)
        self.assertEqual(0, self.run_compare(g('5'), g('9'))[0], 'the grouping id is never compared')

    def test_only_ascii_whitespace_is_stripped(self):
        e = lambda sp: self.record('  eventToString: x', '    - n: { v: ' + sp + '1}')
        # conservative: the analyser's node-entry tokenizer would strip an em space; the script compares it as text,
        # which can only report a difference, never hide one
        self.assertEqual(1, self.run_compare(e(''), e('\u2003'))[0], 'an em space is compared as text')

    def test_an_empty_node_body_is_no_entry(self):
        # sixth review of PR 127: `{ }` became a business entry (the analyser's parseItem splits only a non-blank body)
        a = BEFORE.replace('- quoteDEMO: { thread: main, method: onTrigger}',
                           '- quoteDEMO: { thread: main, method: onTrigger}\n    - m: { }')
        self.assertNotEqual(a, BEFORE)
        self.assertEqual(0, self.run_compare(a, BEFORE)[0])


# ---- quoted scalars (format-spec 3a, Format 1 revision 1.2): a capture from before the runtime quoted against one after ----
OLD_RUNTIME = textwrap.dedent(r"""
    ---
    eventLogRecord:{SP}
        eventTime: 1000
        logTime: 1001
        groupingId: null
        event: MemberEvent
        eventToString: MemberEvent{id=7, note: vip}
        thread: main
        nodeLogs:{SP}
            - gymAccess: { thread: main, method: onMember, reason: membership not active, balance due 30.0, granted: false}
            - order: { o: Order{id=1, px=2.0}, said: say "hi", name: Renée, smile: 😀, code: 123}
        endTime: 1002
    ---
    eventLogRecord:{SP}
        logTime: 2001
        event: Tick
        nodeLogs:{SP}
            - gymAccess: { granted: true}
        endTime: 2002
    ---
    """).lstrip('\n').replace('{SP}', ' ')   # {SP}: a trailing space the runtime writes, kept out of the source
NEW_RUNTIME = textwrap.dedent(r"""
    ---
    eventLogRecord:{SP}
        textEncoding: QUOTED_SCALARS
        eventTime: 5000
        logTime: 5001
        groupingId: null
        event: MemberEvent
        eventToString: "MemberEvent{id=7, note: vip}"
        thread: main
        nodeLogs:{SP}
            - gymAccess: { thread: main, method: onMember, reason: "membership not active, balance due 30.0", granted: false}
            - order: { o: "Order{id=1, px=2.0}", said: "say \x22hi\x22", name: "Renée", smile: "😀", code: "123"}
        endTime: 5002
    ---
    eventLogRecord:{SP}
        logTime: 6001
        event: Tick
        nodeLogs:{SP}
            - gymAccess: { granted: true}
        endTime: 6002
    ---
    """).lstrip('\n').replace('{SP}', ' ')


class QuotedScalarsTest(unittest.TestCase):
    run_compare = AuditCompareTest.run_compare
    _run = AuditCompareTest._run
    ANNOTATIONS_ARE_TRACE = True

    def test_a_capture_before_the_runtime_quoted_matches_one_after(self):
        for before, after in ((OLD_RUNTIME, NEW_RUNTIME), (NEW_RUNTIME, OLD_RUNTIME)):
            code, out = self.run_compare(before, after)
            self.assertEqual(0, code, out + self.last_stderr)
            self.assertIn('quoted scalars: 1 business record(s) match only as the legacy grammar reads them', out)
            self.assertIn('framework (reported, does not decide the verdict): same', out)

    def test_WRONG_RESULT_CONTROL_a_changed_value_across_the_upgrade_still_differs(self):
        for changed in (NEW_RUNTIME.replace('balance due 30.0"', 'balance due 40.0"'),
                        NEW_RUNTIME.replace('code: "123"', 'code: "124"'),
                        NEW_RUNTIME.replace(r'\x22hi\x22', r'\x22bye\x22'),
                        NEW_RUNTIME.replace('note: vip}"', 'note: VIP}"'),
                        NEW_RUNTIME.replace('{ granted: true}', '{ granted: false}')):
            self.assertNotEqual(changed, NEW_RUNTIME)
            code, out = self.run_compare(OLD_RUNTIME, changed)
            self.assertEqual(1, code, out)
            self.assertIn('verdict: logged business entries DIFFER', out)

    def test_the_marker_is_never_compared_and_a_bare_value_reads_the_same_either_way(self):
        # an exact match needs no legacy reading: "123" decodes to 123, the header decodes to the bare text
        plain_old = OLD_RUNTIME.replace('reason: membership not active, balance due 30.0, ', '')
        plain_new = NEW_RUNTIME.replace('reason: "membership not active, balance due 30.0", ', '')
        code, out = self.run_compare(plain_old, plain_new)
        self.assertEqual(0, code, out)
        self.assertNotIn('quoted scalars:', out)
        self.assertNotIn('textEncoding', out)

    def test_two_quoting_captures_are_compared_exactly(self):
        # both captures come from a runtime that quotes (each holds a marked record): `a: "x, b: y"` is one value and
        # `a: x, b: y` two, so they differ even though the legacy grammar reads them alike
        one = NEW_RUNTIME.replace('{ granted: true}', '{ a: x, b: y}')
        other = NEW_RUNTIME.replace('{ granted: true}', '{ a: "x, b: y"}').replace(
            '    logTime: 6001', '    textEncoding: QUOTED_SCALARS\n    logTime: 6001')
        self.assertIn('textEncoding: QUOTED_SCALARS\n    logTime: 6001', other)
        code, out = self.run_compare(one, other)
        self.assertEqual(1, code, out)
        both_marked = other.replace('{ a: "x, b: y"}', '{ a: "x", b: "y"}')
        self.assertEqual(1, self.run_compare(other, both_marked)[0])
        self.assertEqual(0, self.run_compare(both_marked, both_marked.replace('{ a: "x", b: "y"}', '{ a: x, b: y}'))[0])

    def test_the_declaration_counts_only_as_the_first_line(self):
        # anywhere else the spelling is content (an unknown field, compared) and the record stays legacy
        late = NEW_RUNTIME.replace('    textEncoding: QUOTED_SCALARS\n', '', 1).replace(
            '    endTime: 5002', '    endTime: 5002\n    textEncoding: QUOTED_SCALARS', 1)
        code, out = self.run_compare(OLD_RUNTIME, late)
        self.assertEqual(1, code, out)
        commented = NEW_RUNTIME.replace('eventLogRecord: \n    textEncoding', 'eventLogRecord: \n    # note\n    textEncoding', 1)
        records = audit_compare.parse(self._write(commented))
        self.assertFalse(records[0]['marked'])
        self.assertTrue(audit_compare.parse(self._write(NEW_RUNTIME))[0]['marked'])
        other_value = NEW_RUNTIME.replace('QUOTED_SCALARS', 'QUOTED_SCALARS_V9', 1)
        record = audit_compare.parse(self._write(other_value))[0]
        self.assertFalse(record['marked'])
        self.assertIn(('textEncoding', 'QUOTED_SCALARS_V9'), record['other'])

    def test_a_marked_record_decodes_and_an_unmarked_one_keeps_its_quotes(self):
        marked, = [r for r in audit_compare.parse(self._write(NEW_RUNTIME)) if r['event'] == 'MemberEvent']
        self.assertEqual('MemberEvent{id=7, note: vip}', marked['text'])
        self.assertEqual(('order', [('o', 'Order{id=1, px=2.0}'), ('said', 'say "hi"'), ('name', 'Renée'),
                                    ('smile', '😀'), ('code', '123')]), marked['items'][1])
        self.assertEqual(('reason', 'membership not active, balance due 30.0'), marked['items'][0][1][2])
        unmarked, = [r for r in audit_compare.parse(self._write(NEW_RUNTIME.replace('    textEncoding: QUOTED_SCALARS\n', '')))
                     if r['event'] == 'MemberEvent']
        self.assertEqual('"MemberEvent{id=7, note: vip}"', unmarked['text'])
        self.assertIn(('said', r'"say \x22hi\x22"'), unmarked['items'][1][1])

    def test_unquote_follows_the_analysers_escapes(self):
        u = audit_compare.unquote
        self.assertEqual(('a\\b"c\nd\re\tf\0g"hé', True), u(r'"a\\b\"c\nd\re\tf\0g\x22hé"'))
        self.assertEqual(('😀', True), u(r'"😀"'))
        # C35's malformed run: unknown escape, too few hex digits and half a surrogate pair are kept verbatim
        self.assertEqual((r'x\q\u12G4\uD83Dy', True), u(r'"x\q\u12G4\uD83Dy"'))
        self.assertEqual((r'"dangling\"', False), u(r'"dangling\"'))
        self.assertEqual(('"a" b "c"', False), u('"a" b "c"'))
        self.assertEqual(('plain', False), u('plain'))
        self.assertEqual([('k@1', 'v, w'), ('e', ''), (None, 'flag')], audit_compare.split_quoted('"k@1": "v, w", e:, flag'))

    PROBE = textwrap.dedent("""
        ---
        eventLogRecord:{SP}
        {marker}    event: Ev
            eventToString: {text}
            nodeLogs:{SP}
                - gate: {{ {body}}}
            endTime: 2
        ---
        """).lstrip('\n')

    def probe(self, body, text='Ev{x}', marked=False):
        return self.PROBE.format(marker='    textEncoding: QUOTED_SCALARS\n' if marked else '', text=text, body=body,
                                 SP=' ')

    def test_WRONG_RESULT_CONTROL_a_quoting_runtime_capture_that_needed_no_quotes_is_not_taken_for_an_old_one(self):
        # two captures from the quoting runtime - reason="a, b: c" (one value, marked) and reason="a",
        # b="c" (two values, nothing to quote, so unmarked) - read alike under the legacy grammar. The unmarked record
        # proves nothing about its age, so they are compared exactly.
        one = self.probe('reason: "a, b: c"', marked=True)
        two = self.probe('reason: a, b: c')
        for before, after in ((one, two), (two, one)):
            code, out = self.run_compare(before, after)
            self.assertEqual(1, code, out)
            self.assertNotIn('quoted scalars:', out)
        # text the quoting runtime writes bare is no proof of age either (core d601af6/dae594b)
        # and (core 3c1ee83) an inner `:` - times, timestamps, URLs, drive paths, IPv6 - is written bare too
        for bare in ('d: -dash', 'e: ', 't: ~', 'h: .hidden', 'p: +x', 'n: 3', 'b: true',
                     'at: 12:30:00', 'ts: 2026-11-04T10:00:00Z', 'url: http://x:8/a', 'ab: a:b', 'dir: C:\\temp',
                     'ip: fe80::1'):
            same_runtime = (self.probe('a: x, b: y, ' + bare), self.probe('a: "x, b: y", ' + bare, marked=True))
            for before, after in (same_runtime, same_runtime[::-1]):
                code, out = self.run_compare(before, after)
                self.assertEqual(1, code, bare + ': ' + out)
                self.assertNotIn('quoted scalars:', out)

    def test_an_unmarked_record_proves_its_age_only_by_text_a_quoting_runtime_would_quote(self):
        proves = lambda body: audit_compare.proves_pre_quoting(audit_compare.parse(self._write(self.probe(body)))[0])
        # mirrors the runtime's final flow rule (core dae594b): typed-looking and empty text is written bare
        for body in ('reason: membership not active, balance due 30.0',   # a keyless fragment
                     'o: Order(id=1, px=2.0)', 'tags: [a, b]', 'kv: x: y', 'c: a:', 'q: say "hi"', 'p: open(',
                     'h: #1', 'm: - x', 'a: &x', 'r: %d', 's: @me', 'l: <tag>', 'eq: =x', 'one: -', 'nb: x\u00a0',
                     'u: a\u2028b'):
            self.assertTrue(proves(body), body)
        for body in ('reason: a, b: c', 'n: 3', 'd: -2.5', 'b: true', 'x: null', 'f: NaN', 'w: plain words', 'k: f(x)',
                     'e: ', 'm: -x', 'dash: -dash', 't: ~', 'h: .hidden', 'p: +x', 'y: yes', 'z: 0x1F',
                     'at: 12:30', 'url: http://x:8/a', 'ts: 2026-11-04T10:00:00Z', 'ab: a:b', 'ip: fe80::1'):
            # a `:` without a space after it is left bare (core 3c1ee83)
            self.assertFalse(proves(body), body)

    def test_header_whitespace_a_bare_header_lost_matches_across_the_upgrade(self):
        # the old runtime wrote `eventToString: Ev{x} ` and the reader stripped it; the new one quotes it
        old, new = self.probe('reason: ok', text='Ev{x} '), self.probe('reason: ok', text='"Ev{x} "', marked=True)
        code, out = self.run_compare(old, new)
        self.assertEqual(0, code, out)
        self.assertIn('quoted scalars: 1 business record(s)', out)
        # accepted residual: a quoting runtime's bare `Ev{x}` reads the same as the old runtime's stripped one, so header
        # edge whitespace is matched without proof; entries that merge under the legacy grammar always need it
        self.assertEqual(0, self.run_compare(self.probe('reason: ok'), new)[0])
        self.assertEqual(1, self.run_compare(self.probe('reason: ok', text='Ev{y} '), new)[0])

    def test_one_real_change_across_the_upgrade_is_reported_once(self):
        # one changed record made every record fall back to the exact view and report as changed
        changed = NEW_RUNTIME.replace('{ granted: true}', '{ granted: false}')
        code, out = self.run_compare(OLD_RUNTIME, changed)
        self.assertEqual(1, code, out)
        self.assertIn('business: DIFFERENT (1 change(s))', out)
        self.assertIn('quoted scalars: 1 business record(s)', out)
        self.assertEqual(1, out.count('    - '), out)

    def _write(self, text):
        handle = tempfile.NamedTemporaryFile('w', suffix='.yaml', delete=False, encoding='utf-8')
        with handle:
            handle.write(text)
        self.addCleanup(Path(handle.name).unlink)
        return handle.name

if __name__ == '__main__':
    unittest.main()
