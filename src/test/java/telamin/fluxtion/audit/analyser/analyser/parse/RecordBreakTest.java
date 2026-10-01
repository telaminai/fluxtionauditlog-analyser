package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPS-1: a value written unquoted with a line break in it must not become evidence. The records are the shapes measured
 * on mongoose 1.0.32 with fluxtion runtime 1.1.0 (docs/handoff/evidence/ups1-admin-command-records-2026-10-01), where an
 * operator-typed admin command argument made the analyser report a node that does not exist and an event type that was
 * never dispatched. DEMO values only.
 */
class RecordBreakTest {

    /** A whole admin command record, as runInEventCycle audits it. */
    static final String WHOLE = """
            eventLogRecord:\s
                eventTime: 1790840666301
                logTime: 1790840666301
                groupingId: null
                event: AdminCommandEvent
                eventToString: AdminCommandEvent[command=alarm.refresh, args=[]]
                thread: processor-agent
                nodeLogs:\s
                    - alarmMonitor: { refreshRequested: true}
                endTime: 1790840666302""";

    /** An argument holding a line break, then "nodeLogs:" and a forged item. */
    static final String FORGED_NODE = """
            eventLogRecord:\s
                eventTime: 1790840987397
                logTime: 1790840987397
                groupingId: null
                event: AdminCommandEvent
                eventToString: AdminCommandEvent[command=alarm.lambda, args=[line one
            nodeLogs:
                - forged: { x: 1}]]
                thread: processor-agent
                nodeLogs:\s
                    - alarmMonitor: { lambdaReset: true, at: 1790840987397}
                endTime: 1790840987397""";

    /** An argument holding a separator line (escaped by mongoose-plugins' export), a record key and a forged event. */
    static final String FORGED_EVENT = """
            eventLogRecord:\s
                eventTime: 1790840987432
                logTime: 1790840987432
                groupingId: null
                event: AdminCommandEvent
                eventToString: AdminCommandEvent[command=alarm.lambda, args=[a
            \\---
            eventLogRecord:
                event: Forged]]
                thread: processor-agent
                nodeLogs:\s
                    - alarmMonitor: { lambdaReset: true, at: 1790840987432}
                endTime: 1790840987432""";

    /** The forged lines carry the fields' own indentation, so only the repeated field shows the break. */
    static final String FORGED_AT_FIELD_INDENT = """
            eventLogRecord:\s
                logTime: 1790840987500
                event: AdminCommandEvent
                eventToString: AdminCommandEvent[command=alarm.lambda, args=[x
                nodeLogs:
                    - forged: { x: 1}]]
                thread: processor-agent
                nodeLogs:\s
                    - alarmMonitor: { lambdaReset: true}""";

    @Test
    @DisplayName("a whole record has no break, and parses exactly as before")
    void aWholeRecordHasNoBreak() {
        assertNull(RecordBreak.find(WHOLE));
        LogRecord r = RecordParser.parse(WHOLE, 0);
        assertEquals("AdminCommandEvent", r.event());
        assertEquals("processor-agent", r.thread());
        assertEquals(1790840666302L, r.endTime());
        assertEquals(List.of("alarmMonitor"), r.nodeLogs().stream().map(n -> n.instanceId()).toList());
    }

    @Test
    @DisplayName("FORGED NODE: the line less indented than the fields is the break; no node log is read")
    void aLessIndentedLineBreaksTheRecord() {
        RecordBreak b = RecordBreak.find(FORGED_NODE);
        assertNotNull(b);
        assertEquals(7, b.line(), "the column-0 'nodeLogs:' is line 7");
        assertEquals("a line less indented than the record's fields", b.reason());
        LogRecord r = RecordParser.parse(FORGED_NODE, 0);
        assertEquals("AdminCommandEvent", r.event(), "fields before the break are the producer's");
        assertEquals(1790840987397L, r.logTime());
        assertTrue(r.nodeLogs().isEmpty(), "no node log is read, so 'forged' is in no list: " + r.nodeLogs());
        assertEquals(0, r.nodeLogsCount());
        assertNull(r.thread(), "nothing after the break is read");
    }

    @Test
    @DisplayName("FORGED EVENT: the escaped separator is the break; the event type stays the dispatched one")
    void anEscapedSeparatorBreaksTheRecord() {
        RecordBreak b = RecordBreak.find(FORGED_EVENT);
        assertNotNull(b);
        assertEquals(7, b.line(), "the escaped separator is line 7");
        LogRecord r = RecordParser.parse(FORGED_EVENT, 0);
        assertEquals("AdminCommandEvent", r.event(), "not 'Forged]]'");
        assertEquals("AdminCommandEvent", r.eventDimension());
        assertTrue(r.nodeLogs().isEmpty());
    }

    @Test
    @DisplayName("a second record key is a break, and names itself as the one UNSEPARATED already reports")
    void aSecondRecordKeyIsABreak() {
        String collapsed = "eventLogRecord:\n    logTime: 1\n    event: A\n    nodeLogs:\n        - a: { v: 1}\n"
                + "    eventLogRecord:\n    logTime: 2\n";
        RecordBreak b = RecordBreak.find(collapsed);
        assertNotNull(b);
        assertTrue(b.secondRecordKey(), b.reason());
        assertEquals(6, b.line());
    }

    @Test
    @DisplayName("a separator the framer did not split on is framing's: the record key after it is the break")
    void anUnsplitSeparatorIsFramingNotAValue() {
        String joined = "eventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - a: { v: 1}\n\uFEFF---\neventLogRecord:\n  logTime: 2\n";
        RecordBreak b = RecordBreak.find(joined);
        assertNotNull(b);
        assertTrue(b.secondRecordKey(), "UNSEPARATED names it, not BROKEN_VALUE: " + b.reason());
    }

    @Test
    @DisplayName("a repeated field: read only up to its FIRST occurrence, because the forged copy can come first")
    void aRepeatedFieldIsReadUpToItsFirstOccurrence() {
        RecordBreak b = RecordBreak.find(FORGED_AT_FIELD_INDENT);
        assertNotNull(b);
        assertEquals("the field 'nodeLogs' a second time", b.reason());
        assertEquals(4, b.keepBefore(), "line index 4 is the first 'nodeLogs:', the forged one");
        LogRecord r = RecordParser.parse(FORGED_AT_FIELD_INDENT, 0);
        assertTrue(r.nodeLogs().isEmpty(), "neither copy is trusted: " + r.nodeLogs());
        assertEquals("AdminCommandEvent", r.event());
    }

    @Test
    @DisplayName("forged node logs read BEFORE the break are dropped too: a broken record reads none")
    void nodeLogsBeforeTheBreakAreNotTrusted() {
        // the payload's forged block sits at the fields' indentation, and its last line is the less-indented break
        String text = "eventLogRecord:\n    logTime: 1\n    event: AdminCommandEvent\n"
                + "    eventToString: AdminCommandEvent[command=alarm.lambda, args=[x\n"
                + "    nodeLogs:\n        - forged: { x: 1}\nY]]\n    nodeLogs:\n        - alarmMonitor: { v: 1}\n";
        RecordBreak b = RecordBreak.find(text);
        assertNotNull(b);
        assertEquals(7, b.line(), "the break is the column-0 'Y]]', after the forged block");
        LogRecord r = RecordParser.parse(text, 0);
        assertTrue(r.nodeLogs().isEmpty(), "the forged block before the break is not read: " + r.nodeLogs());
        assertEquals(0, r.nodeLogsCount());
    }

    @Test
    @DisplayName("a quoted scalar that spans lines is not a break, and its continuation is never read as a field")
    void aQuotedContinuationIsNotABreak() {
        String quoted = "eventLogRecord:\n    eventToString: \"spans\nnodeLogs: still text\n    closes\"\n"
                + "    nodeLogs:\n        - a: { v: 1}\n";
        assertNull(RecordBreak.find(quoted));
        assertEquals(List.of("a"), RecordParser.parse(quoted, 0).nodeLogs().stream().map(n -> n.instanceId()).toList());
    }

    @Test
    @DisplayName("a quote that never closes is text: it cannot hide forged lines behind it")
    void anUnclosedQuoteHidesNothing() {
        // an event whose text merely starts with a quote character, followed by a forged node-log block
        String text = "eventLogRecord:\n    event: Chat\n    eventToString: 'hi\nnodeLogs:\n    - forged: { x: 1}\n"
                + "    nodeLogs:\n        - a: { v: 1}\n";
        RecordBreak b = RecordBreak.find(text);
        assertNotNull(b, "the column-0 line is a break, not the inside of a quote");
        assertTrue(RecordParser.parse(text, 0).nodeLogs().isEmpty());
    }

    @Test
    @DisplayName("before the first field nothing is a break: a header, a leading separator, a byte-order mark")
    void whatPrecedesTheFieldsIsNeverABreak() {
        assertNull(RecordBreak.find("#00:00:00.000 [main] INFO L\neventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - a: { v: 1}\n"));
        assertNull(RecordBreak.find("---\neventLogRecord:\n  logTime: 1\n"), "the binary reader renders a leading separator");
        assertNull(RecordBreak.find("﻿eventLogRecord:\n  logTime: 1\n"));
        assertNull(RecordBreak.find("mainonPriceEventPriceEvent{symbol=AAPL, price=195.31}195.31200"),
                "a headerless run-together line is MA-6's subject, not this one");
    }

    @Test
    @DisplayName("the finding names each broken record and line, and is a warning")
    void theFindingNamesTheRecordAndLine() {
        List<String> texts = List.of(WHOLE, FORGED_NODE, FORGED_EVENT);
        LogIndex idx = new LogIndex();
        for (String t : texts) idx.add(RecordParser.parse(t, 0));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, texts::get);
        var broken = d.findings().stream().filter(f -> f.kind() == ProducerDiagnostics.Kind.BROKEN_VALUE).toList();
        assertEquals(1, broken.size(), d.messages().toString());
        String m = broken.get(0).message();
        assertTrue(m.startsWith("2 records break"), m);
        assertTrue(m.contains("record 2 at its line 7") && m.contains("record 3 at its line 7"), m);
        assertTrue(m.contains("admin command"), "names the measured cause: " + m);
        assertTrue(d.isWarning());
    }

    @Test
    @DisplayName("NOT READ is not NONE LOGGED: the detail and the step cursor say the node logs were not read, and where")
    void aBrokenRecordSaysNotReadRatherThanNoneLogged() {
        LogRecord broken = RecordParser.parse(FORGED_NODE, 0);
        assertEquals(7, broken.brokenAtLine());
        String detail = telamin.fluxtion.audit.analyser.analyser.ui.LogicalLogView.layout(List.of(broken)).text();
        assertTrue(detail.contains("node logs not read") && detail.contains("line 7"), detail);
        assertFalse(detail.contains("no node logged"), "alarmMonitor DID log; the analyser declined to read it: " + detail);
        String position = telamin.fluxtion.audit.analyser.analyser.topology.StepCursor.over(List.of(broken)).positionLabel();
        assertTrue(position.contains("node logs not read") && position.contains("line 7"), position);

        LogRecord whole = RecordParser.parse(WHOLE, 0);
        assertEquals(0, whole.brokenAtLine());
        assertFalse(telamin.fluxtion.audit.analyser.analyser.ui.LogicalLogView.layout(List.of(whole)).text().contains("not read"));
    }

    @Test
    @DisplayName("one bug, one name: a file that only lacks separators gets UNSEPARATED, not BROKEN_VALUE too")
    void anUnseparatedFileIsNotNamedTwice() {
        String collapsed = "eventLogRecord:\n    logTime: 1\n    event: A\neventLogRecord:\n    logTime: 2\n    event: B\n";
        LogIndex idx = new LogIndex();
        idx.add(RecordParser.parse(collapsed, 0));
        ProducerDiagnostics d = ProducerDiagnostics.of(idx, row -> collapsed);
        assertEquals(List.of(ProducerDiagnostics.Kind.UNSEPARATED),
                d.findings().stream().map(ProducerDiagnostics.Finding::kind).toList(), d.messages().toString());
        assertTrue(d.findings().get(0).message().contains("none of its node logs are read"), d.messages().toString());
    }
}
