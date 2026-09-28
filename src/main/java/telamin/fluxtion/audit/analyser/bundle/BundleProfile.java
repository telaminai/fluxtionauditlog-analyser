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
 * A machine path here is absolute POSIX with at least two segments, home-relative ({@code ~/…}, {@code ~user/…}),
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
    static final Pattern WHOLE_PATH = Pattern.compile("^(?:/|~[/\\\\]|~$|~[\\w.-]+/|[A-Za-z]:[/\\\\]|\\\\\\\\|(?i:file):)\\S*$");

    /** A machine path INSIDE prose: redacted. Each alternative needs a real path shape, not just a slash or a colon. */
    static final Pattern EMBEDDED_PATH = Pattern.compile(String.join("|",
            "(?i:\\bfile:/+[\\w.~%@:/+-]*)",                                      // file:///etc/x
            "(?<![\\w.~:/\\\\-])/[\\w.-]+(?:/[\\w.-]+)+/?",                          // /Users/x/y, not a/b or https://h/p
            "(?<![\\w/~])~[\\w.-]*/[\\w.-]+(?:/[\\w.-]+)*/?",                        // ~/x, ~user/x, not ~5%
            "(?<![\\w])[A-Za-z]:[\\\\/][\\w.$-]+(?:[\\\\/][\\w.$-]+)*[\\\\/]?",          // C:\\Users\\x, not C: or C:\\ alone
            "(?<![\\w\\\\])\\\\\\\\[\\w.$-]+(?:\\\\[\\w.$-]+)+"));                          // \\\\server\\share

    /**
     * Write the allow-listed profile of {@code settings} to {@code out}. {@code settings} is the open project's
     * profile (a {@code *.fluxtion-settings} file) or, when no project is open, the analyser's own settings file.
     *
     * @throws IOException when {@code settings} cannot be read, {@code out} exists, or a kept value is path-shaped
     */
    public static Export export(Path settings, Path out) throws IOException {
        if (!Files.isRegularFile(settings)) throw new IOException("no settings file at " + settings);
        if (Files.exists(out)) throw new IOException("will not overwrite " + out);
        AppConfig c = read(settings);

        List<String> leftOut = new ArrayList<>();
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
