package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the stream-end marker meets TA-6's live read — the integration's own contract.
 *
 * <p>The integration commit named two interactions it relied on and shipped neither with a test. The
 * reviewer mutated both away and the entire suite stayed green, which under rule 8 means they were not
 * closed at all. Every assertion here fails without the behaviour it names.
 *
 * <p>The rule the whole class turns on is §1a's: <b>a marker must be followed by its {@code ---}</b>.
 * At the byte level a finished marker and a half-written one are the same bytes, so an unterminated
 * final record is never a marker — an ordinary record in a static read, a pending record in a live one.
 */
class StreamEndLiveReadTest {

    private static final String REC =
            "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - book: { mid: 1.0}\n";

    @TempDir
    Path dir;

    private static String records(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append("---\n").append(String.format(REC, 1000 + i));
        return sb.toString();
    }

    /** The marker document itself, with no separators — the caller decides whether it is terminated. */
    private static String marker(int declared) {
        return "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: " + declared + "\n";
    }

    /** A marker that closes properly: a claim. */
    private static String terminated(int declared) {
        return "---\n" + marker(declared) + "---\n";
    }

    /** A marker with no closing separator: bytes that claim nothing yet (§1a). */
    private static String unterminated(int declared) {
        return "---\n" + marker(declared).stripTrailing();
    }

    private Path write(String text) throws IOException {
        Path p = dir.resolve("live.yaml");
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    // ---- V-1: a live read and a fresh read must agree about the same bytes --------------------

    /**
     * The layout the forthcoming writer produces: separators BETWEEN items and nothing after the last.
     * Before the termination rule, a fresh read called this COMPLETE while a live read called it
     * UNKNOWN with a pending record — for ever, because the writer had finished.
     */
    @Test
    void anUnterminatedMarkerIsNotAMarkerInEitherKindOfRead() throws IOException {
        Path p = write(records(3) + unterminated(3));   // no closing separator

        HeapLogStore ordinary = HeapLogStore.fromFile(p);
        HeapLogStore live = ordinary.forFollow();

        assertEquals(StreamEnd.State.UNKNOWN, ordinary.streamEnd().state(),
                "an unterminated final record claims nothing: a writer may be halfway through it");
        assertEquals(StreamEnd.State.UNKNOWN, live.streamEnd().state(),
                "and a live read of the same bytes must not disagree with a fresh one");
        assertEquals(4, ordinary.size(), "statically it is an ordinary record, kept as evidence");
        assertEquals(3, live.size(), "live, it is pending and not yet indexed");
    }

    @Test
    void theSameMarkerWithItsSeparatorIsAClaim() throws IOException {
        Path p = write(records(3) + terminated(3));

        HeapLogStore ordinary = HeapLogStore.fromFile(p);
        assertEquals(3, ordinary.size(), "the marker is not a record");
        assertEquals(StreamEnd.State.COMPLETE, ordinary.streamEnd().state());
        assertEquals(0, ordinary.trailingRecordsIncluded(), "nothing is dangling");
        assertEquals(StreamEnd.State.COMPLETE, ordinary.forFollow().streamEnd().state());
    }

    /** The mapped path reads the same bytes through a different framer and must reach the same verdict. */
    @Test
    void theMappedReaderAgreesAboutTerminationToo() throws IOException {
        Path open = write(records(3) + unterminated(3));
        try (MappedLogStore m = new MappedLogStore(open)) {
            assertEquals(StreamEnd.State.UNKNOWN, m.streamEnd().state());
            assertEquals(4, m.size());
        }
        Path closed = dir.resolve("closed.yaml");
        Files.writeString(closed, records(3) + terminated(3), StandardCharsets.UTF_8);
        try (MappedLogStore m = new MappedLogStore(closed)) {
            assertEquals(StreamEnd.State.COMPLETE, m.streamEnd().state());
            assertEquals(3, m.size());
        }
    }

    // ---- V-2: a marker caught mid-write must not become a verdict ------------------------------

    /**
     * The measured false verdict. Twelve records and a marker caught after its first digit read as
     * "this log declares 1 record and 12 were read — the marker is wrong", about a file that was simply
     * still being written.
     */
    @Test
    void aMarkerCaughtMidWriteInventsNoVerdict() throws IOException {
        Path p = write(records(12) + "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1");
        HeapLogStore s = HeapLogStore.fromFile(p);

        assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(),
                "'declares 1 record and 12 were read' was a fabrication about a live file");
        assertTrue(s.completenessDiagnostics().isEmpty(),
                () -> "and it must say nothing at all: " + s.completenessDiagnostics());
        assertEquals(13, s.size(), "the half-written text is kept as an ordinary record, not discarded");
    }

    @Test
    void aPartiallyWrittenKeyIsNotAMarkerEither() throws IOException {
        Path p = write(records(3) + "---\neventLogRecord:\n  streamEnd: norm");
        HeapLogStore s = HeapLogStore.fromFile(p);
        assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(),
                "this reported UNVERIFIED — an end claimed by a writer that had claimed nothing");
    }

    // ---- the integration guards the reviewer mutated away --------------------------------------

    /**
     * Guard I3/I4: a live read with a record still being written cannot be COMPLETE, whatever an earlier
     * marker declared. Removing the override left the whole suite green.
     */
    @Test
    void aPendingRecordAfterAMatchingMarkerIsNotACompleteFile() throws IOException {
        Path p = write(records(2) + terminated(2) + records(1).stripTrailing());
        HeapLogStore live = HeapLogStore.fromFile(p).forFollow();

        assertEquals(1, live.trailingRecordsPending(), "the third record is still being written");
        assertNotEquals(StreamEnd.State.COMPLETE, live.streamEnd().state(),
                "a file with a record in flight is not a finished file, whatever the marker said");
        assertEquals(StreamEnd.State.UNKNOWN, live.streamEnd().state());
    }

    /**
     * Guard I1/I2: a terminated marker at the end of a file is not a dangling EOF record, so following
     * such a file is not refused. Dropping the guard made {@code appendFrom} return -1 for a file that
     * had simply finished.
     */
    @Test
    void aFinishedFileEndingInAMarkerCanStillBeFollowed() throws IOException {
        Path p = write(records(2) + terminated(2));
        HeapLogStore s = HeapLogStore.fromFile(p);

        assertEquals(0, s.trailingRecordsIncluded(), "a marker with its separator dangles nothing");
        assertSame(s, s.forFollow(), "so there is nothing to reopen as a live read");
        Files.writeString(p, records(2) + terminated(2) + records(1) + "---\n", StandardCharsets.UTF_8);
        assertEquals(1, s.appendFrom(p), "and following it appends the next record rather than refusing");
    }

    // ---- V-4: the two reads must apply one rule ------------------------------------------------

    /**
     * A proven loss followed by an open run. §1a's precedence: the FILE is unknown because nothing
     * vouches for the tail, and the run that already failed its count is still reported. Both reads must
     * say the same thing; before this they disagreed, one reporting MISSING_RECORDS and one UNKNOWN.
     */
    @Test
    void aProvenLossSurvivesAnOpenRunAndBothReadsAgree() throws IOException {
        Path p = write(records(3) + terminated(5) + records(1));
        HeapLogStore ordinary = HeapLogStore.fromFile(p);
        HeapLogStore live = ordinary.forFollow();

        for (HeapLogStore s : new HeapLogStore[]{ordinary, live}) {
            assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(),
                    "records follow the last marker, so the file vouches for nothing");
            assertEquals(1, s.streamEnd().runs().size(),
                    "but run 1 proved it lost records, and that proof must not be swallowed");
            StreamEnd.Run bad = s.streamEnd().runs().get(0);
            assertEquals(StreamEnd.State.MISSING_RECORDS, bad.state());
            assertEquals(5, bad.declaredRecords());
            assertEquals(3, bad.emittedRecords());
            assertFalse(s.completenessDiagnostics().isEmpty(), "and a person is told");
        }
    }
}
