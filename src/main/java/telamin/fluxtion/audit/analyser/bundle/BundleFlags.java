package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.report.Finding;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * #178: the flags an evidence bundle carries, so a report's {@code finding} sections arrive WITH their text.
 *
 * <p>A flag is log-derived state — it belongs to one record of one log — so it was never part of a profile and a
 * bundle used to arrive with every finding section unresolved ("record N has no flag"). The bundle now carries each
 * flag's text (note, fix, kind) in its profile member, under the {@value #FAMILY} key family, ANCHORED by the flagged
 * record's fingerprint: the same record digest a walk's record target is bound by ({@link WalkIdentity#recordDigest}),
 * SHA-256 over that record's exact raw text. One "same content" rule for both.
 *
 * <p>On the recipient's side a flag is put back only on a record whose text has that digest. A record that is missing,
 * or whose text differs, gets NOTHING — a note attached to the wrong record would be a confident wrong finding — and the
 * mismatch is stated by record, never dropped in silence.
 *
 * <p>What it is NOT: a second place a finding is written. The flag stays the one write site (D-I1); this only carries
 * what the flag wrote to the machine the bundle is opened on. The prose passes the profile member's path redaction like
 * every other kept value, so nothing machine-shaped leaves.
 */
public final class BundleFlags {

    private BundleFlags() {
    }

    /** The profile key family the bundle's flags travel under; unknown to the profile writer, so it is preserved. */
    public static final String FAMILY = "bundleFlag";

    /** One carried flag: the record it is on, that record's digest, and what the flag says. */
    public record Flag(int record, String digest, String kind, String note, String fix) {
        public Flag {
            kind = kind == null || kind.isBlank() ? "fault" : kind;
            note = note == null ? "" : note;
            fix = fix == null || fix.isBlank() ? null : fix;
            digest = digest == null ? "" : digest;
        }

        public Finding finding() {
            return new Finding(record, note, fix, kind);
        }
    }

    /** What rebinding found: the findings put back, and one line per flag that was not, saying why. */
    public record Rebind(List<Finding> restored, List<String> withheld) {
        public Rebind {
            restored = List.copyOf(restored);
            withheld = List.copyOf(withheld);
        }
    }

    /** The flags of a session, each anchored to its record's digest. A record whose text is unknown is left out. */
    public static List<Flag> of(Map<Integer, Finding> findings, java.util.Collection<Integer> flaggedRows,
                                IntFunction<String> rawText) {
        List<Flag> out = new ArrayList<>();
        for (int row : new java.util.TreeSet<>(flaggedRows)) {
            String text = rawText.apply(row);
            if (text == null) continue;
            Finding f = findings.getOrDefault(row, new Finding(row, null, null));
            out.add(new Flag(row, WalkIdentity.recordDigest(text), f.kind(), f.note(), f.fix()));
        }
        return out;
    }

    /** Write {@code flags} as {@value #FAMILY} keys into {@code out}. */
    public static void write(Properties out, List<Flag> flags) {
        for (int i = 0; i < flags.size(); i++) {
            Flag f = flags.get(i);
            String k = FAMILY + "." + i;
            out.setProperty(k + ".record", Integer.toString(f.record()));
            out.setProperty(k + ".digest", f.digest());
            out.setProperty(k + ".kind", f.kind());
            out.setProperty(k + ".note", f.note());
            if (f.fix() != null) out.setProperty(k + ".fix", f.fix());
        }
    }

    /**
     * The capture's half: {@code settingsText} (the session's settings, as a save would write them) with the flags
     * appended as {@value #FAMILY} lines, escaped as {@link Properties#store} escapes them. The settings are the
     * session's; the flags are the session's too, so they are taken at the same moment (EB.F11).
     */
    public static String append(String settingsText, List<Flag> flags) {
        if (flags.isEmpty()) return settingsText;
        Properties p = new Properties();
        write(p, flags);
        java.io.StringWriter w = new java.io.StringWriter();
        try {
            p.store(w, null);
        } catch (java.io.IOException impossible) {
            throw new java.io.UncheckedIOException(impossible);
        }
        StringBuilder out = new StringBuilder(settingsText);
        if (!settingsText.isEmpty() && !settingsText.endsWith("\n")) out.append('\n');
        for (String line : w.toString().split("\n")) if (!line.startsWith("#") && !line.isBlank()) out.append(line).append('\n');
        return out.toString();
    }

    /** As {@link #write(Properties, List)}, into a sorted map of the profile member's kept values. */
    static void write(java.util.SortedMap<String, String> out, List<Flag> flags) {
        Properties p = new Properties();
        write(p, flags);
        for (String k : p.stringPropertyNames()) out.put(k, p.getProperty(k));
    }

    /** The flags {@code p} carries, in order; an entry that is malformed is skipped (ignore, never reject). */
    public static List<Flag> read(Properties p) {
        TreeMap<Integer, Flag> out = new TreeMap<>();
        if (p == null) return List.of();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(FAMILY + ".") || !key.endsWith(".record")) continue;
            String k = key.substring(0, key.length() - ".record".length());
            try {
                int i = Integer.parseInt(k.substring(FAMILY.length() + 1));
                int record = Integer.parseInt(p.getProperty(key).trim());
                if (record < 0) continue;
                String kind = p.getProperty(k + ".kind");
                if (kind != null && !kind.equals("fault") && !kind.equals("confirmation")) continue;
                out.put(i, new Flag(record, p.getProperty(k + ".digest"), kind, p.getProperty(k + ".note"),
                        p.getProperty(k + ".fix")));
            } catch (NumberFormatException e) {
                // skipped: a hand-edited or damaged entry is not a flag
            }
        }
        return List.copyOf(out.values());
    }

    /** The flags of {@code file}'s profile, or none when it has none or cannot be read. */
    public static List<Flag> read(java.nio.file.Path file) {
        if (file == null || !java.nio.file.Files.isRegularFile(file)) return List.of();
        Properties p = new Properties();
        try (var r = java.nio.file.Files.newBufferedReader(file)) {
            p.load(r);
        } catch (java.io.IOException | RuntimeException e) {
            return List.of();
        }
        return read(p);
    }

    /**
     * Put each flag back on the record whose text still has its digest; withhold, and say why, every one that does not.
     * The same rule as a walk's record target: unknown is never equal, and a differing digest is never attached.
     */
    public static Rebind rebind(List<Flag> flags, int size, IntFunction<String> rawText) {
        List<Finding> restored = new ArrayList<>();
        List<String> withheld = new ArrayList<>();
        for (Flag f : flags) {
            if (f.record() >= size) {
                withheld.add("record " + f.record() + " is not in this log (" + size + " records), so its flag was not put back");
                continue;
            }
            String now = f.record() < 0 ? null : rawText.apply(f.record());
            if (WalkIdentity.compare(f.digest(), now == null ? null : WalkIdentity.recordDigest(now))
                    != WalkIdentity.State.CURRENT) {
                withheld.add("record " + f.record() + "'s text differs from the record that was flagged, so its flag "
                        + "was not put back (a note on the wrong record would be a wrong finding)");
                continue;
            }
            restored.add(f.finding());
        }
        return new Rebind(restored, withheld);
    }

    /**
     * An excerpt's re-base: records {@code first..last} become {@code 0..}. A flag outside the excerpt cannot travel,
     * and is named in {@code leftOut}; the digest is unchanged, because an excerpt record's text is the source's.
     */
    static List<Flag> rebase(List<Flag> flags, int first, int last, List<String> leftOut) {
        List<Flag> out = new ArrayList<>();
        for (Flag f : flags) {
            if (f.record() < first || f.record() > last) {
                leftOut.add("flag on record " + f.record() + " (outside the excerpt's records " + first + ".." + last + ")");
                continue;
            }
            out.add(new Flag(f.record() - first, f.digest(), f.kind(), f.note(), f.fix()));
        }
        return out;
    }
}
