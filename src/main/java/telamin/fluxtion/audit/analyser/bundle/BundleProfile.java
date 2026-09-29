package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Evidence bundle v1, the profile member (spec-evidence-bundle-packaging.md r3 §4.2, EP-A9). WHAT leaves the sender's
 * machine is decided here, not in a skill: the spec's rule puts in the analyser anything a skill would have to guess
 * or re-implement, and the allow-list is both. A skill that filtered keys itself would be a second, drifting copy of
 * {@link SettingsShare}'s categories, and the first key it forgot would carry a source root or a key onto another
 * machine.
 *
 * <p>The member holds {@link #CATEGORIES} only: saved charts and named focuses, reports and walks, hidden columns. A
 * chart with an external series or external markers is LEFT OUT and named, because its CSV path would re-anchor
 * silently on the recipient's machine (§2).
 *
 * <p><b>No machine path leaves (review F2, spec r4 §4.2, EP-A9).</b> Two cases, because they are different things:
 * <ul>
 *   <li>a value that IS a path (a path-valued key, such as a report section's rolled-set {@code file}) refuses the
 *       export, naming the key: it is structure, and redacting it would silently break the reference;</li>
 *   <li>a path INSIDE prose (a narrative, a caption, a note: "we saw it in /Users/…/x.yaml") is REDACTED to
 *       {@link #REDACTED} and named in {@link Export#redacted()}, so the author sees exactly what was removed. Refusing
 *       ordinary writing would get this check turned off.</li>
 * </ul>
 * <p><b>Latin letters by explicit range, NOT {@code UNICODE_CHARACTER_CLASS}.</b> The blanket flag failed in
 * both directions at once. It widened {@code \w} inside the negative lookbehinds, so a path written against a
 * non-ASCII letter stopped matching AT ALL — {@code ログは/Users/greg/logs/x.yaml} exported whole and reported
 * nothing, worse than the half-redaction the flag was added to fix, and CJK prose has no inter-word spaces so
 * adjacency is the normal case there. It also widened the SEGMENT classes, so the same path swallowed the rest
 * of the sentence ({@code …q.yamlにあります}) and deleted the author's words. It also widened {@code \s}, so
 * {@link #WHOLE_PATH}'s {@code \S} tail stopped refusing a path containing a non-breaking space.
 * Adding {@code \u00C0-\u024F} — Latin-1 Supplement and Latin Extended-A/B — covers the accented usernames
 * and directories this is actually about ({@code démo}, {@code josé}) while leaving CJK and Cyrillic as the
 * prose they are, so a path touching them is redacted and stops where the prose resumes.
 *
 * <p><b>Historic note.</b> Turning the flag on wholesale
 * widened {@code \w} inside every negative lookbehind too, so a path written immediately after a non-ASCII
 * letter stopped matching AT ALL: {@code ログは/Users/greg/logs/x.yaml} exported whole and reported nothing,
 * where even the ASCII pattern had redacted it. CJK prose has no inter-word spaces, so adjacency is the
 * normal case there, and a silent total leak is worse than the half-leak the flag was added to fix. The
 * lookbehinds are pinned to ASCII; only the segment classes are widened. For the same reason
 * {@link #WHOLE_PATH} spells its tail as "not ASCII whitespace" instead of {@code \S}: the flag made
 * {@code \s} include U+00A0, so a path holding a non-breaking space — routine when pasted from a browser —
 * stopped being refused as a whole value.
 *
 * <p><b>Both patterns are Unicode-aware, and must stay that way.</b> Java's {@code \w} is ASCII-only unless
 * told otherwise, so {@code /home/démo/logs/x.yaml} redacted as far as the accent and left
 * {@code ‹path removed›émo/logs/x.yaml} — a reported redaction that still carries the path. Worse,
 * {@link #WHOLE_PATH} did not recognise {@code ~josé/logs/x.yaml} as a path at all, so a path-VALUED key with
 * an accented username was exported instead of refusing the bundle. A half-redaction is worse than none: it
 * tells the author the path was removed.
 *
 * <p><b>A digit-leading username is still a username.</b> The tilde form is three alternatives because a
 * {@code ~user} segment that must start with a letter silently stopped redacting {@code ~7dev/logs/x.yaml} and
 * {@code ~123/secret/a.yaml} — legal accounts wherever they are provisioned from employee numbers — while
 * {@code ~1/price} had to keep passing. One residual is accepted and cannot be removed: {@code ~123/secret},
 * a purely numeric user with a single extensionless segment, is indistinguishable from a ratio, so it is NOT
 * redacted in prose. {@link #WHOLE_PATH} still refuses it as a whole value.
 *
 * <p>A machine path here is absolute POSIX with at least two segments, home-relative ({@code ~/…}, {@code ~user/…}),
 * a Windows drive path with a segment, a UNC path, or a {@code file:} URI. Relative paths ({@code logs/uat/x.yaml}),
 * URLs, ratios, times and {@code and/or} are not: they name no machine. A segment is cut at whitespace, so a path
 * with a space in a directory name is redacted up to the space.
 */
public final class BundleProfile {

    private BundleProfile() {
    }

    public static final Set<SettingsShare.Category> CATEGORIES =
            EnumSet.of(SettingsShare.Category.GRAPHS, SettingsShare.Category.REPORTS, SettingsShare.Category.VIEW);

    /** What was written: the charts left out, the walks and reports that name a left-out chart, the paths redacted. */
    public record Export(List<String> leftOut, List<String> dangling, List<String> redacted) { }

    /** What a redacted path is replaced with, in the text the recipient reads. */
    public static final String REDACTED = "\u2039path removed\u203a";

    /** A value that is, as a whole, a machine path: refused, because it is structure. */
    static final Pattern WHOLE_PATH = Pattern.compile("^(?:/|~[/\\\\]|~$|~[\\w.\\-\\u00C0-\\u024F]+/|[A-Za-z]:[/\\\\]|\\\\\\\\|(?i:file):)[^ \\t\\n\\x0B\\f\\r]*$");

    /** A machine path INSIDE prose: redacted. Each alternative needs a real path shape, not just a slash or a colon. */
    static final Pattern EMBEDDED_PATH = Pattern.compile(String.join("|",
            "(?i:(?<![A-Za-z0-9_\\u00C0-\\u024F])file:/+[\\w.\\-\\u00C0-\\u024F~%@:/+]*)",                                      // file:///etc/x
            "(?<![A-Za-z0-9_.~:/\\\\\\-\\u00C0-\\u024F])/[\\w.\\-\\u00C0-\\u024F]+(?:/[\\w.\\-\\u00C0-\\u024F]+)+/?",                          // /Users/x/y, not a/b or https://h/p
            "(?<![A-Za-z0-9_/~\\u00C0-\\u024F])~(?:[\\w.\\-\\u00C0-\\u024F]*[A-Za-z_\\u00C0-\\u024F][\\w.\\-\\u00C0-\\u024F]*)?/[\\w.\\-\\u00C0-\\u024F]+(?:/[\\w.\\-\\u00C0-\\u024F]+)*/?",  // ~/x, ~alice/x, ~7dev/logs/x
            "(?<![A-Za-z0-9_/~\\u00C0-\\u024F])~[0-9][\\w.\\-\\u00C0-\\u024F]*/[\\w.\\-\\u00C0-\\u024F]+(?:/[\\w.\\-\\u00C0-\\u024F]+)+/?",                   // ~123/secret/a.yaml
            "(?<![A-Za-z0-9_/~\\u00C0-\\u024F])~[0-9][\\w.\\-\\u00C0-\\u024F]*/[\\w-]+\\.[A-Za-z][\\w.\\-\\u00C0-\\u024F]*",                  // ~123/notes.yaml
            "(?<![A-Za-z0-9_])[A-Za-z]:[\\\\/][\\w.$-]+(?:[\\\\/][\\w.$-]+)*[\\\\/]?",          // C:\\Users\\x, not C: or C:\\ alone
            "(?<![A-Za-z0-9_\\\\])\\\\\\\\[\\w.$-]+(?:\\\\[\\w.$-]+)+"));                          // \\\\server\\share

    /**
     * Write the allow-listed profile of {@code settings} to {@code out}. {@code settings} is the open project's
     * profile (a {@code *.fluxtion-settings} file) or, when no project is open, the analyser's own settings file.
     *
     * @throws IOException when {@code settings} cannot be read, {@code out} exists, or a kept value is path-shaped
     */
    public static Export export(Path settings, Path out) throws IOException {
        return export(settings, out, null);
    }

    /**
     * An excerpt's re-base (owner, 2026-09-28: capture takes an optional time window): records {@code first..last} of
     * the source become records {@code 0..} of the excerpt. The excerpt's own run basis and index (from
     * {@link BundleExcerpt#check}, the recipient's computation) replace the source's in every walk and report.
     */
    public record Rebase(int first, int last, List<String> runBasis, telamin.fluxtion.audit.analyser.analyser.index.LogIndex index) { }

    /** As {@link #export(Path, Path)}, re-basing every walk and report onto an excerpt when {@code rebase} is given. */
    public static Export export(Path settings, Path out, Rebase rebase) throws IOException {
        if (!Files.isRegularFile(settings)) throw new IOException("no settings file at " + settings);
        if (Files.exists(out)) throw new IOException("will not overwrite " + out);
        AppConfig c = read(settings);
        List<String> rebased = rebase == null ? List.of() : rebase(c, rebase);

        List<String> leftOut = new ArrayList<>(rebased);
        Set<String> gone = new TreeSet<>();
        c.savedGraphs.removeIf(g -> {
            boolean external = !g.external().isEmpty() || g.markers().stream().anyMatch(GraphSpec.MarkerSpec::isExternal);
            if (external) {
                leftOut.add("chart '" + g.name() + "' (external series or markers: their files are not in the bundle)");
                gone.add(g.name());
            }
            return external;
        });
        List<String> dangling = new ArrayList<>();
        for (WalkSpec w : c.walks) {
            for (int i = 0; i < w.steps().size(); i++) {
                WalkSpec.Step s = w.steps().get(i);
                String graph = s.view() == null ? null : s.view().graph();
                if (graph != null && gone.contains(graph)) {
                    dangling.add("walk '" + w.name() + "' step " + (i + 1) + " shows left-out chart '" + graph + "'");
                }
            }
        }
        // OA-3 (§7): a kept walk's dialogue was written against the whole log. Its step targets are re-based; the words
        // cannot be, so a record number in them would now name another record. Said, not silently shipped.
        if (rebase != null && rebase.first() > 0) {
            for (WalkSpec w : c.walks) {
                if (w.conversation() != null && !w.conversation().turns().isEmpty()) {
                    dangling.add("walk '" + w.name() + "' carries a conversation written against the whole log: record "
                            + "numbers in its words are not re-based (this excerpt's record 0 was record " + rebase.first() + ")");
                }
            }
        }
        for (var r : c.reports) {
            for (var s : r.sections()) {
                if (s.kind() == telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.Kind.CHART && gone.contains(s.ref())) {
                    dangling.add("report '" + r.name() + "' has a section on left-out chart '" + s.ref() + "'");
                }
            }
        }

        // A project root makes the export deterministic (no timestamp, no date line) and nothing in it is a path.
        String text = new SettingsShare("").export(c, CATEGORIES, out.toAbsolutePath().getParent(), null, null);
        Properties written = new Properties();
        written.load(new StringReader(text));
        List<String> redacted = new ArrayList<>();
        java.util.TreeMap<String, String> kept = new java.util.TreeMap<>();
        for (String key : new TreeSet<>(written.stringPropertyNames())) {
            String v = written.getProperty(key);
            if (WHOLE_PATH.matcher(v.trim()).matches()) {
                throw new IOException("the profile would carry a machine path as the value of " + key + " (" + v.trim()
                        + "); no machine path leaves in an evidence bundle, and a path-valued key cannot be redacted");
            }
            java.util.regex.Matcher m = EMBEDDED_PATH.matcher(v);
            StringBuilder b = new StringBuilder();
            int at = 0;
            while (m.find()) {
                int end = m.end();
                while (end > m.start() + 1 && v.charAt(end - 1) == '.') end--;     // a sentence's full stop is not the path's
                redacted.add(key + ": " + v.substring(m.start(), end));
                b.append(v, at, m.start()).append(REDACTED);
                at = end;
            }
            b.append(v.substring(at));
            kept.put(key, b.toString());
        }
        if (!redacted.isEmpty()) text = serialise(text, kept);        // untouched otherwise: the exporter's own bytes
        Files.writeString(out, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Export(List.copyOf(leftOut), List.copyOf(dangling), List.copyOf(redacted));
    }

    /** The exporter's comment lines, then every key in order, each escaped exactly as {@link Properties#store} does. */
    private static String serialise(String original, java.util.SortedMap<String, String> values) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : original.split("\n")) {
            if (!line.startsWith("#")) break;
            out.append(line).append('\n');
        }
        for (var e : values.entrySet()) {
            Properties one = new Properties();
            one.setProperty(e.getKey(), e.getValue());
            java.io.StringWriter w = new java.io.StringWriter();
            one.store(w, null);
            for (String line : w.toString().split("\n")) {
                if (!line.startsWith("#") && !line.isEmpty()) out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Shift every record reference by {@code r.first()} and give each walk and report the excerpt's own identity. A walk
     * or report that points at a record outside the excerpt cannot be re-based honestly, so it is LEFT OUT and named, as
     * a chart with external data is; so is a report whose table or series is derived by record index.
     */
    static List<String> rebase(AppConfig c, Rebase r) {
        List<String> leftOut = new ArrayList<>();
        String window = "records " + r.first() + ".." + r.last();
        List<WalkSpec> walks = new ArrayList<>();
        for (WalkSpec w : c.walks) {
            List<WalkSpec.Step> steps = new ArrayList<>();
            String outside = null;
            for (int i = 0; i < w.steps().size() && outside == null; i++) {
                WalkSpec.Step s = w.steps().get(i);
                WalkSpec.View v = s.view();
                Integer record = v == null ? null : v.record();
                if (record != null && (record < r.first() || record > r.last())) {
                    outside = "step " + (i + 1) + " shows record " + record;
                    break;
                }
                List<WalkSpec.Target> targets = new ArrayList<>();
                for (WalkSpec.Target t : s.targets()) {
                    var parsed = telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.parse(t.target());
                    if (parsed.ok() && parsed.target().family() == telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.Family.RECORDS_ROW) {
                        int n = parsed.target().number();
                        if (n < r.first() || n > r.last()) {
                            outside = "step " + (i + 1) + " points at record " + n;
                            break;
                        }
                        targets.add(new WalkSpec.Target("records:row:" + (n - r.first()), t.caption(), t.basis()));
                    } else {
                        targets.add(t);
                    }
                }
                WalkSpec.View shifted = v == null || record == null ? v
                        : new WalkSpec.View(v.tab(), v.filter(), record - r.first(), v.graph(), v.focus());
                steps.add(new WalkSpec.Step(s.caption(), shifted, targets, s.id(), s.through()));   // OA-3: bindings kept
            }
            if (outside != null) {
                leftOut.add("walk '" + w.name() + "' (" + outside + ", outside the excerpt's " + window + ")");
                continue;
            }
            walks.add(new WalkSpec(w.name(), w.title(), w.author(), w.createdAt(), w.updatedAt(),
                    refingerprint(w.fingerprint(), r), w.runBasis().isEmpty() ? w.runBasis() : r.runBasis(), steps, w.extras(),
                    w.conversation()));
        }
        c.walks.clear();
        c.walks.addAll(walks);

        List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports = new ArrayList<>();
        for (var rep : c.reports) {
            List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec> sections = new ArrayList<>();
            String outside = null;
            for (var s : rep.sections()) {
                if (s.call().containsKey("recordIndex")) {
                    outside = "a " + s.kind().name().toLowerCase(java.util.Locale.ROOT) + " section is derived by record index";
                    break;
                }
                if (s.recordIndex() >= 0) {
                    if (s.recordIndex() < r.first() || s.recordIndex() > r.last()) {
                        outside = "a section is on record " + s.recordIndex() + ", outside the excerpt's " + window;
                        break;
                    }
                    s = new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec(s.kind(),
                            s.recordIndex() - r.first(), s.file(), s.ref(), s.call(), s.text(), s.columns(), s.rowWhen(), s.rowWhenLabel());
                }
                sections.add(s);
            }
            if (outside != null) {
                leftOut.add("report '" + rep.name() + "' (" + outside + ")");
                continue;
            }
            reports.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec(rep.name(), rep.title(), rep.createdAt(),
                    rep.notes(), refingerprint(rep.fingerprint(), r), rep.filter(), sections));
        }
        c.reports.clear();
        c.reports.addAll(reports);
        return leftOut;
    }

    private static telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint refingerprint(
            telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint fp, Rebase r) {
        return fp == null ? null : telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint.of(
                r.index(), fp.logName(), fp.provenance(), fp.provenanceSource());
    }

    private static AppConfig read(Path settings) throws IOException {
        String name = settings.getFileName().toString();
        if (name.endsWith(".fluxtion-settings")) {
            AppConfig c = new AppConfig();
            var loaded = ProjectProfile.load(settings, c, new SettingsShare());
            if (!loaded.loaded()) throw new IOException(loaded.message());
            return c;
        }
        return new ConfigStore(settings).load();
    }
}
