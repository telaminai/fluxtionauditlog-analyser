package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-8 driven through {@link CoverageService#assess}, which is where it has to work.
 *
 * <p><b>Why these exist.</b> The first pass tested the parsing helper only, and review showed what that
 * left open: removing the coverage wiring, dropping the event-type check, treating every level as quiet,
 * or annotating the first change instead of the last all left the suite green. A helper test proves a
 * helper works, not that the product uses it.
 *
 * <p>The behaviour under test: a node set to a quiet level still RUNS, but its lines are suppressed, so
 * coverage lists it as uncovered with no explanation — while the control record naming it sits in the
 * log.
 */
class CoveragePerNodeLevelTest {

    private static String resource(String path) {
        try (InputStream in = CoveragePerNodeLevelTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "not in the test runtime: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** One control record, rendered as the runtime renders it. */
    /** A control record with NO logTime — legal under §1, and a real case for the window wording. */
    private static String untimedControl(String sourceId, String level) {
        return """
                eventLogRecord:
                  groupingId: null
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=%s, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(level, sourceId);
    }

    private static String control(long logTime, String sourceId, String level) {
        return """
                eventLogRecord:
                  logTime: %d
                  groupingId: null
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=%s, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(logTime, level, sourceId);
    }

    private static String plainRecord(long logTime) {
        return """
                eventLogRecord:
                  logTime: %d
                  groupingId: null
                  event: Quote
                  nodeLogs:
                    - quoteHandler: { seen: true}
                ---
                """.formatted(logTime);
    }

    private static CoverageService.Result assess(String yaml, boolean filtered,
                                                 telamin.fluxtion.audit.analyser.analyser.filter.FilterState f) {
        ProcessorTopology topology =
                GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(yaml);
        return CoverageService.assess(store, filtered, f,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
    }

    /** A filter admitting only records in [from, to]. */
    private static telamin.fluxtion.audit.analyser.analyser.filter.FilterState window(long from, long to) {
        var f = new telamin.fluxtion.audit.analyser.analyser.filter.FilterState();
        f.setTimeRange(from, to);
        return f;
    }

    private static CoverageService.Result assess(String yaml) {
        ProcessorTopology topology =
                GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        HeapLogStore store = new HeapLogStore(yaml);
        return CoverageService.assess(store, false, null,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> annotations(CoverageService.Result r) {
        Object a = r.echo().get("levelAnnotations");
        return a == null ? Map.of() : (Map<String, String>) a;
    }

    /** The node the graph declares and the log never covers; the one MA-8 should explain. */
    private static String anUncoveredNode(CoverageService.Result r) {
        return r.ledger().stream()
                .filter(row -> "uncovered".equals(row.get("status")))
                .map(row -> String.valueOf(row.get("instanceId")))
                .findFirst().orElseThrow(() -> new AssertionError("fixture has no uncovered node"));
    }

    /** MA-8.1 — a per-node quiet level is stated against the node it names. */
    @Test
    void aQuietPerNodeLevelIsAnnotatedAgainstThatNode() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        assertTrue(annotations(base).isEmpty(), "no level change, no annotation");

        CoverageService.Result withLevel = assess(control(1000, node, "WARN") + plainRecord(1001));
        assertTrue(annotations(withLevel).containsKey(node),
                "MA-8.1: the log names this node's level; coverage must say so rather than listing it "
                        + "as plainly uncovered. annotations=" + annotations(withLevel));
        assertTrue(annotations(withLevel).get(node).contains("WARN"), "and names the level");
    }

    /** MA-8.2 — annotate, NEVER excuse: the node stays uncovered and in the ratio. */
    @Test
    void anAnnotatedNodeStaysUncoveredAndInTheRatio() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result withLevel = assess(control(1000, node, "WARN") + plainRecord(1001));

        assertEquals(base.echo().get("uncovered"), withLevel.echo().get("uncovered"),
                "MA-8.2: the count must not move — excusing it would hide a node that never ran");
        assertEquals(base.echo().get("ratio"), withLevel.echo().get("ratio"),
                "MA-8.2: and neither must the ratio");
        assertTrue(withLevel.ledger().stream()
                        .anyMatch(row -> node.equals(row.get("instanceId"))
                                && "uncovered".equals(row.get("status"))),
                "MA-8.2: it is still uncovered in the ledger");
    }

    /** A level that does NOT suppress info lines must not be offered as an explanation. */
    @Test
    void aLoudLevelIsNotAnExplanation() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result withDebug = assess(control(1000, node, "DEBUG") + plainRecord(1001));

        assertFalse(annotations(withDebug).containsKey(node),
                "DEBUG does not suppress info lines, so it explains nothing: "
                        + annotations(withDebug));
    }

    /** MA-8.5 — keyed on the event TYPE. A record that merely looks like one is not a level change. */
    @Test
    void aRecordThatIsNotAControlEventIsNotReadAsALevelChange() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String lookalike = """
                eventLogRecord:
                  logTime: 1000
                  groupingId: null
                  event: Quote
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        // Second re-review S5.1: this rendering used to lack logRecordProcessor, so parse() refused it before
        // the event type was ever consulted, and the test stayed green with isControlEvent -> true. It is now
        // the complete pinned rendering, and the twin below proves the event name is the ONLY thing refusing it.
        assertTrue(annotations(assess(lookalike.replace("event: Quote", "event: EventLogControlEvent")
                        + plainRecord(1001))).containsKey(node),
                "precondition: with the real event name this exact record IS a level change");
        assertFalse(annotations(assess(lookalike + plainRecord(1001))).containsKey(node),
                "MA-8.5: the event TYPE selects a control record; content that imitates one does not");
    }

    /**
     * MA-8.4 — intervals. A node quietened and later restored is annotated inside the window, and the
     * restore must not erase it.
     *
     * <p>Review found both directions wrong when only the LAST change was used: a node set to WARN and
     * restored to INFO was annotated NOWHERE, because the last change is the restore.
     */
    @Test
    void aQuietenedThenRestoredNodeIsStillAnnotatedForItsWindow() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);

        CoverageService.Result r = assess(
                control(1000, node, "WARN") + plainRecord(1001) + control(1007, node, "INFO"));

        assertTrue(annotations(r).containsKey(node),
                "MA-8.4: the WARN window is real even though the LAST change is the restore — "
                        + "using only the last change annotated nothing. annotations=" + annotations(r));
        // Independent review F4/F5 moved intervals from logTime to record order, so the window is stated
        // by the records that open and close it rather than by "between 1000 and 1007".
        assertTrue(annotations(r).get(node).contains("at record 1 (logTime 1000)")
                        && annotations(r).get(node).contains("until record 3 (logTime 1007)"),
                "and the annotation states the window: " + annotations(r).get(node));
    }

    /** MA-8.2's note must travel with the annotations, or a reader may take them as an excuse. */
    @Test
    void theAnnotationCarriesItsOwnCaveat() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        CoverageService.Result r = assess(control(1000, node, "WARN") + plainRecord(1001));

        assertTrue(String.valueOf(r.echo().get("levelAnnotationsNote")).contains("still counted as uncovered"),
                "the echo must say these are not excuses: " + r.echo().get("levelAnnotationsNote"));
        assertTrue(String.valueOf(r.echo().get("levelAnnotationsNote")).contains("which nothing in a record establishes"),
                "and states the processor-identity limit (re-review RR-3): " + r.echo().get("levelAnnotationsNote"));
    }

    // ------------------------------------------------------------------ filtered scope

    /**
     * MA-8.3 — a filter that hides the control record must NOT drop the annotation.
     *
     * <p>A level change is configuration state, not an event you happen to be looking at. Every earlier
     * test ran unfiltered, which is why "drop all annotations when filtered" survived the whole suite.
     */
    @Test
    void aFilterHidingTheControlRecordStillAnnotates() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = control(1000, node, "WARN") + plainRecord(1001) + plainRecord(1002);

        CoverageService.Result filtered = assess(yaml, true, window(1001, 1002));
        assertEquals("current filter", filtered.echo().get("scope"), "precondition: the filter is on");
        assertTrue(annotations(filtered).containsKey(node),
                "MA-8.3: the control record is outside the filter, but the level still applies to it. "
                        + "annotations=" + annotations(filtered));
    }

    /**
     * MA-8.4, scope END — a change that has not happened yet cannot explain earlier silence.
     */
    @Test
    void aChangeAfterTheScopeDoesNotExplainSilenceBeforeIt() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = plainRecord(1000) + plainRecord(1001) + control(5000, node, "WARN");

        CoverageService.Result filtered = assess(yaml, true, window(1000, 1001));
        assertFalse(annotations(filtered).containsKey(node),
                "the WARN was set at 5000, after everything in view — it explains nothing here: "
                        + annotations(filtered));
    }

    /**
     * MA-8.4, scope START — a window that CLOSED before the scope began explains nothing in it.
     *
     * <p>Clipping only the end left this annotated "WARN between 1001 and 1007" for a filter entirely
     * after the restore — a window the scope never overlapped.
     */
    @Test
    void aWindowThatClosedBeforeTheScopeDoesNotExplainSilenceInIt() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String yaml = control(1001, node, "WARN") + plainRecord(1002)
                + control(1007, node, "INFO") + plainRecord(3000) + plainRecord(3001);

        CoverageService.Result filtered = assess(yaml, true, window(3000, 3001));
        assertFalse(annotations(filtered).containsKey(node),
                "the WARN window closed at 1007, long before this scope began: " + annotations(filtered));
    }

    /**
     * A change addressed to ANOTHER processor grouping does not apply — the runtime's rule, not a guess.
     *
     * <p>This test used to assert that {@code beta}'s INFO left {@code alpha}'s WARN window open, on the
     * model that {@code groupId} named a group of NODES. It does not. fluxtion-runtime 1.0.16 applies a change
     * only when the PROCESSOR's {@code groupingId} is null or equals the change's {@code groupId}, and a change
     * with no {@code sourceId} then sets every node. So the old assertion was right for a processor grouped as
     * {@code alpha} and wrong for an ungrouped one, where beta's INFO really does restore every node. Both are
     * asserted now, and the difference is read from each record's own {@code groupingId:} field.
     */
    @Test
    void aChangeAddressedToAnotherGroupingDoesNotApply_andInAnUngroupedProcessorItDoes() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String controls = """
                eventLogRecord:
                  logTime: 1000
                  groupingId: %1$s
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=null, groupId=alpha}
                  nodeLogs:
                ---
                eventLogRecord:
                  logTime: 1005
                  groupingId: %1$s
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=INFO, logRecordProcessor=null, sourceId=null, groupId=beta}
                  nodeLogs:
                ---
                """;

        CoverageService.Result grouped = assess(controls.formatted("alpha") + plainRecord(1006));
        assertTrue(annotations(grouped).containsKey(node),
                "in a processor grouped 'alpha', beta's change does not apply and alpha's WARN still governs: "
                        + annotations(grouped));
        // Third re-review O-D: the grouping is its own sentence, so it cannot read as a gloss on what precedes it.
        assertTrue(annotations(grouped).get(node).contains(". It was addressed to processor grouping 'alpha', "
                        + "which is the grouping the control record itself declares"),
                "and it says why the change applied: " + annotations(grouped).get(node));

        CoverageService.Result ungrouped = assess(controls.formatted("null") + plainRecord(1006));
        assertFalse(annotations(ungrouped).containsKey(node),
                "in an ungrouped processor BOTH apply, so beta's INFO restored every node before record 3: "
                        + annotations(ungrouped));
    }

    /** LOW — a lookalike event name is not a control event. */
    @Test
    void aLookalikeEventNameIsNotAControlEvent() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String fake = """
                eventLogRecord:
                  logTime: 1000
                  groupingId: null
                  event: FakeEventLogControlEventX
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        // Second re-review S5.2: RR-3 scopes a change to records of the SAME declared grouping. If this fixture
        // and plainRecord ever declare different groupings, the record is hidden by scoping and the assertion
        // below passes whatever the event-name rule does — which it did, under the contains() mutant. Pinned
        // two ways: the contexts are asserted equal, and the real-name twin must annotate.
        assertEquals(PerNodeLevelChanges.groupingOf(fake), PerNodeLevelChanges.groupingOf(plainRecord(1001)),
                "precondition: the control fixture and the record it would explain declare the same grouping");
        assertTrue(annotations(assess(fake.replace("event: FakeEventLogControlEventX", "event: EventLogControlEvent")
                        + plainRecord(1001))).containsKey(node),
                "precondition: with the real event name this exact record IS a level change");
        assertFalse(annotations(assess(fake + plainRecord(1001))).containsKey(node),
                "contains() accepted this; the simple name must match exactly");
    }

    /** LOW — a fully-qualified control event name is still recognised. */
    @Test
    void aFullyQualifiedControlEventNameIsRecognised() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String qualified = """
                eventLogRecord:
                  logTime: 1000
                  groupingId: null
                  event: com.telamin.fluxtion.runtime.audit.EventLogControlEvent
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=%s, groupId=null}
                  nodeLogs:
                ---
                """.formatted(node);

        assertTrue(annotations(assess(qualified + plainRecord(1001))).containsKey(node),
                "the qualified name is the same event");
    }

    // ------------------------------------------------------------------ untimed windows

    /** An untimed WARN closed by an untimed INFO must not print Long.MIN_VALUE at a reader. */
    @Test
    void twoUntimedChangesReadAsUntimedRatherThanAsAHugeNegativeNumber() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);

        CoverageService.Result r = assess(
                untimedControl(node, "WARN") + plainRecord(1001) + untimedControl(node, "INFO"));

        String note = annotations(r).get(node);
        assertNotNull(note, "the WARN window is still real: " + annotations(r));
        assertFalse(note.contains("-9223372036854775808"),
                "a stand-in for 'no time given' must never reach a reader: " + note);
        assertTrue(note.contains("untimed"), "it says so in words: " + note);
    }

    /**
     * A TIMED WARN closed by an UNTIMED change must keep its window.
     *
     * <p>Long.MIN_VALUE is "no time given", not an early instant, so comparing it against the scope
     * start dropped the window entirely and a genuinely quietened node lost its explanation.
     */
    @Test
    void aTimedWindowClosedByAnUntimedChangeIsKept() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);

        CoverageService.Result r = assess(
                control(1001, node, "WARN") + plainRecord(1002) + untimedControl(node, "INFO"));

        assertTrue(annotations(r).containsKey(node),
                "the window must survive an untimed close: " + annotations(r));
        assertTrue(annotations(r).get(node).contains("until record 3 (untimed)"),
                "and say what closed it: " + annotations(r).get(node));
    }

    /**
     * A loud change that is not the first must not stop a later quiet one being found. Originally the
     * multi-group fall-through test; under the runtime's rule both changes apply to an ungrouped processor
     * and both set every node, so it now asserts that the LATER, quiet one is the one that explains.
     */
    @Test
    void aLaterGroupsWindowIsFoundWhenTheFirstDoesNotApply() {
        CoverageService.Result base = assess(plainRecord(1000));
        String node = anUncoveredNode(base);
        String twoGroups = """
                eventLogRecord:
                  logTime: 1000
                  groupingId: null
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=DEBUG, logRecordProcessor=null, sourceId=null, groupId=alpha}
                  nodeLogs:
                ---
                eventLogRecord:
                  logTime: 1001
                  groupingId: null
                  event: EventLogControlEvent
                  eventToString: EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=null, groupId=beta}
                  nodeLogs:
                ---
                """;

        CoverageService.Result r = assess(twoGroups + plainRecord(1002));
        assertTrue(annotations(r).containsKey(node),
                "alpha is DEBUG and explains nothing; beta's WARN does, and must still be reached: "
                        + annotations(r));
        assertTrue(annotations(r).get(node).contains("beta"), "and it names beta: " + annotations(r).get(node));
    }

    // ------------------------------------------------------------------ independent review F4, F5, O3

    private static String globalControl(long logTime, String level) {
        return control(logTime, "null", level);
    }

    private static final String MARKER_2 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n---\n";
    private static final String MARKER_1 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";

    /**
     * F4 — a GLOBAL restore closes a per-node interval. The runtime sets every node's logger on a change
     * with no sourceId (measured on 1.0.16: after per-node WARN, canLog(INFO) is false; after a global INFO it
     * is true). The time-based version kept saying "WARN to the end of this log" after the restore.
     */
    @Test
    void aGlobalRestoreClosesAPerNodeInterval() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = control(1000, node, "WARN") + plainRecord(1003) + globalControl(1007, "INFO") + plainRecord(1008);

        assertTrue(annotations(assess(seq, true, window(1003, 1003))).containsKey(node),
                "positive control: a record inside the WARN interval is explained");
        assertFalse(annotations(assess(seq, true, window(1008, 1008))).containsKey(node),
                "F4: after a global INFO the node is not WARN any more, so nothing explains its silence: "
                        + annotations(assess(seq, true, window(1008, 1008))));
    }

    /** MA-8.2, kept apart from F4's wording: the level changes never move the denominator or the ledger. */
    @Test
    void levelChangesNeverMoveTheDenominatorOrTheLedger() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String withChanges = control(1000, node, "WARN") + plainRecord(1003) + globalControl(1007, "INFO") + plainRecord(1008);
        String without = plainRecord(1003) + plainRecord(1008);
        for (long at : new long[]{1003, 1008}) {
            var a = assess(withChanges, true, window(at, at));
            var b = assess(without, true, window(at, at));
            assertEquals(b.echo().get("uncovered"), a.echo().get("uncovered"), "uncovered at " + at);
            assertEquals(b.echo().get("ratio"), a.echo().get("ratio"), "ratio at " + at);
            assertEquals(b.ledger(), a.ledger(), "ledger at " + at);
        }
    }

    /** F5 — the interval is half-open by POSITION: a record after the restore is outside it. */
    @Test
    void theRestoreBoundaryIsHalfOpen() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = control(1000, node, "WARN") + control(1007, node, "INFO") + plainRecord(1007);
        var r = assess(seq, true, window(1007, 1007));
        assertEquals(2, r.echo().get("recordsScanned"), "precondition: the restore and the record are both in view");
        assertFalse(annotations(r).containsKey(node),
                "F5: nothing in view lies inside [WARN, INFO), so nothing is explained: " + annotations(r));
    }

    @Test
    void oneInstantInsideIsExplainedAndOneAfterIsNot() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = control(1000, node, "WARN") + plainRecord(1003) + control(1007, node, "INFO") + plainRecord(1008);
        assertTrue(annotations(assess(seq, true, window(1003, 1003))).containsKey(node), "inside");
        assertFalse(annotations(assess(seq, true, window(1008, 1008))).containsKey(node), "after");
    }

    /** F5 — an EMPTY selection is not an unbounded one, before or after every control. */
    @Test
    void anEmptySelectionIsExplainedByNothing() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = plainRecord(500) + control(1000, node, "WARN") + plainRecord(1001);
        for (long[] w : new long[][]{{1, 2}, {9000, 9001}}) {
            var r = assess(seq, true, window(w[0], w[1]));
            assertEquals(0, r.echo().get("recordsScanned"), "precondition: nothing is in view");
            assertFalse(annotations(r).containsKey(node),
                    "F5: no record is in view, so no level explains one [" + w[0] + "," + w[1] + "]: " + annotations(r));
        }
    }

    /** F5's last clause — a control AFTER the records in view cannot explain them, timed or not. */
    @Test
    void aControlAfterTheRecordsInViewExplainsNothing_evenUntimed() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        var r = assess(plainRecord(1000) + untimedControl(node, "WARN"), true, window(1000, 1000));
        assertFalse(annotations(r).containsKey(node),
                "an untimed control after the record was assigned to the start of the log: " + annotations(r));
    }

    /** O3 — whole fields only, and a rendering this class does not know is skipped, never read as global. */
    @Test
    void aFieldThatIsNotTheFieldIsNotReadAsItOrAsAbsent() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String wrong = control(1000, node, "WARN").replace("sourceId=" + node, "not_sourceId=" + node);
        var r = assess(wrong + plainRecord(1001));
        assertTrue(annotations(r).isEmpty(),
                "not_sourceId is not sourceId — and a missing sourceId must not become 'every node': " + annotations(r));
        String twice = control(1000, node, "WARN").replace("groupId=null", "groupId=null, sourceId=other");
        assertTrue(annotations(assess(twice + plainRecord(1001))).isEmpty(), "a field written twice is ambiguous");
    }

    /**
     * Carried item, corrected by the re-review (RR-4): a level set in one run is not silently carried into
     * the next — and a scope wholly in the later run gets NO definite suppression claim. The first version
     * said "its lines below that level are not in this log" and then admitted the log did not say whether
     * the level survived; the caveat was present and the conclusion it undermines was still asserted.
     */
    @Test
    void aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = control(1000, node, "WARN") + plainRecord(1001) + MARKER_2 + plainRecord(2000) + MARKER_1;
        String later = annotations(assess(seq, true, window(2000, 2000))).get(node);
        assertNotNull(later, "the annotation is not dropped — the level may well have survived: " + later);
        assertTrue(later.contains("Every record in view is in a LATER run") && later.contains("survived"),
                "it says the scope is after the boundary: " + later);
        // Eighth re-review R8-6: this guard pinned a form the code no longer writes, so it could not fail. Every
        // definite conclusion now reads ", so after record …"; wholly after a marker there must be none.
        assertFalse(later.contains(", so after record"),
                "RR-4: no definite suppression claim about records the level may not have reached: " + later);
        assertTrue(later.contains("If it survived the marker, then after that marker and before the stream-end marker "
                + "preceding record 4, in the records sharing its grouping, " + node + "'s lines below WARN are not in this log"),
                "the claim is made conditional instead: " + later);
    }

    @Test
    void aScopeSpanningARunBoundaryIsDefiniteOnlyBeforeIt() {
        String node = anUncoveredNode(assess(plainRecord(1000)));
        String seq = control(1000, node, "WARN") + plainRecord(1001) + MARKER_2 + plainRecord(2000) + MARKER_1;
        String spanning = annotations(assess(seq, true, window(1001, 2000))).get(node);
        assertTrue(spanning.contains("so after record 1 and before the stream-end marker preceding record 3, in the records "
                + "sharing its grouping, " + node + "'s lines below WARN are not in this log"),
                "definite within the run the change was made in: " + spanning);
        assertTrue(spanning.contains("for the records in view after that marker and before the stream-end marker preceding "
                + "record 4, in the records sharing its grouping, those lines are absent only if it survived the marker"),
                "conditional after the boundary: " + spanning);
        String same = annotations(assess(seq, true, window(1001, 1001))).get(node);
        // Seventh re-review R7-3: within one run there is still nothing CONDITIONAL — but the definite claim stops at the
        // marker that follows, because the level is not known to reach the run after it
        assertFalse(same.contains("survived"), "within the same run there is nothing conditional: " + same);
        assertTrue(same.contains("so after record 1 and before the stream-end marker preceding record 3, in the records "
                + "sharing its grouping"), "…and the definite claim is bounded by the marker that follows: " + same);
    }
}
