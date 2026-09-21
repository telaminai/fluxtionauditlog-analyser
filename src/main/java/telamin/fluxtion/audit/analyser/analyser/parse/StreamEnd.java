package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * Whether a text audit file says it is whole — {@code spec-audit-stream-end.md} D-E3.
 *
 * <p>{@link State#UNKNOWN} is the point. A file that carries no marker says nothing about its own
 * completeness, and that is every producer today, including every export the analyser has ever read.
 * Silence stays legal; what changes is that the analyser reports the silence instead of reading it as
 * success (D-T8).
 *
 * <p>This is a CONTAINER fact, not a record. The marker that carries it is physically a record because
 * Format 1 §1 has no position for non-record text, but it never reaches the index, the table or any
 * count (D-E4).
 *
 * <p><b>What this cannot tell you, corrected in review.</b> An earlier version of this class reported
 * {@code STOPPED_MID_WRITE} whenever the file's last record had no closing {@code ---}. That was
 * unsound: §1 defines {@code ---} as a SEPARATOR, and explicitly skips blank text after the last one, so
 * a whole file may legitimately end without one — and the dominant real producer, Mongoose's audit
 * export, does exactly that, writing {@code \n---\n} only BETWEEN records. Every real export was
 * therefore reported as a damaged tail. The state is gone, and with it the claim: <b>a text container
 * cannot detect a writer that stopped mid-record.</b> A run killed at any point has no marker, so it is
 * UNKNOWN, which is the honest answer. What the marker's count CAN find is loss in the middle, where
 * no amount of tail inspection would have helped.
 */
public record StreamEnd(State state, long declaredRecords, long emittedRecords) {

    public enum State {
        /** Every marker's count matched the records before it, and a marker is the last thing in the file. */
        COMPLETE,
        /** A marker claims more records than the file holds: records were lost. */
        MISSING_RECORDS,
        /** A marker claims fewer records than precede it: a writer defect, or a marker that is not an end. */
        MORE_THAN_DECLARED,
        /** A marker is present but carries no readable count, so it asserts an end it cannot show (§1a). */
        UNVERIFIED,
        /** No marker, or records after the last one. The file may or may not be whole. */
        UNKNOWN
    }

    /** The state of every file written by a producer that does not emit markers. */
    public static StreamEnd unknown(long emitted) {
        return new StreamEnd(State.UNKNOWN, -1, emitted);
    }

    /** A marker with no readable count: an end is claimed, and nothing backs it. */
    public static StreamEnd unverified(long emitted) {
        return new StreamEnd(State.UNVERIFIED, -1, emitted);
    }

    /**
     * The verdict for one declared count against the records it covers.
     *
     * <p>A negative {@code declaredRecords} means the marker carried no readable count and yields
     * {@link State#UNVERIFIED} — never {@link State#MISSING_RECORDS}, which an earlier version produced
     * and then printed as "holds -1 records … -26 are missing".
     */
    public static StreamEnd declared(long declaredRecords, long emitted) {
        if (declaredRecords < 0) return unverified(emitted);
        State s = declaredRecords == emitted ? State.COMPLETE
                : declaredRecords > emitted ? State.MISSING_RECORDS
                : State.MORE_THAN_DECLARED;
        return new StreamEnd(s, declaredRecords, emitted);
    }

    public boolean isKnownComplete() {
        return state == State.COMPLETE;
    }

    /**
     * A sentence for the source-diagnostic list, or null when there is nothing to say.
     *
     * <p>{@link State#COMPLETE} and {@link State#UNKNOWN} return null. Complete has nothing to warn
     * about, and unknown is the ordinary case for every existing file — a diagnostic on every log the
     * analyser has ever opened would be noise rather than information. Both states still reach
     * {@code context} and the status bar, where a reader is asking about this file rather than being
     * interrupted about it.
     */
    public String diagnostic(String fileName) {
        return switch (state) {
            case COMPLETE, UNKNOWN -> null;
            case UNVERIFIED -> fileName + " ends with a marker saying the writer finished, but the marker "
                    + "carries no readable record count. The claim cannot be checked, so it is not evidence.";
            case MISSING_RECORDS -> fileName + " says it holds " + declaredRecords + " records and "
                    + emittedRecords + " were read. " + (declaredRecords - emittedRecords)
                    + " are missing from the middle or the end.";
            case MORE_THAN_DECLARED -> fileName + " says it holds " + declaredRecords + " records and "
                    + emittedRecords + " were read - " + (emittedRecords - declaredRecords)
                    + " more than the marker declares. The marker is wrong, or it is not the end of "
                    + "this file. Either way the count is not evidence of a whole file.";
        };
    }
}
