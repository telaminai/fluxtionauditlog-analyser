package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * Whether a text audit file says it is whole — {@code spec-audit-stream-end.md} D-E3.
 *
 * <p>Four states, and the fourth is the point. A file that ends cleanly after a separator but carries
 * no marker is {@link State#UNKNOWN}, never "complete": every producer today, including every export
 * the analyser has ever read, lands there. Silence about completeness stays legal; what changes is that
 * the analyser reports the silence instead of reading it as success (D-T8).
 *
 * <p>This is a CONTAINER fact, not a record. The marker that carries it is physically a record because
 * Format 1 §1 has no position for non-record text, but it never reaches the index, the table or any
 * count (D-E4).
 */
public record StreamEnd(State state, long declaredRecords, long emittedRecords, long unreadChars) {

    public enum State {
        /** A marker is present and its count matches what was read. */
        COMPLETE,
        /** A marker is present and claims more records than the file holds. */
        MISSING_RECORDS,
        /** Text after the last separator never closed: the writer stopped mid-record. */
        STOPPED_MID_WRITE,
        /** No marker, and nothing to suggest a stop. The file may or may not be whole. */
        UNKNOWN
    }

    /** The state of every file written by a producer that does not emit markers. */
    public static StreamEnd unknown(long emitted) {
        return new StreamEnd(State.UNKNOWN, -1, emitted, 0);
    }

    public static StreamEnd stoppedMidWrite(long emitted, long unreadChars) {
        return new StreamEnd(State.STOPPED_MID_WRITE, -1, emitted, unreadChars);
    }

    /** COMPLETE when the declared count matches, MISSING_RECORDS when it does not. */
    public static StreamEnd declared(long declaredRecords, long emitted) {
        return new StreamEnd(declaredRecords == emitted ? State.COMPLETE : State.MISSING_RECORDS,
                declaredRecords, emitted, 0);
    }

    public boolean isKnownComplete() {
        return state == State.COMPLETE;
    }

    /**
     * A sentence for the source-diagnostic list, or null when there is nothing to say.
     *
     * <p>{@link State#UNKNOWN} returns null deliberately: it is the ordinary case for every existing
     * file, and a diagnostic on every log the analyser has ever opened would be noise rather than
     * information. The state is still reported through {@code context} and the human surface, where a
     * reader is asking about this file rather than being interrupted about it.
     */
    public String diagnostic(String fileName) {
        return switch (state) {
            case COMPLETE -> null;
            case UNKNOWN -> null;
            case MISSING_RECORDS -> fileName + " says it holds " + declaredRecords + " records and "
                    + emittedRecords + " were read. " + (declaredRecords - emittedRecords)
                    + " are missing from the middle or the end - this is not a truncated tail, which "
                    + "would be reported separately.";
            case STOPPED_MID_WRITE -> "the last " + unreadChars + " characters of " + fileName
                    + " never closed with a --- separator - a writer that stopped mid-record, or a "
                    + "damaged tail. Every whole record before them is here; the one they belong to is not.";
        };
    }
}
