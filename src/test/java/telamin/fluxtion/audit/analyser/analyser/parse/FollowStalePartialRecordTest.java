package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AF-3a — a record indexed while it was still being written is re-read once it is whole.
 *
 * <p>An ordinary load emits a trailing record that has no closing {@code ---}, and that is right: §1
 * makes the separator optional at the end of a file, so a whole file may simply stop there. The defect
 * is what happens if the file then GROWS. Before this fix {@code appendFrom} skipped that row as
 * already-indexed, so its truncated text stayed in the index for ever and the rest of the record was
 * never read. It predates the stream-end work and was found while fixing that branch's review.
 *
 * <p>The distinction the fix turns on: <b>"ends without a separator" and "was cut off" are different
 * questions.</b> The first is answered from the file and matters only to follow. The second cannot be
 * answered at all, which is why {@link StreamEnd} no longer tries — see its class comment. A closed file
 * ending without a separator is whole, and never grows, so it never reaches any of this.
 */
class FollowStalePartialRecordTest {

    private static final String WHOLE =
            "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - book: { mid: 1.0}\n";
    private static final String HALF = "eventLogRecord:\n  logTime: 2000\n  event: Ti";
    private static final String REST = "ck\n  nodeLogs:\n    - book: { mid: 2.0}\n";

    @TempDir
    Path dir;

    private Path write(String text) throws IOException {
        Path p = dir.resolve("growing.yaml");
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void aRecordIndexedHalfWrittenIsRereadOnceTheWriterFinishesIt() throws IOException {
        Path p = write("---\n" + WHOLE + "---\n" + HALF);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(2, store.size(), "the partial record is shown; the file may simply end there");
        assertEquals("Ti", store.record(1).event(), "as far as anyone knows, that is the event's name");

        Files.writeString(p, "---\n" + WHOLE + "---\n" + HALF + REST + "---\n", StandardCharsets.UTF_8);
        assertEquals(0, store.appendFrom(p), "the row is replaced, not added to");

        assertEquals(2, store.size(), "and the index never grew or shrank");
        assertEquals("Tick", store.record(1).event(),
                "this stayed 'Ti' for ever, however complete the file became");
        assertEquals(1, store.record(1).nodeLogsCount(), "and the node logs were never read");
        assertTrue(store.rawText(1).contains("mid: 2.0"), store.rawText(1));
    }

    /**
     * The case that decides the shape of the fix. If the record is STILL half-written, this pass
     * withholds it, so dropping the stale row first would make a row vanish from under whoever is
     * looking at the table. The row is therefore dropped only once its replacement is in hand.
     */
    @Test
    void aRecordStillHalfWrittenKeepsItsRowRatherThanDisappearing() throws IOException {
        Path p = write("---\n" + WHOLE + "---\n" + HALF);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(2, store.size());

        Files.writeString(p, "---\n" + WHOLE + "---\n" + HALF + "ck\n  nodeLo", StandardCharsets.UTF_8);
        int added = store.appendFrom(p);

        assertEquals(0, added);
        assertEquals(2, store.size(), "the index must never shrink under a reader");
        assertEquals("Ti", store.record(1).event(), "still the best that can be said of it");

        // and the NEXT growth, which completes it, still repairs the row
        Files.writeString(p, "---\n" + WHOLE + "---\n" + HALF + REST + "---\n", StandardCharsets.UTF_8);
        store.appendFrom(p);
        assertEquals("Tick", store.record(1).event(), "a missed repair must not be a permanent one");
        assertEquals(2, store.size());
    }

    @Test
    void theRepairedRowBringsTheTimelineWithIt() throws IOException {
        String untimedHalf = "eventLogRecord:\n  event: Ti";
        Path p = write("---\n" + WHOLE + "---\n" + untimedHalf);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(Long.valueOf(1000), store.maxLogTime(), "the half-written row has no time yet");

        Files.writeString(p, "---\n" + WHOLE + "---\n" + untimedHalf
                + "ck\n  logTime: 5000\n  nodeLogs:\n    - book: { mid: 2.0}\n---\n", StandardCharsets.UTF_8);
        store.appendFrom(p);

        assertEquals(Long.valueOf(5000), store.maxLogTime(),
                "the row's real time must reach the timeline the filter and the axis read");
        assertEquals(Long.valueOf(1000), store.minLogTime());
    }

    /**
     * The half-written row's OWN time must leave the range with it.
     *
     * <p>A writer cut mid-number leaves a truncated but perfectly readable {@code logTime} — `9000` cut
     * to `900` is still an integer — so the index takes it as a real time. Dropping the row has to undo
     * that, and a maximum or minimum cannot be undone by subtraction, which is why
     * {@link telamin.fluxtion.audit.analyser.analyser.index.LogIndex#dropLast()} recomputes.
     *
     * <p>Written after mutating that recompute away left every other test in this class GREEN: the
     * half-written record in them carries no time, so there was nothing to remove and the assertion
     * passed either way. The same vacuity as the series assertion two reviews ago, found the same way.
     */
    @Test
    void aTruncatedTimeLeavesTheRangeWhenItsRowIsRepaired() throws IOException {
        String cutMidNumber = "eventLogRecord:\n  logTime: 900";          // 9000, cut after three digits
        Path p = write("---\n" + WHOLE + "---\n" + cutMidNumber);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(Long.valueOf(900), store.minLogTime(), "a truncated number is still a number");

        Files.writeString(p, "---\n" + WHOLE + "---\n" + cutMidNumber
                + "0\n  event: Tick\n  nodeLogs:\n    - book: { mid: 2.0}\n---\n", StandardCharsets.UTF_8);
        store.appendFrom(p);

        assertEquals(2, store.size());
        assertEquals(Long.valueOf(9000), store.record(1).logTime(), "the real time, once it arrived");
        assertEquals(Long.valueOf(1000), store.minLogTime(),
                "900 never existed; leaving it in the range moves every axis and every time filter");
        assertEquals(Long.valueOf(9000), store.maxLogTime());
    }

    /**
     * The node-log maximum is recomputed too, and NOTHING HERE TESTS IT, on purpose.
     *
     * <p>I wrote a test for it and it was unreachable. A repaired record is a superset of its truncated
     * form — the writer only ever appends — so its node-log count can only rise, and dropping the stale
     * row can never lower the maximum. My attempt made the file SHRINK, which {@code appendFrom} rejects
     * outright with -1 before any of this runs. The recompute stays because {@code dropLast} should be
     * correct as an operation rather than only for its one caller, and this note is here so the next
     * person does not spend the same half hour proving it cannot happen.
     *
     * <p>The time range is different, and IS tested above: a number cut mid-digit is still a valid
     * number, so a stale row can hold a time that never existed.
     */
    @Test
    void aFileThatShrinksIsRejectedRatherThanReconciled() throws IOException {
        Path p = write("---\n" + WHOLE + "---\n" + HALF);
        HeapLogStore store = HeapLogStore.fromFile(p);
        Files.writeString(p, "---\n" + WHOLE, StandardCharsets.UTF_8);
        assertEquals(-1, store.appendFrom(p), "rotated or replaced: the caller reloads, nothing is patched");
    }

    /** A file that grows by whole records only, which is the ordinary case, is unaffected. */
    @Test
    void aFileThatOnlyEverGrowsByWholeRecordsIsUntouched() throws IOException {
        Path p = write("---\n" + WHOLE + "---\n");
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(1, store.size());

        Files.writeString(p, "---\n" + WHOLE + "---\n" + WHOLE + "---\n", StandardCharsets.UTF_8);
        assertEquals(1, store.appendFrom(p), "one new record, and nothing re-read");
        assertEquals(2, store.size());
        assertEquals("Tick", store.record(0).event());
        assertEquals("Tick", store.record(1).event());
    }

    /**
     * The export layout — no trailing separator — is a WHOLE file, and the commonest real shape. It ends
     * unterminated, so it is exactly the state this fix watches for, and it must still behave normally.
     */
    @Test
    void theRealExportLayoutIsNotTreatedAsAWoundedFile() throws IOException {
        Path p = write(WHOLE.strip() + "\n---\n" + WHOLE.strip());
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state());
        assertTrue(store.sourceDiagnostics().isEmpty(),
                () -> "a whole export must not be reported as anything: " + store.sourceDiagnostics());

        // If such a file DOES grow, the last record is re-read rather than frozen — but the NEW last
        // record is withheld, because while a file is live an unterminated tail cannot be told from a
        // half-written one. Follow is therefore one record behind on a producer that never writes a
        // trailing separator, and that is the honest position rather than a defect: the alternative is
        // showing a record that may be half a record. A static load makes the opposite choice, because
        // a file that is not growing has no next record to wait for.
        Files.writeString(p, WHOLE.strip() + "\n---\n" + WHOLE.strip() + "\n---\n" + WHOLE.strip(),
                StandardCharsets.UTF_8);
        assertEquals(0, store.appendFrom(p), "the third record is not whole as far as follow can tell");
        assertEquals(2, store.size());

        // and it appears as soon as the record after it proves it finished
        Files.writeString(p, WHOLE.strip() + "\n---\n" + WHOLE.strip() + "\n---\n" + WHOLE.strip()
                + "\n---\n" + WHOLE.strip(), StandardCharsets.UTF_8);
        assertEquals(1, store.appendFrom(p), "now closed by its successor");
        assertEquals(3, store.size());
        for (int i = 0; i < store.size(); i++) {
            assertEquals(1, store.record(i).nodeLogsCount(), "row " + i + " is whole");
        }
    }

    /** A marker arriving after a half-written record still resolves the file, and is still not a record. */
    @Test
    void aMarkerArrivingAfterTheRepairStillClosesTheFile() throws IOException {
        Path p = write("---\n" + WHOLE + "---\n" + HALF);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state());

        Files.writeString(p, "---\n" + WHOLE + "---\n" + HALF + REST
                + "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n---\n",
                StandardCharsets.UTF_8);
        store.appendFrom(p);

        assertEquals(2, store.size(), "two records; the marker is not one");
        assertEquals("Tick", store.record(1).event(), "and the repair still happened");
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state());
    }
}
