package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolled-set discovery and ordering (spec-rolled-logs M30.1). The principle, verbatim from the spec:
 * <b>names discover; content orders; violations are reported, never repaired.</b>
 *
 * <p>A filename suffix is a hint good enough to find siblings and nothing more — index suffixes are
 * genuinely ambiguous (logrotate's {@code x.log.1} is the <i>newest</i> rolled file; an incrementing
 * writer's {@code x.log.1} is the <i>oldest</i>), so the load order comes from each file's <b>first
 * timed record's {@code logTime}</b> (D-R1), probed cheaply from the head rather than fully parsed.
 * A file with no timed record at all has no orderable position: it is placed by its NAME among its
 * siblings and the {@link TimeOrderReport} says so loudly (review R2 — refusing the whole set over one
 * benign boundary file was rejected).
 */
public final class RollSetResolver {

    /** The initial head/tail probe window; doubled progressively up to {@link #MAX_PROBE}. */
    static final int PROBE_BYTES = 256 * 1024;

    /**
     * The probe gives up past this bound and declares the file's position unverifiable — stated in the
     * report message rather than silently scanning gigabytes at discovery time.
     */
    static final int MAX_PROBE = 4 * 1024 * 1024;

    /** One member of a candidate set: the probed first/last timed {@code logTime}s (null = none found). */
    public record Sibling(Path file, Long firstTime, Long lastTime) {
        public boolean untimed() {
            return firstTime == null;
        }
    }

    /** The content-ordered set plus the discovery-time half of the validation (cross-file checks). */
    public record RollSet(List<Sibling> ordered, TimeOrderReport report) {
    }

    private RollSetResolver() {
    }

    // ---- discovery (names only — no file content is read here) ------------------------------------

    /**
     * {@code maker.log.2} → root {@code maker.log}; {@code maker-2026-08-17_09.log} → root
     * {@code maker.log}. Null when the name carries no recognised roll suffix.
     */
    static String rootOf(String fileName) {
        Matcher idx = INDEX_SUFFIX.matcher(fileName);
        if (idx.matches()) return idx.group(1);
        Matcher dt = DATETIME_SUFFIX.matcher(fileName);
        if (dt.matches()) return dt.group(1) + dt.group(6);   // base + extension
        return null;
    }

    /** {@code <root>.<digits>} — logrotate shape ({@code maker.log.1}). */
    private static final Pattern INDEX_SUFFIX = Pattern.compile("(.+)\\.(\\d{1,4})");

    /**
     * {@code <base><sep><date[-time]>.<ext>} — {@code maker-2026-08-17.log},
     * {@code maker.2026-08-17_09-00.log}, {@code maker-20260817.log}.
     */
    private static final Pattern DATETIME_SUFFIX = Pattern.compile(
            "(.+?)[-._](\\d{4}-?\\d{2}-?\\d{2}([-_.]\\d{2}([-:.]\\d{2})?([-:.]\\d{2})?)?)(\\.[A-Za-z0-9]+)");

    /**
     * Same-directory files that look like rolled siblings of {@code opened} (including {@code opened}
     * itself, and the bare root file when it exists). Names only — content is read later, and only for
     * the files actually returned.
     */
    public static List<Path> discoverSiblings(Path opened) throws IOException {
        Path dir = opened.toAbsolutePath().getParent();
        if (dir == null || !Files.isDirectory(dir)) return List.of(opened);
        String openedName = opened.getFileName().toString();
        String root = rootOf(openedName);
        if (root == null) root = openedName;   // the bare file may be the live member of a rolled set

        List<Path> out = new ArrayList<>();
        final String wanted = root;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.sorted().toList()) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                if (name.equals(wanted) || wanted.equals(rootOf(name))) out.add(p);
            }
        }
        return out.size() > 1 ? out : List.of(opened);
    }

    // ---- ordering + the discovery-time half of validation -----------------------------------------

    public static RollSet resolve(List<Path> files) throws IOException {
        return resolve(files, MAX_PROBE);
    }

    /** {@code maxProbe} parameterised for tests. */
    public static RollSet resolve(List<Path> files, int maxProbe) throws IOException {
        List<Sibling> siblings = new ArrayList<>();
        for (Path f : files) {
            siblings.add(probe(f, maxProbe));
        }

        // content orders (D-R1): timed files by first logTime; a wholly-untimed file keeps its NAME
        // position among the timed ones — placed after the timed file its name sorts after
        List<Sibling> ordered = new ArrayList<>(siblings);
        ordered.sort(Comparator.comparing((Sibling s) -> s.untimed() ? null : s.firstTime(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.file().getFileName().toString()));

        List<TimeOrderReport.Violation> violations = new ArrayList<>();
        for (Sibling s : ordered) {
            if (s.untimed()) {
                violations.add(new TimeOrderReport.Violation(TimeOrderReport.Kind.UNTIMED_FILE,
                        s.file().getFileName().toString(), -1,
                        "'" + s.file().getFileName() + "' contains no timed record in the probed head — "
                                + "positioned by name, order unverifiable"));
            }
        }
        Sibling prev = null;
        for (Sibling s : ordered) {
            if (s.untimed()) continue;
            if (prev != null && prev.lastTime() != null && s.firstTime() < prev.lastTime()) {
                long overlapMs = prev.lastTime() - s.firstTime();
                violations.add(new TimeOrderReport.Violation(TimeOrderReport.Kind.FILE_OVERLAP,
                        s.file().getFileName().toString(), -1,
                        "'" + s.file().getFileName() + "' overlaps '" + prev.file().getFileName()
                                + "' by " + overlapMs + "ms — the files interleave in time"));
            }
            prev = s;
        }
        return new RollSet(List.copyOf(ordered), new TimeOrderReport(List.copyOf(violations)));
    }

    // ---- the probe ---------------------------------------------------------------------------------

    /** First and last timed {@code logTime}, from bounded head/tail windows (doubling up to the cap). */
    static Sibling probe(Path file, int maxProbe) throws IOException {
        long size = Files.size(file);
        Long first = null, last = null;
        long window = PROBE_BYTES;
        while (true) {
            int len = (int) Math.min(window, Math.min(size, maxProbe));
            first = firstTimed(readChunk(file, 0, len), false);
            if (first != null || len >= size || len >= maxProbe) break;
            window *= 2;
        }
        window = PROBE_BYTES;
        while (true) {
            int len = (int) Math.min(window, Math.min(size, maxProbe));
            last = firstTimed(readChunk(file, Math.max(0, size - len), len), true);
            if (last != null || len >= size || len >= maxProbe) break;
            window *= 2;
        }
        return new Sibling(file, first, last);
    }

    private static String readChunk(Path file, long at, int len) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(at);
            byte[] buf = new byte[len];
            int read = raf.read(buf);
            return new String(buf, 0, Math.max(0, read), StandardCharsets.UTF_8);
        }
    }

    /**
     * The first (or, {@code fromEnd}, the LAST) timed record's {@code logTime} in this chunk. A chunk
     * cut mid-record simply yields an unparseable fragment whose {@code logTime} is null — skipped, the
     * lenient-parser behaviour everything else already relies on.
     */
    private static Long firstTimed(String chunk, boolean fromEnd) {
        final Long[] found = {null};
        RecordFramer.frame(chunk, raw -> {
            LogRecord rec = RecordParser.parse(raw.text(), raw.offset());
            Long lt = rec == null ? null : rec.logTime();
            if (lt != null && (fromEnd || found[0] == null)) found[0] = lt;
        });
        return found[0];
    }
}
