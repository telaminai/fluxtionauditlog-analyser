package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.model.EventKind;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordParserTest {

    private static List<RawRecord> sample() {
        return RecordFramer.frame(Samples.sample());
    }

    private static LogRecord parse(int i) {
        RawRecord r = sample().get(i);
        return RecordParser.parse(r.text(), r.offset());
    }

    @Test
    void parsesLifecycleStartCompleteScalars() {
        LogRecord r = parse(0);
        assertEquals(EventKind.OK, r.kind());
        assertEquals("LifecycleEvent", r.event());
        assertEquals("StartComplete", r.eventToString());
        assertEquals(1786355857407L, r.eventTime());
        assertEquals(1786355857407L, r.logTime());
        assertEquals(1786355857407L, r.endTime());
        assertEquals("marketMaker-DEMO", r.thread());
        assertEquals("MAKER_USDMXN_DEMO", r.logger());
        assertEquals("INFO", r.level());
        assertEquals("LifecycleEvent", r.eventDimension());
        assertNull(r.callback());
        assertEquals(1, r.nodeLogs().size());
        assertEquals("positionNode", r.nodeLogs().get(0).instanceId());
    }

    @Test
    void normalisesMinusOneEventTimeToNull() {
        LogRecord r = parse(1);      // ExportFunctionAuditEvent, eventTime: -1
        assertNull(r.eventTime(), "eventTime -1 -> null");
        assertNotNull(r.logTime());
    }

    @Test
    void derivesCallbackDimensionFromMethodSignature() {
        LogRecord r = parse(1);
        assertEquals("orderVenueConnected", r.eventDimension());
        assertEquals("orderVenueConnected", r.callback());
        assertEquals("com.acme.tradecalculator.api.lib.node.hedging.VenueHedgeMonitorCalculator",
                r.declaringType());
    }

    @Test
    void scheduledTriggerNodeStaysAsRawEventDimension() {
        LogRecord scheduled = sample().stream()
                .map(rr -> RecordParser.parse(rr.text(), rr.offset()))
                .filter(rec -> "ScheduledTriggerNode".equals(rec.event()))
                .findFirst().orElseThrow();
        assertEquals("ScheduledTriggerNode", scheduled.eventDimension());
        assertNull(scheduled.callback());
    }

    @Test
    void keepsComplexToStringValuesIntact() {
        LogRecord withOrder = sample().stream()
                .map(rr -> RecordParser.parse(rr.text(), rr.offset()))
                .filter(rec -> rec.nodeLogs().stream()
                        .anyMatch(nl -> "bidMakerOrder".equals(nl.instanceId()) && nl.last("orderUpdate") != null))
                .findFirst().orElseThrow();
        NodeLog bid = withOrder.nodeLogs().stream()
                .filter(nl -> "bidMakerOrder".equals(nl.instanceId()) && nl.last("orderUpdate") != null)
                .findFirst().orElseThrow();
        String order = bid.last("orderUpdate").rawValue();
        assertTrue(order.startsWith("MutableOrder("), order);
        assertTrue(order.contains("cancelledQuantity=0.0"), "inner commas of the toString survive parsing");
        assertTrue(Double.isNaN(Double.NaN));
    }

    @Test
    void preservesDuplicateInstanceIdsWithinARecord() {
        LogRecord dup = sample().stream()
                .map(rr -> RecordParser.parse(rr.text(), rr.offset()))
                .filter(rec -> rec.nodeLogs().stream()
                        .filter(nl -> "hedgeToOrdersNode".equals(nl.instanceId())).count() >= 2)
                .findFirst().orElseThrow();
        long count = dup.nodeLogs().stream().filter(nl -> "hedgeToOrdersNode".equals(nl.instanceId())).count();
        assertTrue(count >= 2, "hedgeToOrdersNode logs twice in a scheduled cycle");
    }

    @Test
    void parsesEndTimeEvenWhenScalarsAreDeeplyIndented() {
        // scalars indented 4 spaces (deeper than 2); endTime follows nodeLogs
        String rec = "#00:00:00.000 [t] INFO L\n"
                + "eventLogRecord:\n"
                + "    eventTime: 5\n"
                + "    logTime: 6\n"
                + "    event: X\n"
                + "    nodeLogs:\n"
                + "        - a: { k: 1}\n"
                + "    endTime: 7\n";
        LogRecord r = RecordParser.parse(rec, 0);
        assertEquals(5L, r.eventTime());
        assertEquals(6L, r.logTime());
        assertEquals(7L, r.endTime(), "endTime after nodeLogs must parse regardless of indentation");
        assertEquals(1, r.nodeLogs().size());
    }

    @Test
    void everySampleRecordParsesWithoutError() {
        for (RawRecord rr : sample()) {
            LogRecord r = RecordParser.parse(rr.text(), rr.offset());
            assertEquals(EventKind.OK, r.kind(), "record parsed: " + rr.text().lines().findFirst().orElse(""));
            assertNotNull(r.logTime());
            assertDoesNotThrow(r::nodeLogs);
        }
    }
}
