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
        assertTrue(note.contains("If it named no node, riskMonitor's lines below WARN are not in this log; "
                + "otherwise this change explains nothing here"), "S3: and concludes only on that condition: " + note);
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
        assertTrue(note.contains("If it applied here and it survived the marker, riskMonitor's lines below WARN are "
                + "not in this log; otherwise this change explains nothing here"),
                "S2: one condition, both premises: " + note);
        assertFalse(note.contains("If it survived the marker, riskMonitor's"),
                "S2: survival alone is never enough while applicability is not established: " + note);
        assertFalse(note.contains("this processor's"), "S2: no processor is presumed: " + note);
        assertTrue(note.contains("the records that, like it, state no grouping"), note);
    }

    @Test
    void notEstablishedAndSpanningABoundaryConditionsBothHalves() {
        var change = new EventLogControlEvent("riskMonitor", null, LogLevel.WARN);
        String log = control(1, ABSENT, change) + row(2, ABSENT) + MARKER_1 + row(3, ABSENT) + MARKER_1;
        String note = annotate(log, "riskMonitor", 1, 2);
        assertTrue(note.contains("Within the run it was made in, if it applied here, riskMonitor's lines below WARN are not in this log"),
                "S2: even within the run the claim waits on applicability: " + note);
        assertTrue(note.contains("those lines are absent only if it applied here and it survived the marker"),
                "S2: after the marker, both premises: " + note);
        assertFalse(note.contains("so within the run"), "S2: nothing is definite while applicability is open: " + note);
    }

    @Test
    void aNullSourceInAnUndeclaredGroupingCarriesBothPremises() {
        var literal = new EventLogControlEvent("null", null, LogLevel.WARN);
        String note = annotate(control(1, ABSENT, literal) + row(2, ABSENT), "riskMonitor", 1);
        assertTrue(note.contains("If it named no node and it applied here, riskMonitor's lines below WARN"),
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
        assertTrue(note.contains("so null's lines below WARN are not in this log"), "O-A: a definite conclusion: " + note);
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
