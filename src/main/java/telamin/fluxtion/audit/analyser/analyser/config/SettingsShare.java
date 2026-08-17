package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Settings export / import — shareable analysis setups (M15, spec-settings-share.md).
 *
 * <p>Pure and headless: {@link #export} produces the text of a share file, {@link #preview} parses one
 * into an {@link ImportPlan} (a summary the UI renders before touching anything), and {@link #apply}
 * merges a plan into an {@link AppConfig}. No Swing, no filesystem — the UI layer owns the file chooser
 * and dialogs.
 *
 * <p><b>Whitelist, both ways.</b> Export copies only the {@link Category} keys the user selected;
 * import reads only those same keys. A key outside the whitelist (an {@code apiKey}, an AWS profile, a
 * recent-file path) can therefore neither leak into a share file nor be planted by a hand-crafted one —
 * the whitelist is the single gate on both directions.
 *
 * <p>Paths under the user's home are written {@code ~}-relative so a share survives a machine hop;
 * everything else is verbatim. The file reuses {@link ConfigStore}'s exact key layout (graphs included),
 * so there is one on-disk format, not two.
 */
public final class SettingsShare {

    /** Bumped only on a breaking format change; an importer rejects a file with a newer version. */
    public static final int SHARE_VERSION = 1;

    /** A selectable group of settings. Each maps to a fixed set of whitelisted config keys. */
    public enum Category {
        SOURCE_ROOTS("Source roots", true),
        MAVEN_REPOS("Maven repos", true),
        EVENT_PROCESSORS("Event processors", true),
        GRAPHS("Graphs", true),
        VIEW("View (hidden columns)", true),
        ASSISTANT("Assistant", true),
        LLM("LLM provider/model/base-URL (never the API key)", false);

        public final String label;
        public final boolean defaultOn;

        Category(String label, boolean defaultOn) {
            this.label = label;
            this.defaultOn = defaultOn;
        }

        /** The categories checked by default on export (everything except LLM). */
        public static Set<Category> defaults() {
            Set<Category> s = EnumSet.noneOf(Category.class);
            for (Category c : values()) if (c.defaultOn) s.add(c);
            return s;
        }
    }

    /** Thrown when a share file declares a {@link #SHARE_VERSION} this build can't read. */
    public static final class IncompatibleVersionException extends RuntimeException {
        public IncompatibleVersionException(String message) {
            super(message);
        }
    }

    private final String homeDir;

    public SettingsShare() {
        this(System.getProperty("user.home"));
    }

    /** Test seam: inject the home directory used for {@code ~}-relative path rewriting. */
    public SettingsShare(String homeDir) {
        this.homeDir = homeDir == null ? "" : homeDir;
    }

    // ---- export ---------------------------------------------------------------------------------

    /** Serialize the selected categories of {@code c} to share-file text (properties). */
    public String export(AppConfig c, Set<Category> categories) {
        Properties p = sortedProps();
        p.setProperty("share.version", Integer.toString(SHARE_VERSION));
        p.setProperty("share.exportedAt", Instant.now().toString());

        if (categories.contains(Category.SOURCE_ROOTS)) {
            ConfigStore.writeList(p, "sourceRoot", toPortable(c.sourceRoots));
        }
        if (categories.contains(Category.MAVEN_REPOS)) {
            ConfigStore.writeList(p, "mavenRepo", toPortable(c.mavenRepos));
            ConfigStore.put(p, "mavenRepoSearch", Boolean.toString(c.searchMavenRepos));
        }
        if (categories.contains(Category.EVENT_PROCESSORS)) {
            ConfigStore.writeList(p, "eventProcessorFqn", c.eventProcessorFqns);
            ConfigStore.put(p, "selectedEventProcessor", c.selectedEventProcessor);
        }
        if (categories.contains(Category.GRAPHS)) {
            ConfigStore.writeGraphs(p, c.savedGraphs);
            // named focuses (M27.3) ride the GRAPHS category — same kind of named analysis artifact,
            // and folding keeps ProjectProfile.PROJECT_SCOPED at its five pinned categories
            ConfigStore.writeFocuses(p, c.namedFocuses);
        }
        if (categories.contains(Category.VIEW)) {
            ConfigStore.writeList(p, "hiddenColumn", c.hiddenColumns);
        }
        if (categories.contains(Category.ASSISTANT)) {
            ConfigStore.put(p, "assistant.inProcess", Boolean.toString(c.assistantActionsInProcess));
            ConfigStore.put(p, "assistant.rest", Boolean.toString(c.assistantActionsRest));
            ConfigStore.put(p, "assistant.maxRounds", Integer.toString(c.maxActionRounds));
            ConfigStore.put(p, "assistant.maxActionsPerReply", Integer.toString(c.maxActionsPerReply));
        }
        if (categories.contains(Category.LLM)) {
            ConfigStore.put(p, "llmProvider", c.llmProvider);
            ConfigStore.put(p, "llmModel", c.llmModel);
            ConfigStore.put(p, "llmBaseUrl", c.llmBaseUrl);
        }

        StringWriter sw = new StringWriter();
        try {
            p.store(sw, "fluxtion-analyser shared settings — API keys and machine-local settings are never included");
        } catch (IOException e) {
            throw new UncheckedIOException(e);   // StringWriter can't do IO — defensive only
        }
        return sw.toString();
    }

    // ---- preview --------------------------------------------------------------------------------

    /**
     * Parse share-file text and diff it against {@code current} into a renderable {@link ImportPlan}.
     * Only whitelisted keys are read (a stray {@code apiKey=…} is ignored). Throws
     * {@link IncompatibleVersionException} if the file's version is newer than this build.
     */
    public ImportPlan preview(String text, AppConfig current) {
        return preview(text, current, null);
    }

    /**
     * As {@link #preview(String, AppConfig)}, but {@code baseDir} (the imported file's directory) is used
     * to resolve <b>bundle-relative</b> source roots / Maven repos — a path that is neither absolute nor
     * {@code ~}-prefixed resolves against {@code baseDir} (M19.2). Pass {@code null} (e.g. clipboard
     * imports) to leave relative paths untouched.
     */
    public ImportPlan preview(String text, AppConfig current, Path baseDir) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text == null ? "" : text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int version = ConfigStore.parseInt(p.getProperty("share.version"), 1);   // missing → 1
        if (version > SHARE_VERSION) {
            throw new IncompatibleVersionException(
                    "This settings file was written by a newer version of the analyser (format v" + version
                    + "; this build reads up to v" + SHARE_VERSION + "). Please update to import it.");
        }

        Set<Category> present = EnumSet.noneOf(Category.class);
        Map<Category, String> summary = new LinkedHashMap<>();

        // --- lists (paths expanded from ~) ---
        List<String> sourceRoots = null;
        if (p.getProperty("sourceRoot.count") != null) {
            present.add(Category.SOURCE_ROOTS);
            sourceRoots = resolveAgainstBase(fromPortable(readList(p, "sourceRoot")), baseDir);
            summary.put(Category.SOURCE_ROOTS, listSummary(sourceRoots, current.sourceRoots));
        }

        List<String> mavenRepos = null;
        Boolean mavenRepoSearch = null;
        if (p.getProperty("mavenRepo.count") != null || p.getProperty("mavenRepoSearch") != null) {
            present.add(Category.MAVEN_REPOS);
            mavenRepos = p.getProperty("mavenRepo.count") != null
                    ? resolveAgainstBase(fromPortable(readList(p, "mavenRepo")), baseDir) : List.of();
            if (p.getProperty("mavenRepoSearch") != null) {
                mavenRepoSearch = ConfigStore.parseBool(p.getProperty("mavenRepoSearch"), current.searchMavenRepos);
            }
            summary.put(Category.MAVEN_REPOS, listSummary(mavenRepos, current.mavenRepos));
        }

        List<String> eventProcessorFqns = null;
        String selectedEventProcessor = null;
        if (p.getProperty("eventProcessorFqn.count") != null || p.getProperty("selectedEventProcessor") != null) {
            present.add(Category.EVENT_PROCESSORS);
            eventProcessorFqns = p.getProperty("eventProcessorFqn.count") != null
                    ? readList(p, "eventProcessorFqn") : List.of();
            selectedEventProcessor = p.getProperty("selectedEventProcessor");
            summary.put(Category.EVENT_PROCESSORS, listSummary(eventProcessorFqns, current.eventProcessorFqns)
                    + (selectedEventProcessor != null ? " · selects " + shortFqn(selectedEventProcessor) : ""));
        }

        List<GraphSpec> graphs = null;
        List<FocusSpec> focuses = null;
        if (p.getProperty("graph.count") != null || p.getProperty("focus.count") != null) {
            present.add(Category.GRAPHS);
            graphs = new ArrayList<>();
            ConfigStore.readGraphs(p, graphs);
            focuses = new ArrayList<>();
            ConfigStore.readFocuses(p, focuses);
            String s = graphSummary(graphs, current.savedGraphs);
            summary.put(Category.GRAPHS, focuses.isEmpty() ? s : s + " · " + focuses.size() + " named focus(es)");
        }

        List<String> hiddenColumns = null;
        if (p.getProperty("hiddenColumn.count") != null) {
            present.add(Category.VIEW);
            hiddenColumns = readList(p, "hiddenColumn");
            summary.put(Category.VIEW, "sets " + hiddenColumns.size() + " hidden column(s)");
        }

        Boolean assistantInProcess = null, assistantRest = null;
        Integer maxRounds = null, maxActionsPerReply = null;
        if (hasAny(p, "assistant.inProcess", "assistant.rest", "assistant.maxRounds", "assistant.maxActionsPerReply")) {
            present.add(Category.ASSISTANT);
            if (p.getProperty("assistant.inProcess") != null)
                assistantInProcess = ConfigStore.parseBool(p.getProperty("assistant.inProcess"), current.assistantActionsInProcess);
            if (p.getProperty("assistant.rest") != null)
                assistantRest = ConfigStore.parseBool(p.getProperty("assistant.rest"), current.assistantActionsRest);
            if (p.getProperty("assistant.maxRounds") != null)
                maxRounds = ConfigStore.parseInt(p.getProperty("assistant.maxRounds"), current.maxActionRounds);
            if (p.getProperty("assistant.maxActionsPerReply") != null)
                maxActionsPerReply = ConfigStore.parseInt(p.getProperty("assistant.maxActionsPerReply"), current.maxActionsPerReply);
            summary.put(Category.ASSISTANT, "applies assistant preferences");
        }

        String llmProvider = null, llmModel = null, llmBaseUrl = null;
        if (hasAny(p, "llmProvider", "llmModel", "llmBaseUrl")) {
            present.add(Category.LLM);
            llmProvider = p.getProperty("llmProvider");
            llmModel = p.getProperty("llmModel");
            llmBaseUrl = p.getProperty("llmBaseUrl");
            summary.put(Category.LLM, "provider=" + nz(llmProvider) + " model=" + nz(llmModel));
        }

        return new ImportPlan(version, present, sourceRoots, mavenRepos, mavenRepoSearch,
                eventProcessorFqns, selectedEventProcessor, graphs, focuses, hiddenColumns,
                assistantInProcess, assistantRest, maxRounds, maxActionsPerReply,
                llmProvider, llmModel, llmBaseUrl, Map.copyOf(summary));
    }

    // ---- apply ----------------------------------------------------------------------------------

    /**
     * Merge a plan into {@code target}, restricted to {@code selected} (a subset of the plan's present
     * categories — the user can deselect at import time). Lists merge additively (never delete);
     * graphs replace by name; scalars overwrite. Nothing else in {@code target} is touched.
     */
    public void apply(ImportPlan plan, Set<Category> selected, AppConfig target) {
        if (selected.contains(Category.SOURCE_ROOTS) && plan.sourceRoots() != null) {
            addAllMissing(target.sourceRoots, plan.sourceRoots());
        }
        if (selected.contains(Category.MAVEN_REPOS) && plan.present().contains(Category.MAVEN_REPOS)) {
            if (plan.mavenRepos() != null) addAllMissing(target.mavenRepos, plan.mavenRepos());
            if (plan.mavenRepoSearch() != null) target.searchMavenRepos = plan.mavenRepoSearch();
        }
        if (selected.contains(Category.EVENT_PROCESSORS) && plan.present().contains(Category.EVENT_PROCESSORS)) {
            if (plan.eventProcessorFqns() != null) addAllMissing(target.eventProcessorFqns, plan.eventProcessorFqns());
            if (plan.selectedEventProcessor() != null) target.selectedEventProcessor = plan.selectedEventProcessor();
        }
        if (selected.contains(Category.GRAPHS) && plan.graphs() != null) {
            mergeGraphsByName(target.savedGraphs, plan.graphs());
        }
        if (selected.contains(Category.GRAPHS) && plan.focuses() != null) {
            for (FocusSpec f : plan.focuses()) {
                target.namedFocuses.removeIf(existing -> existing.name().equals(f.name()));
                target.namedFocuses.add(f);   // replace-by-name, like graphs
            }
        }
        if (selected.contains(Category.VIEW) && plan.hiddenColumns() != null) {
            // View is the sender's column layout — replace the set wholesale (not additive)
            target.hiddenColumns.clear();
            target.hiddenColumns.addAll(plan.hiddenColumns());
            target.hiddenColumnsSet = true;
        }
        if (selected.contains(Category.ASSISTANT) && plan.present().contains(Category.ASSISTANT)) {
            if (plan.assistantInProcess() != null) target.assistantActionsInProcess = plan.assistantInProcess();
            if (plan.assistantRest() != null) target.assistantActionsRest = plan.assistantRest();
            if (plan.maxRounds() != null) target.maxActionRounds = plan.maxRounds();
            if (plan.maxActionsPerReply() != null) target.maxActionsPerReply = plan.maxActionsPerReply();
        }
        if (selected.contains(Category.LLM) && plan.present().contains(Category.LLM)) {
            if (plan.llmProvider() != null) target.llmProvider = plan.llmProvider();
            if (plan.llmModel() != null) target.llmModel = plan.llmModel();
            if (plan.llmBaseUrl() != null) target.llmBaseUrl = plan.llmBaseUrl();
        }
    }

    /**
     * The parsed, whitelisted, categorised content of a share file plus a human-readable {@link #summary}
     * diffed against the importing config. Built once by {@link #preview} and consumed by {@link #apply}
     * (no re-parse between the summary dialog and applying). Fields for an absent category are {@code null}.
     */
    public record ImportPlan(
            int version,
            Set<Category> present,
            List<String> sourceRoots,
            List<String> mavenRepos,
            Boolean mavenRepoSearch,
            List<String> eventProcessorFqns,
            String selectedEventProcessor,
            List<GraphSpec> graphs,
            List<FocusSpec> focuses,
            List<String> hiddenColumns,
            Boolean assistantInProcess,
            Boolean assistantRest,
            Integer maxRounds,
            Integer maxActionsPerReply,
            String llmProvider,
            String llmModel,
            String llmBaseUrl,
            Map<Category, String> summary) {
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static void addAllMissing(List<String> target, List<String> incoming) {
        for (String s : incoming) if (!target.contains(s)) target.add(s);
    }

    /** Replace a same-named graph in place (preserving its position); append genuinely new names. */
    private static void mergeGraphsByName(List<GraphSpec> target, List<GraphSpec> incoming) {
        for (GraphSpec g : incoming) {
            int at = -1;
            for (int i = 0; i < target.size(); i++) {
                if (java.util.Objects.equals(target.get(i).name(), g.name())) { at = i; break; }
            }
            if (at >= 0) target.set(at, g);
            else target.add(g);
        }
    }

    private List<String> toPortable(List<String> paths) {
        List<String> out = new ArrayList<>(paths.size());
        for (String s : paths) out.add(toPortable(s));
        return out;
    }

    /** A path under the user's home becomes {@code ~/…} (or {@code ~}); anything else is verbatim. */
    String toPortable(String path) {
        if (path == null || homeDir.isEmpty()) return path;
        if (path.equals(homeDir)) return "~";
        String hp = homeDir.endsWith("/") ? homeDir : homeDir + "/";
        return path.startsWith(hp) ? "~/" + path.substring(hp.length()) : path;
    }

    private List<String> fromPortable(List<String> paths) {
        List<String> out = new ArrayList<>(paths.size());
        for (String s : paths) out.add(fromPortable(s));
        return out;
    }

    /** Expand a leading {@code ~} against the importer's home; anything else is verbatim. */
    String fromPortable(String path) {
        if (path == null || homeDir.isEmpty()) return path;
        String home = homeDir.endsWith("/") ? homeDir.substring(0, homeDir.length() - 1) : homeDir;
        if (path.equals("~")) return home;
        return path.startsWith("~/") ? home + "/" + path.substring(2) : path;
    }

    /**
     * Resolve any still-relative path against {@code baseDir} (the imported file's directory) — so a
     * bundle that ships {@code sourceRoot.0=src/main/java} lands at {@code <bundle>/src/main/java} rather
     * than resolving against the working directory. Absolute (incl. {@code ~}-expanded) paths are left
     * alone; {@code baseDir == null} leaves everything untouched (M19.2).
     */
    private static List<String> resolveAgainstBase(List<String> paths, Path baseDir) {
        if (baseDir == null) return paths;
        List<String> out = new ArrayList<>(paths.size());
        for (String s : paths) {
            if (s == null || s.isBlank()) {
                out.add(s);
                continue;
            }
            Path p = Path.of(s);
            out.add(p.isAbsolute() ? s : baseDir.resolve(p).normalize().toString());
        }
        return out;
    }

    private static List<String> readList(Properties p, String prefix) {
        List<String> out = new ArrayList<>();
        ConfigStore.readList(p, prefix, out);
        return out;
    }

    private static boolean hasAny(Properties p, String... keys) {
        for (String k : keys) if (p.getProperty(k) != null) return true;
        return false;
    }

    /** "2 new, 1 already present" for an additive-list category. */
    private static String listSummary(List<String> incoming, List<String> current) {
        int novel = 0;
        for (String s : incoming) if (!current.contains(s)) novel++;
        return novel + " new, " + (incoming.size() - novel) + " already present";
    }

    /** "adds A, B; replaces C" for graphs, by name. */
    private static String graphSummary(List<GraphSpec> incoming, List<GraphSpec> current) {
        Set<String> have = new LinkedHashSet<>();
        for (GraphSpec g : current) have.add(g.name());
        List<String> adds = new ArrayList<>();
        List<String> replaces = new ArrayList<>();
        for (GraphSpec g : incoming) (have.contains(g.name()) ? replaces : adds).add(quote(g.name()));
        StringBuilder sb = new StringBuilder();
        if (!adds.isEmpty()) sb.append("adds ").append(String.join(", ", adds));
        if (!replaces.isEmpty()) sb.append(sb.length() > 0 ? "; " : "").append("replaces ").append(String.join(", ", replaces));
        return sb.length() == 0 ? "no graphs" : sb.toString();
    }

    private static String shortFqn(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String quote(String s) {
        return "'" + (s == null ? "" : s) + "'";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** A {@link Properties} whose {@code store} emits keys in sorted order — diff-friendly share files. */
    private static Properties sortedProps() {
        return new Properties() {
            @Override
            public synchronized Enumeration<Object> keys() {
                return Collections.enumeration(new TreeSet<>(super.keySet()));
            }

            @Override
            public Set<Map.Entry<Object, Object>> entrySet() {
                // build from super.entrySet() directly — NOT stringPropertyNames(), which itself
                // iterates entrySet() and would recurse forever
                TreeMap<String, Object> sorted = new TreeMap<>();
                for (Map.Entry<Object, Object> e : super.entrySet()) {
                    sorted.put(String.valueOf(e.getKey()), e.getValue());
                }
                Set<Map.Entry<Object, Object>> out = new LinkedHashSet<>();
                for (Map.Entry<String, Object> e : sorted.entrySet()) {
                    out.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
                }
                return out;
            }
        };
    }
}
