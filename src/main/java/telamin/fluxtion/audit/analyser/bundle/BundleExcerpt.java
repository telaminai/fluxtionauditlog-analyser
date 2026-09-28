package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

/**
 * Evidence bundle, the optional time-window excerpt (owner, 2026-09-28: capture takes an optional time window). An
 * excerpt is the CONTIGUOUS run of records, in file order, from the first whose log time is at or after {@code from} to
 * the last whose log time is at or before {@code to}. Each record is copied as its exact raw text, so its digest, the
 * basis a walk's record target is bound to, is unchanged; only its index shifts, by {@link Range#first}.
 *
 * <p>The excerpt checks itself by mechanism rather than trust: it is re-read with the analyser's own opener, and it
 * must yield exactly the chosen records, each with the digest the source record has, and make no stream-end claim of
 * its own (an excerpt is not a whole stream). Anything else refuses the capture, naming what differed.
 */
public final class BundleExcerpt {

    private BundleExcerpt() {
    }

    /** Records {@code first..last} (0-based, inclusive) of a log of {@code sourceRecords}, chosen by {@code from}/{@code to}. */
    public record Range(int first, int last, int sourceRecords, Long from, Long to) {
        public int size() {
            return last - first + 1;
        }
    }

    /** What re-reading the written excerpt established: its run basis and its fingerprint's index. */
    public record Checked(List<String> runBasis, LogIndex index, String sha256) { }

    /**
     * The record range {@code from}..{@code to} selects in {@code store}, or null when it selects none. Either bound may be
     * null (open-ended). A record with no log time is inside the range only between two records that are.
     */
    public static Range range(LogStore store, Long from, Long to) {
        LogIndex idx = store.index();
        int n = store.size(), first = -1, last = -1;
        for (int i = 0; i < n && first < 0; i++) {
            Long t = idx.logTime(i);
            if (t != null && (from == null || t >= from) && (to == null || t <= to)) first = i;
        }
        for (int i = n - 1; i >= 0 && last < 0; i--) {
            Long t = idx.logTime(i);
            if (t != null && (from == null || t >= from) && (to == null || t <= to)) last = i;
        }
        return first < 0 || last < first ? null : new Range(first, last, n, from, to);
    }

    /**
     * What is taken from the LIVE store, on the session's thread, so that nothing later reads a store another open may
     * have closed: the text to write and each chosen record's digest to hold it to.
     */
    public record Taken(Range range, String text, List<String> digests) { }

    public static Taken take(LogStore store, Range r) {
        List<String> digests = new java.util.ArrayList<>(r.size());
        for (int i = r.first(); i <= r.last(); i++) digests.add(WalkIdentity.recordDigest(store.rawText(i)));
        return new Taken(r, text(store, r), List.copyOf(digests));
    }

    /** The excerpt's text: each record as its exact raw text, framed as Format 1 frames it, every record closed. */
    public static String text(LogStore store, Range r) {
        StringBuilder b = new StringBuilder();
        for (int i = r.first(); i <= r.last(); i++) b.append("---\n").append(store.rawText(i)).append('\n');
        return b.append("---\n").toString();
    }

    /**
     * Re-read {@code written} and hold it to the source: the same records, the same digests, no end claim. Null
     * {@link Checked#runBasis()} never escapes: a mismatch throws, naming it, and the capture refuses.
     */
    public static Checked check(Path written, Taken taken, int thresholdMb) throws IOException {
        Range r = taken.range();
        try (LogStore back = LogStores.open(written, thresholdMb)) {
            if (back.size() != r.size()) {
                throw new IOException("the excerpt re-reads as " + back.size() + " records, not the " + r.size() + " chosen");
            }
            for (int i = 0; i < r.size(); i++) {
                if (!taken.digests().get(i).equals(WalkIdentity.recordDigest(back.rawText(i)))) {
                    throw new IOException("the excerpt's record " + i + " differs from source record " + (r.first() + i));
                }
            }
            if (back.streamEnd().isKnownComplete()) {
                throw new IOException("the excerpt claims a complete stream; an excerpt is never a whole stream");
            }
            String sha = HexFormat.of().formatHex(sha256(Files.readAllBytes(written)));
            return new Checked(WalkIdentity.runBasisOf(List.of(sha), back.size()), back.index(), sha);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("the excerpt could not be re-read: " + e.getMessage(), e);
        }
    }

    /** Write what was taken to {@code out}, then check it. */
    public static Checked write(Taken taken, Path out, int thresholdMb) throws IOException {
        Files.writeString(out, taken.text(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        return check(out, taken, thresholdMb);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
