package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A rolled set as ONE logical log (spec-rolled-logs M30.2): a gap-free global {@code recordIndex} over
 * per-file backends, with byte offsets kept <b>file-local</b> — an offset stays a real offset into a
 * real file the copy-prompt can hand to a grep-capable agent (D-R2); the merged index's {@code fileId}
 * column says which file.
 *
 * <p><b>D-R6 (corrected in review):</b> the heap threshold applies to the SET TOTAL — member sizes are
 * summed first, and past the threshold every member opens memory-mapped. A set must never cost more
 * heap than one file of the same total size would.
 *
 * <p>The merged index is built by column-copy from each member's own index ({@link LogIndex#addFrom})
 * — no record is parsed twice. Follow is unsupported on a composite (M31's capability flag will carry
 * this; rotation-aware follow is a recorded follow-up, not a v1 feature).
 */
public final class RolledLogStore implements LogStore {

    private final List<LogStore> members;
    private final List<Path> paths;
    private final int[] firstRow;      // global row of each member's first record
    private final LogIndex merged;

    private RolledLogStore(List<LogStore> members, List<Path> paths, int[] firstRow, LogIndex merged) {
        this.members = members;
        this.paths = paths;
        this.firstRow = firstRow;
        this.merged = merged;
    }

    /** Open {@code orderedFiles} (content order — the resolver's output) as one logical log. */
    public static RolledLogStore open(List<Path> orderedFiles, int thresholdMb) throws IOException {
        long total = 0;
        for (Path f : orderedFiles) total += Files.size(f);
        long thresholdBytes = (long) Math.max(0, thresholdMb) * 1024 * 1024;
        boolean mapAll = total > thresholdBytes;   // D-R6: the SET total decides, not each member

        List<LogStore> members = new ArrayList<>();
        LogIndex merged = new LogIndex();
        int[] firstRow = new int[orderedFiles.size()];
        int global = 0;
        for (int i = 0; i < orderedFiles.size(); i++) {
            Path f = orderedFiles.get(i);
            LogStore member = mapAll ? new MappedLogStore(f) : HeapLogStore.fromFile(f);
            members.add(member);
            int fid = merged.registerFile(f.getFileName().toString());
            firstRow[i] = global;
            LogIndex src = member.index();
            for (int row = 0; row < src.size(); row++) {
                merged.addFrom(src, row, fid);
            }
            global += src.size();
        }
        return new RolledLogStore(members, List.copyOf(orderedFiles), firstRow, merged);
    }

    @Override public List<FileReadIdentity> readIdentities() {
        return members.stream().flatMap(m -> m.readIdentities().stream()).toList();
    }

    /** The member files, load (content) order. */
    public List<Path> files() {
        return paths;
    }

    @Override
    public int size() {
        return merged.size();
    }

    @Override
    public LogIndex index() {
        return merged;
    }

    private int memberOf(int globalRow) {
        int m = 0;
        for (int i = 1; i < firstRow.length; i++) {
            if (globalRow >= firstRow[i]) m = i;
            else break;
        }
        return m;
    }

    @Override
    public LogRecord record(int row) {
        int m = memberOf(row);
        return members.get(m).record(row - firstRow[m]);
    }

    @Override
    public String rawText(int row) {
        int m = memberOf(row);
        return members.get(m).rawText(row - firstRow[m]);
    }

    @Override
    public Long minLogTime() {
        return merged.minLogTime();
    }

    @Override
    public Long maxLogTime() {
        return merged.maxLogTime();
    }

    /**
     * What the set's members say about themselves — {@code spec-audit-stream-end.md} D-E3.
     *
     * <p><b>A set is NEVER COMPLETE.</b> This is the correction re-review made, and it was a blocker. The
     * first version returned COMPLETE when every member was COMPLETE, which sounds right and is not: a
     * marker vouches for the FILE THAT CARRIES IT and for nothing else. Nothing in a rolled set records
     * how many files there should be, so a set whose middle file was never copied, or was deleted, or
     * never rotated in, is a set of individually whole files with an hour missing between two of them.
     * Observed on the first version: members of 10 and 5 records, each marked and each complete, with the
     * file between them absent, reported <b>complete, 15 records</b>. An agent reading that can conclude a
     * node never ran. It is the exact D-T8 failure this contract exists to prevent, one level up.
     *
     * <p>So: the worst member's state, and UNKNOWN when the worst is COMPLETE. A member that lost records
     * still makes the set say so, because that is a fact a member CAN establish about itself, and
     * {@link #sourceDiagnostics()} names which file. Set-level completeness would need set-level
     * evidence — a manifest, or a marker that names its successor — which Format 1 has no room for.
     */
    @Override
    public StreamEnd streamEnd() {
        StreamEnd worst = null;
        int worstIndex = -1;
        for (int i = 0; i < members.size(); i++) {
            StreamEnd s = members.get(i).streamEnd();
            if (worst == null || severity(s.state()) > severity(worst.state())) {
                worst = s;
                worstIndex = i;
            }
        }
        if (worst == null || worst.state() == StreamEnd.State.COMPLETE) return StreamEnd.unknown(size());
        // The numbers are the MEMBER's; its name travels with them so no surface can print them beside
        // the set's own count as though they described the same thing (re-review B2).
        return worst.inMember(paths.get(worstIndex).getFileName().toString(),
                members.get(worstIndex).size());
    }

    /** True when every member carries a marker that checks out — worth SAYING, never worth believing. */
    private boolean everyMemberIsWhole() {
        if (members.isEmpty()) return false;
        for (LogStore m : members) {
            if (m.streamEnd().state() != StreamEnd.State.COMPLETE) return false;
        }
        return true;
    }

    private static int severity(StreamEnd.State s) {
        return switch (s) {
            case COMPLETE -> 0;
            case UNKNOWN -> 1;
            case UNVERIFIED -> 2;
            case MORE_THAN_DECLARED -> 3;
            case MISSING_RECORDS -> 4;
        };
    }

    /**
     * Each member's own diagnostic, named by its file so a set of twelve says WHICH one is short.
     *
     * <p>When every member IS whole, one further statement is added. It is not a warning: it says what
     * the members established and, in the same breath, what they did not. Without it the set is silently
     * UNKNOWN and a reader who can see twelve files each marked complete will supply the wrong
     * conclusion themselves.
     */
    @Override
    public java.util.List<String> sourceDiagnostics() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            String d = members.get(i).streamEnd().diagnostic(paths.get(i).getFileName().toString());
            if (d != null) out.add(d);
        }
        if (everyMemberIsWhole()) {
            out.add((members.size() == 1
                    ? "the single file in this set says it is whole, and it says so only about itself."
                    : "each of the " + StreamEndReport.plural(members.size(), "file")
                            + " in this set says it is whole, and each says so only about itself.")
                    + " Nothing records how many files the set should hold, so a file that was never "
                    + "rotated in, copied or kept would leave a gap that looks exactly like this. The "
                    + "set's completeness is unknown.");
        }
        return List.copyOf(out);
    }

    @Override
    public void close() {
        for (LogStore m : members) {
            try {
                m.close();
            } catch (Exception ignored) {
                // best-effort: closing one member must not leak the rest
            }
        }
    }
}
