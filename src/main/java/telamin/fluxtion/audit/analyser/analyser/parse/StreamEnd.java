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
public record StreamEnd(State state, long declaredRecords, long emittedRecords, Segment segment,
                        String member) {

    /**
     * Which run a verdict is about, when the file holds more than one. Null when the file is a single
     * run, which is the ordinary case.
     *
     * <p>Re-review found the diagnostic reporting a segment's numbers as the whole file's: two runs, the
     * first marker claiming 30 over 25 records, produced <i>"this log says it holds 30 records and 25
     * were read"</i> for a file holding 50. Both numbers were true of the run and false of the file, and
     * nothing said which was meant.
     *
     * @param ordinal     1-based position of this run in the file
     * @param firstRecord global index of the run's first record
     * @param lastRecord  global index of the run's last record
     * @param fileRecords records in the whole file, so the sentence can distinguish them
     */
    public record Segment(int ordinal, long firstRecord, long lastRecord, long fileRecords) {
        /**
         * A run with no records at all: two markers in a row, the second declaring a count.
         *
         * <p>Re-review found this printing "records 25 to 24", because the last record of an empty run
         * is one before its first. The arithmetic is right and the sentence is nonsense, so the empty
         * case is named rather than described by a range.
         */
        public boolean isEmpty() {
            return lastRecord < firstRecord;
        }
    }

    public StreamEnd(State state, long declaredRecords, long emittedRecords) {
        this(state, declaredRecords, emittedRecords, null, null);
    }

    public StreamEnd(State state, long declaredRecords, long emittedRecords, Segment segment) {
        this(state, declaredRecords, emittedRecords, segment, null);
    }

    /** The same verdict, said about a named run rather than about the file. */
    public StreamEnd inSegment(Segment s) {
        return new StreamEnd(state, declaredRecords, emittedRecords, s, member);
    }

    /**
     * The same verdict, said about one named FILE of a rolled set.
     *
     * <p>A set's verdict comes from a member, and the member's numbers are about that file. Re-review
     * found them sitting beside the SET's record count in {@code context} with no file named, so an
     * agent read "declares 6, read 25" under a missing-records state. The member's name travels with
     * the numbers so the two scopes can never be printed as one.
     */
    public StreamEnd inMember(String file) {
        return new StreamEnd(state, declaredRecords, emittedRecords, segment, file);
    }

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
        // "this log" vs "run 1 of this log (records 0-24), which holds 50 in all" — the numbers below
        // are the RUN's whenever a run is named, and saying so is the whole point of the distinction.
        String subject = segment == null ? fileName
                : segment.isEmpty()
                        ? "run " + segment.ordinal() + " of " + fileName + " (which holds no records at "
                                + "all, of " + segment.fileRecords() + " in the file)"
                        : "run " + segment.ordinal() + " of " + fileName + " (records "
                                + segment.firstRecord() + " to " + segment.lastRecord() + ", of "
                                + segment.fileRecords() + " in the file)";
        return switch (state) {
            case COMPLETE, UNKNOWN -> null;
            case UNVERIFIED -> subject + " ends with a marker saying the writer finished, but the marker "
                    + "carries no readable record count. The claim cannot be checked, so it is not evidence.";
            case MISSING_RECORDS -> subject + " declares " + declaredRecords + " records and "
                    + emittedRecords + " were read. " + (declaredRecords - emittedRecords)
                    + " are missing from the middle or the end.";
            case MORE_THAN_DECLARED -> subject + " declares " + declaredRecords + " records and "
                    + emittedRecords + " were read - " + (emittedRecords - declaredRecords)
                    + " more than the marker says. The marker is wrong, or it is not the end of "
                    + "what it claims to end. Either way the count is not evidence of a whole file.";
        };
    }
}
