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
