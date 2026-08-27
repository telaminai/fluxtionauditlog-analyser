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
        c.projectPanelCollapsed = parseBool(p.getProperty("projectPanelCollapsed"), c.projectPanelCollapsed);
        c.westDivider = parseInt(p.getProperty("westDivider"), c.westDivider);
        c.westWidth = parseInt(p.getProperty("westWidth"), c.westWidth);
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
        readReports(p, c.reports);
        readRunbooks(p, c.runbooks);
        c.vocabularyPath = readVocabulary(p).orElse("");
        readEnvironments(p, c.environments);
        c.defaultEnvironment = p.getProperty("environment.default", c.defaultEnvironment);
        readAnalyses(p, c.analyses);
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
        put(p, "projectPanelCollapsed", Boolean.toString(c.projectPanelCollapsed));
        put(p, "westDivider", Integer.toString(c.westDivider));
        put(p, "westWidth", Integer.toString(c.westWidth));
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
        writeReports(p, globalTier == null ? c.reports : globalTier.reports());
        writeRunbooks(p, globalTier == null ? c.runbooks : globalTier.runbooks());
        writeVocabulary(p, globalTier == null ? c.vocabularyPath : globalTier.vocabularyPath());
        writeEnvironments(p, globalTier == null ? c.environments : globalTier.environments(),
                globalTier == null ? c.defaultEnvironment : globalTier.defaultEnvironment());
        writeAnalyses(p, globalTier == null ? c.analyses : globalTier.analyses());
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

    static void writeReports(Properties p,
                             List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports) {
        p.setProperty("report.count", Integer.toString(reports.size()));
        for (int i = 0; i < reports.size(); i++) {
            var r = reports.get(i);
            String base = "report." + i;
            put(p, base + ".name", r.name());
            put(p, base + ".title", r.title());
            put(p, base + ".created", r.createdAt().isBlank() ? null : r.createdAt());
            put(p, base + ".notes", r.notes().isBlank() ? null : r.notes());
            if (r.fingerprint() != null) {
                put(p, base + ".fp.log", r.fingerprint().logName());
                put(p, base + ".fp.prov", r.fingerprint().provenance());   // §E; absent when nobody said
                put(p, base + ".fp.provSource", r.fingerprint().provenanceSource());   // M38.3 F1: declared, or matched
                p.setProperty(base + ".fp.records", Integer.toString(r.fingerprint().records()));
                if (r.fingerprint().firstTime() != null)
                    p.setProperty(base + ".fp.first", Long.toString(r.fingerprint().firstTime()));
                if (r.fingerprint().lastTime() != null)
                    p.setProperty(base + ".fp.last", Long.toString(r.fingerprint().lastTime()));
            }
            var f = r.filter();
            if (f.fromMillis() != null) p.setProperty(base + ".filter.from", Long.toString(f.fromMillis()));
            if (f.toMillis() != null) p.setProperty(base + ".filter.to", Long.toString(f.toMillis()));
            put(p, base + ".filter.text", f.text().isBlank() ? null : f.text());
            put(p, base + ".filter.mode", f.groupMode().name());
            if (f.dimensions() != null) {                       // null = all; absence of the count says so
                List<String> dims = new java.util.ArrayList<>(f.dimensions());
                java.util.Collections.sort(dims);
                writeList(p, base + ".filter.dim", dims);
            }
            p.setProperty(base + ".s.count", Integer.toString(r.sections().size()));
            for (int j = 0; j < r.sections().size(); j++) {
                var s = r.sections().get(j);
                String k = base + ".s." + j;
                put(p, k + ".kind", s.kind().name());
                if (s.recordIndex() >= 0) p.setProperty(k + ".record", Integer.toString(s.recordIndex()));
                put(p, k + ".file", s.file());
                put(p, k + ".ref", s.ref());
                put(p, k + ".text", s.text());
                put(p, k + ".rowWhen", s.rowWhen());
                put(p, k + ".rowWhenLabel", s.rowWhenLabel());
                p.setProperty(k + ".call.count", Integer.toString(s.call().size()));
                int ci = 0;
                for (var e : s.call().entrySet()) {
                    put(p, k + ".call." + ci + ".key", e.getKey());
                    put(p, k + ".call." + ci + ".val", e.getValue());
                    ci++;
                }
                p.setProperty(k + ".col.count", Integer.toString(s.columns().size()));
                for (int m = 0; m < s.columns().size(); m++) {
                    var col = s.columns().get(m);
                    String ck = k + ".col." + m;
                    put(p, ck + ".key", col.key());
                    put(p, ck + ".heading", col.heading());
                    put(p, ck + ".format", col.format().isBlank() ? null : col.format());
                    put(p, ck + ".align", col.align().isBlank() ? null : col.align());
                    put(p, ck + ".emphasis", col.emphasis().isBlank() ? null : col.emphasis());
                    if (col.width() > 0) p.setProperty(ck + ".width", Integer.toString(col.width()));
                }
            }
        }
    }

    static void readReports(Properties p,
                            List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> out) {
        out.clear();
        int n = parseInt(p.getProperty("report.count"), 0);
        for (int i = 0; i < n; i++) {
            String base = "report." + i;
            String name = p.getProperty(base + ".name");
            if (name == null || name.isBlank()) continue;
            telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint fp = null;
            if (p.getProperty(base + ".fp.records") != null) {
                fp = new telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint(
                        p.getProperty(base + ".fp.log", ""),
                        parseInt(p.getProperty(base + ".fp.records"), 0),
                        longOrNull(p.getProperty(base + ".fp.first")),
                        longOrNull(p.getProperty(base + ".fp.last")),
                        p.getProperty(base + ".fp.prov"),    // pre-§E configs simply have none
                        p.getProperty(base + ".fp.provSource"));
            }
            java.util.Set<String> dims = null;
            if (p.getProperty(base + ".filter.dim.count") != null) {
                List<String> list = new java.util.ArrayList<>();
                readList(p, base + ".filter.dim", list);
                dims = new java.util.HashSet<>(list);
            }
            var filter = new telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot(
                    longOrNull(p.getProperty(base + ".filter.from")),
                    longOrNull(p.getProperty(base + ".filter.to")),
                    dims,
                    p.getProperty(base + ".filter.text", ""),
                    parseGroupMode(p.getProperty(base + ".filter.mode")));
            List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec> sections =
                    new java.util.ArrayList<>();
            int sc = parseInt(p.getProperty(base + ".s.count"), 0);
            for (int j = 0; j < sc; j++) {
                String k = base + ".s." + j;
                String kindStr = p.getProperty(k + ".kind");
                telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.Kind kind;
                try {
                    kind = telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.Kind.valueOf(
                            kindStr == null ? "" : kindStr);
                } catch (IllegalArgumentException e) {
                    continue;                                   // a kind this build does not know: skip
                }
                java.util.Map<String, String> call = new java.util.LinkedHashMap<>();
                int cc = parseInt(p.getProperty(k + ".call.count"), 0);
                for (int ci = 0; ci < cc; ci++) {
                    String key = p.getProperty(k + ".call." + ci + ".key");
                    String val = p.getProperty(k + ".call." + ci + ".val");
                    if (key != null && val != null) call.put(key, val);
                }
                List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.ColumnSpec> cols =
                        new java.util.ArrayList<>();
                int colc = parseInt(p.getProperty(k + ".col.count"), 0);
                for (int m = 0; m < colc; m++) {
                    String ck = k + ".col." + m;
                    cols.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.ColumnSpec(
                            p.getProperty(ck + ".key"), p.getProperty(ck + ".heading"),
                            p.getProperty(ck + ".format"), p.getProperty(ck + ".align"),
                            p.getProperty(ck + ".emphasis"),
                            parseInt(p.getProperty(ck + ".width"), 0)));
                }
                sections.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec(
                        kind, parseInt(p.getProperty(k + ".record"), -1), p.getProperty(k + ".file"),
                        p.getProperty(k + ".ref"), call, p.getProperty(k + ".text"), cols,
                        p.getProperty(k + ".rowWhen"), p.getProperty(k + ".rowWhenLabel")));
            }
            out.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec(
                    name, p.getProperty(base + ".title"), p.getProperty(base + ".created", ""),
                    p.getProperty(base + ".notes", ""), fp, filter, sections));
        }
    }

    private static Long longOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static telamin.fluxtion.audit.analyser.analyser.filter.FilterState.GroupMode parseGroupMode(String s) {
        try {
            return telamin.fluxtion.audit.analyser.analyser.filter.FilterState.GroupMode.valueOf(
                    s == null ? "" : s);
        } catch (IllegalArgumentException e) {
            return telamin.fluxtion.audit.analyser.analyser.filter.FilterState.GroupMode.DIMENSION;
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
            List<GraphSpec.GuideSpec> guides = g.guides();
            p.setProperty("graph." + i + ".guide.count", Integer.toString(guides.size()));
            for (int j = 0; j < guides.size(); j++) {
                p.setProperty("graph." + i + ".guide." + j + ".value", Double.toString(guides.get(j).value()));
                put(p, "graph." + i + ".guide." + j + ".label", guides.get(j).label());
                p.setProperty("graph." + i + ".guide." + j + ".right", Boolean.toString(guides.get(j).rightAxis()));
            }
            List<GraphSpec.BandSpec> bands = g.bands();
            p.setProperty("graph." + i + ".band.count", Integer.toString(bands.size()));
            for (int j = 0; j < bands.size(); j++) {
                put(p, "graph." + i + ".band." + j + ".expr", bands.get(j).expr());
                put(p, "graph." + i + ".band." + j + ".label", bands.get(j).label());
            }
            List<GraphSpec.MarkerSpec> mk = g.markers();
            p.setProperty("graph." + i + ".marker.count", Integer.toString(mk.size()));
            for (int j = 0; j < mk.size(); j++) {
                String k = "graph." + i + ".marker." + j;
                put(p, k + ".label", mk.get(j).label());
                put(p, k + ".glyph", mk.get(j).glyph());
                put(p, k + ".when", mk.get(j).when());
                put(p, k + ".y", mk.get(j).y());
                put(p, k + ".payload", mk.get(j).payload());
                if (mk.get(j).isExternal()) {                     // M32.8: the CSV source persists as
                    put(p, k + ".ext.path", mk.get(j).extPath()); // its DEFINITION, never its points
                    put(p, k + ".ext.time", mk.get(j).extTime());
                    put(p, k + ".ext.format", mk.get(j).extTimeFormat());
                    put(p, k + ".ext.zone", mk.get(j).extZone());
                    put(p, k + ".ext.value", mk.get(j).extValue());
                    put(p, k + ".ext.payload", mk.get(j).extPayload());
                    p.setProperty(k + ".ext.offset", Long.toString(mk.get(j).extOffsetMillis()));
                }
            }
            List<GraphSpec.ExternalSpec> ext = g.external();
            p.setProperty("graph." + i + ".ext.count", Integer.toString(ext.size()));
            for (int j = 0; j < ext.size(); j++) {
                String k = "graph." + i + ".ext." + j;
                put(p, k + ".path", ext.get(j).path());
                put(p, k + ".label", ext.get(j).label());
                put(p, k + ".time", ext.get(j).time());
                put(p, k + ".format", ext.get(j).timeFormat());
                put(p, k + ".zone", ext.get(j).zone());
                put(p, k + ".value", ext.get(j).value());
                p.setProperty(k + ".offset", Long.toString(ext.get(j).offsetMillis()));
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
            List<GraphSpec.GuideSpec> guides = new ArrayList<>();
            int guideCount = parseInt(p.getProperty("graph." + i + ".guide.count"), 0);
            for (int j = 0; j < guideCount; j++) {
                String value = p.getProperty("graph." + i + ".guide." + j + ".value");
                if (value == null) continue;
                try {
                    guides.add(new GraphSpec.GuideSpec(Double.parseDouble(value),
                            nz(p.getProperty("graph." + i + ".guide." + j + ".label")),
                            Boolean.parseBoolean(p.getProperty("graph." + i + ".guide." + j + ".right"))));
                } catch (NumberFormatException ignored) {
                    // a hand-edited non-numeric guide is dropped rather than poisoning the load
                }
            }
            List<GraphSpec.BandSpec> bands = new ArrayList<>();
            int bandCount = parseInt(p.getProperty("graph." + i + ".band.count"), 0);
            for (int j = 0; j < bandCount; j++) {
                String expr = p.getProperty("graph." + i + ".band." + j + ".expr");
                if (expr != null) {
                    bands.add(new GraphSpec.BandSpec(expr, nz(p.getProperty("graph." + i + ".band." + j + ".label"))));
                }
            }
            List<GraphSpec.ExternalSpec> ext = new ArrayList<>();
            int extCount = parseInt(p.getProperty("graph." + i + ".ext.count"), 0);
            for (int j = 0; j < extCount; j++) {
                String k = "graph." + i + ".ext." + j;
                String path = p.getProperty(k + ".path");
                String label = p.getProperty(k + ".label");
                if (path == null || label == null) continue;
                ext.add(new GraphSpec.ExternalSpec(path, label,
                        p.getProperty(k + ".time"), p.getProperty(k + ".format"),
                        nz(p.getProperty(k + ".zone")), p.getProperty(k + ".value"),
                        parseLongOrNull(p.getProperty(k + ".offset")) == null
                                ? 0L : parseLongOrNull(p.getProperty(k + ".offset"))));
            }
            List<GraphSpec.MarkerSpec> mk = new ArrayList<>();
            int mkCount = parseInt(p.getProperty("graph." + i + ".marker.count"), 0);
            for (int j = 0; j < mkCount; j++) {
                String k = "graph." + i + ".marker." + j;
                String label = p.getProperty(k + ".label");
                String when = p.getProperty(k + ".when");
                String extPath = p.getProperty(k + ".ext.path");    // M32.8: the CSV-sourced form
                if (label == null || (when == null && extPath == null)) continue;
                mk.add(new GraphSpec.MarkerSpec(label, nz(p.getProperty(k + ".glyph")), when,
                        nz(p.getProperty(k + ".y")), nz(p.getProperty(k + ".payload")),
                        extPath, p.getProperty(k + ".ext.time"), p.getProperty(k + ".ext.format"),
                        p.getProperty(k + ".ext.zone"), p.getProperty(k + ".ext.value"),
                        p.getProperty(k + ".ext.payload"),
                        longOrNull(p.getProperty(k + ".ext.offset")) == null
                                ? 0L : longOrNull(p.getProperty(k + ".ext.offset"))));
            }
            out.add(new GraphSpec(name, series, exprs, from, to, note, explanation, notes, right,
                    guides, bands, ext, mk));
        }
    }

    static void put(Properties p, String k, String v) {
        if (v != null) p.setProperty(k, v);
    }

    /** M38.1: runbook pointers, validated on the way IN — a refused entry is dropped, never stored. */
    static void writeRunbooks(Properties p, java.util.Map<String, String> runbooks) {
        int i = 0;
        for (var e : runbooks.entrySet()) {
            if (Runbooks.refuse(e.getKey(), e.getValue()).isPresent()) continue;
            p.setProperty("runbook." + i + ".name", e.getKey());
            p.setProperty("runbook." + i + ".path", e.getValue());
            i++;
        }
        p.setProperty("runbook.count", Integer.toString(i));
    }

    /** M38.3: environments, gated on the way in and out like every other declaration. */
    static void writeEnvironments(Properties p, List<Environment> envs, String defaultName) {
        int i = 0;
        for (Environment e : envs) {
            if (Environment.refuse(e).isPresent()) continue;
            p.setProperty("environment." + i + ".name", e.name());
            p.setProperty("environment." + i + ".provenance", e.provenance());
            if (e.logDir() != null) p.setProperty("environment." + i + ".logDir", e.logDir());
            i++;
        }
        if (i > 0) {
            p.setProperty("environment.count", Integer.toString(i));
            if (defaultName != null && !defaultName.isBlank()) p.setProperty("environment.default", defaultName);
        }
    }

    /** @return the reasons for every declaration refused (empty when all were sound) */
    static List<String> readEnvironments(Properties p, List<Environment> out) {
        out.clear();
        List<String> refused = new ArrayList<>();
        int n = parseInt(p.getProperty("environment.count"), 0);
        for (int i = 0; i < n; i++) {
            Environment e = new Environment(p.getProperty("environment." + i + ".name"),
                    p.getProperty("environment." + i + ".provenance"), p.getProperty("environment." + i + ".logDir"));
            Environment.refuse(e).ifPresentOrElse(refused::add, () -> out.add(e));
        }
        return refused;
    }

    /** M38.4: analyses. Steps' params travel as one JSON object each — the shape the socket receives. */
    static void writeAnalyses(Properties p, List<AnalysisSpec> analyses) {
        java.util.Set<String> verbs = telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all().keySet();
        int i = 0;
        for (AnalysisSpec a : analyses) {
            if (AnalysisSpec.refuse(a, verbs).isPresent()) continue;
            String k = "analysis." + i + ".";
            put(p, k + "name", a.name());
            put(p, k + "rationale", a.rationale());
            p.setProperty(k + "param.count", Integer.toString(a.parameters().size()));
            for (int j = 0; j < a.parameters().size(); j++) {
                String name = a.parameters().get(j);
                put(p, k + "param." + j + ".name", name);
                if (a.defaults().containsKey(name)) put(p, k + "param." + j + ".default", a.defaults().get(name));
            }
            p.setProperty(k + "step.count", Integer.toString(a.steps().size()));
            for (int j = 0; j < a.steps().size(); j++) {
                put(p, k + "step." + j + ".action", a.steps().get(j).action());
                put(p, k + "step." + j + ".params", telamin.fluxtion.audit.analyser.analyser.llm.Json.write(a.steps().get(j).params()));
            }
            i++;
        }
        if (i > 0) p.setProperty("analysis.count", Integer.toString(i));
    }

    /** @return the reasons for every analysis refused (empty when all were sound) */
    @SuppressWarnings("unchecked")
    static List<String> readAnalyses(Properties p, List<AnalysisSpec> out) {
        out.clear();
        List<String> refused = new ArrayList<>();
        java.util.Set<String> verbs = telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all().keySet();
        int n = parseInt(p.getProperty("analysis.count"), 0);
        for (int i = 0; i < n; i++) {
            String k = "analysis." + i + ".";
            List<String> params = new ArrayList<>();
            java.util.Map<String, String> defaults = new java.util.LinkedHashMap<>();
            int pn = parseInt(p.getProperty(k + "param.count"), 0);
            for (int j = 0; j < pn; j++) {
                String name = p.getProperty(k + "param." + j + ".name", "");
                params.add(name);
                String d = p.getProperty(k + "param." + j + ".default");
                if (d != null) defaults.put(name, d);
            }
            List<AnalysisSpec.Step> steps = new ArrayList<>();
            int sn = parseInt(p.getProperty(k + "step.count"), 0);
            String bad = null;
            for (int j = 0; j < sn; j++) {
                String action = p.getProperty(k + "step." + j + ".action", "");
                Object parsed;
                try {
                    parsed = telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(p.getProperty(k + "step." + j + ".params", "{}"));
                } catch (RuntimeException e) {
                    parsed = null;
                }
                if (!(parsed instanceof java.util.Map<?, ?> m)) { bad = "step " + (j + 1) + " params are not a JSON object"; break; }
                steps.add(new AnalysisSpec.Step(action, (java.util.Map<String, Object>) m));
            }
            AnalysisSpec a = new AnalysisSpec(p.getProperty(k + "name"), p.getProperty(k + "rationale"), params, defaults, steps);
            if (bad != null) { refused.add("analysis '" + a.name() + "': " + bad); continue; }
            AnalysisSpec.refuse(a, verbs).ifPresentOrElse(refused::add, () -> out.add(a));
        }
        return refused;
    }

    /** M38.2: the vocabulary pointer — written only when it passes the gate. */
    static void writeVocabulary(Properties p, String path) {
        if (path == null || path.isBlank() || Runbooks.refusePointer("vocabulary", path).isPresent()) return;
        p.setProperty("vocabulary", path);
    }

    /** @return the stored pointer, or empty when absent OR refused (the reason is in {@link #vocabularyRefusal}). */
    static java.util.Optional<String> readVocabulary(Properties p) {
        String v = p.getProperty("vocabulary");
        if (v == null || v.isBlank()) return java.util.Optional.empty();
        return Runbooks.refusePointer("vocabulary", v).isPresent() ? java.util.Optional.empty() : java.util.Optional.of(v);
    }

    static java.util.Optional<String> vocabularyRefusal(Properties p) {
        String v = p.getProperty("vocabulary");
        if (v == null || v.isBlank()) return java.util.Optional.empty();
        return Runbooks.refusePointer("vocabulary", v);
    }

    /** @return the reasons for every entry refused (empty when all were plain relative paths) */
    static List<String> readRunbooks(Properties p, java.util.Map<String, String> out) {
        out.clear();
        List<String> refused = new ArrayList<>();
        int n = parseInt(p.getProperty("runbook.count"), 0);
        for (int i = 0; i < n; i++) {
            String name = p.getProperty("runbook." + i + ".name");
            String path = p.getProperty("runbook." + i + ".path");
            Runbooks.refuse(name, path).ifPresentOrElse(refused::add, () -> out.put(name, path));
        }
        return refused;
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
