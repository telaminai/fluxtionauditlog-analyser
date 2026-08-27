package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A project's settings, kept beside the project instead of in the one global config (M20).
 *
 * <p>The problem this solves: there is one config file, so working across several Fluxtion projects
 * means importing each project's settings on top of the last one's. Import is <b>additive</b> — right
 * for sharing a setup, wrong for switching between projects, because project A's source roots pile up
 * under project B's.
 *
 * <h2>The tier, and why it is five categories rather than seven</h2>
 *
 * <p>M15 already split settings into shareable and never-shared, and the brief describes the project
 * tier as "the M15 shareable whitelist". That is a useful shorthand and slightly too broad: the
 * whitelist has seven categories, and two of them — {@code ASSISTANT} caps and {@code LLM}
 * provider/model — are listed under <em>global</em> in the design spec's own tier table. They are
 * shareable because a colleague may want your assistant setup; they are not <em>project</em> facts.
 *
 * <p><b>Shareable and project-scoped are different questions</b>, and this class answers the second.
 * The five here are the ones the spec's project tier names: source roots, Maven repos, event
 * processors, saved graphs, hidden columns.
 *
 * <h2>Two tiering decisions the brief asked to be made and recorded</h2>
 *
 * <p>{@code graphmlFile} and {@code recentGraphml} arrived in M22, after the tiering was designed, and
 * both are arguably "which graph am I working on" — which travels with a project the way source roots
 * do. <b>Both stay GLOBAL</b>, following the spec rather than widening the boundary:
 *
 * <ul>
 *   <li>{@code graphmlFile} is the topology <em>currently open</em> — session state, exactly the same
 *       kind of thing as the loaded log. Open question O3 ("should switching projects re-open that
 *       project's last log?") was <b>deferred</b> precisely to avoid coupling session state to a
 *       profile, and a graphml is session state by the same argument. Deciding differently for the
 *       graph than for the log would make switching projects reopen half your workspace, which is the
 *       surprise O3 was avoiding.</li>
 *   <li>{@code recentGraphml} is a recent-files list, and the spec's global tier names "recent files"
 *       explicitly. A recent list is a fact about this machine's history, not about a project.</li>
 * </ul>
 *
 * <p>The boundary is therefore unchanged from the spec. That constraint exists to stop the boundary
 * drifting by accident; nothing here required moving it.
 *
 * <h2>Replace, not merge</h2>
 *
 * <p>Loading a profile <b>replaces</b> every project-scoped category, including ones the profile does
 * not mention. If project B's profile has no graphs, switching from A to B must leave you with no
 * graphs — not with A's. Applying only the categories present would reintroduce the pile-up this
 * milestone exists to remove.
 *
 * <p>Pure and headless: paths, policy and IO only. No Swing, no dialogs.
 */
public final class ProjectProfile {

    /** Where a project's profile lives, relative to the project root. One rule, one path (spec O2). */
    public static final String CANONICAL_RELATIVE = ".analyser/project.fluxtion-settings";

    /**
     * The categories a project owns. Everything else — the API key, theme, window bounds, recent files,
     * LLM and assistant settings, topology display prefs — stays global by not being here.
     */
    public static final Set<SettingsShare.Category> PROJECT_SCOPED = EnumSet.of(
            SettingsShare.Category.SOURCE_ROOTS,
            SettingsShare.Category.MAVEN_REPOS,
            SettingsShare.Category.EVENT_PROCESSORS,
            SettingsShare.Category.GRAPHS,
            SettingsShare.Category.REPORTS,
            SettingsShare.Category.VIEW,
            SettingsShare.Category.RUNBOOKS,       // M38.1: pointers are project context (D-C1 tier 1)
            SettingsShare.Category.VOCABULARY);    // M38.2: the glossary pointer, same rule

    private ProjectProfile() {
    }

    /** The canonical profile path for a project directory. */
    public static Path pathFor(Path projectDir) {
        return projectDir.resolve(CANONICAL_RELATIVE);
    }

    /**
     * What a RELATIVE path inside {@code file} is relative to (M35.10).
     *
     * <p>For the canonical profile — {@code <project>/.analyser/project.fluxtion-settings} — it is the
     * <b>project root</b>, not the {@code .analyser/} directory the file happens to sit in: a committed
     * profile is {@code .vscode/settings.json}'s kind of file, and nobody writes {@code ../src} in one
     * of those. The M19 bundle contract says {@code sourceRoot.0=src/main/java} lands at
     * {@code <bundle>/src/main/java}; until this method, {@link #load} handed the importer the file's
     * own directory and it landed at {@code <bundle>/.analyser/src/main/java} — a directory that does
     * not exist, found by opening a hand-written fixture during M35.8.
     *
     * <p>For any other {@code .fluxtion-settings} file — one exported and imported by hand from
     * wherever it was saved — it stays the file's own directory, which is the only sensible anchor a
     * loose file has. {@code null} when the file has no parent.
     */
    public static Path baseDirFor(Path file) {
        if (file == null) return null;
        Path dir = file.toAbsolutePath().normalize().getParent();
        if (dir == null) return null;
        Path dirName = dir.getFileName();
        boolean canonical = dirName != null && dirName.toString().equals(".analyser")
                && file.getFileName() != null
                && file.getFileName().toString().equals(Path.of(CANONICAL_RELATIVE).getFileName().toString());
        return canonical && dir.getParent() != null ? dir.getParent() : dir;
    }

    /**
     * The nearest project profile at or above {@code start}, or {@code null}.
     *
     * <p>Walks upwards so opening a log deep inside a repo still finds the profile at its root — which
     * is what makes the M19 zero-setup path work: download a bundle, open its log, and the profile
     * beside the repo root is found without anyone configuring anything.
     */
    public static Path findNear(Path start) {
        if (start == null) {
            return null;
        }
        Path dir = Files.isDirectory(start) ? start : start.getParent();
        while (dir != null) {
            Path candidate = dir.resolve(CANONICAL_RELATIVE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * The project-scoped slice of a config, so it can be put back.
     *
     * <p>Needed because the global config's project values are the <b>"no project" defaults</b>: the
     * first time a project is opened they are not deleted, and switching back to no project restores
     * them. Without a snapshot, opening a project once would silently consume the settings a user had
     * before they ever had projects.
     */
    public record Snapshot(List<String> sourceRoots,
                           List<String> mavenRepos,
                           boolean searchMavenRepos,
                           List<String> eventProcessorFqns,
                           String selectedEventProcessor,
                           List<GraphSpec> savedGraphs,
                           List<FocusSpec> namedFocuses,
                           List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports,
                           List<String> hiddenColumns,
                           boolean hiddenColumnsSet,
                           java.util.Map<String, String> runbooks,
                           String vocabularyPath) {

        public Snapshot {
            sourceRoots = List.copyOf(sourceRoots);
            mavenRepos = List.copyOf(mavenRepos);
            eventProcessorFqns = List.copyOf(eventProcessorFqns);
            savedGraphs = List.copyOf(savedGraphs);
            namedFocuses = List.copyOf(namedFocuses);
            reports = List.copyOf(reports);
            hiddenColumns = List.copyOf(hiddenColumns);
            runbooks = java.util.Map.copyOf(runbooks == null ? java.util.Map.of() : runbooks);
            vocabularyPath = vocabularyPath == null ? "" : vocabularyPath;
        }
    }

    public static Snapshot snapshot(AppConfig c) {
        return new Snapshot(c.sourceRoots, c.mavenRepos, c.searchMavenRepos, c.eventProcessorFqns,
                c.selectedEventProcessor, c.savedGraphs, c.namedFocuses, c.reports, c.hiddenColumns,
                c.hiddenColumnsSet, c.runbooks, c.vocabularyPath);
    }

    /** Put a snapshot back over the project-scoped categories, leaving global untouched. */
    public static void restore(Snapshot s, AppConfig into) {
        clearProjectScoped(into);
        into.sourceRoots.addAll(s.sourceRoots());
        into.mavenRepos.addAll(s.mavenRepos());
        into.searchMavenRepos = s.searchMavenRepos();
        into.eventProcessorFqns.addAll(s.eventProcessorFqns());
        into.selectedEventProcessor = s.selectedEventProcessor();
        into.savedGraphs.addAll(s.savedGraphs());
        into.namedFocuses.addAll(s.namedFocuses());
        into.reports.addAll(s.reports());
        into.hiddenColumns.addAll(s.hiddenColumns());
        into.hiddenColumnsSet = s.hiddenColumnsSet();
        into.runbooks.putAll(s.runbooks());
        into.vocabularyPath = s.vocabularyPath();
    }

    /**
     * Empty every project-scoped category. Public because "switch to no project" and "load a profile"
     * both need it, and a second copy of this list is a second thing to forget to update.
     */
    public static void clearProjectScoped(AppConfig c) {
        c.sourceRoots.clear();
        c.mavenRepos.clear();
        c.eventProcessorFqns.clear();
        c.savedGraphs.clear();
        c.namedFocuses.clear();
        c.reports.clear();
        c.runbooks.clear();
        c.vocabularyPath = "";
        c.hiddenColumns.clear();
        // the scalars belong to the same categories, so a replace that left them behind would carry
        // project A's selected event processor into project B — a class that may not exist there
        c.searchMavenRepos = true;
        c.selectedEventProcessor = "";
        c.hiddenColumnsSet = false;
    }

    /** What happened when a profile was loaded — never an exception, so startup cannot fail on it. */
    public record LoadResult(boolean loaded, String message) { }

    /**
     * Load {@code file} over {@code target}'s project-scoped categories, replacing them.
     *
     * <p>Never throws. A missing, unreadable or unparseable profile returns {@code loaded=false} with a
     * reason: a moved repository must degrade to "global only, and here is why", not to a dead app.
     */
    public static LoadResult load(Path file, AppConfig target, SettingsShare share) {
        if (file == null) {
            return new LoadResult(false, "no project file");
        }
        if (!Files.isRegularFile(file)) {
            return new LoadResult(false, "project settings not found: " + file);
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            return new LoadResult(false, "could not read " + file + ": " + e.getMessage());
        }
        try {
            // relative roots resolve against the PROJECT ROOT for the canonical profile (M19.2 as the
            // bundle contract meant it; M35.10 made it so) — which is what lets a committed profile use
            // repo-relative paths and still work on a teammate's machine
            SettingsShare.ImportPlan plan = share.preview(text, target, baseDirFor(file));
            clearProjectScoped(target);
            share.apply(plan, PROJECT_SCOPED, target);
            // A profile that names no Maven repo means "I did not say", not "never search one" — and an
            // empty list silently disables source lookup for every dependency. Our own writer always
            // emits the category, so this only catches hand-edited or partial profiles; it catches them
            // as a missing convenience rather than as a capability that vanished without a message.
            if (target.mavenRepos.isEmpty()) {
                target.mavenRepos.add(AppConfig.defaultMavenRepo());
            }
            // M38.1: a refused runbook entry is loud, not silent — the summary the importer would have seen
            // is the message the status bar shows, so a hand-edited or hostile pointer degrades to "dropped,
            // here is why" in front of the person who opened the project
            String rb = plan.summary().get(SettingsShare.Category.RUNBOOKS);
            String warn = rb != null && rb.contains("REFUSED") ? "  ·  ⚠ runbooks: " + rb : "";
            String vb = plan.summary().get(SettingsShare.Category.VOCABULARY);
            if (vb != null && vb.contains("REFUSED")) warn += "  ·  ⚠ vocabulary: " + vb;
            return new LoadResult(true, "project loaded: " + file + warn);
        } catch (RuntimeException e) {
            return new LoadResult(false, "could not load " + file + ": " + e.getMessage());
        }
    }

    /**
     * Serialise only the project-scoped categories — the API key cannot appear, by construction — in
     * the form a COMMITTED file needs (M35.11): paths under the project written project-relative
     * against {@link #baseDirFor}, no timestamp, no date comment. Loading this text and writing it
     * back yields the same bytes.
     */
    public static String write(AppConfig c, SettingsShare share, Path file) {
        return share.export(c, PROJECT_SCOPED, baseDirFor(file));
    }

    /**
     * Write a profile, creating {@code .analyser/} if needed — and NOT writing at all when the file
     * already holds exactly this text (M35.11). Opening a project and switching away used to rewrite
     * its committed profile with absolute paths and a fresh timestamp: a diff on every teammate's
     * machine, asked for by nobody. Now a no-op edit is a no-op write, and mtime is left alone too.
     *
     * @return true if the file was written, false if it already held this content
     */
    public static boolean save(Path file, AppConfig c, SettingsShare share) throws IOException {
        String text = write(c, share, file);
        if (Files.isRegularFile(file)) {
            try {
                if (Files.readString(file).equals(text)) return false;
            } catch (IOException ignored) {
                // unreadable: fall through and write — the write will report its own failure
            }
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, text);
        return true;
    }

    /** Most-recent-first, de-duplicated, capped — the recent-projects index (spec O2). */
    public static void addRecent(List<String> recents, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        seen.add(path);
        seen.addAll(recents);
        List<String> capped = new ArrayList<>(seen);
        while (capped.size() > 10) {
            capped.remove(capped.size() - 1);
        }
        recents.clear();
        recents.addAll(capped);
    }
}
