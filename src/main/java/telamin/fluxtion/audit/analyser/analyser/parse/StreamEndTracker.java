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
 * makes that divergence impossible rather than merely unlikely.
 *
 * <p>Not thread-safe; a store indexes on one thread.
 */
public final class StreamEndTracker {

    private StreamEndMarker marker;
    private long unterminatedTailChars;
    private int indexed;

    /**
     * Whether this record text should be indexed.
     *
     * <p>Returns false for the marker, which is a container fact and not a record. Call it for every
     * framed record, in order, and index only what it accepts.
     */
    public boolean accept(String recordText) {
        var m = StreamEndMarker.of(recordText);
        if (m.isPresent()) {
            marker = m.get();
            return false;
        }
        indexed++;
        return true;
    }

    /** The framer's report that a trailing record never closed with a separator. */
    public void unterminatedTail(long chars) {
        this.unterminatedTailChars = chars;
    }

    /**
     * The file's state, in precedence order.
     *
     * <p>A stop mid-write outranks a marker: a file cannot both have finished and have been cut off, and
     * if both appear the cut is the later fact — the marker then describes a run that did not end the way
     * it claims. With neither, the answer is UNKNOWN, which is the ordinary case for every producer that
     * is silent and must never be reported as complete (D-T8).
     */
    public StreamEnd resolve() {
        if (unterminatedTailChars > 0) return StreamEnd.stoppedMidWrite(indexed, unterminatedTailChars);
        if (marker != null) return StreamEnd.declared(marker.records(), indexed);
        return StreamEnd.unknown(indexed);
    }
}
