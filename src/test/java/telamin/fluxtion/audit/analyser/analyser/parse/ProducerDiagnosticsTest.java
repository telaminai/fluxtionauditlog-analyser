package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three producer mistakes, each of which shipped a WRONG log that opened without complaint.
 *
 * <p>All three were hit by following *Producing an audit log* end to end and all three were silent —
 * so the tests assert not only that each is detected, but that the message names the fix. A
 * diagnostic that says "something is wrong" leaves the reader exactly where they were.
 */
class ProducerDiagnosticsTest {

    private static LogRecord record(String event, int nodeLogs) {
        LogRecord.Builder b = LogRecord.builder();
        b.event(event);
        b.logTime(1_767_258_000_000L);
        b.nodeLogsCount(nodeLogs);
        return b.build();
    }

    private static LogIndex indexOf(LogRecord... records) {
        LogIndex idx = new LogIndex();
        for (LogRecord r : records) idx.add(r);
        return idx;
    }

    /** The real shape of an unseparated file: every record's text delivered as one blob. */
    private static final String TWO_RUN_TOGETHER = """
            eventLogRecord:\s
                logTime: 1787696934848
                event: Temperature
                nodeLogs:\s
                    - thermostat: { celsius: 21.5}
            eventLogRecord:\s
                logTime: 1787696934849
                event: Temperature
                nodeLogs:\s
                    - thermostat: { celsius: 16.0}
            """;

    @Test
    void aFileWithNoSeparatorsIsCaughtAndTheFixIsNamed() {
        LogIndex idx = indexOf(record("Temperature", 1));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, row -> TWO_RUN_TOGETHER);

        assertFalse(d.isClean());
        assertEquals(ProducerDiagnostics.Kind.UNSEPARATED, d.findings().get(0).kind());
        String m = d.findings().get(0).message();
        assertTrue(m.contains("2 records run together"), m);
        assertTrue(m.contains("---"), "the message must name the separator: " + m);
        assertTrue(m.contains("record.toString()"), "and say who does not write it: " + m);
    }

    @Test
    void aProperlySeparatedLogIsClean() {
        // one record's text, exactly one header — the normal case must stay silent, or the diagnostic
        // becomes noise on every healthy log and is learned as something to ignore
        LogIndex idx = indexOf(record("Temperature", 1), record("Temperature", 1));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx,
                row -> "eventLogRecord: \n    event: Temperature\n    nodeLogs: \n"
                        + "        - thermostat: { celsius: 21.5}\n");
        assertTrue(d.isClean(), d.messages().toString());
    }

    @Test
    void noNodeLogsAnywherePointsAtAddEventAudit() {
        LogIndex idx = indexOf(record("Temperature", 0), record("Temperature", 0));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, row -> "eventLogRecord: \n  event: X\n");

        assertEquals(ProducerDiagnostics.Kind.NO_NODE_LOGS, d.findings().get(0).kind());
        String m = d.findings().get(0).message();
        assertTrue(m.contains("addEventAudit()"), m);
        assertTrue(m.contains("EventLogNode"), "the other half of the cause belongs here too: " + m);
    }

    @Test
    void oneNodeLogAnywhereIsEnoughToStaySilent() {
        // a graph where only some cycles log is NORMAL — a node can run, decide nothing changed and say
        // nothing. Reporting that as a producer fault would be the analyser inventing a defect.
        LogIndex idx = indexOf(record("Temperature", 0), record("Temperature", 1));
        assertTrue(ProducerDiagnostics.of(idx, row -> "eventLogRecord: \n").isClean());
    }

    @Test
    void aLogOfNothingButControlEventsExplainsTheOrdering() {
        LogIndex idx = indexOf(record("EventLogControlEvent", 0), record("EventLogControlEvent", 0));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, row -> "eventLogRecord: \n");

        assertEquals(ProducerDiagnostics.Kind.ONLY_CONTROL_EVENTS, d.findings().get(0).kind());
        String m = d.findings().get(0).message();
        assertTrue(m.contains("setAuditLogLevel"), m);
        assertTrue(m.contains("setAuditLogProcessor"), "name the order to use: " + m);
    }

    @Test
    void aControlEventMixedWithRealRecordsIsNotAFault() {
        // capture-and-restore workflows legitimately leave a control record in the log
        LogIndex idx = indexOf(record("EventLogControlEvent", 0), record("Temperature", 1));
        assertTrue(ProducerDiagnostics.of(idx, row -> "eventLogRecord: \n").isClean());
    }

    @Test
    void onlyTheROOTCauseIsReported() {
        // an unseparated file ALSO has no node logs on the rows that vanished; naming both would be two
        // names for one bug, and the second would send the reader to fix something that is not broken
        LogIndex idx = indexOf(record("Temperature", 0));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, row -> TWO_RUN_TOGETHER);
        assertEquals(1, d.findings().size(), d.messages().toString());
        assertEquals(ProducerDiagnostics.Kind.UNSEPARATED, d.findings().get(0).kind());
    }

    @Test
    void anEmptyLogSaysNothing() {
        assertTrue(ProducerDiagnostics.of(new LogIndex(), row -> "").isClean());
        assertTrue(ProducerDiagnostics.of(null, row -> "").isClean());
    }

    @Test
    void aMissingRawTextSupplierDoesNotBreakTheOtherChecks() {
        // a store that cannot hand back text must not silence the checks that do not need it
        LogIndex idx = indexOf(record("Temperature", 0));
        IntFunction<String> none = row -> null;
        List<String> messages = ProducerDiagnostics.of(idx, none).messages();
        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("addEventAudit()"), messages.toString());
    }
}
