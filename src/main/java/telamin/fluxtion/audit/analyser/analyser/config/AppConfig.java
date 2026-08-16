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
    /** The topology showing when the app last closed, reopened on the next start beside the log. */
    public String graphmlFile;
    public final List<String> sourceRoots = new ArrayList<>();
    public String llmProvider = "anthropic";     // anthropic | openai
    public String llmModel = "";
    public String llmBaseUrl = "";
    public String apiKey = "";                    // cleartext (by decision)
    public final List<String> eventProcessorFqns = new ArrayList<>();
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

    /** Everything under "how the topology is displayed" — cleared as a group from Settings ▸ History. */
    public void clearTopologyView() {
        topologySpacingPercent = 100;
        topologyTextSize = 11;
        topologyZoom = 0;
        topologyPanX = 0;
        topologyPanY = 0;
        topologyOrientation = "TOP_DOWN";
    }

    /** Saved graphs: one entry per graph tab, each a list of series encoded as {@code instanceIdkey}. */
    public final List<GraphSpec> savedGraphs = new ArrayList<>();

    // Assistant actions (M10): the in-process executor runs actions from the model's replies and feeds
    // results back, bounded by the round/per-reply caps. REST transport (default off) lands in slice 4.
    public boolean assistantActionsInProcess = true;
    public boolean assistantActionsRest = false;   // localhost REST transport (opt-in; §5.2)
    public int maxActionRounds = 3;
    public int maxActionsPerReply = 20;

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
