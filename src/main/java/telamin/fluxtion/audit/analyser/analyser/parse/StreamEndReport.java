package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The ONE place a {@link StreamEnd} becomes something a reader sees — a sentence for a person and a map
 * for an agent, from the same model.
 *
 * <p><b>Why this class exists, which is the only interesting thing about it.</b> The same defect was
 * found and fixed five times across three review rounds, and every fix was made where the defect was
 * spotted:
 *
 * <ol>
 *   <li>the diagnostic sentence reported one run's numbers as the whole file's;</li>
 *   <li>{@code context} did the same for runs, fixed only after the sentence was;</li>
 *   <li>{@code context} did the same for a rolled set, fixed only after runs were;</li>
 *   <li>{@code context} flattened a run's numbers into a member's, fixed only after sets were;</li>
 *   <li>the SPI path reported a verdict to an agent that it never showed a person at all.</li>
 * </ol>
 *
 * <p>Five instances of "one scope's numbers wearing another's label", and five local repairs. The shape
 * of the defect is that two surfaces were built separately from the same facts, so they could disagree,
 * and a sixth could appear by simple omission. The fix is structural: the scopes nest — a SET contains a
 * MEMBER file, which contains a RUN — every scope states its own record count beside its own numbers,
 * and both renderings are produced here, from that one model. A new surface calls this or it has no
 * numbers at all.
 */
public final class StreamEndReport {

    private StreamEndReport() {
    }

    /**
     * A sentence for the source-diagnostic list, or null when there is nothing to say.
     *
     * <p>{@link StreamEnd.State#COMPLETE} and {@link StreamEnd.State#UNKNOWN} say nothing: complete has
     * no warning in it, and unknown is the ordinary case for every file that predates §1a. Both still
     * reach {@link #facts}, where a reader is asking rather than being interrupted.
     */
    public static String sentence(StreamEnd end, String logName) {
        if (end.state() == StreamEnd.State.COMPLETE) return null;
        // D-E7: an unknown FILE may still contain a run that proved it lost records. Silence about the
        // file is right; silence about the proof is not.
        if (end.state() == StreamEnd.State.UNTERMINATED_MARKER) {
            return logName + " ends with a stream-end marker that has no closing --- separator, so its "
                    + "claim is unfinished and completeness is unknown. A writer MUST terminate its "
                    + "marker (format specification 1a): until it does, a file that has finished and one "
                    + "still being written are the same bytes. The marker is not shown as a record."
                    + (end.runs().isEmpty() ? "" : " " + runsSentence(end.runs()));
        }
        if (end.state() == StreamEnd.State.UNKNOWN) {
            return end.runs().isEmpty() ? null : logName + " does not say whether it is whole - it carries "
                    + "records after its last marker. " + runsSentence(end.runs()) + " That much is proven "
                    + "whatever the rest of the file turns out to be.";
        }
        String subject = subject(end, logName);
        long declared = end.declaredRecords();
        long read = end.emittedRecords();
        return switch (end.state()) {
            case UNVERIFIED -> subject + " ends with a marker saying the writer finished, but the marker "
                    + "carries no readable record count. The claim cannot be checked, so it is not evidence." + others(end);
            case MISSING_RECORDS -> subject + " declares " + plural(declared, "record") + " and "
                    + read + were(read) + " read. " + plural(declared - read, "record")
                    + is(declared - read) + " missing from the middle or the end." + others(end);
            case MORE_THAN_DECLARED -> subject + " declares " + plural(declared, "record") + " and "
                    + read + were(read) + " read - " + plural(read - declared, "record")
                    + " more than the marker says. The marker is wrong, or it is not the end of what it "
                    + "claims to end. Either way the count is not evidence of a whole file." + others(end);
            default -> null;
        };
    }

    /** "Run 1 (records 0 to 2) declares 5 records and 3 were read." — every failing run, in file order. */
    private static String runsSentence(java.util.List<StreamEnd.Run> runs) {
        StringBuilder b = new StringBuilder();
        for (StreamEnd.Run r : runs) {
            b.append(b.length() == 0 ? "" : " ").append("Run ").append(r.ordinal());
            b.append(r.isEmpty() ? " (which holds no records)"
                    : " (records " + r.firstRecord() + " to " + r.lastRecord() + ")");
            b.append(r.declaredRecords() < 0
                    ? " ends with a marker carrying no readable count."
                    : " declares " + plural(r.declaredRecords(), "record") + " and " + r.emittedRecords()
                            + were(r.emittedRecords()) + " read.");
        }
        return b.toString();
    }

    /** ", and 2 other runs in this file also disagree with their marker" — never a silent drop. */
    private static String others(StreamEnd end) {
        int n = end.runs().size() - 1;                 // the headline run is one of them
        return n <= 0 ? "" : " " + plural(n, "other run") + " in this file also "
                + (n == 1 ? "disagrees" : "disagree") + " with its marker; `context` lists them.";
    }

    private static String were(long n) {
        return n == 1 ? " was" : " were";
    }

    private static String is(long n) {
        return n == 1 ? " is" : " are";
    }

    /** "the run ending X of the file Y" — each scope named, so no number is ever unattributed. */
    private static String subject(StreamEnd end, String logName) {
        String file = end.member() == null ? logName : end.member().file();
        var seg = end.segment();
        if (seg == null) return file;
        long of = end.member() != null ? end.member().fileRecords() : seg.fileRecords();
        if (seg.isEmpty()) {
            return "run " + seg.ordinal() + " of " + file + " (which holds no records at all, of "
                    + of + " in the file)";
        }
        // A-6: inside a rolled set a run's own positions are member-local, and every verb — `read`,
        // `goto`, the table — indexes the whole set. Reporting the local pair sent a reader to a
        // different file's run. The set-global pair leads, because it is the one that can be used.
        long shift = rowShift(end);
        String where = "records " + (seg.firstRecord() + shift) + " to " + (seg.lastRecord() + shift);
        return "run " + seg.ordinal() + " of " + file + " (" + where
                + (shift == 0 ? ", of " + of + " in the file)"
                        : " of this set; records " + seg.firstRecord() + " to " + seg.lastRecord()
                                + " of " + of + " in that file)");
    }

    /**
     * Row positions, in the ONE numbering every verb accepts — the open log's — with the file's own
     * pair beside them when they differ.
     *
     * <p>A-6 fixed this for a run and round six found the same numbers unshifted two keys away, under
     * identical names. The conversion lives here and nowhere else, so a third surface cannot get it
     * wrong by omission; an empty run has no positions to give and gets none.
     */
    private static void putRows(Map<String, Object> scope, long first, long last, boolean empty,
                                long shift, StreamEnd.Member member) {
        if (empty) return;
        scope.put("firstRecord", first + shift);
        scope.put("lastRecord", last + shift);
        if (member != null) {
            scope.put("inFile", member.file());
            scope.put("firstRecordInFile", first);
            scope.put("lastRecordInFile", last);
        }
    }

    /** How far a member's own row numbers sit into the whole log; zero for a single file. */
    private static long rowShift(StreamEnd end) {
        return end.member() == null ? 0 : end.member().firstRowInLog();
    }

    /** "1 record", "5 records" — re-review found "declares 1 records" and "each of the 1 files". */
    public static String plural(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /**
     * What {@code context} carries, with every number inside the scope that owns it.
     *
     * <p>{@code recordsRead} at the top level is ALWAYS the whole log. A verdict that came from one file
     * of a rolled set nests under {@code member}, which states that file's own count; a verdict about one
     * run of several nests under {@code run} inside whichever scope contains it. So a number is never
     * read as belonging to a scope it does not describe, which is the mistake this contract kept making.
     *
     * @param logRecords records in the whole log, set or file that is open
     */
    public static Map<String, Object> facts(StreamEnd end, int logRecords) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", end.state().name().toLowerCase(Locale.ROOT));
        out.put("recordsRead", (long) logRecords);
        if (end.member() != null) {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("file", end.member().file());
            member.put("recordsRead", end.member().fileRecords());   // the MEMBER's own count
            member.put("firstRowInLog", end.member().firstRowInLog());
            putRunOrNumbers(member, end);
            out.put("member", member);
        } else {
            putRunOrNumbers(out, end);
        }
        // D-E6/D-E7: every failing run, so nothing an agent could act on is reachable only through the
        // sentence. An unknown FILE with proven losses reaches context this way and no other.
        if (!end.runs().isEmpty()) {
            java.util.List<Map<String, Object>> bad = new java.util.ArrayList<>();
            for (StreamEnd.Run r : end.runs()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("ordinal", r.ordinal());
                one.put("state", r.state().name().toLowerCase(Locale.ROOT));
                // Round six S-3: these were the MEMBER's own row numbers, sitting in the same payload,
                // under the same key names, as `member.run`'s set rows — with no file named. Following
                // them landed in a different file's run, which was whole. One conversion point now.
                putRows(one, r.firstRecord(), r.lastRecord(), r.isEmpty(), rowShift(end), end.member());
                if (r.declaredRecords() >= 0) one.put("declaredRecords", r.declaredRecords());
                one.put("recordsRead", r.emittedRecords());
                bad.add(one);
            }
            out.put("failingRuns", java.util.List.copyOf(bad));
        }
        return out;
    }

    /** A run's numbers go under {@code run}; without a run they belong to the scope itself. */
    private static void putRunOrNumbers(Map<String, Object> scope, StreamEnd end) {
        var seg = end.segment();
        if (seg == null) {
            if (end.declaredRecords() >= 0) scope.put("declaredRecords", end.declaredRecords());
            return;
        }
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("ordinal", seg.ordinal());
        putRows(run, seg.firstRecord(), seg.lastRecord(), seg.isEmpty(), rowShift(end), end.member());
        if (end.declaredRecords() >= 0) run.put("declaredRecords", end.declaredRecords());
        run.put("recordsRead", end.emittedRecords());
        scope.put("run", run);
    }
}
