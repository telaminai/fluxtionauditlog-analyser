package telamin.fluxtion.audit.analyser.analyser.topology;

import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.audit.EventLogManager;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver;
import telamin.fluxtion.audit.analyser.analyser.parse.RolledLogStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which node a control event addresses, and in which processor — decided as the RUNTIME decides it.
 *
 * <p><b>Re-review RR-2.</b> The runtime renders {@code EventLogConfig} without escaping and looks a
 * {@code sourceId} up by exact map key. The parser stopped values at commas and braces and trimmed them,
 * so addresses that reach no node were read as naming riskMonitor, and an empty one as naming every node.
 *
 * <p><b>Re-review RR-3.</b> Record order is a clock only within one processor's records. A change from a
 * processor grouped {@code alpha} explained a {@code beta} record, and {@code beta}'s restore closed
 * {@code alpha}'s window.
 *
 * <p>Every address case is checked against a real {@code EventLogManager} and logger, so the expected answer
 * comes from the runtime, not from the parser under test.
 */
class ControlAddressAndScopeTest {

    static final class Node implements EventLogSource {
        EventLogger logger;
        @Override public void setLogger(EventLogger logger) { this.logger = logger; }
    }

    /** What the real runtime does to riskMonitor: set it to INFO, apply the change, report canLog(INFO). */
    private static boolean runtimeStillLogsInfo(String grouping, EventLogControlEvent change) {
        EventLogManager m = new EventLogManager();
        m.clock = new Clock();
        m.init();
        if (grouping != null) m.setLogGroupId(grouping);
        Node node = new Node();
        m.nodeRegistered(node, "riskMonitor");
        m.calculationLogConfig(new EventLogControlEvent(null, grouping, LogLevel.INFO));
        m.calculationLogConfig(change);
        return node.logger.canLog(LogLevel.INFO);
    }

    /** A record declaring {@code grouping} as the runtime does — or, for ABSENT, declaring nothing. */
    private static final String ABSENT = "<absent>";

    private static String groupingLine(String grouping) {
        return ABSENT.equals(grouping) ? "" : "  groupingId: " + grouping + "\n";
    }

    private static String row(int t, String grouping) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + groupingLine(grouping)
                + "  event: Tick\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n";
    }

    private static String control(int t, String grouping, EventLogControlEvent change) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + groupingLine(grouping)
                + "  event: EventLogControlEvent\n  eventToString: " + change + "\n  nodeLogs:\n---\n";
    }

    /**
     * A control record this reader cannot read (sixth re-review R6-1): a listener whose {@code toString} contains a
     * separator makes the rendering ambiguous, so {@code parse()} refuses it; the other two forms are a level the
     * runtime does not have and a record with no {@code eventToString} at all.
     */
    private static String unreadable(int t, String grouping, String form) {
        String body = switch (form) {
            case "unknownLevel" -> "  eventToString: EventLogConfig{level=FINE, logRecordProcessor=null, sourceId=riskMonitor, groupId=null}\n";
            case "missing" -> "";
            default -> "  eventToString: EventLogConfig{level=INFO, logRecordProcessor=Proc{a, sourceId=x}, sourceId=riskMonitor, groupId=null}\n";
        };
        return "eventLogRecord:\n  logTime: " + t + "\n" + groupingLine(grouping)
                + "  event: EventLogControlEvent\n" + body + "  nodeLogs:\n---\n";
    }

    private static String annotate(String log, String node, int... inView) {
        return PerNodeLevelChanges.of(new HeapLogStore(log)).annotationFor(node, inView);
    }

    // ------------------------------------------------------------------ RR-2: addresses

    @Test
    void positiveControls_aRealPerNodeAndARealGlobalChange() {
        var perNode = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        assertFalse(runtimeStillLogsInfo(null, perNode), "runtime: a per-node WARN quietens riskMonitor");
        assertNotNull(annotate(control(1, "null", perNode) + row(2, "null"), "riskMonitor", 1), "and it is explained");

        var global = new EventLogControlEvent(LogLevel.WARN);
        assertFalse(runtimeStillLogsInfo(null, global), "runtime: a global WARN quietens every node");
        String note = annotate(control(1, "null", global) + row(2, "null"), "riskMonitor", 1);
        assertTrue(note != null && note.contains("every node"), "and it is explained as every node: " + note);
        // Third re-review R2: the DECLARED-grouping branch of S2's rule had no witness. Every runtime record
        // declares a grouping, so this is the branch every real log takes.
        String perNodeNote = annotate(control(1, "null", perNode) + row(2, "null"), "riskMonitor", 1);
        for (String n : new String[]{note, perNodeNote}) {
            assertFalse(n.contains("this processor"), "R2: a shared grouping is not a processor: " + n);
            assertTrue(n.contains("the records sharing its grouping"), "R2: it says what it does know: " + n);
        }
    }

    @Test
    void addressesThatReachNoNodeInTheRuntimeReachNoNodeHere() {
        for (String source : new String[]{"riskMonitor, DEMO", "riskMonitor}DEMO", " riskMonitor ", ""}) {
            var change = new EventLogControlEvent(source, null, LogLevel.WARN);
            assertTrue(runtimeStillLogsInfo(null, change), "precondition, runtime: '" + source + "' reaches no node");
            String log = control(1, "null", change) + row(2, "null");
            assertNull(annotate(log, "riskMonitor", 1),
                    "RR-2: '" + source + "' left riskMonitor at INFO in the runtime, and must not explain it here");
            assertNull(annotate(log, "spreadCalculator", 1),
                    "RR-2: '" + source + "' names a node, so it is never read as every node");
        }
    }

    @Test
    void aGroupAddressIsComparedWhole() {
        var change = new EventLogControlEvent("riskMonitor", "alpha, DEMO", LogLevel.WARN);
        assertTrue(runtimeStillLogsInfo("alpha", change), "precondition, runtime: 'alpha, DEMO' is not alpha");
        assertNull(annotate(control(1, "alpha", change) + row(2, "alpha"), "riskMonitor", 1),
                "RR-2: the address was truncated to 'alpha' and read as this processor's");
    }

    /**
     * The limit no parser of this text can remove: the runtime renders Java null and the string "null"
     * identically. It is read as "no node", and the sentence says the log cannot tell.
     */
    @Test
    void theLiteralNullIsTheStatedLimitNotAClaimedFix() {
        var literal = new EventLogControlEvent("null", null, LogLevel.WARN);
        assertTrue(runtimeStillLogsInfo(null, literal), "runtime: the string \"null\" names no registered node");
        assertEquals(literal.toString(), new EventLogControlEvent(null, null, LogLevel.WARN).toString(),
                "precondition: the two renderings really are identical");
        String note = annotate(control(1, "null", literal) + row(2, "null"), "riskMonitor", 1);
        assertNotNull(note, "not dropped: it may well have named no node");
        // Second re-review S3: the ambiguity LEADS, and the conclusion is conditioned on it.
        String ambiguity = "names no node — which would set every node's audit level — or a node literally called "
                + "\"null\"; the log renders both identically";
        assertTrue(note.contains(ambiguity), "S3: the sentence states what the record says, both readings: " + note);
        assertTrue(note.contains("If it named no node, then after record 1, in the records sharing its grouping, "
                + "riskMonitor's lines below WARN are not in this log; otherwise this change explains nothing here"),
                "S3: and concludes only on that condition (bounded, seventh re-review R7-3): " + note);
        assertFalse(note.startsWith("this log sets every node's audit level"),
                "S3: the no-node reading is not asserted before the disclosure: " + note);
        assertTrue(note.indexOf("renders both identically") < note.indexOf("lines below"),
                "S3: disclosure first, conclusion after: " + note);
    }

    // ------------------------------------------------------------------ S2: every premise in one condition

    private static final String MARKER_1 = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";

    /**
     * Second re-review S2. With applicability NOT established and the scope wholly after a run boundary, the
     * sentence had two "if it did"s with different antecedents, and the second read as though survival alone
     * were enough. Both premises now travel in one condition, and nothing presumes a processor.
     */
    @Test
    void notEstablishedAndWhollyAfterABoundaryConcludesOnBothPremises() {
        var change = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        String note = annotate(control(1, ABSENT, change) + MARKER_1 + row(2, ABSENT) + MARKER_1, "riskMonitor", 1);
        assertNotNull(note);
        assertTrue(note.contains("If it applied here and it survived the marker, then after that marker and before the "
                + "stream-end marker preceding record 3, in the records that, like it, state no grouping, riskMonitor's "
                + "lines below WARN are not in this log; otherwise this change explains nothing here"),
                "S2: one condition, both premises (bounded, R7-3): " + note);
        assertFalse(note.contains("If it survived the marker, riskMonitor's"),
                "S2: survival alone is never enough while applicability is not established: " + note);
        assertFalse(note.contains("this processor"), "S2: no processor is presumed: " + note);
        assertTrue(note.contains("the records that, like it, state no grouping"), note);
    }

    @Test
    void notEstablishedAndSpanningABoundaryConditionsBothHalves() {
        var change = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        String log = control(1, ABSENT, change) + row(2, ABSENT) + MARKER_1 + row(3, ABSENT) + MARKER_1;
        String note = annotate(log, "riskMonitor", 1, 2);
        assertTrue(note.contains("If it applied here, then after record 1 and before the stream-end marker preceding record 3, "
                        + "in the records that, like it, state no grouping, riskMonitor's lines below WARN are not in this log"),
                "S2: even within the run the claim waits on applicability: " + note);
        assertTrue(note.contains("those lines are absent only if it applied here and it survived the marker"),
                "S2: after the marker, both premises: " + note);
        assertFalse(note.contains("so within the run"), "S2: nothing is definite while applicability is open: " + note);
    }

    @Test
    void aNullSourceInAnUndeclaredGroupingCarriesBothPremises() {
        var literal = new EventLogControlEvent("null", null, LogLevel.WARN);
        String note = annotate(control(1, ABSENT, literal) + row(2, ABSENT), "riskMonitor", 1);
        assertTrue(note.contains("If it named no node and it applied here, then after record 1, in the records that, like it, "
                + "state no grouping, riskMonitor's lines below WARN"),
                "S2 × S3: every premise the log leaves open, in one condition: " + note);
    }

    // ------------------------------------------------------------------ third re-review R1 and O-A

    /**
     * R1. The clause describing the change that CLOSES the window said "sets it to INFO for every node" when that
     * change was rendered sourceId=null — asserting the no-node reading the opening clause, since S3, discloses.
     */
    @Test
    void aClosingChangeRenderedNullIsDisclosedNotAsserted() {
        var warn = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        var closeNull = new EventLogControlEvent(null, null, LogLevel.INFO);   // renders sourceId=null
        String log = control(1, "null", warn) + row(2, "null") + control(3, "null", closeNull) + row(4, "null");
        String note = annotate(log, "riskMonitor", 1);
        assertNotNull(note, "record 2 is inside the WARN window");
        assertTrue(note.contains("names no node — or a node literally called \"null\"; the log renders both identically"),
                "R1: the closing change's rendering is disclosed with both readings: " + note);
        assertFalse(note.contains("for every node") && !note.contains("literally called \"null\""),
                "R1: 'for every node' is never said without the literal reading beside it: " + note);
        assertFalse(note.contains("sets it to INFO for every node"), "R1: the old assertion is gone: " + note);
    }

    /**
     * O-A. For a node literally NAMED "null", a sourceId=null change sets that node under both readings, so there is
     * no open premise and an "otherwise" would be false.
     */
    @Test
    void aNodeNamedNullIsSetUnderBothReadings() {
        var change = new EventLogControlEvent(null, null, LogLevel.WARN);
        String note = annotate(control(1, "null", change) + row(2, "null"), "null", 1);
        assertNotNull(note);
        assertFalse(note.contains("If it named no node"), "O-A: not a premise for this node: " + note);
        assertFalse(note.contains("otherwise this change explains nothing here"), "O-A: the otherwise was false: " + note);
        assertTrue(note.contains("either way it sets this node"), note);
        assertTrue(note.contains("so after record 1, in the records sharing its grouping, null's lines below WARN are not in "
                + "this log"), "O-A: a definite conclusion, bounded (R7-3): " + note);

        // Fourth re-review R-A: the CLOSING branch for that node. An INFO change rendered sourceId=null sets this node
        // under both readings, so it ends the window either way — "only the first would end it" would be false.
        var closeNull = new EventLogControlEvent(null, null, LogLevel.INFO);
        String closed = annotate(control(1, "null", change) + row(2, "null") + control(3, "null", closeNull) + row(4, "null"),
                "null", 1);
        assertNotNull(closed, "record 2 is inside the window");
        assertTrue(closed.contains("either way it ends here"), "R-A: both readings end it: " + closed);
        assertFalse(closed.contains("only the first would end it"), "R-A: the one-reading clause is false here: " + closed);
    }

    /**
     * Fourth re-review R-B. With no grouping declared, a closing change addressed to a DIFFERENT grouping than the
     * change it closes is not shown to have applied: if the processor was grouped 'alpha', a change to 'beta' did
     * nothing. The window still closes there — the conservative direction — but the clause must not say it "sets it".
     */
    @Test
    void aClosingChangeWhoseApplyingIsOpenSaysSo() {
        var warnAlpha = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.WARN);
        var infoBeta = new EventLogControlEvent("riskMonitor", "beta", LogLevel.INFO);
        String note = annotate(control(1, ABSENT, warnAlpha) + row(2, ABSENT) + control(3, ABSENT, infoBeta) + row(4, ABSENT),
                "riskMonitor", 1);
        assertNotNull(note);
        assertFalse(note.contains("sets it to INFO"), "R-B: that the closing change applied is not established: " + note);
        assertTrue(note.contains("addressed to processor grouping 'beta'; whether that applied here is not established either"),
                "R-B: disclosed: " + note);
        // …and where it IS established — the same groupId — the clause stays definite
        var infoAlpha = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.INFO);
        String same = annotate(control(1, ABSENT, warnAlpha) + row(2, ABSENT) + control(3, ABSENT, infoAlpha) + row(4, ABSENT),
                "riskMonitor", 1);
        // R7-5: definite only under the change's own condition, and never "sets it" in an ungrouped note (R7-4's check)
        assertTrue(same.contains("If the change at record 1 (logTime 1) applied here, it holds until record 3 (logTime 3), "
                + "whose change to INFO applied wherever this one did"),
                "positive control: same grouping, so if c applied, so did this: " + same);
        assertFalse(same.contains("whether that applied here is not established either"), same);
    }

    /**
     * Sixth re-review R6-1. A control record this reader cannot read was skipped, and the window ran on past it:
     * "Nothing later … changes it, so riskMonitor's lines below WARN are not in this log" — false if that record
     * restored INFO. It now ends the window, and the clause says why.
     */
    @Test
    void anUnreadableControlRecordEndsTheWindowAndSaysWhy() {
        var warn = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        for (String form : new String[]{"ambiguous", "unknownLevel", "missing"}) {
            String log = control(1, "null", warn) + row(2, "null") + unreadable(3, "null", form) + row(4, "null");
            String before = annotate(log, "riskMonitor", 1);
            assertNotNull(before, form + ": record 2 is inside the window");
            assertTrue(before.contains("It holds at least until record 3 (logTime 3), a control record this reader "
                    + "could not read; whether that record changed riskMonitor's audit level is not established"),
                    "R6-1 " + form + ": the window ends at the unreadable record, and says why: " + before);
            assertFalse(before.contains("Nothing later"), "R6-1 " + form + ": nothing is claimed past it: " + before);
            assertTrue(before.contains("is not established. After record 1 and before record 3, in the records sharing its "
                    + "grouping, riskMonitor's lines below WARN are not in this log"),
                    "R6-1/R7-2 " + form + ": bounded from the change to the record, within the grouping: " + before);
            assertNull(annotate(log, "riskMonitor", 3), "R6-1 " + form + ": record 4 is not explained by the WARN");
        }
        // …and an unreadable record in ANOTHER grouping does not touch this one (the same rule as a readable change)
        String other = control(1, "null", warn) + row(2, "null") + unreadable(3, "beta", "ambiguous") + row(4, "null");
        assertTrue(annotate(other, "riskMonitor", 3).contains("Nothing later"), "another grouping's record: "
                + annotate(other, "riskMonitor", 3));
    }

    /**
     * Sixth re-review R6-2. "It holds until record 4 sets it to INFO" asserted that the level survived a stream-end
     * marker, and the next sentence of the same note said that is not established.
     */
    @Test
    void aClosingChangeAcrossAMarkerIsNamedNotHeldUntil() {
        var warn = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        var info = new EventLogControlEvent("riskMonitor", null, LogLevel.INFO);
        String across = control(1, "null", warn) + row(2, "null") + MARKER_1 + row(3, "null") + control(5, "null", info)
                + row(6, "null");
        String note = annotate(across, "riskMonitor", 1, 2);
        assertNotNull(note);
        assertFalse(note.contains("It holds"), "R6-2: survival across the marker is not asserted: " + note);
        assertTrue(note.contains("The next change to riskMonitor's audit level in the same grouping is at record 4 "
                + "(logTime 5), which sets it to INFO"), "R6-2: the next change is named: " + note);
        assertTrue(note.contains("That marker begins a later run, and the log does not say whether the level survived"),
                note);
        // viewing only the record before the marker: the conclusion is bounded by the MARKER, not by record 4
        String onlyBefore = annotate(across, "riskMonitor", 1);
        assertTrue(onlyBefore.contains("which sets it to INFO. After record 1 and before the stream-end marker preceding "
                + "record 3, in the records sharing its grouping, riskMonitor's lines below WARN are not in this log"),
                "R6-2/R7-2: bounded from the change to the marker: " + onlyBefore);
        assertFalse(onlyBefore.contains("Before record 4"), onlyBefore);
        // positive control: with no marker between them, it does hold until then
        String within = control(1, "null", warn) + row(2, "null") + row(3, "null") + control(5, "null", info) + row(6, "null");
        assertTrue(annotate(within, "riskMonitor", 1, 2).contains("It holds until record 4 (logTime 5) sets it to INFO"),
                annotate(within, "riskMonitor", 1, 2));
        // and the same for an unreadable closer across a marker
        String unread = control(1, "null", warn) + row(2, "null") + MARKER_1 + row(3, "null") + unreadable(5, "null", "missing")
                + row(6, "null");
        String u = annotate(unread, "riskMonitor", 1, 2);
        assertFalse(u.contains("It holds"), "R6-2 × R6-1: " + u);
        assertTrue(u.contains("A later control record in the same grouping, record 4 (logTime 5), could not be read by "
                + "this reader"), u);
    }

    /** A record in which {@code node} itself logs — a line below WARN (seventh re-review R7-2). */
    private static String nodeLine(int t, String grouping, String node) {
        return "eventLogRecord:\n  logTime: " + t + "\n" + groupingLine(grouping)
                + "  event: Tick\n  nodeLogs:\n    - " + node + ": { checked: true}\n---\n";
    }

    /**
     * Seventh re-review R7-1–R7-3 (owner decision 2026-09-26, option a): every "not in this log" is bounded by the
     * change, the window's end and the grouping. Round 6's bound started at the beginning of the log (probe D: the node
     * logs at record 1, before the WARN at record 2), covered other groupings (probe F), and was missing on the premise
     * branch (probes A, B) and on the plain closer and "Nothing later" (probes I, C). The reviewer's probes, verbatim.
     */
    @Test
    void theConclusionIsBoundedByTheChangeTheWindowAndTheGrouping() {
        var warn = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        var info = new EventLogControlEvent("riskMonitor", null, LogLevel.INFO);
        // D: the node logs before the change
        String d = annotate(nodeLine(1, "null", "riskMonitor") + control(2, "null", warn) + row(3, "null")
                + unreadable(4, "null", "missing") + row(5, "null"), "riskMonitor", 2);
        assertTrue(d.contains("After record 2 and before record 4, in the records sharing its grouping, riskMonitor's lines "
                + "below WARN are not in this log"), "R7-2 D: from the change, not from the start of the log: " + d);
        assertFalse(d.contains("Before record 4"), d);
        // F: another grouping's record carries the node's line inside the window
        var warnAlpha = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.WARN);
        String f = annotate(control(1, "alpha", warnAlpha) + nodeLine(2, "beta", "riskMonitor") + row(3, "alpha")
                + unreadable(4, "alpha", "missing") + row(5, "alpha"), "riskMonitor", 2);
        assertTrue(f.contains("After record 1 and before record 4, in the records sharing its grouping, riskMonitor's"),
                "R7-2 F: within the change's grouping only: " + f);
        // A: no grouping declared, an unreadable closer — the premise branch is bounded too
        String a = annotate(control(1, ABSENT, warn) + row(2, ABSENT) + unreadable(3, ABSENT, "missing") + row(4, ABSENT),
                "riskMonitor", 1);
        assertTrue(a.contains("If the change at record 1 (logTime 1) applied here, then after record 1 and before record 3, "
                + "in the records that, like it, state no grouping, riskMonitor's lines below WARN are not in this log; "
                + "otherwise"), "R7-1 A: the conditional is bounded by the unreadable record: " + a);
        // B: no grouping declared, a closer across a marker, viewing the record before the marker
        String b = annotate(control(1, ABSENT, warn) + row(2, ABSENT) + MARKER_1 + row(3, ABSENT) + control(4, ABSENT, info)
                + row(5, ABSENT), "riskMonitor", 1);
        assertTrue(b.contains("then after record 1 and before the stream-end marker preceding record 3, in the records that, "
                + "like it, state no grouping"), "R7-1 B: the conditional stops at the marker: " + b);
        // I: a plain readable closer, and the node logs after it
        String i = annotate(control(1, "null", warn) + row(2, "null") + control(3, "null", info) + nodeLine(4, "null", "riskMonitor"),
                "riskMonitor", 1);
        assertTrue(i.contains("It holds until record 3 (logTime 3) sets it to INFO, so after record 1 and before record 3, in "
                + "the records sharing its grouping, riskMonitor's lines below WARN are not in this log"),
                "R7-3 I: the plain closer's conclusion ends at the closer: " + i);
        // C: no closer, a later run after the view
        String c = annotate(control(1, "null", warn) + row(2, "null") + MARKER_1 + row(3, "null"), "riskMonitor", 1);
        assertTrue(c.contains("Nothing later in the records sharing its grouping changes it, so after record 1 and before the "
                + "stream-end marker preceding record 3, in the records sharing its grouping"),
                "R7-3 C: \"Nothing later\" does not run into the later run: " + c);
    }

    /**
     * Seventh re-review R7-4 (owner decision 2026-09-26): R-B's rule — the closer applied whenever the change before it
     * did — does NOT extend across a stream-end marker. With no grouping declared, a later run is not shown to be the
     * same processor, so the closer there is open (probe L). R7-6: "A later control record", not "The next".
     */
    @Test
    void anUngroupedCloserAcrossAMarkerIsOpen() {
        var warnAlpha = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.WARN);
        var infoAlpha = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.INFO);
        String l = annotate(control(1, ABSENT, warnAlpha) + row(2, ABSENT) + MARKER_1 + row(3, ABSENT)
                + control(4, ABSENT, infoAlpha) + row(5, ABSENT), "riskMonitor", 1, 2);
        assertFalse(l.contains(" sets it to "), "R7-4 L: not definite across a marker in an ungrouped context: " + l);
        assertTrue(l.contains("which records a change to INFO addressed to processor grouping 'alpha'; whether that applied "
                + "here is not established either"), "R7-4 L: open, and says so: " + l);
        // positive control: the same log DECLARING its grouping stays definite across the marker
        var warn = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        var info = new EventLogControlEvent("riskMonitor", null, LogLevel.INFO);
        String declared = annotate(control(1, "null", warn) + row(2, "null") + MARKER_1 + row(3, "null")
                + control(4, "null", info) + row(5, "null"), "riskMonitor", 1, 2);
        assertTrue(declared.contains("which sets it to INFO"), "declared: " + declared);
        // R7-6 (probe J): a readable control record for another node comes first
        String j = annotate(control(1, "null", warn) + row(2, "null") + MARKER_1
                + control(3, "null", new EventLogControlEvent("priceListener", null, LogLevel.DEBUG))
                + unreadable(4, "null", "missing") + row(5, "null"), "riskMonitor", 1);
        assertTrue(j.contains("A later control record in the same grouping, record 4 (logTime 4), could not be read"),
                "R7-6 J: " + j);
        assertFalse(j.contains("The next control record"), j);
    }

    /**
     * Fourth re-review R-C, fifth re-review R5-1 and R5-2: no branch of the sentence presumes a processor, and none
     * says a change "sets" a level while its applying is open. The matrix is source × grouping × boundary × closing,
     * plus a node literally named "null", and it REACHES every branch: each branch's wording must appear in some note,
     * so a branch the inputs stop reaching fails here rather than going unchecked. Round 4's matrix claimed every
     * branch and missed four — the declared-null YES note and the three open closings — because its groupings had no
     * declared-null row with a groupId and its closing change always reused the opening's groupId.
     */
    @Test
    void noBranchOfTheSentencePresumesAProcessor() {
        record G(String recordGrouping, String gid) { }
        java.util.List<G> groupings = java.util.List.of(new G("null", null), new G("null", "alpha"), new G(ABSENT, null),
                new G("alpha", "alpha"), new G(ABSENT, "alpha"));
        java.util.regex.Pattern presumes = java.util.regex.Pattern.compile("(?i)processor(?! grouping)");   // O6-1
        // after a closing clause, a condition on "it" could read as the closing change: name the change instead
        java.util.regex.Pattern bareIt = java.util.regex.Pattern.compile("(?i)\\bif it (applied|survived|named)\\b(?! here:)");   // O6-2
        // Every branch's wording, as a pattern, so a phrase can be pinned to ONE branch (sixth re-review R6-3; seventh
        // re-review: checked cell by cell with the replay probe, not by reading).
        java.util.Map<String, java.util.regex.Pattern> branches = new java.util.LinkedHashMap<>();
        for (String literal : new String[]{
                "this log sets ", "this log records a change setting ", "that names no node — which would set every node's",
                "either way it sets this node", "either way it addresses this node",
                "which applies because the control record declares no grouping",
                "which is the grouping the control record itself declares",
                "states no processor grouping, so whether this change applied",
                "— addressed to processor grouping 'alpha' — applied here",
                "Nothing later in the records sharing its grouping", "Nothing later in the records that, like it, state no grouping",
                " sets it to INFO", "which records a change to INFO addressed to ",
                "either way it ends here", "either way it would end the window there, and only if it applied here",
                "only the first would end it there.", "only the first would end it there, and only if it applied here",
                "only if the change at record 1 (logTime 1) survived",
                "Every record in view is in a LATER run",
                "the log renders both identically. ",
                ", a control record this reader could not read; ", "could not be read by this reader; ",
                "The next change to riskMonitor's audit level in the same grouping is at ",
                "among the records that likewise state no grouping is at ",
                "either way it would change this node, and only if", "either way it changes this node",
                "only the first would change it",
                // seventh re-review R7-4, R7-5, R7-6
                "whose change to INFO applied wherever this one did", "applied here, it holds until ",
                "applied here, it holds at least until ", "A later control record "}) {
            branches.put(literal, java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(literal)));
        }
        // R7-1–R7-3: the conclusion, one pattern per branch that writes it
        branches.put("bound: no marker, plain \", so\"", java.util.regex.Pattern.compile(
                ", so after record \\d+(?: and before record \\d+)?, in [^.]*are not in this log\\. It is still"));
        branches.put("bound: no marker, own sentence", java.util.regex.Pattern.compile(
                "is not established\\. After record \\d+ and before record \\d+, in "));
        branches.put("bound: no marker, with a premise (R7-1)", java.util.regex.Pattern.compile(
                "If [^.]*, then after record \\d+(?: and before record \\d+)?, in [^.]*; otherwise"));
        branches.put("bound: before a marker, no closer", java.util.regex.Pattern.compile(
                ", so after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*are not in this log\\. It is still"));
        branches.put("bound: view wholly before a marker, closer past it", java.util.regex.Pattern.compile(
                "\\. After record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*are not in this log\\. It is still"));
        branches.put("bound: view wholly before a marker, with a premise", java.util.regex.Pattern.compile(
                "If [^.]*, then after record \\d+ and before the stream-end marker preceding record \\d+, in [^.;]*; otherwise"));
        branches.put("bound: spanning, plain \", so\"", java.util.regex.Pattern.compile(
                ", so after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        branches.put("bound: spanning, own sentence", java.util.regex.Pattern.compile(
                "\\. After record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        branches.put("bound: spanning, with a premise", java.util.regex.Pattern.compile(
                "\\. If [^.]*, then after record \\d+ and before the stream-end marker preceding record \\d+, in [^.]*\\. That marker"));
        branches.put("bound: wholly after", java.util.regex.Pattern.compile(", then after that marker(?: and before [^,]*)?, in "));
        java.util.Map<String, Integer> reached = new java.util.LinkedHashMap<>();
        branches.keySet().forEach(k -> reached.put(k, 0));
        int logs = 0;
        int notes = 0;
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (String node : new String[]{"riskMonitor", "null"}) {
            for (boolean nullSource : new boolean[]{false, true}) {
                if ("null".equals(node) && !nullSource) continue;           // a per-node change to "null" is the same case
                for (G g : groupings) {
                    for (String boundary : new String[]{"none", "spanning", "whollyAfter", "beforeMarker"}) {
                        for (String closing : new String[]{"none", "perNode", "null", "unreadable"}) {
                            for (String closeGid : closing.equals("none") || closing.equals("unreadable") ? new String[]{"-"}
                                    : new String[]{"same", "beta", "none"}) {
                                // R7-2: the node logs BEFORE the change, or another grouping's record carries its line
                                for (String lead : new String[]{"none", "nodeLogsBefore", "otherGrouping"}) {
                                    String gid = switch (closeGid) { case "same" -> g.gid(); case "beta" -> "beta"; default -> null; };
                                    String rg = g.recordGrouping();
                                    var open = new EventLogControlEvent(nullSource ? null : node, g.gid(), LogLevel.WARN);
                                    String close = switch (closing) {
                                        case "perNode" -> control(90, rg, new EventLogControlEvent(node, gid, LogLevel.INFO));
                                        case "null" -> control(90, rg, new EventLogControlEvent(null, gid, LogLevel.INFO));
                                        case "unreadable" -> unreadable(90, rg, "ambiguous");
                                        default -> "";
                                    };
                                    String before = lead.equals("nodeLogsBefore") ? nodeLine(0, rg, node) : "";
                                    String opening = before + control(1, rg, open)
                                            + (lead.equals("otherGrouping") ? nodeLine(1, "gamma", node) : "");
                                    int shift = lead.equals("none") ? 0 : 1;
                                    int changeRecord = 1 + (lead.equals("nodeLogsBefore") ? 1 : 0);
                                    String log;
                                    int[] view;
                                    switch (boundary) {
                                        case "spanning" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + close
                                                + row(95, rg); view = new int[]{1 + shift, 2 + shift}; }
                                        case "whollyAfter" -> { log = opening + MARKER_1 + row(2, rg) + close + row(95, rg);
                                                view = new int[]{1 + shift}; }
                                        case "beforeMarker" -> { log = opening + row(2, rg) + MARKER_1 + row(3, rg) + close
                                                + row(95, rg); view = new int[]{1 + shift}; }
                                        default -> { log = opening + row(2, rg) + close + row(95, rg); view = new int[]{1 + shift}; }
                                    }
                                    logs++;
                                    String note = annotate(log, node, view);
                                    if (note == null) continue;
                                    notes++;
                                    String at = node + "/" + (nullSource ? "null" : "perNode") + "/" + g + "/" + boundary + "/"
                                            + closing + "/" + closeGid + "/" + lead;
                                    if (presumes.matcher(note).find()) offenders.add("R5-1 " + at + ": " + note);
                                    // R5-2, widened for R7-4: with no grouping declared, applying is open, so nothing may
                                    // say a change "sets" — neither the opening nor any closer
                                    if (ABSENT.equals(rg) && (note.startsWith("this log sets") || note.contains("either way it sets")
                                            || note.contains(" sets it to "))) {
                                        offenders.add("R5-2/R7-4 " + at + ": " + note);
                                    }
                                    String lower = note.toLowerCase(java.util.Locale.ROOT);
                                    boolean either = note.contains("is not established either");
                                    boolean atLeast = lower.contains("it holds at least until");
                                    boolean named = note.contains("The next change to") || note.contains("A later control record");
                                    boolean unreadableClause = note.contains("a control record this reader could not read");
                                    if ((either && !(atLeast || named)) || (atLeast && !(either || unreadableClause))) {
                                        offenders.add("O5-1 " + at + ": " + note);
                                    }
                                    // R6-2: a closer past a marker is named, never held until
                                    if (!boundary.equals("none") && !closing.equals("none") && lower.contains("it holds")) {
                                        offenders.add("R6-2 " + at + ": " + note);
                                    }
                                    // R6-1: an unreadable closer ends the window and says so
                                    if (closing.equals("unreadable") && (note.contains("Nothing later")
                                            || !(note.contains("could not read") || note.contains("could not be read")))) {
                                        offenders.add("R6-1 " + at + ": " + note);
                                    }
                                    boolean closedClause = lower.contains("it holds ") || named;
                                    if (closedClause && bareIt.matcher(note).find()) {
                                        offenders.add("unnamed condition " + at + ": " + note);
                                    }
                                    // R7-1–R7-3: EVERY "not in this log" is bounded from the change (or, wholly after a
                                    // marker, from that marker), within the change's grouping — and, with a marker after
                                    // the change, never past it
                                    for (String sentence : note.split("\\. ")) {
                                        if (!sentence.contains("are not in this log")) continue;
                                        String low = sentence.toLowerCase(java.util.Locale.ROOT);   // "After record…"
                                        boolean fromChange = low.contains("after record " + changeRecord + " ")
                                                || low.contains("after record " + changeRecord + ",")
                                                || low.contains("after that marker");
                                        boolean inScope = sentence.contains("in the records sharing its grouping")
                                                || sentence.contains("in the records that, like it, state no grouping");
                                        // a bound that starts at the change, in a log with a marker after it, must stop
                                        // at (or before) that marker; one that starts after the marker may run on
                                        boolean endsBeforeMarker = boundary.equals("none") || low.contains("after that marker")
                                                || sentence.contains("before the stream-end marker") || sentence.contains("before record ");
                                        if (!fromChange || !inScope || !endsBeforeMarker) {
                                            offenders.add("R7 bound " + at + ": " + sentence);
                                        }
                                    }
                                    reached.replaceAll((branch, n) -> branches.get(branch).matcher(note).find() ? n + 1 : n);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(1440, logs, "the matrix: 3 openings × 5 groupings × 4 boundaries × 8 closings × 3 leads");
        assertEquals(1440, notes, "every log in the matrix annotates, so every one is checked");
        assertEquals(java.util.List.of(), reached.entrySet().stream().filter(e -> e.getValue() == 0).map(java.util.Map.Entry::getKey)
                .toList(), "a branch of the sentence the matrix no longer reaches");
        assertEquals(java.util.List.of(), offenders, "R-C/R5/R6/R7: a branch presumes a processor, says an open change sets, "
                + "leaves the condition's subject open, holds across a marker, runs past an unreadable control record, or "
                + "concludes beyond the change, the window or the grouping");
    }

    // ------------------------------------------------------------------ RR-3: which processor

    private static final EventLogControlEvent ALPHA_WARN = new EventLogControlEvent("riskMonitor", "alpha", LogLevel.WARN);

    @Test
    void aChangeExplainsOnlyItsOwnProcessorsRecords() {
        assertNotNull(annotate(control(1, "alpha", ALPHA_WARN) + row(2, "alpha"), "riskMonitor", 1),
                "positive control: alpha's WARN explains an alpha record");
        assertNull(annotate(control(1, "alpha", ALPHA_WARN) + row(2, "beta"), "riskMonitor", 1),
                "RR-3: alpha's change said nothing about a beta processor's record");
    }

    @Test
    void anotherProcessorsRestoreDoesNotCloseTheWindow() {
        var betaInfo = new EventLogControlEvent(null, "beta", LogLevel.INFO);
        String log = control(1, "alpha", ALPHA_WARN) + control(2, "beta", betaInfo) + row(3, "alpha");
        assertNotNull(annotate(log, "riskMonitor", 2),
                "RR-3: beta's INFO does not touch alpha's loggers, so alpha's WARN still governs record 3");
        String sameProcessor = control(1, "alpha", ALPHA_WARN)
                + control(2, "alpha", new EventLogControlEvent(null, "alpha", LogLevel.INFO)) + row(3, "alpha");
        assertNull(annotate(sameProcessor, "riskMonitor", 2), "positive control: alpha's own restore does close it");
    }

    @Test
    void anAbsentGroupingIsNotADeclaredNull() {
        var ungrouped = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        String absent = annotate(control(1, ABSENT, ungrouped) + row(2, ABSENT), "riskMonitor", 1);
        assertNotNull(absent, "not dropped: the change may well have applied");
        assertTrue(absent.contains("states no processor grouping") && absent.contains("not established"),
                "RR-3: a record with no groupingId line does not declare 'ungrouped': " + absent);
        String declared = annotate(control(1, "null", ungrouped) + row(2, "null"), "riskMonitor", 1);
        assertFalse(declared.contains("not established"), "a DECLARED null is the runtime's ungrouped: " + declared);
        assertNull(annotate(control(1, ABSENT, ungrouped) + row(2, "null"), "riskMonitor", 1),
                "an undeclared record and a declared-ungrouped one are not shown to be one processor");
    }

    @Test
    void aGroupingOnlyAPayloadStatesIsNotAGrouping() {
        String payloadSaysAlpha = "eventLogRecord:\n  logTime: 2\n  event: Tick\n  eventToString: x\n"
                + "  groupingId: alpha\n  nodeLogs:\n    - priceListener: { seen: true}\n---\n";
        assertNull(annotate(control(1, "alpha", ALPHA_WARN) + payloadSaysAlpha, "riskMonitor", 1),
                "a groupingId line after event: is content, not the record's declaration");
    }

    @Test
    void theScopeHoldsAcrossARolledSet(@TempDir Path dir) throws Exception {
        Path first = dir.resolve("demo.log.1"), second = dir.resolve("demo.log.2");
        Files.writeString(first, control(1, "alpha", ALPHA_WARN));
        Files.writeString(second, row(2, "beta"));
        List<Path> ordered = RollSetResolver.resolve(List.of(second, first)).ordered().stream()
                .map(RollSetResolver.Sibling::file).toList();
        for (int threshold : new int[]{10, 0}) {
            try (var rolled = RolledLogStore.open(ordered, threshold)) {
                assertEquals(2, rolled.size(), "precondition: both files are one set");
                assertNull(PerNodeLevelChanges.of(rolled).annotationFor("riskMonitor", new int[]{1}),
                        "RR-3 (rolled, threshold " + threshold + "): rolling does not make beta alpha");
            }
        }
    }
}
