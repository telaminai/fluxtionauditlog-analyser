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
        assertTrue(note != null && note.contains("literally named \"null\""),
                "read as every node, and the sentence discloses that it cannot be told apart: " + note);
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
