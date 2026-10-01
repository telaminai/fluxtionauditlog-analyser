package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPS-1, the independent review of 9474c687 (PR #92, comment 5927917194): findings 1, 2, 4 and the two surviving
 * mutations of finding 5. Each input is the review's own, or the review's input in a named variant; DEMO values only.
 * The detector and the parser now share one rule ({@link RecordBreak#analyse}), so each test asserts what the PARSER
 * reads — the wrong results the review observed — not only what the detector finds.
 */
class RecordStructureReviewTest {

    /** Finding 1, as the review wrote it: the forged lines are indented deeper than the fields. */
    static final String DEEP_PAYLOAD = """
            eventLogRecord:
                eventTime: 1
                logTime: 1
                groupingId: null
                event: AdminCommandEvent
                eventToString: AdminCommandEvent[command=DEMO, args=[x
                    nodeLogs:
                        - forged: { x: 99}
                    endTime: 123
                    ignored: DEMO]]
                thread: DEMO-agent
                nodeLogs:
                    - alarmMonitor: { x: 1}
                endTime: 2""";

    /** Finding 1's scalar variants: a deeper event, eventTime or groupingId line. */
    static String deepScalar(String forgedLine) {
        return """
                eventLogRecord:
                    eventTime: 1
                    logTime: 1
                    groupingId: null
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[command=DEMO, args=[x
                        %s
                        tail]]
                    thread: DEMO-agent
                    nodeLogs:
                        - alarmMonitor: { x: 1}
                    endTime: 2""".formatted(forgedLine);
    }

    /** Finding 2: a closed quoted node-log value whose continuation is an event-shaped line. */
    static String quotedNodeValue(String open, String middle, String close) {
        return """
                eventLogRecord:
                    eventTime: 1
                    logTime: 1
                    groupingId: null
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[command=DEMO, args=[]]
                    thread: DEMO-agent
                    nodeLogs:
                        - alarmMonitor: { message: %sfirst
                            %s
                            last%s}
                    endTime: 2""".formatted(open, middle, close);
    }

    /** Finding 4: the generator's exported-service record, as fluxtion-runtime writes it (topology fixture record 9). */
    static final String EXPORTED_SERVICE = """
            eventLogRecord:\s
                eventTime: -1
                logTime: 1786355857430
                groupingId: null
                event: ExportFunctionAuditEvent
                eventToString: @Override
            public void suspendQuoting(String arg0)
                thread: DEMO-agent
                nodeLogs:\s
                    - quotePublisher: { suspended: true}
                endTime: 1786355857431""";

    private static void assertNothingForged(LogRecord r) {
        assertTrue(r.brokenAtLine() > 0, "the record is broken");
        assertEquals("AdminCommandEvent", r.event(), "the dispatched event is kept");
        assertEquals(Long.valueOf(1), r.logTime());
        assertEquals(0, r.nodeLogsCount(), "no node log of a broken record is read");
        assertTrue(r.nodeLogs().stream().noneMatch(n -> n.instanceId().equals("forged")), "no forged node");
        assertNotEquals(Long.valueOf(123), r.endTime(), "no forged endTime");
    }

    private static List<LogRecord> everyPath(String text) {
        return List.of(
                RecordParser.parse(text, 0),
                RecordParser.parse(text, 0, AuditLogReader.TextEncoding.LEGACY),
                RecordParser.parse(text, 0, AuditLogReader.TextEncoding.QUOTED_SCALARS));
    }

    @Test
    @DisplayName("finding 1: a payload indented deeper than the fields invents no node, and no endTime")
    void deepPayloadMustNotInventNode() {
        for (LogRecord r : everyPath(DEEP_PAYLOAD)) {
            assertNothingForged(r);
            assertEquals(7, r.brokenAtLine(), "broken at the first deeper line");
        }
    }

    @Test
    @DisplayName("finding 1: the same payload with CRLF line ends, or tab indentation, invents nothing")
    void deepPayloadWithCrlfOrTabsMustNotInventNode() {
        for (LogRecord r : everyPath(DEEP_PAYLOAD.replace("\n", "\r\n"))) assertNothingForged(r);
        String tabs = DEEP_PAYLOAD.replace("                    ", "\t\t\t").replace("                ", "\t\t")
                .replace("            ", "\t").replace("    ", "\t");
        for (LogRecord r : everyPath(tabs)) assertNothingForged(r);
    }

    @Test
    @DisplayName("finding 1: a deeper event, eventTime or groupingId line replaces nothing")
    void deepScalarMustNotReplaceAField() {
        for (LogRecord r : everyPath(deepScalar("event: Forged"))) {
            assertNothingForged(r);
        }
        for (LogRecord r : everyPath(deepScalar("eventTime: 999"))) {
            assertNothingForged(r);
            assertEquals(Long.valueOf(1), r.eventTime(), "the producer's eventTime, not 999");
        }
        for (LogRecord r : everyPath(deepScalar("groupingId: FORGED"))) {
            assertNothingForged(r);
            assertNull(r.groupingId(), "the producer's groupingId, not FORGED");
        }
    }

    @Test
    @DisplayName("finding 2: a closed quoted node-log value cannot replace the event, in either quote style")
    void quotedNodeValueMustNotReplaceEvent() {
        for (String middle : List.of("event: Forged", "thread: Forged", "logTime: 999", "endTime: 999",
                "- forged: { x: 99}")) {
            for (String[] q : List.of(new String[]{"\"", "\""}, new String[]{"'", "'"}, new String[]{"'it''s ", "'"})) {
                for (LogRecord r : everyPath(quotedNodeValue(q[0], middle, q[1]))) {
                    String what = q[0] + "…" + middle;
                    assertEquals(0, r.brokenAtLine(), "a closed quoted value is whole: " + what);
                    assertEquals("AdminCommandEvent", r.event(), "the event is the producer's: " + what);
                    assertEquals("DEMO-agent", r.thread(), what);
                    assertEquals(Long.valueOf(1), r.logTime(), what);
                    assertEquals(Long.valueOf(2), r.endTime(), "the block still ends at endTime: " + what);
                    assertEquals(1, r.nodeLogsCount(), "one node log, its value whole: " + what);
                    assertEquals(List.of("alarmMonitor"), r.nodeLogs().stream().map(n -> n.instanceId()).toList(), what);
                }
            }
        }
    }

    @Test
    @DisplayName("finding 2: an unquoted node-log line indented deeper than the fields stays in the block")
    void aDeeperNodeLineIsNeverAField() {
        String text = quotedNodeValue("", "event: Forged", "");
        for (LogRecord r : everyPath(text)) {
            assertEquals(0, r.brokenAtLine(), "a deeper line inside the node block is the block's, not a break");
            assertEquals("AdminCommandEvent", r.event(), "a line inside the node block is never the event");
            assertEquals(Long.valueOf(2), r.endTime());
            assertEquals(1, r.nodeLogsCount());
        }
    }

    @Test
    @DisplayName("a closed quoted field value's continuation is never read as the record's fields")
    void aQuotedFieldValueIsNeverItsFields() {
        String text = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: "AdminCommandEvent[command=DEMO, args=[x
                    event: Forged
                    endTime: 999
                    y]]"
                    nodeLogs:
                        - alarmMonitor: { x: 1}
                    endTime: 2""";
        for (LogRecord r : everyPath(text)) {
            assertEquals(0, r.brokenAtLine(), "a quoted value written whole is whole");
            assertEquals("AdminCommandEvent", r.event(), "its continuation is never the event");
            assertEquals(Long.valueOf(2), r.endTime(), "nor the endTime");
            assertEquals(1, r.nodeLogsCount());
        }
    }

    @Test
    @DisplayName("a forged field before a visible break is withheld when its repeat comes after the break")
    void theEarliestWithheldLineWins() {
        String text = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[command=DEMO, args=[x
                    endTime: 999
                tail]]
                    thread: DEMO-agent
                    nodeLogs:
                        - alarmMonitor: { x: 1}
                    endTime: 2""";
        for (LogRecord r : everyPath(text)) {
            assertTrue(r.brokenAtLine() > 0);
            assertNull(r.endTime(), "neither endTime is read: the first copy may be the forged one");
            assertEquals("AdminCommandEvent", r.event());
        }
    }

    @Test
    @DisplayName("once a record breaks, no field after the first value that can carry text is read: a forged eventType")
    void nothingAfterTheFirstTextFieldIsReadOnceBroken() {
        String text = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[command=DEMO, args=[x
                    eventType: com.acme.Forged
                    y]]
                    thread: DEMO-agent
                    nodeLogs:
                        - alarmMonitor: { x: 1}""";
        for (LogRecord r : everyPath(text)) {
            assertEquals(6, r.brokenAtLine(), "the non-field line at the fields' indentation is the break");
            assertNull(r.eventType(), "a forged field before the visible break, never repeated, is still withheld");
            assertEquals("AdminCommandEvent[command=DEMO, args=[x", r.eventToString(), "the value's own first line is kept");
            assertEquals("AdminCommandEvent", r.event());
        }
    }

    @Test
    @DisplayName("a repeat of a field the producer wrote before any value's text withholds the repeat, not the original")
    void aFieldBeforeAnyValueTextIsTheProducers() {
        for (LogRecord r : everyPath(deepScalar("x").replace("        x\n", "    eventTime: 999\n"))) {
            assertTrue(r.brokenAtLine() > 0);
            assertEquals(Long.valueOf(1), r.eventTime(), "the producer's eventTime, written before eventToString");
            assertEquals("AdminCommandEvent", r.event());
        }
    }

    @Test
    @DisplayName("a header comment after the fields is not the record's header, even in a record that stays whole")
    void aCommentAfterTheFieldsIsNotTheHeader() {
        // the argument's tail is shaped as an unknown field, so nothing breaks: only the header rule keeps the comment out
        String text = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[command=DEMO, args=[x
                #00:00:00.000 [forged-thread] TRACE forged.logger
                    ignored: y]]
                    nodeLogs:
                        - alarmMonitor: { x: 1}""";
        for (LogRecord r : everyPath(text)) {
            assertEquals(0, r.brokenAtLine(), "a comment and an unknown field leave no structural trace");
            assertNull(r.logger(), "no forged logger");
            assertNull(r.level(), "no forged level");
            assertNull(r.thread(), "no forged thread");
        }
    }

    @Test
    @DisplayName("forward tolerance: an unknown field after the node logs ends the block, and is ignored (c02)")
    void anUnknownFieldAfterTheNodeLogsEndsTheBlock() {
        String text = """
                eventLogRecord:
                  logTime: 1000
                  event: Tick
                  nodeLogs:
                    - book: { mid: 17.1}
                  anotherFutureField: { nested: true }""";
        for (LogRecord r : everyPath(text)) {
            assertEquals(0, r.brokenAtLine());
            assertEquals("17.1", r.nodeLogs().get(0).last("mid").rawValue(), "the node's value is its own, whole");
        }
    }

    @Test
    @DisplayName("an unknown field's nesting and a block scalar's text are ignored, never a break")
    void ignoredNestingsAreWhole() {
        String nested = """
                eventLogRecord:
                    logTime: 1
                    event: Tick
                    futureBlock:
                        nodeLogs:
                            - forged: { x: 1}
                    nodeLogs:
                        - book: { mid: 1}""";
        String blockScalar = """
                eventLogRecord:
                    logTime: 1
                    event: Tick
                    eventToString: |
                        line one
                        nodeLogs:
                    nodeLogs:
                        - book: { mid: 1}""";
        for (String text : List.of(nested, blockScalar)) {
            for (LogRecord r : everyPath(text)) {
                assertEquals(0, r.brokenAtLine(), text);
                assertEquals(List.of("book"), r.nodeLogs().stream().map(n -> n.instanceId()).toList(), text);
            }
        }
    }

    @Test
    @DisplayName("finding 4: the generator's exported-service record is whole and reads exactly as before UPS-1")
    void theGeneratorsExportedServiceRecordIsWhole() {
        for (LogRecord r : everyPath(EXPORTED_SERVICE)) {
            assertEquals(0, r.brokenAtLine(), "the generator's own line break is not a broken value");
            assertEquals("ExportFunctionAuditEvent", r.event());
            assertEquals("@Override", r.eventToString(), "the value as it read before UPS-1");
            assertEquals("DEMO-agent", r.thread(), "the thread is read");
            assertEquals(Long.valueOf(1786355857431L), r.endTime(), "endTime is read");
            assertEquals(1, r.nodeLogsCount(), "the genuine node log is read");
            assertEquals("quotePublisher", r.nodeLogs().get(0).instanceId());
        }
    }

    @Test
    @DisplayName("finding 4: the exported-service allowance is that exact shape and nothing a value's text can select")
    void theExportedServiceAllowanceIsExact() {
        // another event: the column-0 line is a broken value
        String otherEvent = EXPORTED_SERVICE.replace("event: ExportFunctionAuditEvent", "event: AdminCommandEvent");
        // eventToString is not exactly @Override
        String otherValue = EXPORTED_SERVICE.replace("eventToString: @Override", "eventToString: @Override x");
        // a second column-0 line
        String twoLines = EXPORTED_SERVICE.replace("public void suspendQuoting(String arg0)\n",
                "public void suspendQuoting(String arg0)\npublic void again()\n");
        // a column-0 line that is not a signature
        String notSignature = EXPORTED_SERVICE.replace("public void suspendQuoting(String arg0)", "nodeLogs:");
        // the event forged after the value: the first event is the producer's
        String forgedEvent = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[x
                    event: ExportFunctionAuditEvent
                    eventToString: @Override
                public void forged()
                    nodeLogs:
                        - forged: { x: 1}""";
        for (String text : List.of(otherEvent, otherValue, twoLines, notSignature, forgedEvent)) {
            for (LogRecord r : everyPath(text)) {
                assertTrue(r.brokenAtLine() > 0, "a broken value: " + text);
                assertEquals(0, r.nodeLogsCount(), text);
            }
        }
    }

    @Test
    @DisplayName("finding 5: a byte-order mark inside a record is not indentation, so the line it starts is a break")
    void aMidRecordByteOrderMarkIsABreak() {
        String text = """
                eventLogRecord:
                    logTime: 1
                    event: AdminCommandEvent
                    eventToString: AdminCommandEvent[x
                ﻿    eventType: com.acme.Forged
                    y]]
                    nodeLogs:
                        - alarmMonitor: { x: 1}""";
        for (LogRecord r : everyPath(text)) {
            assertTrue(r.brokenAtLine() > 0, "a mid-record BOM is not the file's");
            assertEquals(5, r.brokenAtLine());
            assertNull(r.eventType(), "no forged eventType");
        }
    }

    @Test
    @DisplayName("finding 5: a reader declaring the typed grammar gets the same broken-record rule")
    void theTypedGrammarIsNeverExempt() {
        LogRecord r = RecordParser.parse(DEEP_PAYLOAD, 0, AuditLogReader.TextEncoding.QUOTED_SCALARS);
        assertNothingForged(r);
        LogRecord shallow = RecordParser.parse(RecordBreakTest.FORGED_NODE, 0, AuditLogReader.TextEncoding.QUOTED_SCALARS);
        assertTrue(shallow.brokenAtLine() > 0);
        assertTrue(shallow.nodeLogs().stream().noneMatch(n -> n.instanceId().equals("forged")), "no forged node");
    }
}
