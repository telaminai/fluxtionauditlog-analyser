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
                        Member member, java.util.List<Run> runs) {

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

    /**
     * One file of a rolled set, and how many records THAT file holds.
     *
     * <p>Re-review found a member's numbers sitting beside the SET's record count in {@code context}
     * with no file named, so an agent read "declares 6, read 25" under a missing-records state. Round
     * four then found the same thing one level down: a run's numbers presented as the member's, with the
     * member's own count missing entirely. Every scope now states its own count beside its own numbers.
     */
    /**
     * @param firstRowInLog the set-global index of this file's first record — the numbering `read` and
     *                      `goto` accept. Round five A-6: a run's positions were reported inside their
     *                      member, so "records 2 to 4" named set rows 4 to 6 and an agent following them
     *                      landed in a different file's run, which was whole.
     */
    public record Member(String file, long fileRecords, long firstRowInLog) {}

    /**
     * One run that did not match its marker — {@code spec-audit-stream-end.md} D-E6.
     *
     * <p>Round five found the tracker keeping only the FIRST worst run and dropping every other, on both
     * surfaces. A file of {@code 3 records, marker 5, 2 records, marker 12} reported "run 1 is missing 2"
     * and said nothing at all about run 2, which was missing ten. A verdict that names one failure while
     * concealing another is the D-T8 failure wearing a number.
     */
    public record Run(int ordinal, long firstRecord, long lastRecord, State state,
                      long declaredRecords, long emittedRecords) {
        public boolean isEmpty() {
            return lastRecord < firstRecord;
        }
    }

    public StreamEnd(State state, long declaredRecords, long emittedRecords) {
        this(state, declaredRecords, emittedRecords, null, null, java.util.List.of());
    }

    public StreamEnd(State state, long declaredRecords, long emittedRecords, Segment segment) {
        this(state, declaredRecords, emittedRecords, segment, null, java.util.List.of());
    }

    /** The same verdict, said about a named run rather than about the file. */
    public StreamEnd inSegment(Segment s) {
        return new StreamEnd(state, declaredRecords, emittedRecords, s, member, runs);
    }

    /** The same verdict, carrying every run that did not match its marker (D-E6). */
    public StreamEnd withRuns(java.util.List<Run> all) {
        return new StreamEnd(state, declaredRecords, emittedRecords, segment, member, java.util.List.copyOf(all));
    }

    /** The same verdict, said about one named FILE of a rolled set, with that file's own count. */
    public StreamEnd inMember(String file, long fileRecords, long firstRowInLog) {
        return new StreamEnd(state, declaredRecords, emittedRecords, segment,
                new Member(file, fileRecords, firstRowInLog), runs);
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
        UNKNOWN,
        /**
         * The file ends with a stream-end marker that has no closing {@code ---} (§1a rule 1).
         *
         * <p>Its writer has not finished making the claim, or has finished writing and not terminated
         * it — at the byte level those are the same file. Either way completeness is unknown, and
         * unlike plain UNKNOWN there is something to say and someone to say it to: the producer must
         * terminate its marker. Round six asked for this rather than letting the half-written marker
         * become a phantom record in the table with nothing explaining it.
         */
        UNTERMINATED_MARKER
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
    public String diagnostic(String logName) {
        return StreamEndReport.sentence(this, logName);
    }

}
