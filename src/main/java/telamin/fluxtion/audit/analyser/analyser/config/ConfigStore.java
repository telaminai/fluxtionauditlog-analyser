package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads/saves {@link AppConfig} as cleartext properties under {@code ~/.fluxtion-analyser/config}
 * (spec §11). Lists are stored newline-free as {@code key.N} entries. Never throws on load — a
 * missing/garbled file yields defaults.
 */
public final class ConfigStore {

    private final Path file;

    public ConfigStore() {
        this(Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "config"));
    }

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Path path() {
        return file;
    }

    /** True when a config file has been saved before — used to detect a first run. */
    public boolean exists() {
        return Files.isRegularFile(file);
    }

    public AppConfig load() {
        AppConfig c = new AppConfig();
        if (!Files.isRegularFile(file)) return c;
        Properties p = new Properties();
        try {
            p.load(Files.newBufferedReader(file));
        } catch (IOException e) {
            return c;   // lenient: defaults on any read problem
        }
        c.logFile = nz(p.getProperty("logFile"));
        c.graphmlFile = nz(p.getProperty("graphmlFile"));
        readList(p, "sourceRoot", c.sourceRoots);
        c.llmProvider = p.getProperty("llmProvider", c.llmProvider);
        c.llmModel = p.getProperty("llmModel", c.llmModel);
        c.llmBaseUrl = p.getProperty("llmBaseUrl", c.llmBaseUrl);
        c.apiKey = p.getProperty("apiKey", c.apiKey);
        readList(p, "eventProcessorFqn", c.eventProcessorFqns);
        c.selectedEventProcessor = p.getProperty("selectedEventProcessor", c.selectedEventProcessor);
        c.memoryThresholdMb = parseInt(p.getProperty("memoryThresholdMb"), c.memoryThresholdMb);
        if (p.getProperty("mavenRepo.count") != null) {   // configured before → honour it (even if empty)
            readList(p, "mavenRepo", c.mavenRepos);
        }
        c.searchMavenRepos = parseBool(p.getProperty("mavenRepoSearch"), c.searchMavenRepos);
        c.eventFilterCollapsed = parseBool(p.getProperty("eventFilterCollapsed"), c.eventFilterCollapsed);
        c.topologySpacingPercent = parseInt(p.getProperty("topologySpacing"), c.topologySpacingPercent);
        c.topologyTextSize = parseInt(p.getProperty("topologyTextSize"), c.topologyTextSize);
        c.topologyZoom = parseDouble(p.getProperty("topologyZoom"), c.topologyZoom);
        c.topologyPanX = parseDouble(p.getProperty("topologyPanX"), c.topologyPanX);
        c.topologyPanY = parseDouble(p.getProperty("topologyPanY"), c.topologyPanY);
        c.topologySyncSource = parseBool(p.getProperty("topologySyncSource"), c.topologySyncSource);
        String orientation = nz(p.getProperty("topologyOrientation"));
        if (orientation != null && !orientation.isBlank()) c.topologyOrientation = orientation;
        c.awsProfile = p.getProperty("awsProfile", c.awsProfile);
        c.awsRegion = p.getProperty("awsRegion", c.awsRegion);
        c.theme = p.getProperty("theme", c.theme);
        readList(p, "recentFile", c.recentFiles);
        readList(p, "recentGraphml", c.recentGraphml);
        // M20: which project is active, and the switcher's list. Both GLOBAL — a profile recording
        // which profile is active would be circular, and a recent list is machine history.
        c.activeProjectPath = p.getProperty("activeProjectPath", c.activeProjectPath);
        readList(p, "recentProject", c.recentProjects);
        if (p.getProperty("hiddenColumn.count") != null) {   // configured before → honour it (even if empty)
            readList(p, "hiddenColumn", c.hiddenColumns);
            c.hiddenColumnsSet = true;
        }
        readGraphs(p, c.savedGraphs);
        readFocuses(p, c.namedFocuses);
        c.assistantActionsInProcess = parseBool(p.getProperty("assistant.inProcess"), c.assistantActionsInProcess);
        c.assistantActionsRest = parseBool(p.getProperty("assistant.rest"), c.assistantActionsRest);
        c.assistantExports = parseBool(p.getProperty("assistant.exports"), c.assistantExports);
        c.assistantExportDir = p.getProperty("assistant.exportDir", c.assistantExportDir);
        c.maxActionRounds = parseInt(p.getProperty("assistant.maxRounds"), c.maxActionRounds);
        c.maxActionsPerReply = parseInt(p.getProperty("assistant.maxActionsPerReply"), c.maxActionsPerReply);
        readList(p, "searchHistory", c.searchHistory);
        c.lastRunVersion = p.getProperty("lastRunVersion", c.lastRunVersion);
        c.windowX = parseInt(p.getProperty("windowX"), c.windowX);
        c.windowY = parseInt(p.getProperty("windowY"), c.windowY);
        c.windowW = parseInt(p.getProperty("windowW"), c.windowW);
        c.windowH = parseInt(p.getProperty("windowH"), c.windowH);
        return c;
    }

    public void save(AppConfig c) {
        save(c, null);
    }

    /**
     * Persist the global config, optionally writing the project-scoped keys from {@code globalTier}
     * instead of from the live config.
     *
     * <p>Needed because one {@link AppConfig} holds both tiers in memory while a project is open. Saving
     * it wholesale writes that project's source roots into the <b>global</b> file — and then deleting the
     * project directory leaves the user with a stale project's settings as their personal ones, with
     * their own pre-project configuration gone. The spec promises those values survive as the "no
     * project" defaults, so the global file must keep holding them.
     *
     * @param globalTier the project-scoped values to persist globally, or {@code null} for none active
     */
    public void save(AppConfig c, ProjectProfile.Snapshot globalTier) {
        Properties p = new Properties();
        put(p, "logFile", c.logFile);
        put(p, "graphmlFile", c.graphmlFile);
        writeList(p, "sourceRoot", globalTier == null ? c.sourceRoots : globalTier.sourceRoots());
        put(p, "llmProvider", c.llmProvider);
        put(p, "llmModel", c.llmModel);
        put(p, "llmBaseUrl", c.llmBaseUrl);
        put(p, "apiKey", c.apiKey);
        writeList(p, "eventProcessorFqn", globalTier == null ? c.eventProcessorFqns : globalTier.eventProcessorFqns());
        put(p, "selectedEventProcessor", globalTier == null ? c.selectedEventProcessor : globalTier.selectedEventProcessor());
        put(p, "memoryThresholdMb", Integer.toString(c.memoryThresholdMb));
        writeList(p, "mavenRepo", globalTier == null ? c.mavenRepos : globalTier.mavenRepos());
        put(p, "mavenRepoSearch", Boolean.toString(globalTier == null ? c.searchMavenRepos : globalTier.searchMavenRepos()));
        put(p, "eventFilterCollapsed", Boolean.toString(c.eventFilterCollapsed));
        put(p, "topologySpacing", Integer.toString(c.topologySpacingPercent));
        put(p, "topologyTextSize", Integer.toString(c.topologyTextSize));
        put(p, "topologyZoom", Double.toString(c.topologyZoom));
        put(p, "topologyPanX", Double.toString(c.topologyPanX));
        put(p, "topologyPanY", Double.toString(c.topologyPanY));
        put(p, "topologyOrientation", c.topologyOrientation);
        put(p, "topologySyncSource", Boolean.toString(c.topologySyncSource));
        put(p, "awsProfile", c.awsProfile);
        put(p, "awsRegion", c.awsRegion);
        put(p, "theme", c.theme);
        writeList(p, "recentFile", c.recentFiles);
        writeList(p, "recentGraphml", c.recentGraphml);
        put(p, "activeProjectPath", c.activeProjectPath);
        writeList(p, "recentProject", c.recentProjects);
        writeList(p, "hiddenColumn", globalTier == null ? c.hiddenColumns : globalTier.hiddenColumns());
        writeList(p, "searchHistory", c.searchHistory);
        put(p, "lastRunVersion", c.lastRunVersion);
        writeGraphs(p, globalTier == null ? c.savedGraphs : globalTier.savedGraphs());
        writeFocuses(p, globalTier == null ? c.namedFocuses : globalTier.namedFocuses());
        put(p, "assistant.inProcess", Boolean.toString(c.assistantActionsInProcess));
        put(p, "assistant.rest", Boolean.toString(c.assistantActionsRest));
        put(p, "assistant.exports", Boolean.toString(c.assistantExports));
        put(p, "assistant.exportDir", c.assistantExportDir);
        put(p, "assistant.maxRounds", Integer.toString(c.maxActionRounds));
        put(p, "assistant.maxActionsPerReply", Integer.toString(c.maxActionsPerReply));
        put(p, "windowX", Integer.toString(c.windowX));
        put(p, "windowY", Integer.toString(c.windowY));
        put(p, "windowW", Integer.toString(c.windowW));
        put(p, "windowH", Integer.toString(c.windowH));
        try {
            Files.createDirectories(file.getParent());
            try (var w = Files.newBufferedWriter(file)) {
                p.store(w, "fluxtion-analyser config (cleartext)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // package-visible so SettingsShare (settings export/import, M15) reuses the exact same
    // list/graph serialization rather than duplicating the key layout
    /** M27.3 — named focuses ride the same wire shape as graphs: focus.N.name/rationale/node.M. */
    static void writeFocuses(java.util.Properties p, java.util.List<FocusSpec> focuses) {
        p.setProperty("focus.count", Integer.toString(focuses.size()));
        for (int i = 0; i < focuses.size(); i++) {
            FocusSpec f = focuses.get(i);
            put(p, "focus." + i + ".name", f.name());
            put(p, "focus." + i + ".rationale", f.rationale());
            p.setProperty("focus." + i + ".node.count", Integer.toString(f.nodeIds().size()));
            for (int j = 0; j < f.nodeIds().size(); j++) {
                put(p, "focus." + i + ".node." + j, f.nodeIds().get(j));
            }
        }
    }

    static void readFocuses(java.util.Properties p, java.util.List<FocusSpec> into) {
        into.clear();
        int count = parseInt(p.getProperty("focus.count"), 0);
        for (int i = 0; i < count; i++) {
            String name = p.getProperty("focus." + i + ".name");
            if (name == null || name.isBlank()) continue;
            int nodes = parseInt(p.getProperty("focus." + i + ".node.count"), 0);
            java.util.List<String> ids = new java.util.ArrayList<>(nodes);
            for (int j = 0; j < nodes; j++) {
                String id = p.getProperty("focus." + i + ".node." + j);
                if (id != null && !id.isBlank()) ids.add(id);
            }
            if (!ids.isEmpty()) into.add(new FocusSpec(name, p.getProperty("focus." + i + ".rationale"), ids));
        }
    }

    static void writeGraphs(Properties p, List<GraphSpec> graphs) {
        p.setProperty("graph.count", Integer.toString(graphs.size()));
        for (int i = 0; i < graphs.size(); i++) {
            GraphSpec g = graphs.get(i);
            put(p, "graph." + i + ".name", g.name());
            put(p, "graph." + i + ".note", g.note());
            if (g.from() != null) put(p, "graph." + i + ".from", Long.toString(g.from()));
            if (g.to() != null) put(p, "graph." + i + ".to", Long.toString(g.to()));
            List<String> series = g.series();
            p.setProperty("graph." + i + ".count", Integer.toString(series.size()));
            for (int j = 0; j < series.size(); j++) p.setProperty("graph." + i + "." + j, series.get(j));
            List<GraphSpec.ExprSpec> exprs = g.exprs();
            p.setProperty("graph." + i + ".expr.count", Integer.toString(exprs.size()));
            for (int j = 0; j < exprs.size(); j++) {
                put(p, "graph." + i + ".expr." + j + ".label", exprs.get(j).label());
                put(p, "graph." + i + ".expr." + j + ".expr", exprs.get(j).expr());
                put(p, "graph." + i + ".expr." + j + ".resolve", exprs.get(j).resolve());
            }
            // annotations: the reading of the chart, which is the part worth keeping
            put(p, "graph." + i + ".explanation", g.explanation().isBlank() ? null : g.explanation());
            List<GraphSpec.NoteSpec> notes = g.notes();
            p.setProperty("graph." + i + ".note.count", Integer.toString(notes.size()));
            for (int j = 0; j < notes.size(); j++) {
                p.setProperty("graph." + i + ".note." + j + ".at", Long.toString(notes.get(j).at()));
                put(p, "graph." + i + ".note." + j + ".text", notes.get(j).text());
                put(p, "graph." + i + ".note." + j + ".series", notes.get(j).series());
            }
            List<String> right = g.rightAxis();
            p.setProperty("graph." + i + ".right.count", Integer.toString(right.size()));
            for (int j = 0; j < right.size(); j++) {
                put(p, "graph." + i + ".right." + j, right.get(j));
            }
        }
    }

    static void readGraphs(Properties p, List<GraphSpec> out) {
        out.clear();
        int n = parseInt(p.getProperty("graph.count"), 0);
        for (int i = 0; i < n; i++) {
            String name = p.getProperty("graph." + i + ".name");   // null for pre-naming configs → default later
            String note = p.getProperty("graph." + i + ".note");
            Long from = parseLongOrNull(p.getProperty("graph." + i + ".from"));
            Long to = parseLongOrNull(p.getProperty("graph." + i + ".to"));
            int m = parseInt(p.getProperty("graph." + i + ".count"), 0);
            List<String> series = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                String v = p.getProperty("graph." + i + "." + j);
                if (v != null) series.add(v);
            }
            int ec = parseInt(p.getProperty("graph." + i + ".expr.count"), 0);
            List<GraphSpec.ExprSpec> exprs = new ArrayList<>();
            for (int j = 0; j < ec; j++) {
                String label = p.getProperty("graph." + i + ".expr." + j + ".label");
                String expr = p.getProperty("graph." + i + ".expr." + j + ".expr");
                String resolve = p.getProperty("graph." + i + ".expr." + j + ".resolve");
                if (expr != null) exprs.add(new GraphSpec.ExprSpec(label, expr, resolve));
            }
            String explanation = nz(p.getProperty("graph." + i + ".explanation"));
            List<GraphSpec.NoteSpec> notes = new ArrayList<>();
            int noteCount = parseInt(p.getProperty("graph." + i + ".note.count"), 0);
            for (int j = 0; j < noteCount; j++) {
                String text = p.getProperty("graph." + i + ".note." + j + ".text");
                Long at = parseLongOrNull(p.getProperty("graph." + i + ".note." + j + ".at"));
                if (text != null && at != null) {
                    notes.add(new GraphSpec.NoteSpec(at, text,
                            nz(p.getProperty("graph." + i + ".note." + j + ".series"))));
                }
            }
            List<String> right = new ArrayList<>();
            int rightCount = parseInt(p.getProperty("graph." + i + ".right.count"), 0);
            for (int j = 0; j < rightCount; j++) {
                String label = p.getProperty("graph." + i + ".right." + j);
                if (label != null) right.add(label);
            }
            out.add(new GraphSpec(name, series, exprs, from, to, note, explanation, notes, right));
        }
    }

    static void put(Properties p, String k, String v) {
        if (v != null) p.setProperty(k, v);
    }

    static void writeList(Properties p, String prefix, List<String> values) {
        p.setProperty(prefix + ".count", Integer.toString(values.size()));
        for (int i = 0; i < values.size(); i++) p.setProperty(prefix + "." + i, values.get(i));
    }

    static void readList(Properties p, String prefix, List<String> out) {
        out.clear();
        int n = parseInt(p.getProperty(prefix + ".count"), 0);
        List<String> tmp = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String v = p.getProperty(prefix + "." + i);
            if (v != null) tmp.add(v);
        }
        out.addAll(tmp);
    }

    static double parseDouble(String s, double def) {
        try {
            return s == null || s.isBlank() ? def : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static boolean parseBool(String s, boolean def) {
        return s == null ? def : Boolean.parseBoolean(s.trim());
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
