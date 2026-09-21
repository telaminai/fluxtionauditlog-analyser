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
        if (end.state() == StreamEnd.State.COMPLETE || end.state() == StreamEnd.State.UNKNOWN) return null;
        String subject = subject(end, logName);
        long declared = end.declaredRecords();
        long read = end.emittedRecords();
        return switch (end.state()) {
            case UNVERIFIED -> subject + " ends with a marker saying the writer finished, but the marker "
                    + "carries no readable record count. The claim cannot be checked, so it is not evidence.";
            case MISSING_RECORDS -> subject + " declares " + plural(declared, "record") + " and "
                    + read + were(read) + " read. " + plural(declared - read, "record")
                    + is(declared - read) + " missing from the middle or the end.";
            case MORE_THAN_DECLARED -> subject + " declares " + plural(declared, "record") + " and "
                    + read + were(read) + " read - " + plural(read - declared, "record")
                    + " more than the marker says. The marker is wrong, or it is not the end of what it "
                    + "claims to end. Either way the count is not evidence of a whole file.";
            default -> null;
        };
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
        return seg.isEmpty()
                ? "run " + seg.ordinal() + " of " + file + " (which holds no records at all, of "
                        + of + " in the file)"
                : "run " + seg.ordinal() + " of " + file + " (records " + seg.firstRecord() + " to "
                        + seg.lastRecord() + ", of " + of + " in the file)";
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
            putRunOrNumbers(member, end);
            out.put("member", member);
        } else {
            putRunOrNumbers(out, end);
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
        if (!seg.isEmpty()) {                        // an empty run has no positions to give
            run.put("firstRecord", seg.firstRecord());
            run.put("lastRecord", seg.lastRecord());
        }
        if (end.declaredRecords() >= 0) run.put("declaredRecords", end.declaredRecords());
        run.put("recordsRead", end.emittedRecords());
        scope.put("run", run);
    }
}
