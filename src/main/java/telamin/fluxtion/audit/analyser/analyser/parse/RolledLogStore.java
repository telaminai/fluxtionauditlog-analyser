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
     * The set is whole only when every member says it is — {@code spec-audit-stream-end.md} D-E3.
     *
     * <p>Review found the set taking {@link LogStore}'s default, so a member that had lost records
     * reported UNKNOWN for the whole set and nothing surfaced the member's problem. A rolled set is
     * presented as ONE log, so it owes one honest answer about that log: the weakest member's.
     *
     * <p>COMPLETE requires every member to be COMPLETE. One silent member makes the set UNKNOWN, because
     * a gap could sit inside it and nothing would say so. A member that lost records makes the set say
     * so, and {@link #sourceDiagnostics()} names which file.
     */
    @Override
    public StreamEnd streamEnd() {
        StreamEnd worst = null;
        for (LogStore m : members) {
            StreamEnd s = m.streamEnd();
            if (worst == null || severity(s.state()) > severity(worst.state())) worst = s;
        }
        if (worst == null) return StreamEnd.unknown(size());
        return worst.state() == StreamEnd.State.COMPLETE
                ? new StreamEnd(StreamEnd.State.COMPLETE, size(), size())
                : new StreamEnd(worst.state(), worst.declaredRecords(), worst.emittedRecords());
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

    /** Each member's own diagnostic, named by its file so a set of twelve says WHICH one is short. */
    @Override
    public java.util.List<String> sourceDiagnostics() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            String d = members.get(i).streamEnd().diagnostic(paths.get(i).getFileName().toString());
            if (d != null) out.add(d);
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
