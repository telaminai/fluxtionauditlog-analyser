package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two-depth step cursor (S1). Driven by the <b>real</b> fixtures rather than hand-built records, so
 * the regime-dependent behaviour — what a row means, and what the label is allowed to claim — is
 * asserted against logs the Fluxtion compiler and runtime actually produced.
 */
class StepCursorTest {

    private static List<LogRecord> load(String fixture) throws IOException {
        try (InputStream in = StepCursorTest.class.getResourceAsStream("/topology/" + fixture)) {
            assertNotNull(in, fixture + " missing");
            HeapLogStore store = new HeapLogStore(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<LogRecord> out = new ArrayList<>();
            for (int row = 0; row < store.size(); row++) out.add(store.record(row));
            return out;
        }
    }

    private static StepCursor sparse() throws IOException {
        return StepCursor.over(load("demo-quote-audit.yaml"));
    }

    private static StepCursor traced() throws IOException {
        return StepCursor.over(load("demo-quote-audit-traced.yaml"));
    }

    // ---- walking ----------------------------------------------------------------------------------

    @Test
    void startsAtTheFirstRecordsEntryNotItsFirstRow() throws IOException {
        StepCursor cursor = sparse();
        assertEquals(0, cursor.recordIndex());
        assertTrue(cursor.atEntry(), "entering a cycle is its own stop — where the entry point is marked");
        assertNull(cursor.currentRow());
        assertTrue(cursor.steppedSoFar().isEmpty());
    }

    @Test
    void nextWalksTheRowsThenRollsIntoTheNextRecordsEntry() throws IOException {
        StepCursor cursor = sparse();
        int rows = cursor.rowCount();
        assertTrue(rows > 0);

        for (int i = 0; i < rows; i++) {
            assertTrue(cursor.next());
            assertEquals(i, cursor.rowIndex());
            assertEquals(0, cursor.recordIndex(), "still inside the first cycle");
        }
        assertTrue(cursor.next(), "past the last row");
        assertEquals(1, cursor.recordIndex());
        assertTrue(cursor.atEntry(), "a new cycle starts at its entry, not its first row");
    }

    @Test
    void prevFromAnEntryLandsOnThePreviousRecordsLastRow() throws IOException {
        StepCursor cursor = sparse();
        cursor.moveToRecord(1);
        assertTrue(cursor.atEntry());

        assertTrue(cursor.prev());
        assertEquals(0, cursor.recordIndex());
        assertEquals(cursor.rowCount() - 1, cursor.rowIndex(),
                "the log is complete backwards too — retreat to the last row, not the entry");
    }

    @Test
    void steppingIsReversible() throws IOException {
        StepCursor cursor = sparse();
        List<String> forward = new ArrayList<>();
        while (cursor.canNext()) {
            cursor.next();
            forward.add(cursor.recordIndex() + ":" + cursor.rowIndex());
        }
        List<String> backward = new ArrayList<>();
        while (cursor.canPrev()) {
            backward.add(cursor.recordIndex() + ":" + cursor.rowIndex());
            cursor.prev();
        }
        java.util.Collections.reverse(backward);
        assertEquals(forward, backward, "the same positions in reverse");
        assertEquals(0, cursor.recordIndex());
        assertTrue(cursor.atEntry(), "back at the start");
    }

    @Test
    void stopsAtBothEnds() throws IOException {
        StepCursor cursor = sparse();
        assertFalse(cursor.canPrev());
        assertFalse(cursor.prev());
        while (cursor.canNext()) cursor.next();
        assertFalse(cursor.next());
    }

    @Test
    void selectingARecordPutsTheCursorAtItsEntry() throws IOException {
        StepCursor cursor = sparse();
        cursor.next();
        cursor.moveToRecord(2);
        assertEquals(2, cursor.recordIndex());
        assertTrue(cursor.atEntry());
        assertTrue(cursor.steppedSoFar().isEmpty(), "a new cycle clears the accumulation");
    }

    // ---- accumulation within a cycle --------------------------------------------------------------

    @Test
    void steppedSoFarAccumulatesWithinACycleAndClearsOnRollover() throws IOException {
        StepCursor cursor = sparse();
        cursor.next();
        assertEquals(1, cursor.steppedSoFar().size());
        cursor.next();
        assertEquals(2, cursor.steppedSoFar().size());

        while (!cursor.atEntry()) cursor.next();          // roll into the next record
        assertTrue(cursor.steppedSoFar().isEmpty(), "accumulation is per cycle");
    }

    @Test
    void aNodeLoggingTwiceGivesTwoStepsAndIsNotDeduped() throws IOException {
        // find a record where one instanceId occupies more than one row
        List<LogRecord> records = load("demo-quote-audit-traced.yaml");
        LogRecord repeated = null;
        for (LogRecord record : records) {
            List<String> ids = record.nodeLogs().stream().map(n -> n.instanceId()).toList();
            if (ids.size() != ids.stream().distinct().count()) {
                repeated = record;
                break;
            }
        }
        if (repeated == null) return;   // fixture has no repeat today; the rule is asserted below anyway

        StepCursor cursor = StepCursor.over(List.of(repeated));
        List<String> stepped = new ArrayList<>();
        while (cursor.canNext()) {
            cursor.next();
            stepped.add(cursor.currentInstanceId());
        }
        assertEquals(repeated.nodeLogs().size(), stepped.size(),
                "one step per row — a node firing twice is an event, not a duplicate");
    }

    @Test
    void rowsAreStepsEvenWhenTheSameNodeRepeats() {
        // explicit, fixture-independent: two rows naming the same node are two distinct steps
        LogRecord record = new HeapLogStore("""
                ---
                eventLogRecord:
                    logTime: 1
                    event: RepeatEvent
                    nodeLogs:
                        - worker: { pass: 1}
                        - worker: { pass: 2}
                """).record(0);
        StepCursor cursor = StepCursor.over(List.of(record));
        cursor.next();
        assertEquals("worker", cursor.currentInstanceId());
        assertEquals(1, cursor.steppedSoFar().size());
        cursor.next();
        assertEquals("worker", cursor.currentInstanceId());
        assertEquals(List.of("worker", "worker"), cursor.steppedSoFar());
    }

    // ---- regime-aware labelling -------------------------------------------------------------------

    @Test
    void anUntracedRecordSaysItsRowsAreOnlyTheLoggedNodes() throws IOException {
        StepCursor cursor = sparse();
        assertFalse(cursor.traced());
        cursor.next();
        String label = cursor.positionLabel();
        assertTrue(label.contains("logged nodes"),
                "'row 3 / 8' alone invites reading 8 as 'the nodes that ran': " + label);
    }

    @Test
    void aTracedRecordCountsInvocations() throws IOException {
        StepCursor cursor = traced();
        assertTrue(cursor.traced(), "the traced fixture records every invocation");
        cursor.next();
        String label = cursor.positionLabel();
        assertTrue(label.startsWith("invocation "), label);
        assertFalse(label.contains("logged nodes"), "nothing to qualify — stepping is exact");
    }

    @Test
    void theTwoRegimesDisagreeAboutTheSameCycle() throws IOException {
        // the branch's thesis, as an assertion: same graph, different audit level, different row count
        StepCursor sparse = sparse();
        StepCursor traced = traced();
        assertTrue(traced.rowCount() > sparse.rowCount(),
                "tracing records the silent nodes too — " + traced.rowCount() + " vs " + sparse.rowCount());
    }

    @Test
    void theEntryLabelNamesTheRegimeToo() throws IOException {
        assertTrue(sparse().positionLabel().contains("logged row(s)"));
        assertTrue(traced().positionLabel().contains("invocation(s)"));
    }

    // ---- entry points and summaries ---------------------------------------------------------------

    @Test
    void theEntryPointIsResolvedForTheCurrentRecord() throws IOException {
        ProcessorTopology topology;
        try (InputStream in = StepCursorTest.class.getResourceAsStream("/topology/demo-quote-processor.graphml")) {
            topology = GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        StepCursor cursor = sparse();
        assertEquals(List.of("MarketDataEvent"), cursor.entryPoints(topology));
    }

    @Test
    void theRowSummaryCarriesTheValuesAndIsEmptyAtEntry() throws IOException {
        StepCursor cursor = sparse();
        assertEquals("", cursor.rowSummary(), "nothing is under the cursor at the entry position");
        cursor.next();
        String summary = cursor.rowSummary();
        assertTrue(summary.startsWith(cursor.currentInstanceId()));
        assertTrue(summary.contains("="), "shows what the node logged: " + summary);
    }

    @Test
    void anEmptySequenceIsInertRatherThanThrowing() {
        StepCursor cursor = StepCursor.over(List.of());
        assertTrue(cursor.isEmpty());
        assertFalse(cursor.next());
        assertFalse(cursor.prev());
        assertNull(cursor.record());
        assertNull(cursor.currentRow());
        assertEquals("no records", cursor.positionLabel());
    }
}
