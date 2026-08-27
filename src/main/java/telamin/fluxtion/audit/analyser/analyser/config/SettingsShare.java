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
        // the label must NAME everything the category carries — a user ticking the box is consenting
        // to what leaves the machine, and named focuses ride this category (M27.3 D5 / review F1)
        GRAPHS("Graphs and named focuses", true),
        // reports get their OWN category (M33.4 D-I4), never a passenger on Graphs: a shared report
        // carries PROSE an agent wrote about your data — a different kind of cargo from key names
        // and formulas, deserving its own consent checkbox. The F1 lesson, applied in advance.
        REPORTS("Investigation reports (definitions + narrative text — never log data)", true),
        VIEW("View (hidden columns)", true),
        ASSISTANT("Assistant", true),
        LLM("LLM provider/model/base-URL (never the API key)", false),
        /** M38.1 D-C8: off by default, and the exporter refuses any value that is not a project-relative path. */
        RUNBOOKS("Runbook LOCATIONS (paths in your repository — never their contents)", false),
        /** M38.2 D-C3/D-C8: a pointer to the glossary file — inert, so it travels by default. */
        VOCABULARY("Domain glossary LOCATION (a markdown file in your repository — never its contents)", true),
        /** M38.3 D-C4/D-C8 (owner decision 2: travels by default). The label names the cargo: names, systems, hosts. */
        ENVIRONMENTS("Environments (names, the provenance string each stamps — which may name systems and hosts — and their log directories; never log data)", true),
        /** M38.4 D-C5/D-C8: tier 2 — analyser verb sequences; they can only drive this viewer. */
        ANALYSES("Saved analyses (named analyser-verb sequences with their rationale — they can only drive this viewer, never a server)", true),
        /**
         * M38.5 D-C6/D-C8. OFF by default (review F1): the gate refuses every credential SHAPE it knows, but a
         * webhook URL is a credential in path form and cannot be told from a place by inspection — the same
         * reason the LLM category exists and does not travel. The field can hold a secret even though it usually
         * does not, so the person sharing ticks it knowingly.
         */
        DESTINATIONS("Report destinations (where reports are published — a bucket, directory or base URL; may hold a webhook URL, which is a secret)", false);

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
        return export(c, categories, null);
    }

    /**
     * As {@link #export(AppConfig, Set)}, but written to be COMMITTED at {@code projectRoot}
     * (M35.11). Three things that are provenance on a one-off share file are churn on a file in git:
     * <ul>
     *   <li>paths under the project are written <b>project-relative</b> — checked BEFORE the {@code ~}
     *       rule, because a project under your home must not come out as {@code ~/work/proj/src}: a
     *       teammate's clone lives somewhere else, and the whole point of committing the file is that
     *       it reads the same on both machines;</li>
     *   <li>no {@code share.exportedAt} — a timestamp that changes on every write is a diff on every
     *       write;</li>
     *   <li>no {@code #<date>} comment line from {@link Properties#store}, for the same reason.</li>
     * </ul>
     * The result is that loading a profile and writing it back yields the same bytes, which is what
     * lets {@code ProjectProfile.save} skip a write that would change nothing. {@code null} means an
     * ordinary share export, unchanged.
     */
    public String export(AppConfig c, Set<Category> categories, Path projectRoot) {
        Properties p = sortedProps();
        p.setProperty("share.version", Integer.toString(SHARE_VERSION));
        if (projectRoot == null) {
            p.setProperty("share.exportedAt", Instant.now().toString());
        }

        if (categories.contains(Category.SOURCE_ROOTS)) {
            // M38.6: the anchor rides with the roots it makes portable, and applies to Maven repos too
            ConfigStore.writeList(p, "sourceRoot", toPortable(c.sourceRoots, projectRoot, c.workspaceRoot));
            if (projectRoot != null && c.workspaceRoot != null && !c.workspaceRoot.isBlank()
                    && PathForm.refuseWorkspaceRoot(c.workspaceRoot).isEmpty()) {
                ConfigStore.put(p, "workspaceRoot", c.workspaceRoot.trim());
            }
        }
        if (categories.contains(Category.MAVEN_REPOS)) {
            ConfigStore.writeList(p, "mavenRepo", toPortable(c.mavenRepos, projectRoot, c.workspaceRoot));
            ConfigStore.put(p, "mavenRepoSearch", Boolean.toString(c.searchMavenRepos));
        }
        if (categories.contains(Category.EVENT_PROCESSORS)) {
            ConfigStore.writeList(p, "eventProcessorFqn", c.eventProcessorFqns);
            ConfigStore.put(p, "selectedEventProcessor", c.selectedEventProcessor);
        }
        if (categories.contains(Category.GRAPHS)) {
            ConfigStore.writeGraphs(p, c.savedGraphs);
            // external CSV paths travel like source roots: home-relative so a share survives a machine
            // hop (M29 D-F5); the receiving side expands + resolves in preview()
            for (String key : p.stringPropertyNames()) {
                // covers both external series (graph.i.ext.j.path) and external MARKER sources
                // (graph.i.marker.j.ext.path, M32.8) — one rule, every foreign path portable
                if (key.startsWith("graph.") && key.contains(".ext.") && key.endsWith(".path")) {
                    p.setProperty(key, toPortable(p.getProperty(key), projectRoot));
                }
            }
            // named focuses (M27.3) ride the GRAPHS category — same kind of named analysis artifact,
            // and folding keeps ProjectProfile.PROJECT_SCOPED at its five pinned categories
            ConfigStore.writeFocuses(p, c.namedFocuses);
        }
        if (categories.contains(Category.REPORTS)) {
            ConfigStore.writeReports(p, c.reports);
        }
        if (categories.contains(Category.RUNBOOKS) && !c.runbooks.isEmpty()) {
            ConfigStore.writeRunbooks(p, c.runbooks);      // D-C2: pointers only; refused values never leave
        }
        if (categories.contains(Category.VOCABULARY) && c.vocabularyPath != null && !c.vocabularyPath.isBlank()) {
            ConfigStore.writeVocabulary(p, c.vocabularyPath);
        }
        if (categories.contains(Category.ENVIRONMENTS) && !c.environments.isEmpty()) {
            ConfigStore.writeEnvironments(p, c.environments, c.defaultEnvironment);
        }
        if (categories.contains(Category.ANALYSES) && !c.analyses.isEmpty()) {
            ConfigStore.writeAnalyses(p, c.analyses);      // D-C5: gated — only analyser verbs leave
        }
        if (categories.contains(Category.DESTINATIONS) && !c.reportDestinations.isEmpty()) {
            ConfigStore.writeDestinations(p, c.reportDestinations);   // D-C6: places, never credentials
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
            p.store(sw, projectRoot == null
                    ? "fluxtion-analyser shared settings — API keys and machine-local settings are never included"
                    : "fluxtion-analyser project profile — paths are project-relative; API keys and "
                            + "machine-local settings are never included");
        } catch (IOException e) {
            throw new UncheckedIOException(e);   // StringWriter can't do IO — defensive only
        }
        String text = sw.toString();
        return projectRoot == null ? text : STORE_DATE_LINE.matcher(text).replaceFirst("");
    }

    /** The {@code #Mon Aug 25 09:00:00 BST 2026} line {@link Properties#store} always emits. */
    private static final java.util.regex.Pattern STORE_DATE_LINE = java.util.regex.Pattern.compile(
            "(?m)^#[A-Z][a-z]{2} [A-Z][a-z]{2} \\d{2} \\d{2}:\\d{2}:\\d{2} \\S+ \\d{4}\\R");

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
        String workspaceRoot = null;
        String workspaceRootRefused = null;
        if (p.getProperty("workspaceRoot") != null && !p.getProperty("workspaceRoot").isBlank()) {
            workspaceRootRefused = PathForm.refuseWorkspaceRoot(p.getProperty("workspaceRoot")).orElse(null);
            if (workspaceRootRefused == null) workspaceRoot = p.getProperty("workspaceRoot").trim();
        }
        List<String> sourceRoots = null;
        if (p.getProperty("sourceRoot.count") != null) {
            present.add(Category.SOURCE_ROOTS);
            sourceRoots = resolveAgainstBase(fromPortable(readList(p, "sourceRoot")), baseDir);
            summary.put(Category.SOURCE_ROOTS, listSummary(sourceRoots, current.sourceRoots)
                    + (workspaceRoot != null ? " · workspace anchor " + workspaceRoot : "")
                    + (workspaceRootRefused != null ? " · REFUSED: " + workspaceRootRefused : ""));
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
            // expand ~ and resolve profile-relative external paths against the file's own directory —
            // the same rule source roots follow (M19.2), so a committed profile works on any machine
            for (int gi = 0; gi < graphs.size(); gi++) {
                GraphSpec spec = graphs.get(gi);
                boolean extMarkers = spec.markers().stream().anyMatch(GraphSpec.MarkerSpec::isExternal);
                if (spec.external().isEmpty() && !extMarkers) continue;
                java.util.List<GraphSpec.ExternalSpec> fixed = new ArrayList<>();
                for (GraphSpec.ExternalSpec e : spec.external()) {
                    String path = fromPortable(e.path());
                    if (baseDir != null && !java.nio.file.Path.of(path).isAbsolute()) {
                        path = baseDir.resolve(path).normalize().toString();
                    }
                    fixed.add(new GraphSpec.ExternalSpec(path, e.label(), e.time(), e.timeFormat(),
                            e.zone(), e.value(), e.offsetMillis()));
                }
                // external MARKER sources travel by the same rule (M32.8)
                java.util.List<GraphSpec.MarkerSpec> fixedMarkers = new ArrayList<>();
                for (GraphSpec.MarkerSpec mk : spec.markers()) {
                    if (!mk.isExternal()) {
                        fixedMarkers.add(mk);
                        continue;
                    }
                    String path = fromPortable(mk.extPath());
                    if (baseDir != null && !java.nio.file.Path.of(path).isAbsolute()) {
                        path = baseDir.resolve(path).normalize().toString();
                    }
                    fixedMarkers.add(new GraphSpec.MarkerSpec(mk.label(), mk.glyph(), mk.when(),
                            mk.y(), mk.payload(), path, mk.extTime(), mk.extTimeFormat(),
                            mk.extZone(), mk.extValue(), mk.extPayload(), mk.extOffsetMillis()));
                }
                graphs.set(gi, new GraphSpec(spec.name(), spec.series(), spec.exprs(), spec.from(),
                        spec.to(), spec.note(), spec.explanation(), spec.notes(), spec.rightAxis(),
                        spec.guides(), spec.bands(), fixed, fixedMarkers));
            }
            focuses = new ArrayList<>();
            ConfigStore.readFocuses(p, focuses);
            String s = graphSummary(graphs, current.savedGraphs);
            summary.put(Category.GRAPHS, focuses.isEmpty() ? s : s + " · " + focuses.size() + " named focus(es)");
        }

        List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports = null;
        if (p.getProperty("report.count") != null) {
            present.add(Category.REPORTS);
            reports = new ArrayList<>();
            ConfigStore.readReports(p, reports);
            long withNarrative = reports.stream().filter(r -> r.sections().stream()
                    .anyMatch(sec -> sec.kind() == telamin.fluxtion.audit.analyser.analyser.report
                            .ReportSpec.Kind.NARRATIVE) || !r.notes().isBlank()).count();
            // the summary names the cargo the consent is FOR: prose written about the sender's data
            summary.put(Category.REPORTS, reports.size() + " report(s)"
                    + (withNarrative == 0 ? "" : " · " + withNarrative + " carrying narrative text"));
        }

        Map<String, String> runbooks = null;
        if (p.getProperty("runbook.count") != null) {
            present.add(Category.RUNBOOKS);
            runbooks = new LinkedHashMap<>();
            List<String> refused = ConfigStore.readRunbooks(p, runbooks);
            // the summary names what is REFUSED as well as what arrives: a file carrying runbook CONTENTS
            // is exactly the shape D-C2 exists to stop, and the importer must not be told "1 runbook"
            summary.put(Category.RUNBOOKS, runbooks.size() + " runbook location(s)"
                    + (refused.isEmpty() ? "" : " · " + refused.size() + " entry(ies) REFUSED — not a project-relative path: "
                    + String.join("; ", refused)));
        }

        String vocabulary = null;
        if (p.getProperty("vocabulary") != null) {
            present.add(Category.VOCABULARY);
            vocabulary = ConfigStore.readVocabulary(p).orElse(null);
            summary.put(Category.VOCABULARY, vocabulary != null ? "glossary at " + vocabulary
                    : "REFUSED — " + ConfigStore.vocabularyRefusal(p).orElse("not a project-relative path"));
        }

        List<Environment> environments = null;
        String defaultEnvironment = null;
        if (p.getProperty("environment.count") != null) {
            present.add(Category.ENVIRONMENTS);
            environments = new ArrayList<>();
            List<String> refused = ConfigStore.readEnvironments(p, environments);
            defaultEnvironment = p.getProperty("environment.default");
            summary.put(Category.ENVIRONMENTS, environments.size() + " environment(s): "
                    + environments.stream().map(Environment::name).toList()
                    + (defaultEnvironment == null ? "" : " · default " + defaultEnvironment)
                    + (refused.isEmpty() ? "" : " · " + refused.size() + " REFUSED: " + String.join("; ", refused)));
        }

        List<AnalysisSpec> analyses = null;
        if (p.getProperty("analysis.count") != null) {
            present.add(Category.ANALYSES);
            analyses = new ArrayList<>();
            List<String> refused = ConfigStore.readAnalyses(p, analyses);
            summary.put(Category.ANALYSES, analyses.size() + " analysis(es): " + analyses.stream().map(AnalysisSpec::name).toList()
                    + (refused.isEmpty() ? "" : " · " + refused.size() + " REFUSED: " + String.join("; ", refused)));
        }

        List<ReportDestination> destinations = null;
        if (p.getProperty("destination.count") != null) {
            present.add(Category.DESTINATIONS);
            destinations = new ArrayList<>();
            List<String> refused = ConfigStore.readDestinations(p, destinations);
            summary.put(Category.DESTINATIONS, destinations.size() + " destination(s): "
                    + destinations.stream().map(d -> d.name() + " → " + d.location()).toList()
                    + (refused.isEmpty() ? "" : " · " + refused.size() + " REFUSED: " + String.join("; ", refused)));
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
                eventProcessorFqns, selectedEventProcessor, graphs, focuses, reports, hiddenColumns, runbooks, vocabulary, environments, defaultEnvironment, analyses, destinations, workspaceRoot,
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
            if (plan.workspaceRoot() != null) target.workspaceRoot = plan.workspaceRoot();   // M38.6
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
        if (selected.contains(Category.REPORTS) && plan.reports() != null) {
            for (var r : plan.reports()) {
                target.reports.removeIf(existing -> existing.name().equals(r.name()));
                target.reports.add(r);   // replace-by-name, like graphs and focuses
            }
        }
        if (selected.contains(Category.RUNBOOKS) && plan.runbooks() != null) {
            plan.runbooks().forEach((name, path) -> {
                if (Runbooks.refuse(name, path).isEmpty()) target.runbooks.put(name, path);   // replace-by-name
            });
        }
        if (selected.contains(Category.VOCABULARY) && plan.vocabulary() != null) {
            target.vocabularyPath = plan.vocabulary();          // already gated in preview
        }
        if (selected.contains(Category.ENVIRONMENTS) && plan.environments() != null) {
            for (Environment e : plan.environments()) {
                target.environments.removeIf(x -> x.name().equals(e.name()));
                target.environments.add(e);                          // replace-by-name, like graphs
            }
            if (plan.defaultEnvironment() != null) target.defaultEnvironment = plan.defaultEnvironment();
        }
        if (selected.contains(Category.ANALYSES) && plan.analyses() != null) {
            for (AnalysisSpec a : plan.analyses()) {
                target.analyses.removeIf(x -> x.name().equals(a.name()));
                target.analyses.add(a);                                  // replace-by-name, like graphs
            }
        }
        if (selected.contains(Category.DESTINATIONS) && plan.destinations() != null) {
            for (ReportDestination d : plan.destinations()) {
                target.reportDestinations.removeIf(x -> x.name().equals(d.name()));
                target.reportDestinations.add(d);
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
            List<telamin.fluxtion.audit.analyser.analyser.report.ReportSpec> reports,
            List<String> hiddenColumns,
            Map<String, String> runbooks,
            String vocabulary,
            List<Environment> environments,
            String defaultEnvironment,
            List<AnalysisSpec> analyses,
            List<ReportDestination> destinations,
            String workspaceRoot,
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

    private List<String> toPortable(List<String> paths, Path projectRoot, String workspaceRoot) {
        List<String> out = new ArrayList<>(paths.size());
        for (String s : paths) out.add(toPortable(s, projectRoot, workspaceRoot));
        return out;
    }

    /**
     * M38.6 D-C9 — project-relative first; then, when the project declares a workspace anchor and the path
     * sits under it, relative to the project root WITH {@code ..} steps (bounded by the anchor: a sibling
     * checkout becomes {@code ../shared-lib/src/main/java}, which resolves against the profile's own
     * directory on every machine); then {@code ~}; then absolute. One rule, applied consistently — never a
     * per-path choice — because portability that varies row by row fails on somebody else's machine.
     */
    String toPortable(String path, Path projectRoot, String workspaceRoot) {
        if (path == null || projectRoot == null || path.isBlank()) return toPortable(path, projectRoot);
        Path p = Path.of(path);
        if (!p.isAbsolute()) return path;
        Path root = projectRoot.toAbsolutePath().normalize();
        Path abs = p.normalize();
        if (abs.equals(root) || abs.startsWith(root)) return toPortable(path, projectRoot);
        Path ws = PathForm.workspaceDir(projectRoot, workspaceRoot);
        if (ws != null && abs.startsWith(ws)) {
            return root.relativize(abs).toString().replace(java.io.File.separatorChar, '/');
        }
        return toPortable(path, projectRoot);
    }

    /**
     * Project-relative when the path is under {@code projectRoot} (M35.11) — tried first, so a project
     * inside the home directory is not written {@code ~}-relative; otherwise {@link #toPortable(String)}.
     * Forward slashes regardless of platform: the file is read by {@code Path.resolve}, which accepts
     * them everywhere, and a committed file must not carry one machine's separator.
     */
    String toPortable(String path, Path projectRoot) {
        if (path == null || projectRoot == null || path.isBlank()) return toPortable(path);
        Path p = Path.of(path);
        if (!p.isAbsolute()) return path;                      // already relative: leave it as written
        Path root = projectRoot.toAbsolutePath().normalize();
        Path abs = p.normalize();
        if (abs.equals(root)) return ".";
        if (!abs.startsWith(root)) return toPortable(path);
        return root.relativize(abs).toString().replace(java.io.File.separatorChar, '/');
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
