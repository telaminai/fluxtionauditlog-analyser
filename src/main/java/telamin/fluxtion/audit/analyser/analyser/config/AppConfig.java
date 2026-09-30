package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.ArrayList;
import java.util.List;

/**
 * User configuration (spec §11). Persisted as cleartext properties under
 * {@code ~/.fluxtion-analyser/config} — a local single-user convenience tool, so API keys are stored
 * in the clear by design. All date/time rendering is UTC.
 */
public final class AppConfig {

    public String logFile;
    /**
     * The topology showing when the app last closed. NOT reopened on the next start -- this and
     * {@link #logFile} are chooser defaults and recent-list fodder. A restored project is offered
     * its logs and topologies instead (O3), which keeps the choice with the person.
     */
    public String graphmlFile;
    public final List<String> sourceRoots = new ArrayList<>();
    public String llmProvider = "anthropic";     // anthropic | openai
    public String llmModel = "";
    public String llmBaseUrl = "";
    public String apiKey = "";                    // cleartext (by decision)
    public final List<String> eventProcessorFqns = new ArrayList<>();
    public final List<ProcessorDeclaration> processorDeclarations = new ArrayList<>();
    public String selectedEventProcessor = "com.acme.marketmaker.strategy.DemoMarketMakerStrategy";
    public int memoryThresholdMb = 500;

    /** Local Maven repositories searched for {@code *-sources.jar} when a class isn't under a source root. */
    public final List<String> mavenRepos = new ArrayList<>(List.of(defaultMavenRepo()));
    public boolean searchMavenRepos = true;   // Settings shows the inverse ("don't search local repos")

    public static String defaultMavenRepo() {
        return java.nio.file.Path.of(System.getProperty("user.home"), ".m2", "repository").toString();
    }

    public String awsProfile = "";        // for s3:// loads via the aws CLI
    public String awsRegion = "";
    public String theme = "Light";        // FlatLaf theme: Light | Dark | IntelliJ | Darcula
    public final List<String> recentFiles = new ArrayList<>();

    /** Record-table columns hidden by default (user-toggleable, persisted). */
    public final List<String> hiddenColumns = new ArrayList<>(List.of(
            "eventTime", "groupingId", "eventToString", "endTime"));
    public boolean hiddenColumnsSet = false;   // distinguishes "never configured" from "user cleared all"
    /** Event-type rail panel collapsed — it costs 240px of a window whose job is showing wide records. */
    public boolean eventFilterCollapsed = false;
    /** M37: the Project panel (what is in force) collapsed. Default SHOWN — it must be seen once to be looked for later. */
    public boolean projectPanelCollapsed = false;
    /** M37: divider of the west column's vertical split (Event types above, Project below); -1 = never dragged. */
    public int westDivider = -1;
    /** M37: width of the whole west column (rail + panels) — the user drags it. 340 (review F2): at 280 the panel elided the verdicts it exists to state. */
    public int westWidth = 340;

    /**
     * Topology display preferences: layout spacing as a percentage, and label point size.
     *
     * <p>Deliberately <b>not</b> in any {@code SettingsShare.Category}, so they are never exported. They
     * are a fact about this screen and these eyes — a shared setup carrying someone else's text size is
     * a nuisance, not a convenience — and the whitelist is opt-in, so leaving them out of it is the whole
     * mechanism. Same reasoning as the theme.
     */
    public int topologySpacingPercent = 100;
    public int topologyTextSize = 11;
    /** Zoom and pan of the topology canvas; 0 zoom means "not saved yet — fit the graph instead". */
    public double topologyZoom;
    public double topologyPanX;
    public double topologyPanY;
    /** "TOP_DOWN" or "LEFT_RIGHT". */
    public String topologyOrientation = "TOP_DOWN";
    /** Topology: does the source pane follow what you click, or do you drive it yourself? */
    public boolean topologySyncSource = true;

    /** Everything under "how the topology is displayed" — cleared as a group from Settings ▸ History. */
    public void clearTopologyView() {
        topologySpacingPercent = 100;
        topologyTextSize = 11;
        topologyZoom = 0;
        topologyPanX = 0;
        topologyPanY = 0;
        topologyOrientation = "TOP_DOWN";
        topologySyncSource = true;
    }

    /** Saved graphs: one entry per graph tab, each a list of series encoded as {@code instanceIdkey}. */
    public final List<GraphSpec> savedGraphs = new ArrayList<>();

    // Assistant actions (M10): the in-process executor runs actions from the model's replies and feeds
    // results back, bounded by the round/per-reply caps. REST transport (default off) lands in slice 4.
    public boolean assistantActionsInProcess = true;
    public boolean assistantActionsRest = false;   // localhost REST transport (opt-in; §5.2)
    public int maxActionRounds = 3;
    public int maxActionsPerReply = 20;
    /** OA-1: an onboard turn's action budget across all its rounds — independent of the per-reply cap (spec §4.2). */
    public int maxActionsPerTurn = 30;

    // M42: machine-local setup reminders only. They intentionally hold neither the endpoint/token nor an
    // absolute launch path: a client re-discovers this app's fresh loopback endpoint through the bridge.
    public String mcpSetupTarget = "";
    public String mcpLauncherIdentity = "";
    public boolean mcpCodexRegistrationInstalled = false;
    public boolean mcpClaudeRegistrationInstalled = false;

    // B1 (review_handoff_16_aug_2026): verb-initiated file writes (screenshot/report) are OPT-IN and
    // confined to one directory — the socket's out-of-box promise stays "nothing outside the loaded log".
    // Machine-scoped (like display prefs): stays GLOBAL under M20 tiering, excluded from SettingsShare.
    public boolean assistantExports = false;
    public String assistantExportDir = "";

    /**
     * Where THIS PROJECT would like the exchange directory to be — project-relative, no {@code ..}
     * (#21). Project-tier, and it says WHERE, never WHETHER: {@link #assistantExports} stays machine
     * tier, so opening a project can widen no permission. See {@link ExchangeDir}.
     */
    public String projectExchangeDir = "";

    /**
     * The project profile currently active, or blank for none (M20).
     *
     * <p>GLOBAL and deliberately so: which project this machine last had open is a fact about the
     * machine, and a profile that recorded which profile was active would be circular. A missing file
     * here clears the pointer with a status note rather than failing startup — a moved repository must
     * not stop the app opening.
     */
    public String activeProjectPath = "";

    /** Recently opened project profiles, most-recent first — the switcher's list (spec O2). */
    public final List<String> recentProjects = new ArrayList<>();

    /** Named topology focuses (M27.3) — project-tier, persisted with the saved graphs. */
    public final List<FocusSpec> namedFocuses = new ArrayList<>();

    /**
     * The focus this PROJECT says to start at — "look here first", named among {@link #namedFocuses}.
     * Project tier, rides the GRAPHS category, and travels to a colleague: it is the repository's
     * opinion about where its own topology is best entered, not a record of anybody's session.
     */
    public String defaultFocus = "";

    /** Where the Facts column's divider sits between the event-type checklist and the records. */
    public int eventTypesDivider;

    /** Whether the "This machine" panel under Context is collapsed. Machine tier, like the panel. */
    public boolean machinePanelCollapsed = false;

    /**
     * The focus each project was last left on, keyed by its profile path. MACHINE tier, like every
     * other "what was I looking at" — a colleague's checkout must not inherit your view, which is the
     * same boundary that keeps the open log and topology out of a profile (O3).
     *
     * <p>Kept apart from {@link #defaultFocus} deliberately: one is the project's advice, the other is
     * your history, and they answer different questions. Yours wins when both exist, because it is
     * the more recent intent; the project's is what a fresh machine gets.
     */
    public final java.util.Map<String, String> lastFocusByProject = new java.util.LinkedHashMap<>();

    /**
     * Investigation reports (M33.4) — project-tier, their OWN share category (D-I4): a shared report
     * carries prose an agent wrote about your data, a different cargo from key names and formulas.
     */
    public final List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports = new ArrayList<>();
    /**
     * PR #33: reports deleted from any project, restorable (see {@link ReportBin}). MACHINE tier, like the recent
     * lists: never written to a project profile or an export, so a deleted report is never committed or shared.
     */
    public final List<DeletedReport> deletedReports = new ArrayList<>();
    /** M69: saved spotlight walks — project-tier, beside reports in the REPORTS category (the M69 walk spec, §3.1). */
    public final List<telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec> walks = new ArrayList<>();
    /** M69: walks deleted from any project, restorable (see {@link WalkBin}). MACHINE tier, like the report bin. */
    public final List<DeletedWalk> deletedWalks = new ArrayList<>();
    /** M38.1: runbook POINTERS — name → project-relative path (never contents). Project-scoped; see {@link Runbooks}. */
    /** M38.1/M43.2: name → pointer (path + optional description). ONE map, because a parallel
     *  description map is two things that can disagree about which runbooks exist. */
    public final java.util.Map<String, Runbooks.Pointer> runbooks = new java.util.LinkedHashMap<>();
    /** M38.2: the domain glossary — a POINTER to a markdown file in the repository (project-relative), or blank. */
    public String vocabularyPath = "";
    /** M38.3: the environments this project declares (name → §E provenance, optional log directory). Project-scoped. */
    public final List<Environment> environments = new ArrayList<>();
    /** M38.3: the environment a log falls under when no logDir matches and nobody declared one; blank = none. */
    public String defaultEnvironment = "";
    /** M38.4: repeatable analyses — named analyser-verb sequences with their rationale (tier 2). Project-scoped. */
    public final List<AnalysisSpec> analyses = new ArrayList<>();
    /** M38.5: where reports are PUBLISHED — a place, never a credential (D-C6). The analyser states it; the publisher acts. */
    public final List<ReportDestination> reportDestinations = new ArrayList<>();
    /** M38.6 D-C9: the workspace anchor — '..', '../..' — at or above the project root; blank = none. Project-scoped, rides SOURCE_ROOTS. */
    public String workspaceRoot = "";

    /**
     * A bundle this machine has opened: where the file is, what it verified as, and the first line of its
     * NOTES.md. MACHINE tier, like the other recent lists — never written to a project profile or an export.
     *
     * <p>The notes line is stored rather than re-read, so the list can say what each bundle CLAIMS without
     * opening five zips to draw a panel. It is what the sender wrote; it is not evidence of anything.
     *
     * @param path     the {@code .fexp} as it was opened; it may since have moved or been deleted
     * @param identity {@code sha256:} of the manifest at the time it was opened
     * @param notes    the first line of {@code notes/NOTES.md}, or blank
     */
    public record RecentBundle(String path, String identity, String notes, List<String> sourceRoots) {
        /** A bundle nobody has anchored yet — the shape every pre-#75 caller uses. */
        public RecentBundle(String path, String identity, String notes) {
            this(path, identity, notes, List.of());
        }

        public RecentBundle {
            sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
        }
    }

    /** Evidence bundles opened on this machine, most-recent first (#73). */
    public final List<RecentBundle> recentBundles = new ArrayList<>();

    /**
     * Record an opened bundle, newest first and de-duplicated by path. An anchor the person already chose for
     * this bundle SURVIVES a reopen — otherwise every reopen would ask again, which is the thing #75 is for.
     */
    public void addRecentBundle(String path, String identity, String notes) {
        if (path == null || path.isBlank()) return;
        List<String> anchored = bundleSourceRoots(path);
        recentBundles.removeIf(b -> b.path().equals(path));
        recentBundles.add(0, new RecentBundle(path, identity == null ? "" : identity,
                notes == null ? "" : notes, anchored));
        while (recentBundles.size() > 25) recentBundles.remove(recentBundles.size() - 1);
    }

    /** The source trees this machine anchored to that bundle, or empty — #75. */
    public List<String> bundleSourceRoots(String path) {
        if (path == null) return List.of();
        return recentBundles.stream().filter(b -> b.path().equals(path))
                .map(RecentBundle::sourceRoots).findFirst().orElse(List.of());
    }

    /**
     * Remember where the code for that bundle lives, so reopening it does not ask again.
     *
     * @return false when there is no such bundle to remember against. The caller must not ignore that:
     *         a silent miss is how this feature looked like it worked while recording nothing.
     */
    public boolean rememberBundleSourceRoots(String path, List<String> roots) {
        if (path == null || roots == null) return false;
        for (int i = 0; i < recentBundles.size(); i++) {
            var b = recentBundles.get(i);
            if (b.path().equals(path)) {
                recentBundles.set(i, new RecentBundle(b.path(), b.identity(), b.notes(), roots));
                return true;
            }
        }
        return false;
    }

    /** Recent search terms (most-recent first), for the search box history/autocomplete. */
    public final List<String> searchHistory = new ArrayList<>();

    /** The app version last run, to show a what's-new note after an upgrade (M16 §7). */
    public String lastRunVersion = "";

    public void addSearch(String term) {
        if (term == null || term.isBlank()) return;
        searchHistory.remove(term);
        searchHistory.add(0, term);
        while (searchHistory.size() > 25) searchHistory.remove(searchHistory.size() - 1);
    }

    // window bounds (-1 = unset)
    public int windowX = -1, windowY = -1, windowW = 1200, windowH = 800;
    /**
     * OA-2: the assistant's own window — whether it was last popped out, and where. Machine tier (a screen layout is a
     * property of this machine, never of a project or a bundle); restored only within today's usable screen bounds.
     */
    public boolean assistantPoppedOut = false;
    public int assistantX = -1, assistantY = -1, assistantW = 520, assistantH = 720;

    public void addRecent(String path) {
        addRecent(recentFiles, path);
    }

    /** Recently opened {@code .graphml} topologies — a separate list: a graph and a log are not
     *  interchangeable, and one list would mean scrolling past logs to find a graph. */
    public final List<String> recentGraphml = new ArrayList<>();

    public void addRecentGraphml(String path) {
        addRecent(recentGraphml, path);
    }

    private static void addRecent(List<String> into, String path) {
        if (path == null) return;
        into.remove(path);
        into.add(0, path);
        while (into.size() > 10) into.remove(into.size() - 1);
    }
}
