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
 * silently on the recipient's machine (§2). Any remaining value that is shaped like a machine path refuses the export,
 * naming the key: the allow-list is meant to hold no path at all, and if it ever does, it must fail loudly here rather
 * than resolve against the wrong home over there.
 */
public final class BundleProfile {

    private BundleProfile() {
    }

    public static final Set<SettingsShare.Category> CATEGORIES =
            EnumSet.of(SettingsShare.Category.GRAPHS, SettingsShare.Category.REPORTS, SettingsShare.Category.VIEW);

    /** What was written: the charts left out, the walks and reports that name a left-out chart. */
    public record Export(List<String> leftOut, List<String> dangling) { }

    /** An absolute POSIX or Windows path, a home-relative path, or a file URI. */
    private static final Pattern PATH_SHAPED = Pattern.compile("^(/|~[/\\\\]|~$|[A-Za-z]:[/\\\\]|\\\\\\\\|file:)");

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
        for (String key : new TreeSet<>(written.stringPropertyNames())) {
            String v = written.getProperty(key).trim();
            if (PATH_SHAPED.matcher(v).find()) {
                throw new IOException("the profile would carry a machine path in " + key + " (" + v
                        + "); an evidence bundle's profile holds no paths");
            }
        }
        Files.writeString(out, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Export(List.copyOf(leftOut), List.copyOf(dangling));
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
