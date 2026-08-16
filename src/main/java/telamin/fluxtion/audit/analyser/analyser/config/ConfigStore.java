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
        c.awsProfile = p.getProperty("awsProfile", c.awsProfile);
        c.awsRegion = p.getProperty("awsRegion", c.awsRegion);
        c.theme = p.getProperty("theme", c.theme);
        readList(p, "recentFile", c.recentFiles);
        if (p.getProperty("hiddenColumn.count") != null) {   // configured before → honour it (even if empty)
            readList(p, "hiddenColumn", c.hiddenColumns);
            c.hiddenColumnsSet = true;
        }
        readGraphs(p, c.savedGraphs);
        c.assistantActionsInProcess = parseBool(p.getProperty("assistant.inProcess"), c.assistantActionsInProcess);
        c.assistantActionsRest = parseBool(p.getProperty("assistant.rest"), c.assistantActionsRest);
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
        Properties p = new Properties();
        put(p, "logFile", c.logFile);
        writeList(p, "sourceRoot", c.sourceRoots);
        put(p, "llmProvider", c.llmProvider);
        put(p, "llmModel", c.llmModel);
        put(p, "llmBaseUrl", c.llmBaseUrl);
        put(p, "apiKey", c.apiKey);
        writeList(p, "eventProcessorFqn", c.eventProcessorFqns);
        put(p, "selectedEventProcessor", c.selectedEventProcessor);
        put(p, "memoryThresholdMb", Integer.toString(c.memoryThresholdMb));
        writeList(p, "mavenRepo", c.mavenRepos);
        put(p, "mavenRepoSearch", Boolean.toString(c.searchMavenRepos));
        put(p, "eventFilterCollapsed", Boolean.toString(c.eventFilterCollapsed));
        put(p, "awsProfile", c.awsProfile);
        put(p, "awsRegion", c.awsRegion);
        put(p, "theme", c.theme);
        writeList(p, "recentFile", c.recentFiles);
        writeList(p, "hiddenColumn", c.hiddenColumns);
        writeList(p, "searchHistory", c.searchHistory);
        put(p, "lastRunVersion", c.lastRunVersion);
        writeGraphs(p, c.savedGraphs);
        put(p, "assistant.inProcess", Boolean.toString(c.assistantActionsInProcess));
        put(p, "assistant.rest", Boolean.toString(c.assistantActionsRest));
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
            out.add(new GraphSpec(name, series, exprs, from, to, note));
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
