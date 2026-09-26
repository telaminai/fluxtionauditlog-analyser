package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

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
     * The nearest project whose root holds {@code path} — the first ancestor carrying the canonical profile — or
     * empty. Used only to say "this file belongs to a project; open it", never to grant a read: a refusal that
     * named source_root instead led models to authorise the whole project directory, wider than the project's own
     * roots.
     */
    public static Optional<Path> enclosingProject(Path path) {
        if (path == null) return Optional.empty();
        Path dir = path.toAbsolutePath().normalize().getParent();
        for (int depth = 0; dir != null && depth < 64; depth++, dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(CANONICAL_RELATIVE))) return Optional.of(dir);
        }
        return Optional.empty();
    }

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
            SettingsShare.Category.VOCABULARY,     // M38.2: the glossary pointer, same rule
            SettingsShare.Category.ENVIRONMENTS,   // M38.3: which system a log from here came from
            SettingsShare.Category.ANALYSES,       // M38.4: tier 2 — the team's repeatable analyses
            SettingsShare.Category.DESTINATIONS);  // M38.5: where this project's reports go

    /**
     * Edit-loop spec §E: the profile's <b>creation nonce</b> — which profile this is, beyond where it lives.
     * Session recovery is keyed by the profile's path, and a project deleted and recreated at that path must
     * not inherit the old project's offer, so each captured session also records this nonce.
     *
     * <p>Lifecycle, chosen and pinned by tests:
     * <ul>
     *   <li><b>minted</b> when {@link #save} creates the file (a new project, or a fork via save-as);</li>
     *   <li><b>kept</b> by every later save — the writer copies it from the file it overwrites;</li>
     *   <li><b>adopted</b> by a profile that has none (written before this key existed, or shipped in a
     *       bundle): the first save that changes the file anyway adds one. A no-op open still writes nothing
     *       (M35.11), so such a profile can stay without a nonce, and its sessions are then withheld as
     *       captured by an unknown profile rather than guessed;</li>
     *   <li><b>never shared</b>: a share export carries no nonce and an import ignores one, so importing a
     *       colleague's settings does not change which profile this is;</li>
     *   <li><b>copied with the file</b>: a byte copy, a git clone or an archive of a profile that already has
     *       a nonce is the same profile. At another path it is a different recovery key anyway; placed at the
     *       same path it is offered that path's session, still subject to the input-byte checks.</li>
     * </ul>
     * It is a random value with no filesystem dependence, so the same rules hold on every OS.
     */
    public static final String NONCE_KEY = "profileNonce";

    private ProjectProfile() {
    }

    /** The creation nonce recorded in {@code file}, or empty — no file, unreadable, no key, or not a nonce. */
    public static Optional<String> nonce(Path file) {
        if (file == null || !Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.ofNullable(nonceIn(Files.readString(file)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The valid creation nonce in profile text, or null. */
    static String nonceIn(String text) {
        java.util.Properties properties = new java.util.Properties();
        try (var reader = new java.io.StringReader(text == null ? "" : text)) {
            properties.load(reader);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return validNonce(properties.getProperty(NONCE_KEY));
    }

    private static String validNonce(String value) {
        return value != null && value.matches("[A-Za-z0-9-]{8,64}") ? value : null;
    }

    /** The canonical profile path for a project directory. */
    public static Path pathFor(Path projectDir) {
        return projectDir.resolve(CANONICAL_RELATIVE);
    }

    /**
     * Is {@code fileName} a project profile kept in a project's {@code .analyser/} directory?
     *
     * <p>Two forms, both owned by the project: the canonical {@code project.fluxtion-settings}, and a
     * NAMED profile {@code project.<name>.fluxtion-settings} saved beside it — several set-ups for one
     * project that you switch between (e.g. {@code project.ws-feed.fluxtion-settings}). A file with any
     * other name that happens to sit in {@code .analyser/} is not a profile: it is a loose settings file
     * someone saved there, and keeps its own directory as its anchor.
     */
    public static boolean isProjectProfileFileName(String fileName) {
        return fileName != null
                && fileName.startsWith("project.")
                && fileName.endsWith(".fluxtion-settings");
    }

    /**
     * What a RELATIVE path inside {@code file} is relative to (M35.10).
     *
     * <p>For a project profile — {@code <project>/.analyser/project.fluxtion-settings} or a named
     * {@code <project>/.analyser/project.<name>.fluxtion-settings} — it is the <b>project root</b>, not
     * the {@code .analyser/} directory the file happens to sit in: a committed profile is
     * {@code .vscode/settings.json}'s kind of file, and nobody writes {@code ../src} in one of those.
     * The M19 bundle contract says {@code sourceRoot.0=src/main/java} lands at
     * {@code <bundle>/src/main/java}; until this method, {@link #load} handed the importer the file's
     * own directory and it landed at {@code <bundle>/.analyser/src/main/java} — a directory that does
     * not exist, found by opening a hand-written fixture during M35.8.
     *
     * <p>Named profiles were matched on the canonical file NAME alone until now, so every relative path
     * in one anchored a directory too deep: {@code src/main/java} became
     * {@code <project>/.analyser/src/main/java} and {@code ../sibling} became {@code <project>/sibling}.
     * The visible symptom was a profile in which every event processor reported "source not found" and
     * every runbook {@code exists:false}, while the canonical profile beside it — same roots, same
     * values — resolved perfectly. The anchor is the profile's ROLE, not its exact file name.
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
        Path fileName = file.getFileName();
        boolean projectProfile = dirName != null && dirName.toString().equals(".analyser")
                && fileName != null && isProjectProfileFileName(fileName.toString());
        return projectProfile && dir.getParent() != null ? dir.getParent() : dir;
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
                           List<ProcessorDeclaration> processorDeclarations,
                           List<GraphSpec> savedGraphs,
                           List<FocusSpec> namedFocuses,
                           List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports,
                           List<String> hiddenColumns,
                           boolean hiddenColumnsSet,
                           java.util.Map<String, Runbooks.Pointer> runbooks,
                           String vocabularyPath,
                           List<Environment> environments,
                           String defaultEnvironment,
                           List<AnalysisSpec> analyses,
                           List<ReportDestination> reportDestinations,
                           String workspaceRoot) {

        public Snapshot {
            sourceRoots = List.copyOf(sourceRoots);
            mavenRepos = List.copyOf(mavenRepos);
            eventProcessorFqns = List.copyOf(eventProcessorFqns);
            processorDeclarations = List.copyOf(processorDeclarations);
            savedGraphs = List.copyOf(savedGraphs);
            namedFocuses = List.copyOf(namedFocuses);
            reports = List.copyOf(reports);
            hiddenColumns = List.copyOf(hiddenColumns);
            runbooks = java.util.Map.copyOf(runbooks == null ? java.util.Map.of() : runbooks);
            vocabularyPath = vocabularyPath == null ? "" : vocabularyPath;
            environments = List.copyOf(environments == null ? List.of() : environments);
            defaultEnvironment = defaultEnvironment == null ? "" : defaultEnvironment;
            analyses = List.copyOf(analyses == null ? List.of() : analyses);
            reportDestinations = List.copyOf(reportDestinations == null ? List.of() : reportDestinations);
            workspaceRoot = workspaceRoot == null ? "" : workspaceRoot;
        }
    }

    public static Snapshot snapshot(AppConfig c) {
        return new Snapshot(c.sourceRoots, c.mavenRepos, c.searchMavenRepos, c.eventProcessorFqns,
                c.selectedEventProcessor, c.processorDeclarations, c.savedGraphs, c.namedFocuses, c.reports, c.hiddenColumns,
                c.hiddenColumnsSet, c.runbooks, c.vocabularyPath, c.environments, c.defaultEnvironment, c.analyses,
                c.reportDestinations, c.workspaceRoot);
    }

    /** Put a snapshot back over the project-scoped categories, leaving global untouched. */
    public static void restore(Snapshot s, AppConfig into) {
        clearProjectScoped(into);
        into.sourceRoots.addAll(s.sourceRoots());
        into.mavenRepos.addAll(s.mavenRepos());
        into.searchMavenRepos = s.searchMavenRepos();
        into.eventProcessorFqns.addAll(s.eventProcessorFqns());
        into.selectedEventProcessor = s.selectedEventProcessor();
        into.processorDeclarations.addAll(s.processorDeclarations());
        into.savedGraphs.addAll(s.savedGraphs());
        into.namedFocuses.addAll(s.namedFocuses());
        into.reports.addAll(s.reports());
        into.hiddenColumns.addAll(s.hiddenColumns());
        into.hiddenColumnsSet = s.hiddenColumnsSet();
        into.runbooks.putAll(s.runbooks());
        into.vocabularyPath = s.vocabularyPath();
        into.environments.addAll(s.environments());
        into.defaultEnvironment = s.defaultEnvironment();
        into.analyses.addAll(s.analyses());
        into.reportDestinations.addAll(s.reportDestinations());
        into.workspaceRoot = s.workspaceRoot();
    }

    /**
     * Empty every project-scoped category. Public because "switch to no project" and "load a profile"
     * both need it, and a second copy of this list is a second thing to forget to update.
     */
    public static void clearProjectScoped(AppConfig c) {
        c.sourceRoots.clear();
        c.mavenRepos.clear();
        c.eventProcessorFqns.clear();
        c.processorDeclarations.clear();
        c.savedGraphs.clear();
        c.namedFocuses.clear();
        c.reports.clear();
        c.runbooks.clear();
        c.vocabularyPath = "";
        c.environments.clear();
        c.defaultEnvironment = "";
        c.analyses.clear();
        c.reportDestinations.clear();
        c.workspaceRoot = "";
        c.hiddenColumns.clear();
        // the scalars belong to the same categories, so a replace that left them behind would carry
        // project A's selected event processor into project B — a class that may not exist there
        c.searchMavenRepos = true;
        c.selectedEventProcessor = "";
        c.hiddenColumnsSet = false;
    }

    /**
     * What happened when a profile was loaded — never an exception, so startup cannot fail on it.
     * {@code nonce} is the loaded profile's creation nonce ({@link #NONCE_KEY}), or null when it has none.
     */
    public record LoadResult(boolean loaded, String message, String nonce) {
        public LoadResult(boolean loaded, String message) {
            this(loaded, message, null);
        }
    }

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
            String eb = plan.summary().get(SettingsShare.Category.ENVIRONMENTS);
            if (eb != null && eb.contains("REFUSED")) warn += "  ·  ⚠ environments: " + eb;
            String an = plan.summary().get(SettingsShare.Category.ANALYSES);
            if (an != null && an.contains("REFUSED")) warn += "  ·  ⚠ analyses: " + an;
            String de = plan.summary().get(SettingsShare.Category.DESTINATIONS);
            if (de != null && de.contains("REFUSED")) warn += "  ·  ⚠ destinations: " + de;
            String sr = plan.summary().get(SettingsShare.Category.SOURCE_ROOTS);
            if (sr != null && sr.contains("REFUSED")) warn += "  ·  ⚠ source roots: " + sr;
            // M19 D-R4: this similarly named key is deliberately NOT the inert provenance fact.
            // A project travels between people and must never redirect where a generator fetches the
            // instructions an agent will read. It was ignored by preview already; name the refusal.
            java.util.Properties declared = new java.util.Properties();
            try (var reader = new java.io.StringReader(text)) { declared.load(reader); }
            if (declared.getProperty("skills.source") != null) {
                warn += "  ·  ⚠ skills.source REFUSED — build/release input, never a project setting";
            }
            return new LoadResult(true, "project loaded: " + file + warn, validNonce(declared.getProperty(NONCE_KEY)));
        } catch (RuntimeException | IOException e) {
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
        return write(c, share, file, false);
    }

    /** {@code adopt}: give a profile that has no creation nonce one now (see {@link #NONCE_KEY}). */
    private static String write(AppConfig c, SettingsShare share, Path file, boolean adopt) {
        // M38.7: carry over what this version does not understand — the file may have been written by a newer one
        java.util.Properties previous = null;
        if (file != null && Files.isRegularFile(file)) {
            previous = new java.util.Properties();
            try (var r = Files.newBufferedReader(file)) {
                previous.load(r);
                // KnownKeys preserves unknown future facts. This one is not future: the M19 contract
                // explicitly forbids it in a project, while retaining the inert skills.provenance key.
                previous.remove("skills.source");
            } catch (IOException | RuntimeException e) {
                previous = null;            // unreadable: nothing to preserve, and load() will say so on its own path
            }
        }
        // §E: the creation nonce is kept from the file being overwritten, minted when the file is created
        String nonce = previous == null ? null : validNonce(previous.getProperty(NONCE_KEY));
        if (nonce == null && file != null && (adopt || !Files.exists(file))) {
            nonce = java.util.UUID.randomUUID().toString();
        }
        return share.export(c, PROJECT_SCOPED, baseDirFor(file), previous, nonce);
    }

    /**
     * The inert, value-free identity the bundle generator declared for its vendored skill snapshot.
     * Invalid or credential-capable shapes are absent rather than echoed to context/the Project panel.
     */
    public static Optional<String> skillsProvenance(Path file) {
        if (file == null || !Files.isRegularFile(file)) return Optional.empty();
        java.util.Properties properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        String value = properties.getProperty("skills.provenance");
        if (value == null) return Optional.empty();
        value = value.trim();
        if (value.equals("none")) return Optional.of(value);
        if (value.length() > 300 || value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f)) {
            return Optional.empty();
        }
        int revisionAt = value.lastIndexOf('@');
        if (revisionAt <= 0 || revisionAt == value.length() - 1) return Optional.empty();
        String source = value.substring(0, revisionAt);
        String revision = value.substring(revisionAt + 1);
        if (!revision.matches("[A-Za-z0-9._-]{1,120}")) return Optional.empty();
        if (source.equals("canonical") || source.equals("local")) return Optional.of(value);
        if (!source.startsWith("mirror:")) return Optional.empty();
        try {
            java.net.URI uri = java.net.URI.create(source.substring("mirror:".length()));
            boolean safe = ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null;
            return safe ? Optional.of(value) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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
            // a real change to a profile with no creation nonce: adopt one now, never on a no-op open
            if (nonceIn(text) == null) text = write(c, share, file, true);
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
