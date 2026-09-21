package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * Recognises the stream-end marker while a store indexes, and resolves the file's state at the end —
 * {@code spec-audit-stream-end.md} D-E3 and D-E4.
 *
 * <p><b>Why one shared object rather than the same few lines in each store.</b> Three stores index text:
 * the heap one, the memory-mapped one, and the SPI one over a reader plugin. D-E4 says the marker must
 * never become a record in ANY of them, and the conformance suite asserts the built-in and SPI paths
 * agree record for record — so a filter present in one and missing in another is a test failure, and
 * worse, a real difference in what two readers of the same file report. Putting the rule in one place
 * makes that divergence impossible rather than merely unlikely. Review found the one signal that was
 * NOT shared — an unterminated tail, seen only by the heap framer — making the same file report
 * differently depending on whether it opened small or large. That signal is gone; see {@link StreamEnd}.
 *
 * <p><b>A marker counts the records since the previous marker.</b> Review asked what happens when two
 * whole runs are appended into one file, which is the shape Mongoose's cumulative export already
 * produces across boots: each run carries its own marker, and a whole-file count would read the second
 * marker as claiming 25 records in a 50-record file. Segments answer it — each marker is checked against
 * the records it actually covers, so two whole runs are COMPLETE, which is what they are.
 *
 * <p>Not thread-safe; a store indexes on one thread.
 */
public final class StreamEndTracker {

    private int indexedTotal;
    private int sinceMarker;
    private boolean sawMarker;

    /** The worst verdict any segment produced, and the numbers that earned it. */
    private StreamEnd.State worst = StreamEnd.State.COMPLETE;
    private long worstDeclared = -1;
    private long worstEmitted = -1;
    /** Which run earned it, so the diagnostic can say the run's numbers are the RUN's (re-review, 3). */
    private int segments;
    private int worstOrdinal = -1;
    private long worstFirstRecord = -1;
    /** Every run that did not match its marker, in file order — D-E6, round five A-3. */
    private final java.util.List<StreamEnd.Run> badRuns = new java.util.ArrayList<>();
    /** §1a rule 1: the file's last item was a marker with no closing separator. */
    private boolean unterminatedMarker;

    /**
     * Whether this record text should be indexed.
     *
     * <p>Returns false for the marker, which is a container fact and not a record. Call it for every
     * framed record, in order, and index only what it accepts.
     */
    public boolean accept(String recordText) {
        var m = StreamEndMarker.of(recordText);
        if (m.isPresent()) {
            closeSegment(m.get().records());
            return false;
        }
        indexedTotal++;
        sinceMarker++;
        return true;
    }

    /**
     * Count one item that is a RECORD without asking whether it looks like a marker.
     *
     * <p>For an unterminated final item: §1a says it cannot be a marker, but it is still a record, and
     * the tracker must count it or the marker before it looks like the end of the file. Skipping the
     * tracker entirely left a run's records uncounted, so a file whose last record was still arriving
     * reported its previous marker's verdict as the file's.
     */
    public void acceptRecord() {
        indexedTotal++;
        sinceMarker++;
    }

    /** Re-run from the start. Follow re-frames the whole file, so the tracker must too. */
    public void reset() {
        indexedTotal = 0;
        sinceMarker = 0;
        sawMarker = false;
        worst = StreamEnd.State.COMPLETE;
        worstDeclared = -1;
        worstEmitted = -1;
        segments = 0;
        worstOrdinal = -1;
        worstFirstRecord = -1;
        badRuns.clear();
        unterminatedMarker = false;
    }

    /**
     * The final item looked like a marker but had no closing {@code ---}.
     *
     * <p>It is neither indexed nor counted: it is not a record, and it is not yet a claim. The file's
     * verdict becomes {@link StreamEnd.State#UNTERMINATED_MARKER} so a reader is told what is wrong and
     * whose job it is to fix it, rather than meeting an unexplained empty row in the table.
     */
    public void unterminatedMarker() {
        this.unterminatedMarker = true;
    }

    private void closeSegment(long declared) {
        sawMarker = true;
        segments++;
        StreamEnd verdict = StreamEnd.declared(declared, sinceMarker);
        long first = indexedTotal - sinceMarker;
        if (verdict.state() != StreamEnd.State.COMPLETE) {
            // D-E6: keep EVERY failing run. Keeping only the worst concealed a second one entirely.
            badRuns.add(new StreamEnd.Run(segments, first, first + sinceMarker - 1, verdict.state(),
                    declared, sinceMarker));
        }
        if (rank(verdict.state()) > rank(worst)) {
            worst = verdict.state();
            worstDeclared = declared;
            worstEmitted = sinceMarker;
            worstOrdinal = segments;
            worstFirstRecord = indexedTotal - sinceMarker;
        }
        sinceMarker = 0;
    }

    /** MISSING beats MORE beats UNVERIFIED beats COMPLETE: the most actionable segment is the verdict. */
    private static int rank(StreamEnd.State s) {
        return switch (s) {
            case COMPLETE -> 0;
            case UNVERIFIED -> 1;
            case MORE_THAN_DECLARED -> 2;
            case MISSING_RECORDS -> 3;
            case UNKNOWN -> 0;
            // Never produced per segment: the tail's ambiguity is decided for the whole file in resolve().
            case UNTERMINATED_MARKER -> 0;
        };
    }

    /**
     * The file's state.
     *
     * <p>With no marker the answer is UNKNOWN, the ordinary case for every producer that is silent, and
     * it must never be reported as complete (D-T8). Records AFTER the last marker are also UNKNOWN: a
     * declared end that is not the end of the file says nothing about what followed it, and that is the
     * live shape of a cumulative export whose current run has not finished.
     */
    public StreamEnd resolve() {
        // The tail is ambiguous, so nothing after it can be trusted; this outranks every other verdict.
        if (unterminatedMarker) {
            return new StreamEnd(StreamEnd.State.UNTERMINATED_MARKER, -1, indexedTotal).withRuns(badRuns);
        }
        if (!sawMarker) return StreamEnd.unknown(indexedTotal);
        // D-E7, round five A-4: records after the last marker leave the FILE unknown — nothing vouches
        // for the tail. But a run that already proved it lost records proved it, and dropping that
        // because a later run is still open is the concealment D-T8 forbids. The state stays unknown;
        // the evidence travels with it.
        if (sinceMarker > 0) return StreamEnd.unknown(indexedTotal).withRuns(badRuns);
        if (worst == StreamEnd.State.COMPLETE) return new StreamEnd(StreamEnd.State.COMPLETE,
                worstDeclared < 0 ? indexedTotal : worstDeclared, indexedTotal);
        StreamEnd verdict = new StreamEnd(worst, worstDeclared, worstEmitted).withRuns(badRuns);
        // Only name a run when there is more than one. In a single-run file "run 1 of this log (records
        // 0 to 24, of 25 in the file)" is noise dressed as precision, and the file IS the run.
        return segments <= 1 ? verdict
                : verdict.inSegment(new StreamEnd.Segment(worstOrdinal, worstFirstRecord,
                        worstFirstRecord + worstEmitted - 1, indexedTotal));
    }
}
