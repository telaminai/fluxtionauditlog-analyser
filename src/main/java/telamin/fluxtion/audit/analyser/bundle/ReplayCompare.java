package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Does a replayed audit log reproduce the one a bundle carries (spec-evidence-bundle-replay §6)? The comparison is
 * what a recipient has to trust, so it is the analyser's, and it states one verdict.
 *
 * <p><b>The rule, measured (spike R1/R2, generator 1.0.75): every record, and every line of it, is exact, except
 * {@code endTime}.</b> {@code endTime} is a live clock read when the cycle ends; replay pins the clock at the recorded
 * instant, so it cannot know it. Nothing else is excepted: an input's {@code eventTime}, a graph-raised record's times,
 * every node's logged values must all agree. A record count that differs is a divergence, named at the first record
 * one side has and the other does not.
 *
 * <p>The bundle is verified first, and only a bundle that carries replay records, of the whole log, is compared: the
 * replay those records drive is of the whole run, and an excerpt is not one. Both logs are read with the analyser's own
 * reader, so how each file frames its records does not matter; each record's text is what is compared.
 */
public final class ReplayCompare {

    private ReplayCompare() {
    }

    /** The one place a comparison may differ, and the reason: a live clock read at the end of the cycle. */
    static final Pattern EXCEPTED = Pattern.compile("^\\s*endTime:.*$");

    /**
     * The verdict. {@code refusal} non-null: nothing was compared, and it says why. Otherwise {@code agrees}, with the
     * records compared and how many had {@code endTime} excepted, or the first {@code divergence}, in words.
     */
    public record Verdict(EvidenceBundle.Verification verification, String refusal, boolean agrees, int records,
                          int excepted, String divergence) {
        static Verdict refused(EvidenceBundle.Verification v, String why) {
            return new Verdict(v, why, false, 0, 0, null);
        }
    }

    public static Verdict compare(Path bundle, Path replayed, int thresholdMb) throws IOException {
        Path scratch = Files.createTempDirectory("fluxtion-replay-compare-");
        try {
            var unpacked = EvidenceBundle.unpack(bundle, scratch);
            var v = unpacked.verification();
            if (!v.ok()) return Verdict.refused(v, v.refusal());
            if (v.replay() == null) {
                return Verdict.refused(v, "this bundle carries no replay records, so no replay can be compared with its log");
            }
            if (v.excerpt() != null) {
                return Verdict.refused(v, "this bundle's log is an excerpt, and a replay is of the whole run");
            }
            String member = v.members().stream().map(EvidenceBundle.Member::path).filter(p -> p.startsWith("log/"))
                    .findFirst().orElse(null);
            if (member == null) return Verdict.refused(v, "this bundle has no log member");
            if (!Files.isRegularFile(replayed)) return Verdict.refused(v, "cannot read " + replayed + ": not a file");
            try (LogStore bundled = LogStores.open(unpacked.workingCopy().resolve(member), thresholdMb);
                 LogStore theirs = LogStores.open(replayed, thresholdMb)) {
                return compare(v, bundled, theirs);
            }
        } finally {
            deleteTree(scratch);
        }
    }

    static Verdict compare(EvidenceBundle.Verification v, LogStore bundled, LogStore replayed) {
        int n = bundled.size(), m = replayed.size();
        int excepted = 0;
        for (int i = 0; i < Math.min(n, m); i++) {
            List<String> a = lines(bundled.rawText(i)), b = lines(replayed.rawText(i));
            String first = firstDifference(a, b);
            if (first != null) {
                return new Verdict(v, null, false, i, excepted,
                        "record " + i + " (" + event(bundled, i) + "): " + first);
            }
            if (differsIn(a, b)) excepted++;
        }
        if (n != m) {
            int k = Math.min(n, m);
            String what = n > m
                    ? "the bundled log has record " + k + " (" + event(bundled, k) + "), and the replay does not"
                    : "the replay has record " + k + " (" + event(replayed, k) + "), and the bundled log does not";
            return new Verdict(v, null, false, k, excepted, "record " + k + ": " + what
                    + " (" + n + " records bundled, " + m + " replayed)");
        }
        return new Verdict(v, null, true, n, excepted, null);
    }

    private static String event(LogStore s, int i) {
        String e = s.index().event(i);
        return e == null ? "no event" : e;
    }

    private static List<String> lines(String record) {
        return List.of(record.split("\n", -1));
    }

    /** Whether the two records differ in an excepted line (so the verdict can say how often the exception applied). */
    private static boolean differsIn(List<String> a, List<String> b) {
        return !a.equals(b);
    }

    /**
     * The first difference between two records outside the excepted lines, as {@code path: 'mine' ≠ 'theirs'}, or null
     * when they agree. Excepted lines are compared by position: a record whose {@code endTime} line moved, or is
     * missing on one side, still differs.
     */
    static String firstDifference(List<String> a, List<String> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            String x = i < a.size() ? a.get(i) : null, y = i < b.size() ? b.get(i) : null;
            if (x != null && x.equals(y)) continue;
            if (x != null && y != null && EXCEPTED.matcher(x).matches() && EXCEPTED.matcher(y).matches()
                    && indent(x) == indent(y)) {
                continue;
            }
            List<String> ctx = x != null ? a : b;
            String path = path(ctx, i);
            return path + ": " + (x == null ? "(nothing)" : "'" + value(x) + "'") + " ≠ "
                    + (y == null ? "(nothing)" : "'" + value(y) + "'");
        }
        return null;
    }

    /** The YAML key path of line {@code i}: its own key, prefixed by each less-indented key above it. */
    static String path(List<String> lines, int i) {
        List<String> keys = new ArrayList<>();
        int depth = indent(lines.get(i));
        keys.add(key(lines.get(i)));
        for (int j = i - 1; j >= 0 && depth > 0; j--) {
            String l = lines.get(j);
            if (l.isBlank()) continue;
            int d = indent(l);
            if (d < depth) {
                keys.add(0, key(l));
                depth = d;
            }
        }
        return String.join(".", keys.stream().filter(k -> !k.isEmpty()).toList());
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static String key(String line) {
        String t = line.strip();
        if (t.startsWith("- ")) t = t.substring(2);
        int colon = t.indexOf(':');
        return colon < 0 ? t : t.substring(0, colon);
    }

    private static String value(String line) {
        String t = line.strip();
        int colon = t.indexOf(':');
        return colon < 0 ? t : t.substring(colon + 1).strip();
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
