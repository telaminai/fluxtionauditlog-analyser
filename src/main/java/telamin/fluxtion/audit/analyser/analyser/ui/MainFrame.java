package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;
import telamin.fluxtion.audit.analyser.analyser.export.RecordExporter;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.io.S3Source;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateArchive;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateClient;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application window (spec §8): a menu/toolbar, the log table over the detail viewer in a vertical
 * split, and a status bar. Loading happens on the background executor; the UI stays responsive.
 */
public final class MainFrame extends JFrame {

    private static final int MAX_DETAIL_RECORDS = 100;
    private static final int INFER_SCAN_LIMIT = 500;
    private static final String STRATEGY_PKG = "com.acme.marketmaker.strategy";

    private final ConfigStore configStore = new ConfigStore();
    /** M19.12: separate from AppConfig so the Fluxtion build key cannot enter shared settings. */
    private final telamin.fluxtion.audit.analyser.analyser.config.FluxtionKeyStore fluxtionKeyStore =
            new telamin.fluxtion.audit.analyser.analyser.config.FluxtionKeyStore();
    private final AppConfig config;
    /** M19.5: one pinned public origin; downloaded content is extracted but never executed. */
    private final TemplateClient templateClient = TemplateClient.playground();
    private final TemplateArchive templateArchive = new TemplateArchive();

    private final LogTablePanel tablePanel = new LogTablePanel();
    /** M36: the LEFT COLUMN holds the start page or the records+detail pair, never both. */
    private final java.awt.CardLayout recordsLayout = new java.awt.CardLayout();
    private final JPanel recordsCards = new JPanel(recordsLayout);
    private StartPanel startPanel;
    private final DetailPanel detailPanel = new DetailPanel();
    private final EventFilterPanel eventFilterPanel = new EventFilterPanel();
    private final SummaryPanel summaryPanel = new SummaryPanel();
    private final SourcePanel sourcePanel = new SourcePanel();
    private final SourceService sourceService = new SourceService();
    private final LlmPanel llmPanel = new LlmPanel();
    private final GraphTabs graphTabs = new GraphTabs();
    private ReportsPanel reportsPanel;   // constructed in the ctor once its collaborators exist
    private final TopologyPanel topologyPanel = new TopologyPanel();
    private final TimeRangeSlider timeSlider = new TimeRangeSlider();
    private final JComboBox<WindowSpan> windowCombo = new JComboBox<>(WindowSpan.ALL_OPTIONS);
    /** Chrome whose colours are derived from the theme, so they must be recomputed when it changes. */
    private JPanel filterBar;
    private NavRail navRail;
    /** Every live "Show flagged only" checkbox, kept in step — the menu's and any open popup's. */
    private final List<JCheckBoxMenuItem> flaggedOnlyToggles = new ArrayList<>();
    private final JScrollBar windowScroll = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1000, 0, 1000);
    private boolean syncingWindow;      // guards the combo/scrollbar ↔ slider feedback loop
    private final HistoryComboBox searchField = new HistoryComboBox();
    private final JLabel showingLabel = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Open a log — File ▸ Open (or the toolbar), drag a file in, or File ▸ Open from S3.");
    private final JMenu recentMenu = new JMenu("Open recent audit log");
    private final JMenu recentGraphmlMenu = new JMenu("Open recent GraphML");
    private final JMenu recentProjectsMenu = new JMenu("Open recent project");
    /** Enabled only with a project open — forking or closing nothing is not an action. */
    private final JMenuItem saveProjectAsItem = new JMenuItem("Save project as…");
    private final JMenuItem closeProjectItem = new JMenuItem("Close project");
    private final JMenuItem closeLogItem = new JMenuItem("Close log");
    private final JMenuItem closeGraphItem = new JMenuItem("Close graph");
    private final JMenuItem resetItem = new JMenuItem("Reset (close log + graph)");
    private telamin.fluxtion.audit.analyser.analyser.config.ProjectSession project;
    /**
     * Coalesces project writes. A profile is often a committed file, so a burst of graph tweaks should
     * be one diff hunk rather than fifteen — {@link ProjectSession} owns the semantics, this owns the
     * clock.
     */
    private javax.swing.Timer projectSaveDebounce;
    /** Whether opening a log should offer the project it sits in, and when to stay quiet (M20.3). */
    private final telamin.fluxtion.audit.analyser.analyser.config.ProjectAutoDetect projectDetect =
            new telamin.fluxtion.audit.analyser.analyser.config.ProjectAutoDetect();
    private JTabbedPane sideTabs;

    private LogStore store;
    private LogTableModel tableModel;
    private String logDisplayLocation;   // what the user opened (path or s3:// URI)
    private String logLocalPath;         // the local file the store actually reads (temp file for S3)
    private FilterState filter;
    private Timer searchDebounce;
    private List<LogRecord> selectedRecords = List.of();
    /** What the startup project load had to say, shown once the status bar exists; null when silent. */
    private final telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.LoadResult projectLoadNote;

    private final java.util.Set<Integer> flaggedRows = new java.util.HashSet<>();
    /**
     * What has been concluded about a flagged record, by model row: the note and any suggested fix.
     *
     * <p>One store, three readers — the table's note column, the callout painted on the topology, and the
     * exported report. Written in exactly one place (a flag), because the same sentence maintained in two
     * places is the same sentence until it isn't.
     */
    private final java.util.Map<Integer, telamin.fluxtion.audit.analyser.analyser.report.Finding> findings
            = new java.util.HashMap<>();
    private boolean flaggedOnly = false;
    /** Guards the table ⇄ topology cursor loop: a selection we caused must not re-drive the cursor. */
    private boolean steppingSelection;

    // follow / tail mode (H8.7): poll a growing local file and append new records live
    private static final int FOLLOW_POLL_MS = 1000;
    private Timer followTimer;
    private boolean following;
    private String followPath;                       // local path being tailed, or null
    private JToggleButton followButton;              // toolbar toggle (kept in sync)
    private JCheckBoxMenuItem followMenuItem;        // File-menu toggle (kept in sync)

    // assistant actions (M10): the render executor + the opt-in localhost REST transport (slice 4)
    private ActionExecutor actionExecutor;
    private telamin.fluxtion.audit.analyser.analyser.net.ActionServer actionServer;
    private final String actionToken = java.util.UUID.randomUUID().toString();   // per-run nonce

    public MainFrame() {
        super("Fluxtion Audit Log Analyser");
        this.config = configStore.load();
        // M20 — the session is built FIRST so it can snapshot the user's own settings before the
        // project overwrites them; then it applies the active project over the project-scoped
        // categories. A moved repository clears the pointer and says so; startup never fails on it.
        this.project = new telamin.fluxtion.audit.analyser.analyser.config.ProjectSession(
                config, new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare(),
                () -> projectSaveDebounce.restart());
        this.projectLoadNote = project.activateOnStartup();
        // 800ms: long enough that dragging a slider is one write, short enough that closing the laptop
        // straight after an edit still persists it
        this.projectSaveDebounce = new javax.swing.Timer(800, e -> flushProject());
        this.projectSaveDebounce.setRepeats(false);
        setIconImages(AppImages.icons());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildMenu();
        buildLayout();
        installTablePopup();
        restoreBounds();
        wireSelection();
        sourcePanel.bind(sourceService);
        graphTabs.setTimeClickHandler(this::gotoNearestRecordByTime);
        graphTabs.setMarkerClickHandler(row -> tablePanel.selectModelRow(row));   // the marker IS the record
        graphTabs.setFlagRugSource(this::flagRugMap);                              // M32.6: the rug's seam
        // B-M20-3: graph edits (UI or verb) persist as they happen, to the ACTIVE tier — and every
        // profile write first captures the live tabs, so no flush can ever write a stale graph list.
        graphTabs.setChangeListener(this::onGraphsEdited);
        project.setPreSave(this::syncOpenGraphsIntoConfig);
        // M27.3: named focuses live in the config's project tier; save/recall/delete persist like graphs
        topologyPanel.bindNamedFocuses(() -> config.namedFocuses, this::onGraphsEdited);
        actionExecutor = new ActionExecutor(
                () -> store, () -> filter, graphTabs, tablePanel, this::flagRowsFromAction);
        llmPanel.setVocabularySupplier(this::vocabularyText);   // M38.2: the glossary reaches the assistant's prompt
        actionControl = new AppControlAdapter();
        actionExecutor.bind(topologyPanel, actionControl);
        refreshProjectPanel();                       // M37: state the empty session too
        refreshMcpIndicator();                       // D-AI9: and say whether an AI client reaches us
        startMcpIndicatorWatch();
        actionExecutor.bindExportPolicy(() -> config);   // B1: file-writing verbs are opt-in + confined
        actionExecutor.setReadGrants(this::sessionFileGrants);   // M29 D-F4: the chooser is the grant
        readerRegistry.loadPlugins(java.nio.file.Path.of(
                System.getProperty("user.home"), ".fluxtion-analyser", "plugins"));
        actionExecutor.setTimeOrderNote(() -> timeOrderReport.isClean() ? null
                : "time order is violated in this log — time-anchored answers may be approximate; "
                        + "see 'context'.timeOrder");   // M30 D-R4
        llmPanel.bind(() -> config, () -> selectedRecords, sourceService::selectedFqn,
                this::currentLogFileInfo, () -> store, actionExecutor, sourceService);
        // M19.7: `--rest` turns the transport on for this launch AND persists it, and says so — an
        // agent-enabled setting that survives is what the next human launch needs; the sin would be
        // persisting it silently, not persisting it
        firstRunAtStart = !configStore.exists();   // decided BEFORE the save below can create the file
        if (Boolean.getBoolean(telamin.fluxtion.audit.analyser.Main.REST_PROPERTY) && !config.assistantActionsRest) {
            config.assistantActionsRest = true;
            saveConfigQuietly();
            System.out.println("[analyser] --rest: REST transport enabled and saved (Settings ▸ Assistant ▸ "
                    + "localhost REST); turn it off there if this machine should not offer it");
        }
        applyRestServer();   // start the localhost REST transport if the profile opted in
        detailPanel.setInstanceSourceOpener(this::openNodeSource);
        detailPanel.setEventHandlerOpener(rec -> {
            sourcePanel.showDispatchFor(rec);              // scroll the EP to the record's handler method
            if (sideTabs != null) sideTabs.setSelectedComponent(sourcePanel);
        });
        detailPanel.setExplainAction(this::explainSelection);
        // one GraphTargets, shared: the detail viewer and the topology plot through the same path, so a
        // series added from either lands on the same graph in the same way (M21.5)
        DetailPanel.GraphTargets graphTargets = new DetailPanel.GraphTargets() {
            @Override public String currentName() { return graphTabs.selectedGraphName(); }
            @Override public java.util.List<String> names() { return graphTabs.graphNames(); }
            @Override public void addSeries(String graphName, String instanceId, String key) {
                graphTabs.addSeriesTo(graphName,
                        new telamin.fluxtion.audit.analyser.analyser.graph.GraphKey(instanceId, key));
                sideTabs.setSelectedComponent(graphTabs);   // show the plot the series landed on
            }
        };
        detailPanel.setGraphTargets(graphTargets);
        topologyPanel.setGraphTargets(graphTargets);
        topologyPanel.setInstanceSourceOpener(this::openNodeSource);
        topologyPanel.setFilterAction(this::filterToInstance);
        // node tooltips pick up the class javadoc when a source root reaches the class
        topologyPanel.setSourceResolver(sourceService::sourceForFqn);
        // one place remembers a loaded topology, whichever entry point loaded it
        topologyPanel.onTopologyLoaded(f -> { rememberGraphml(f); refreshProjectPanel(); });
        // the topology gets its own source viewer, sharing this service — so navigating from the graph
        // keeps the graph on screen instead of switching to the sibling Source tab
        topologyPanel.bindSource(sourceService);
        topologyPanel.setDisplayPrefs(config.topologySpacingPercent, config.topologyTextSize);
        topologyPanel.setSavedView(config.topologyZoom, config.topologyPanX, config.topologyPanY,
                config.topologyOrientation);
        topologyPanel.setSourceSync(config.topologySyncSource);
        topologyPanel.onDisplayPrefsChanged(() -> {
            config.topologySpacingPercent = topologyPanel.spacingPercent();
            config.topologyTextSize = topologyPanel.textSize();
            config.topologyZoom = topologyPanel.zoom();
            config.topologyPanX = topologyPanel.panX();
            config.topologyPanY = topologyPanel.panY();
            config.topologyOrientation = topologyPanel.orientationName();
            config.topologySyncSource = topologyPanel.isSourceSyncOn();
            saveConfigQuietly();
        });
        // stepping walks the FILTERED sequence, so it honours the shared filter like every other view
        topologyPanel.setRecordSource(new telamin.fluxtion.audit.analyser.analyser.topology.StepCursor.RecordSource() {
            @Override public int size() {
                return store == null ? 0 : tablePanel.visibleRowCount();
            }

            @Override public LogRecord record(int index) {
                int modelRow = tablePanel.modelRowAt(index);
                return store == null || modelRow < 0 ? null : store.record(modelRow);
            }
        });
        // cursor rolled into another record → move the table selection to match
        topologyPanel.onRecordChanged(filteredIndex -> {
            int modelRow = tablePanel.modelRowAt(filteredIndex);
            if (modelRow >= 0) {
                steppingSelection = true;
                try {
                    tablePanel.selectModelRow(modelRow);
                } finally {
                    steppingSelection = false;
                }
            }
        });
        // cursor moved within a record → highlight that row in the detail viewer
        topologyPanel.onRowChanged(detailPanel::highlightNodeLog);
        tablePanel.setFlagTester(flaggedRows::contains);
        tablePanel.setFlagToggle(this::toggleFlags);
        tablePanel.setNoteProvider(row -> {
            var f = findings.get(row);
            return f == null || !f.hasNote() ? null : f.note();
        });
        // the callout on the graph is the same finding as the note in the table, resolved through the
        // table's own view→model mapping so stepping and filtering cannot pull them apart
        topologyPanel.setFindingProvider(filteredIndex -> {
            int modelRow = tablePanel.modelRowAt(filteredIndex);
            return modelRow < 0 ? null : findings.get(modelRow);
        });
        // said after the status bar exists, and only when there is something to say: a project that
        // loaded, or a pointer that was stale and has been cleared
        updateProjectMenuState();
        setTitleForProject();
        if (projectLoadNote != null) {
            status.setText(projectLoadNote.message());
        }
        installGlobalKeys();
        installFileDrop();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onExit(); }
        });
    }

    /**
     * The left edge: a {@link NavRail} plus whatever it is currently showing. The event-type checklist
     * starts docked (it is the app's main filter) but can be collapsed to the rail, and the state
     * persists — a window that forgets its layout every launch teaches people not to adjust it.
     */
    private JPanel buildWestRail() {
        NavRail rail = new NavRail();
        this.navRail = rail;
        JPanel west = new JPanel(new BorderLayout());

        eventFilterPanel.setVisible(!config.eventFilterCollapsed);
        rail.addToggle("Event types", !config.eventFilterCollapsed, showing -> {
            eventFilterPanel.setVisible(showing);
            config.eventFilterCollapsed = !showing;
            saveConfigQuietly();
            layoutWest(west);
        });
        // M37: what is in force — the Project panel, stacked under Event types (owner decision 2). It is a
        // rendering of `context` (D-L1); refreshProjectPanel() is the only writer.
        projectPanel = new ProjectPanel(new ProjectPanel.Navigator() {
            @Override public void showTab(String title) { selectTab(title); }
            @Override public void openSettings(String page) {
                ConfigPanel.show(MainFrame.this, config, MainFrame.this::onConfigChanged,
                        MainFrame.this::readerSummaries, page);
            }
        });
        projectPanel.setVisible(!config.projectPanelCollapsed);
        rail.addToggle("Project", !config.projectPanelCollapsed, showing -> {
            projectPanel.setVisible(showing);
            config.projectPanelCollapsed = !showing;
            saveConfigQuietly();
            layoutWest(west);
        });
        // the same column checkboxes as the menu, one click from the table instead of up in the menu bar
        rail.addAction("Columns", () -> {
            JPopupMenu popup = new JPopupMenu();
            for (java.awt.Component item : buildColumnsMenu().getMenuComponents()) popup.add(item);
            popup.show(rail, rail.getWidth(), 0);
        });
        rail.addGap();

        west.add(rail, BorderLayout.WEST);
        layoutWest(west);
        return west;
    }

    /** M38.2: the glossary pointer as context reports it — path, where it lands, whether it exists, its text. */
    private Map<String, Object> vocabularyForContext() {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        if (config.vocabularyPath == null || config.vocabularyPath.isBlank()) return v;
        Path root = project.hasProject()
                ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile()) : null;
        v.put("path", config.vocabularyPath);
        Path abs = telamin.fluxtion.audit.analyser.analyser.config.Runbooks.resolve(root, config.vocabularyPath);
        if (abs != null) {
            v.put("resolved", abs.toString());
            boolean exists = java.nio.file.Files.isRegularFile(abs);
            v.put("exists", exists);
            if (exists) {
                String text = vocabularyText();
                if (text != null) {
                    v.put("text", text.length() > telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.MAX_VOCABULARY_CHARS
                            ? text.substring(0, telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.MAX_VOCABULARY_CHARS) : text);
                    if (text.length() > telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.MAX_VOCABULARY_CHARS) v.put("truncated", true);
                }
            }
        }
        v.put("from", project.hasProject() ? "project" : "own settings");
        return v;
    }

    /** The glossary file's text, or null when there is no pointer, no project root, or no file. */
    private String vocabularyText() {
        if (config.vocabularyPath == null || config.vocabularyPath.isBlank() || !project.hasProject()) return null;
        Path abs = telamin.fluxtion.audit.analyser.analyser.config.Runbooks.resolve(
                telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile()), config.vocabularyPath);
        try {
            return abs != null && java.nio.file.Files.isRegularFile(abs) ? java.nio.file.Files.readString(abs) : null;
        } catch (java.io.IOException | RuntimeException e) {
            return null;                    // a glossary that cannot be read is absent, and the panel says "NOT found"
        }
    }

    /** M38.1: each pointer with where it lands on THIS machine and whether the file is there. */
    private List<Map<String, Object>> runbooksForContext() {
        List<Map<String, Object>> rbs = new ArrayList<>();
        Path root = project.hasProject()
                ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile()) : null;
        config.runbooks.forEach((name, ptr) -> {
            String rel = ptr.path();
            Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("name", name);
            one.put("path", rel);
            // M43.2 (D-AI5): the DECLARED description, so a model can choose which runbook is relevant
            // without opening every file. Served from the profile, never read from the file — a pointer
            // whose file changes must not silently change what context says.
            if (ptr.description() != null) one.put("description", ptr.description());
            Path abs = telamin.fluxtion.audit.analyser.analyser.config.Runbooks.resolve(root, rel);
            if (abs != null) {
                one.put("resolved", abs.toString());
                one.put("exists", java.nio.file.Files.isRegularFile(abs));
            }
            one.put("from", project.hasProject() ? "project" : "own settings");
            one.put("note", "a pointer — read the file from the repository; the analyser stores no instructions and executes nothing");
            rbs.add(one);
        });
        return rbs;
    }

    /**
     * D-AI9 — the AI status light. It answers one question: would an AI client asking right now reach
     * THIS window? Not "is a client connected" — that is a different fact, needs a probe, and is true
     * only at the instant it is measured, so it lives in the setup dialog instead.
     */
    private final JLabel mcpLight = new JLabel();

    /**
     * Keep the light honest about a fact that changes WITHOUT this window doing anything.
     *
     * <p>Found by tools/verify-m43.py, which started a second analyser under the same home and watched
     * the endpoint file change owner: the newcomer takes the endpoint, so the FIRST window silently
     * stops being the one an AI client reaches. Its light said "MCP ready" throughout, because until now
     * it only refreshed at startup, on a theme switch and on the transport toggle — none of which happen
     * when another process takes over.
     *
     * <p>That is the precise state D-AI9 exists to reveal ("MCP elsewhere"), so a light that cannot
     * notice it is worse than no light: it actively asserts the wrong thing. A poll is the right shape
     * here because nothing notifies us — the cost is one small file read and a pid compare, and the
     * window-activation hook means the common case (you come back to this window) is already correct
     * before the timer fires.
     */
    private void startMcpIndicatorWatch() {
        mcpIndicatorTimer = new javax.swing.Timer(5000, e -> refreshMcpIndicator());
        mcpIndicatorTimer.setRepeats(true);
        mcpIndicatorTimer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowActivated(java.awt.event.WindowEvent e) {
                refreshMcpIndicator();   // returning to this window is exactly when you need the truth
            }
        });
    }

    /** Re-read the local transport state and repaint the light. Cheap: a file read and a pid compare. */
    private javax.swing.Timer mcpIndicatorTimer;    // stopped in onExit like followTimer

    private void refreshMcpIndicator() {
        var endpointFile = telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile.wellKnown();
        long pid = ProcessHandle.current().pid();
        var endpoint = endpointFile.read();
        var readiness = telamin.fluxtion.audit.analyser.analyser.mcp.McpSetupState.classify(
                config.assistantActionsRest, endpoint, endpoint != null && endpoint.alive(), pid);
        // The pointer file is disposable, the server is not: when another window took the endpoint and
        // then closed (or crashed), the file is gone or names a dead pid while this server is still
        // listening. Without this, the light said "MCP starting" for ever (owner's eyeball, 2026-08-28).
        // A LIVE owner is never displaced — shouldReclaim is false for OTHER_INSTANCE.
        if (telamin.fluxtion.audit.analyser.analyser.mcp.McpSetupState.shouldReclaim(readiness, actionServer != null)
                && actionServer.republish()) {
            endpoint = endpointFile.read();
            readiness = telamin.fluxtion.audit.analyser.analyser.mcp.McpSetupState.classify(
                    config.assistantActionsRest, endpoint, endpoint != null && endpoint.alive(), pid);
        }
        var view = telamin.fluxtion.audit.analyser.analyser.mcp.McpIndicator.of(readiness);
        mcpLight.setText("\u25cf " + view.label());
        mcpLight.setToolTipText("<html><body style='width:320px'>" + view.detail() + "</body></html>");
        mcpLight.setForeground(switch (view.level()) {
            case GOOD -> UiTheme.okForeground();
            case ATTENTION -> UiTheme.attentionForeground();   // amber, not red (D-AI9)
            case NEUTRAL -> UiTheme.mutedForeground();
        });
    }

    private ProjectPanel projectPanel;
    private JSplitPane westSplit;            // Event types over Project, inside the west column
    private JSplitPane westOuter;            // the west column beside the records — the user drags this
    private AppControlAdapter actionControl;

    private boolean westHasPanel() {
        return eventFilterPanel.isVisible() || (projectPanel != null && projectPanel.isVisible());
    }

    /**
     * The west column's centre: Event types, the Project panel, both in a vertical split, or nothing.
     * Rebuilt on every toggle rather than hiding a split-pane child — JSplitPane keeps giving an invisible
     * child its share, and the divider is persisted only when both are showing (it is meaningless otherwise).
     */
    private void layoutWest(JPanel west) {
        java.awt.Component centre = ((BorderLayout) west.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (centre != null) west.remove(centre);
        if (westSplit != null && westSplit.getTopComponent() != null && westSplit.getBottomComponent() != null) {
            config.westDivider = westSplit.getDividerLocation();
            westSplit.setTopComponent(null);
            westSplit.setBottomComponent(null);
        }
        boolean events = eventFilterPanel.isVisible(), loaded = projectPanel.isVisible();
        if (events && loaded) {
            westSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventFilterPanel, projectPanel);
            westSplit.setResizeWeight(0.55);
            westSplit.setBorder(BorderFactory.createEmptyBorder());
            westSplit.setContinuousLayout(true);
            if (config.westDivider > 0) westSplit.setDividerLocation(config.westDivider);
            west.add(westSplit, BorderLayout.CENTER);
        } else if (events) {
            west.add(eventFilterPanel, BorderLayout.CENTER);
        } else if (loaded) {
            west.add(projectPanel, BorderLayout.CENTER);
        }
        // both toggles off: the column shrinks to the rail; a toggle back on reopens it at the chosen width
        if (westOuter != null) {
            westOuter.setDividerLocation(events || loaded
                    ? Math.max(config.westWidth, navRail.getPreferredSize().width + 8)
                    : navRail.getPreferredSize().width + 4);
        }
        west.revalidate();
        west.repaint();
    }

    /**
     * M37 D-L6 — re-render the Project panel from `context`. Called at every lifecycle event site and
     * nowhere else; it reads the same payload an agent gets, so the two cannot drift.
     */
    private void refreshProjectPanel() {
        if (projectPanel == null || actionControl == null) return;
        try {
            projectPanel.render(ProjectModel.from(actionControl.context().payload()));
        } catch (RuntimeException e) {
            // the panel is a courtesy view of state that already exists; it must never take the app down
            projectPanel.render(ProjectModel.from(null));
        }
    }

    /** View menu with a checkbox per record-table column (persisted; some hidden by default). */
    private JMenu buildColumnsMenu() {
        JMenu view = new JMenu("Columns");
        for (String name : LogTableModel.columnNames()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, !config.hiddenColumns.contains(name));
            item.addActionListener(e -> {
                if (item.isSelected()) config.hiddenColumns.remove(name);
                else if (!config.hiddenColumns.contains(name)) config.hiddenColumns.add(name);
                config.hiddenColumnsSet = true;
                tablePanel.setVisibleColumns(new java.util.HashSet<>(config.hiddenColumns));
                saveConfigQuietly();
            });
            view.add(item);
        }
        return view;
    }

    /** F3 / Shift+F3 anywhere in the window jump to the next / previous anomaly row. */
    private void installGlobalKeys() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "anomaly-next");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK), "anomaly-prev");
        root.getActionMap().put("anomaly-next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { gotoAnomaly(true); }
        });
        root.getActionMap().put("anomaly-prev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { gotoAnomaly(false); }
        });
    }

    private JMenu buildRecordsMenu() {
        JMenu menu = new JMenu("Records");
        addRecordActions(menu);
        return menu;
    }

    /**
     * The record actions, added to whichever container asked for them — the Records menu, or the table's
     * own right-click. Built fresh each time rather than shared, because a Swing menu item lives in one
     * container only; the one piece of state involved (<i>Show flagged only</i>) is read from the field at
     * build time and written back to every copy, so the two entry points cannot drift apart.
     */
    private void addRecordActions(java.awt.Container into) {
        boolean haveSelection = !selectedRecords.isEmpty();

        JMenuItem flag = new JMenuItem("Flag / unflag selected  (F)");
        flag.setEnabled(haveSelection);
        flag.addActionListener(e -> toggleFlags(tablePanel.selectedModelRows()));
        into.add(flag);

        JCheckBoxMenuItem only = new JCheckBoxMenuItem("Show flagged only", flaggedOnly);
        only.addActionListener(e -> {
            flaggedOnly = only.isSelected();
            for (JCheckBoxMenuItem other : flaggedOnlyToggles) other.setSelected(flaggedOnly);
            tablePanel.reFilter();
            onFilterChanged();
        });
        flaggedOnlyToggles.add(only);
        into.add(only);

        JMenuItem clearFlags = new JMenuItem("Clear all flags");
        clearFlags.setEnabled(!flaggedRows.isEmpty());
        clearFlags.addActionListener(e -> {
            flaggedRows.clear();
            findings.clear();
            tablePanel.reFilter();
            tablePanel.repaintRows();
            topologyPanel.refreshFinding();
            graphTabs.refreshFlagRug();
        });
        into.add(clearFlags);

        JMenuItem writeFinding = new JMenuItem("Write a finding for this record…");
        writeFinding.setToolTipText("Write what is wrong with this cycle — shown in the table, "
                                   + "as a callout on the topology, and in an exported report");
        writeFinding.setEnabled(haveSelection);
        writeFinding.addActionListener(e -> writeFindingForSelection());
        into.add(writeFinding);

        JMenuItem report = new JMenuItem("Export finding to PDF…");
        report.setToolTipText("The explanation, the event, the node log, the graph and — if one is "
                             + "open — a plot, as one document");
        report.setEnabled(haveSelection);
        report.addActionListener(e -> exportFindingWithChooser());
        into.add(report);

        addSeparator(into);
        JMenuItem copyYaml = new JMenuItem("Copy selected as YAML");
        copyYaml.setToolTipText("Copy the selected record(s) raw YAML to the clipboard");
        copyYaml.setEnabled(haveSelection);
        copyYaml.addActionListener(e -> copySelectedAsYaml());
        into.add(copyYaml);

        JMenuItem diff = new JMenuItem("Diff selected two records");
        diff.setEnabled(selectedRecords.size() == 2);
        diff.addActionListener(e -> diffSelected());
        into.add(diff);

        JMenuItem explain = new JMenuItem("Explain selected with LLM");
        explain.setEnabled(haveSelection);
        explain.addActionListener(e -> explainSelection());
        into.add(explain);

        addSeparator(into);
        JMenuItem exportCsv = new JMenuItem("Export records (CSV)…");
        exportCsv.addActionListener(e -> exportRecords(false));
        into.add(exportCsv);
        JMenuItem exportYaml = new JMenuItem("Export records (YAML)…");
        exportYaml.addActionListener(e -> exportRecords(true));
        into.add(exportYaml);
    }

    /** JMenu and JPopupMenu both take separators but share no interface that says so. */
    private static void addSeparator(java.awt.Container into) {
        if (into instanceof JMenu menu) menu.addSeparator();
        else if (into instanceof JPopupMenu popup) popup.addSeparator();
    }

    /**
     * Right-click on the records table: the same actions as the Records menu, plus the column chooser,
     * because the table is where a column being hidden is noticed. Built on each show so the enabled
     * states match the selection under the cursor rather than the selection when the window opened.
     */
    private void installTablePopup() {
        javax.swing.JTable table = tablePanel.table();
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }

            private void maybeShow(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                // right-clicking outside the selection acts on the row under the cursor, as everywhere else
                if (row >= 0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row, row);

                JPopupMenu popup = new JPopupMenu();
                int toggles = flaggedOnlyToggles.size();
                addRecordActions(popup);
                addSeparator(popup);
                JMenu columns = buildColumnsMenu();
                columns.setText("Columns");
                popup.add(columns);
                // the popup is rebuilt on every right-click, so its toggle must leave the sync list with
                // it — otherwise the list grows without bound for the life of the window
                popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                    @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent ev) { }
                    @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent ev) {
                        while (flaggedOnlyToggles.size() > toggles) {
                            flaggedOnlyToggles.remove(flaggedOnlyToggles.size() - 1);
                        }
                    }
                    @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent ev) { }
                });
                popup.show(table, e.getX(), e.getY());
            }
        });
    }

    private void copySelectedAsYaml() {
        if (selectedRecords.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedRecords.size(); i++) {
            if (i > 0) sb.append("\n---\n");
            sb.append(selectedRecords.get(i).rawText());
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
        status.setText("Copied " + selectedRecords.size() + " record(s) as YAML to the clipboard.");
    }

    private void clearSearchHistory() {
        config.searchHistory.clear();
        searchField.setHistory(config.searchHistory);
        saveConfigQuietly();
        status.setText("Search history cleared.");
    }

    private void diffSelected() {
        int[] rows = tablePanel.selectedModelRows();
        if (store == null || rows.length != 2) {
            JOptionPane.showMessageDialog(this, "Select exactly two records to diff.", "Diff",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LogRecord a = store.record(rows[0]);
        LogRecord b = store.record(rows[1]);
        RecordDiffDialog.show(this, a, b,
                "A · " + TimeFormat.utc(a.logTime()), "B · " + TimeFormat.utc(b.logTime()));
    }

    private void toggleFlags(int[] modelRows) {
        for (int r : modelRows) {
            if (!flaggedRows.add(r)) { flaggedRows.remove(r); findings.remove(r); }
        }
        tablePanel.repaintRows();
        topologyPanel.refreshFinding();
        graphTabs.refreshFlagRug();   // the rug is DERIVED from the flags (M32.6)
        if (flaggedOnly) tablePanel.reFilter();
    }

    /** Flagged rows -> finding note (or null): the rug's payload map, snapshotted per refresh. */
    private java.util.Map<Integer, String> flagRugMap() {
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        for (int r : flaggedRows) {
            var f = findings.get(r);
            out.put(r, f == null || !f.hasNote() ? null : f.note());
        }
        return out;
    }

    /**
     * Flag rows from the assistant {@code flag} action: sets (never toggles) the flag, and records what
     * was concluded. A caller supplying only one of note/fix is refining the finding, so the other is
     * kept — see {@link telamin.fluxtion.audit.analyser.analyser.report.Finding#merge}.
     */
    private void flagRowsFromAction(int[] modelRows, String note, String fix) {
        for (int r : modelRows) {
            flaggedRows.add(r);
            if (note != null || fix != null) {
                var existing = findings.get(r);
                findings.put(r, existing == null
                        ? new telamin.fluxtion.audit.analyser.analyser.report.Finding(r, note, fix)
                        : existing.merge(note, fix));
            }
        }
        tablePanel.repaintRows();
        topologyPanel.refreshFinding();
        graphTabs.refreshFlagRug();
        if (flaggedOnly) tablePanel.reFilter();
    }

    /**
     * The human half of the finding loop: write (or edit) what is wrong with the selected record.
     *
     * <p>Same store, same fields and same single write path as the assistant's {@code flag} verb — an
     * agent's diagnosis and a person's correction of it are the same kind of thing, and giving each its
     * own store is how they end up contradicting each other on the same screen.
     */
    private void writeFindingForSelection() {
        int[] rows = tablePanel.selectedModelRows();
        if (rows.length == 0) return;
        int row = rows[0];
        var existing = findings.get(row);

        JTextArea note = new JTextArea(existing == null ? "" : existing.note(), 5, 46);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        JTextArea fix = new JTextArea(existing == null || existing.fix() == null ? "" : existing.fix(), 3, 46);
        fix.setLineWrap(true);
        fix.setWrapStyleWord(true);

        JPanel form = new JPanel(new java.awt.BorderLayout(0, 6));
        JPanel top = new JPanel(new java.awt.BorderLayout(0, 4));
        top.add(new JLabel("What is wrong with record " + row + "?"), java.awt.BorderLayout.NORTH);
        top.add(new JScrollPane(note), java.awt.BorderLayout.CENTER);
        JPanel bottom = new JPanel(new java.awt.BorderLayout(0, 4));
        bottom.add(new JLabel("Likely cause / suggested fix (optional)"), java.awt.BorderLayout.NORTH);
        bottom.add(new JScrollPane(fix), java.awt.BorderLayout.CENTER);
        form.add(top, java.awt.BorderLayout.CENTER);
        form.add(bottom, java.awt.BorderLayout.SOUTH);

        int ok = JOptionPane.showConfirmDialog(this, form, "Explain record " + row,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String noteText = note.getText().strip();
        String fixText = fix.getText().strip();
        if (noteText.isEmpty() && fixText.isEmpty()) {
            findings.remove(row);
        } else {
            findings.put(row, new telamin.fluxtion.audit.analyser.analyser.report.Finding(
                    row, noteText, fixText.isEmpty() ? null : fixText));
            flaggedRows.add(row);   // an explained record is a flagged one: findings live on the flag
        }
        tablePanel.repaintRows();
        topologyPanel.refreshFinding();
        graphTabs.refreshFlagRug();
        if (flaggedOnly) tablePanel.reFilter();
    }

    /** Export the selected record's finding, asking where to put it. */
    /** Named investigation reports (M33.4): config.reports IS the store — one list, project-tier. */
    private telamin.fluxtion.audit.analyser.analyser.report.ReportSpec reportByName(String name) {
        for (var r : config.reports) if (r.name().equals(name)) return r;
        return null;
    }

    /** Replace-by-name and persist. Loading config never comes through here: restore is not an edit. */
    private void putReport(telamin.fluxtion.audit.analyser.analyser.report.ReportSpec spec) {
        config.reports.removeIf(r -> r.name().equals(spec.name()));
        config.reports.add(spec);
        onGraphsEdited();
        if (reportsPanel != null) reportsPanel.refresh();
    }

    /**
     * A report's "open record" click (M33.4). A record hidden by the current filter must not fail
     * SILENTLY — a live eyeball pass hit exactly that ("I press the button, nothing happens"). The
     * click's intent is unambiguous, but widening the filter is a view mutation, so it is OFFERED
     * (M20.5/D-R5), then performed with the same minimal relaxation the goto verb's reveal uses.
     */
    private void openRecordFromReport(int row) {
        if (tablePanel.selectModelRow(row)) return;
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Record " + row + " is filtered out of the current view.\nWiden the filter to show it?",
                "Record hidden by the filter", javax.swing.JOptionPane.OK_CANCEL_OPTION);
        if (choice != javax.swing.JOptionPane.OK_OPTION || store == null) return;
        ActionExecutor.revealRecord(filter, store, row);
        if (!tablePanel.selectModelRow(row)) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Relaxed the filter, but the record is still hidden (likely 'Records ▸ Show "
                            + "flagged only').", "Still hidden", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private java.util.Set<String> focusNames() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (var f : config.namedFocuses) out.add(f.name());
        return out;
    }

    private java.util.List<String> reportNames() {
        return config.reports.stream()
                .map(telamin.fluxtion.audit.analyser.analyser.report.ReportSpec::name).toList();
    }

    /**
     * The {@code report {sections}} verb (M33.3): build/replace a named report, resolve it against
     * the live log, optionally render to PDF or export one table section to CSV. The echo follows
     * M26.4 — invalid sections skipped AND named, unresolved references named, nothing silent.
     */
    /**
     * WHERE the open log came from, as declared by whoever opened it (§E). Free text, null when
     * nobody said, and NEVER inferred from the path — a guessed system name is worse than none.
     * Set by {@code open {provenance}} and cleared with the log.
     */
    private String logProvenance;
    /** M38.3: WHERE logProvenance came from — "declared by the opener", or the project environment that supplied it. */
    private String logProvenanceSource;

    /**
     * M36 — show the start page exactly when there is no log, and the table exactly when there is.
     * One call site for the decision, so the two can never both be right.
     */
    private void syncRecordsCard() {
        if (startPanel != null) recordsLayout.show(recordsCards, store == null ? "start" : "table");
    }

    /**
     * Show the start page on demand (Help ▸ Start page, M36.1) without closing the open log.
     *
     * <p>The page normally appears by itself, because it IS the no-log state. That leaves it
     * unreachable for the people most likely to want it: anyone whose last log reopens at launch
     * never sees it, and closing a perfectly good log to read an introduction is a bad trade. So the
     * card can be raised directly — and the page then offers its own way back, which is what keeps
     * this a place you visit rather than a mode you have to escape. The next lifecycle change
     * (opening, closing, switching) calls {@link #syncRecordsCard()} and puts the right card back.
     */
    private void showStartPage() {
        if (startPanel == null) return;
        startPanel.showReturnToRecords(store != null);
        recordsLayout.show(recordsCards, "start");
    }

    /**
     * Open one of the bundled demo logs (M36). Deliberately an ORDINARY open — the same verb path a
     * user or agent takes — so the demo exercises the product rather than a special case that proves
     * nothing about it.
     */
    private void openDemoLog(Path log, boolean withGraph) {
        Path root = DemoAssets.install();
        // M36 review F3: the demo's source root is TRANSIENT. Adding it to config persisted a source
        // root into the user's own settings — and, with a project open, into that project's committed
        // profile — from a button whose contract is "nothing remembered, nothing personalised" (D-S3).
        // It lives in demoRoots and is appended at configure time; a restart forgets it, as it should.
        if (demoRoots.add(root.toString())) {
            sourceService.configure(effectiveSourceRoots(), config.selectedEventProcessor,
                    config.mavenRepos, config.searchMavenRepos);
            refreshProjectPanel();                                    // M37: the transient root is a row, labelled demo
        }
        if (withGraph) topologyPanel.load(DemoAssets.graphml());
        openFile(log, OpenRequest.HUMAN);
    }

    /** Source roots the demo added for this session only — never written to any config tier. */
    private final java.util.Set<String> demoRoots = new java.util.LinkedHashSet<>();

    /** The configured roots plus the session's demo root(s), for the source service — config stays clean. */
    private List<String> effectiveSourceRoots() {
        if (demoRoots.isEmpty()) return config.sourceRoots;
        List<String> all = new java.util.ArrayList<>(config.sourceRoots);
        for (String r : demoRoots) if (!all.contains(r)) all.add(r);
        return all;
    }

    /** The log actually open, as the fingerprint names it — one source for authoring and re-opening. */
    private String loadedLogName() {
        return logDisplayLocation == null ? "" : new File(logDisplayLocation).getName();
    }

    private telamin.fluxtion.audit.analyser.analyser.llm.ActionResult reportVerb(
            java.util.Map<String, Object> params, String resolvedPath) {
        if (store == null) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("no log is loaded");
        }
        var fp = telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint.of(
                store.index(), loadedLogName(), logProvenance, logProvenanceSource);   // M38.3 F1: how it was obtained
        String name = params.get("name") == null ? null : params.get("name").toString();

        // ---- CSV export of one table section from an EXISTING report --------------------------------
        Object csv = params.get("csv");
        if (csv != null) {
            Integer sectionIdx = csv instanceof Number n ? n.intValue() : null;
            if (sectionIdx == null || name == null) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "csv export needs 'name' (an existing report) and 'csv' (a table section index)");
            }
            var spec = reportByName(name);
            if (spec == null) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "no report named '" + name + "' — reports: " + reportNames());
            }
            if (sectionIdx < 0 || sectionIdx >= spec.sections().size()
                    || spec.sections().get(sectionIdx).kind()
                       != telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.Kind.TABLE) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "section " + sectionIdx + " of '" + name + "' is not a table");
            }
            if (resolvedPath == null) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "csv export needs 'path'");
            }
            var assembled = telamin.fluxtion.audit.analyser.analyser.report.ReportVerb
                    .assembleTable(spec.sections().get(sectionIdx), store, this::coverageForReport);
            try {
                Path out = Path.of(resolvedPath);
                if (out.getParent() != null) Files.createDirectories(out.getParent());
                Files.writeString(out, telamin.fluxtion.audit.analyser.analyser.export.RecordExporter
                        .tableToCsv(assembled.table().columns(), assembled.table().rows()));
                Map<String, Object> echo = new java.util.LinkedHashMap<>();
                echo.put("path", out.toAbsolutePath().toString());
                echo.put("rows", assembled.table().rows().size());
                echo.put("columns", assembled.table().columns().size());
                if (!assembled.notes().isEmpty()) echo.put("warnings", assembled.notes());
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("report", "wrote", echo);
            } catch (java.io.IOException e) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "could not write " + resolvedPath + ": " + e.getMessage());
            }
        }

        // ---- build/replace the named report ---------------------------------------------------------
        var parsed = telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.parse(params, fp,
                telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot.of(filter));
        var spec = parsed.spec();
        boolean replaced = reportByName(spec.name()) != null;
        putReport(spec);
        // reveal what was just built, exactly as the graph verb reveals the Graph tab: the report is
        // a navigation surface and the human should see what the agent assembled
        if (reportsPanel != null) {
            sideTabs.setSelectedComponent(reportsPanel);
            reportsPanel.select(spec.name());
        }

        var resolution = telamin.fluxtion.audit.analyser.analyser.report.ReportResolver.resolve(
                spec, store.index(), loadedLogName(), logProvenance, findings,
                new java.util.HashSet<>(graphTabs.graphNames()), focusNames(), filter);

        java.util.List<String> warnings = new java.util.ArrayList<>(parsed.warnings());
        for (var sr : resolution.sections()) {
            if (!sr.resolved()) warnings.add("section " + sr.index() + ": " + sr.reason());
            if (sr.warning() != null) warnings.add("section " + sr.index() + ": " + sr.warning());
        }

        Map<String, Object> echo = new java.util.LinkedHashMap<>();
        echo.put("name", spec.name());
        echo.put("title", spec.title());
        echo.put(replaced ? "replaced" : "created", true);
        echo.put("sections", spec.sections().size());
        echo.put("writtenAgainst", fp.describe());
        if (resolution.summary() != null) echo.put("unresolved", resolution.summary());
        if (resolution.filterDifference() != null) echo.put("view", resolution.filterDifference());

        if (resolvedPath != null) {
            var render = renderReportPdf(spec, resolution, warnings);
            if (render != null) {
                try {
                    Path out = Path.of(resolvedPath);
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    Files.write(out, render);
                    echo.put("path", out.toAbsolutePath().toString());
                } catch (java.io.IOException e) {
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                            "could not write " + resolvedPath + ": " + e.getMessage());
                }
            }
        }
        if (!warnings.isEmpty()) echo.put("warnings", warnings);
        return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("report", "applied", echo);
    }

    /**
     * Coverage's graph facts belong to the UI, but its scoring is pure and shared with the action echo.
     * The report gets the complete ledger rather than coverage's intentionally short agent-facing gap
     * list; a PDF reader needs covered and excluded nodes to check the denominator too.
     */
    private telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.CoverageData coverageForReport(
            boolean filtered) {
        if (store == null || !topologyPanel.hasTopology()) {
            return new telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.CoverageData(
                    java.util.List.of(), null, java.util.List.of("coverage needs a loaded declared topology"),
                    "coverage needs a loaded declared topology");
        }
        var graphSource = topologyPanel.graphSource();
        if (graphSource != null && !graphSource.supportsCoverage()
                && graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE) {
            String reason = "this graph was " + graphSource.describe + ", so coverage cannot mean anything: "
                    + "it subtracts what ran from what was declared, and here the declared set is what ran";
            return new telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.CoverageData(
                    java.util.List.of(), null, java.util.List.of(reason), reason);
        }
        var input = new telamin.fluxtion.audit.analyser.analyser.topology.CoverageService.Input(
                topologyPanel.fullTopology(), topologyPanel.authoredNodeIds(), topologyPanel.sourceResolver());
        var assessed = telamin.fluxtion.audit.analyser.analyser.topology.CoverageService.assess(
                store, filtered, filter, input);
        return new telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.CoverageData(assessed.ledger(),
                assessed.scalarLine(), assessed.notes(), assessed.ledger().isEmpty()
                        ? "the topology declares no reportable nodes" : null);
    }

    /** Assemble what each section can show headlessly, and render (M33.3 — see recorded deviations). */
    private byte[] renderReportPdf(telamin.fluxtion.audit.analyser.analyser.report.ReportSpec spec,
                                   telamin.fluxtion.audit.analyser.analyser.report.ReportResolver.Resolution resolution,
                                   java.util.List<String> warnings) {
        var content = new java.util.ArrayList<telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent>();
        for (int i = 0; i < spec.sections().size(); i++) {
            var s = spec.sections().get(i);
            if (!resolution.sections().get(i).resolved()) {
                content.add(telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent.EMPTY);
                continue;
            }
            content.add(switch (s.kind()) {
                case FINDING, RECORD -> new telamin.fluxtion.audit.analyser.analyser.report
                        .ReportRenderer.SectionContent(null, recordLines(s.recordIndex()), null, null);
                case CHART -> {
                    GraphPanel panel = graphTabs.graphNamed(s.ref());
                    if (panel == null) {
                        yield telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent.EMPTY;
                    }
                    // M32.7: the chart's markers ride the PDF as a table under the picture
                    var mk = telamin.fluxtion.audit.analyser.analyser.report.ReportVerb
                            .markersTable(panel.currentMarkers());
                    warnings.addAll(mk.notes());
                    yield new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent(
                            null, null,
                            new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture(
                                    "Trend · " + s.ref(), null, paintOf(panel)),
                            mk.table().rows().isEmpty() ? null : mk.table());
                }
                case TOPOLOGY ->
                        // recorded deviation: no per-focus offscreen render exists yet; the PDF states
                        // the gap instead of silently omitting the section it resolved
                        new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent(
                                "Focus · " + s.ref(),
                                java.util.List.of("(the focus renders in the app's Topology tab; "
                                        + "image export for focus sections is a recorded gap)"),
                                null, null);
                case SERIES ->
                        new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent(
                                "Series",
                                java.util.List.of("(series sections render as charts in the app; "
                                        + "PDF assembly for them is a recorded gap)"),
                                null, null);
                case TABLE -> {
                    var assembled = telamin.fluxtion.audit.analyser.analyser.report.ReportVerb
                            .assembleTable(s, store, this::coverageForReport);
                    warnings.addAll(assembled.notes());
                    yield new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent(
                            "Table", null, null, assembled.table());
                }
                case NARRATIVE ->
                        telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.SectionContent.EMPTY;
            });
        }
        return telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.render(
                spec, resolution, content,
                logDisplayLocation == null ? null : new File(logDisplayLocation).getName(),
                TimeFormat.utc(System.currentTimeMillis()));
    }

    /** One record as evidence lines: the numbered node log, the same shape the finding report uses. */
    private java.util.List<String> recordLines(int row) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        LogRecord record = store.record(row);
        int n = 0;
        for (var nodeLog : record.nodeLogs()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%3d. %s", ++n, nodeLog.instanceId()));
            for (var kv : nodeLog.entries()) {
                sb.append("  ").append(kv.key()).append('=').append(kv.rawValue());
            }
            lines.add(sb.toString());
        }
        if (record.eventToString() != null) lines.add(0, "event: " + record.eventToString());
        return lines;
    }

    /**
     * The Reports tab's export button (human parity with {@code report {path}}): the chooser IS the
     * consent, exactly like the finding export — a human-picked path never rides the exchange guard.
     */
    private void exportReportPdfWithChooser(String name) {
        var spec = reportByName(name);
        if (spec == null || store == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export report");
        fc.setSelectedFile(new File(name + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var resolution = telamin.fluxtion.audit.analyser.analyser.report.ReportResolver.resolve(
                spec, store.index(), loadedLogName(), logProvenance, findings,
                new java.util.HashSet<>(graphTabs.graphNames()), focusNames(), filter);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        byte[] pdf = renderReportPdf(spec, resolution, warnings);
        try {
            Files.write(fc.getSelectedFile().toPath(), pdf);
            status.setText("Wrote " + fc.getSelectedFile().getName()
                    + (warnings.isEmpty() ? "" : " — " + warnings.size() + " note(s), see the panel"));
        } catch (java.io.IOException e) {
            javax.swing.JOptionPane.showMessageDialog(this, "Could not write: " + e.getMessage(),
                    "Export report", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportFindingWithChooser() {
        int[] rows = tablePanel.selectedModelRows();
        if (rows.length == 0 || store == null) return;
        int row = rows[0];
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export finding");
        fc.setSelectedFile(new File("finding-record-" + row + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        // the currently-selected graph goes in when one exists: on this path the user picked the tab, so
        // "is this plot relevant?" is a question they have already answered
        String graph = graphTabs.selectedGraphName();
        var result = exportFinding(fc.getSelectedFile().getAbsolutePath(), row, null, graph, true);
        if (result.ok()) {
            status.setText("Wrote " + fc.getSelectedFile().getName());
        } else {
            JOptionPane.showMessageDialog(this, result.error(), "Export finding",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Assemble and write a finding report (M23.8).
     *
     * <p>Everything in it is taken from what is <b>on screen</b> rather than recomputed: the same
     * explanation the callout shows, the same node log the detail panel shows, a picture of the topology
     * as it is currently focused. A report assembled from a second, parallel query would be a document
     * that can disagree with the app it came from, which defeats the purpose of exporting evidence.
     */
    private telamin.fluxtion.audit.analyser.analyser.llm.ActionResult exportFinding(
            String path, Integer recordIndex, String title, String graphName, boolean withTopology) {
        if (path == null || path.isBlank()) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'path' is required");
        }
        if (store == null) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("no log is loaded");
        }
        int row = recordIndex != null ? Math.max(0, Math.min(recordIndex, store.size() - 1))
                : firstSelectedModelRow();
        if (row < 0) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                    "no record selected — pass 'recordIndex' or select one first");
        }
        LogRecord record = store.record(row);
        var finding = findings.get(row);
        if (finding == null) {
            finding = new telamin.fluxtion.audit.analyser.analyser.report.Finding(row, "", null);
        }

        java.util.List<String> eventLines = new java.util.ArrayList<>();
        String raw = store.rawText(row);
        if (raw != null) {
            for (String line : raw.split("\n")) {
                // the node log has a section of its own below; repeating it here doubles the page count
                if (line.strip().startsWith("nodeLogs")) break;
                eventLines.add(line);
            }
        }

        java.util.List<String> nodeLogLines = new java.util.ArrayList<>();
        int n = 0;
        for (var nodeLog : record.nodeLogs()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%3d. %s", ++n, nodeLog.instanceId()));
            for (var kv : nodeLog.entries()) {
                sb.append("  ").append(kv.key()).append('=').append(kv.rawValue());
            }
            nodeLogLines.add(sb.toString());
        }

        List<telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture> pictures =
                new java.util.ArrayList<>();
        TopologyPanel.CycleViews views = new TopologyPanel.CycleViews(null, null);
        if (withTopology && topologyPanel.hasTopology()) {
            // rendered offscreen and fitted for the page, NOT screenshotted: a capture of the live panel
            // inherits whatever zoom and pan the user left it at, and the only way to make it look right
            // is to change what they are looking at
            views = topologyPanel.renderCycleViews(record, 1200, 800);
            if (views.trace() != null) {
                pictures.add(new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture(
                        "The cycle",
                        "Only the nodes this event reached, and the order they logged in.",
                        views.trace()));
            }
            if (views.wholeGraph() != null) {
                // H6: on a large processor the picture is the neighbourhood, not the estate, and the
                // caption says so and counts what it left out — a picture that quietly showed less than
                // its heading promised would be evidence of the wrong thing
                String caption = views.wholeNote() != null ? views.wholeNote()
                        : "The whole graph with that cycle lit. What stayed grey is what this event did "
                                + "not reach — which is the evidence for anything of the form "
                                + "\"the check never fired\".";
                pictures.add(new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture(
                        views.wholeNote() != null ? "Where it sits in the processor (neighbourhood)"
                                : "Where it sits in the processor",
                        caption, views.wholeGraph()));
            }
        }
        if (graphName != null && !graphName.isBlank()) {
            GraphPanel panel = graphTabs.graphNamed(graphName);
            if (panel == null) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "no graph named '" + graphName + "' — open graphs: " + graphTabs.graphNames());
            }
            // mark WHERE ON THE TREND this cycle is. Without it the reader has two artefacts — a plot and
            // a record — and has to join them by comparing the header timestamp to an axis by eye.
            // Cleared in a finally: a marker is about the record being diagnosed, not a property of the
            // graph, and leaving one behind would make the next reader think the app was still on it.
            String marker = record.logTime() == null ? null : "record #" + row;
            panel.setRecordMarker(record.logTime(), marker);
            java.awt.image.BufferedImage plot;
            try {
                plot = paintOf(panel);
            } finally {
                panel.setRecordMarker(null, null);
            }
            pictures.add(new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture(
                    "Trend · " + graphName,
                    marker == null ? null
                            : "The dashed rule marks record " + row + " — the cycle this finding is about.",
                    plot));
        }

        String heading = title != null && !title.isBlank() ? title
                : (record.event() == null ? "Record " + row : record.event() + " · record " + row);
        var evidence = new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Evidence(
                heading, finding,
                logDisplayLocation == null ? null : new File(logDisplayLocation).getName(),
                config.selectedEventProcessor,
                record.logTime() == null ? null : TimeFormat.utc(record.logTime()),
                record.eventToString(), eventLines, nodeLogLines, pictures,
                // when the analysis was made, not when the event happened — the two are months apart on
                // an archived log, and a report that only carries the second reads as if it were live
                TimeFormat.utc(System.currentTimeMillis()));

        try {
            Path out = Path.of(path);
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.write(out, telamin.fluxtion.audit.analyser.analyser.report.FindingReport.render(evidence));
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            echo.put("path", out.toAbsolutePath().toString());
            echo.put("recordIndex", row);
            echo.put("title", heading);
            echo.put("hasExplanation", finding.hasNote());
            echo.put("hasFix", finding.hasFix());
            echo.put("cycleView", views.trace() != null);
            echo.put("wholeGraphView", views.wholeGraph() != null);
            echo.put("graph", graphName == null || graphName.isBlank() ? null : graphName);
            echo.put("pages", pictures.size());
            // a topology that has no node from this record is a build mismatch, not an empty cycle —
            // silently omitting the picture would leave the reader wondering where it went
            if (withTopology && topologyPanel.hasTopology() && views.trace() == null) {
                echo.put("warning", "none of this record's nodes are in the loaded topology — "
                        + "the graphml is probably from a different build");
            }
            // an empty finding still produces a valid report; say so rather than let the caller assume
            // the explanation made it in
            if (finding.isEmpty()) {
                echo.put("note", "no explanation is recorded for this record — write one with "
                        + "flag {recordIndexes:[" + row + "], note:\"…\", fix:\"…\"} and export again");
            }
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("report", "wrote", echo);
        } catch (java.io.IOException e) {
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                    "could not write " + path + ": " + e.getMessage());
        }
    }

    /** The first selected record's model row, falling back to the topology cursor; -1 if neither. */
    private int firstSelectedModelRow() {
        int[] rows = tablePanel.selectedModelRows();
        if (rows.length > 0) return rows[0];
        if (!topologyPanel.hasTopology()) return -1;
        Object idx = topologyPanel.cursorState().get("recordIndex");
        return idx instanceof Integer i ? tablePanel.modelRowAt(i) : -1;
    }

    /** A top-level menu by name, case-insensitively — the menu bar is small and this keeps callers simple. */
    private javax.swing.JMenu topLevelMenu(String name) {
        javax.swing.JMenuBar bar = getJMenuBar();
        if (bar == null || name == null) return null;
        for (int i = 0; i < bar.getMenuCount(); i++) {
            javax.swing.JMenu m = bar.getMenu(i);
            if (m != null && m.getText() != null && m.getText().equalsIgnoreCase(name.strip())) return m;
        }
        return null;
    }

    private java.util.List<String> topLevelMenuNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        javax.swing.JMenuBar bar = getJMenuBar();
        for (int i = 0; bar != null && i < bar.getMenuCount(); i++) {
            if (bar.getMenu(i) != null) names.add(bar.getMenu(i).getText());
        }
        return names;
    }

    private static java.awt.image.BufferedImage paintOf(java.awt.Component c) {
        if (c.getWidth() <= 0 || c.getHeight() <= 0) return null;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                c.getWidth(), c.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        return img;
    }

    private void exportRecords(boolean yaml) {
        if (store == null || filter == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(yaml ? "records.yaml" : "records.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path out = fc.getSelectedFile().toPath();
        final LogStore s = store;
        final FilterState f = filter;
        Background.run(
                () -> {
                    String content = yaml ? RecordExporter.toYaml(s, f) : RecordExporter.toCsv(s, f);
                    try {
                        java.nio.file.Files.writeString(out, content);
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    return out;
                },
                p -> JOptionPane.showMessageDialog(this, "Exported to " + p),
                err -> JOptionPane.showMessageDialog(this, "Export failed: " + rootMessage(err),
                        "Export", JOptionPane.ERROR_MESSAGE));
    }

    private JMenu buildThemeMenu() {
        JMenu menu = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();
        for (String t : ThemeManager.THEMES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(t, t.equals(config.theme));
            item.addActionListener(e -> applyTheme(t));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private void applyTheme(String theme) {
        ThemeManager.apply(theme);
        SwingUtilities.updateComponentTreeUI(this);
        detailPanel.refresh();     // re-colour with the theme-appropriate palette
        sourcePanel.refresh();
        topologyPanel.refreshTheme();
        // these hold explicit colours derived from the OLD theme: updateComponentTreeUI keeps the value
        // it was given, so a stale tint survives a theme switch unless it is recomputed
        UiTheme.applyControlSurface(filterBar);
        if (navRail != null) navRail.refreshTheme();
        if (projectPanel != null) projectPanel.refreshTheme();   // owner, 2026-08-27: rows are painted from UiTheme at render time
        refreshMcpIndicator();                                  // D-AI9: its colour is explicit too, so it must be recomputed
        eventFilterPanel.refreshTheme();                          // section border + group headers likewise
        repaint();                 // charts read the theme on paint
        config.theme = theme;
        saveConfigQuietly();
    }

    private void showHelp() {
        JDialog dialog = new JDialog(this, "User guide", false);
        dialog.add(new HelpPanel());
        dialog.setSize(820, 640);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showReleaseNotes() {
        JTextArea ta = new JTextArea(ReleaseNotes.changelog());
        ta.setEditable(false);
        ta.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        ta.setCaretPosition(0);
        JDialog dialog = new JDialog(this, "Release notes", false);
        dialog.add(new JScrollPane(ta));
        dialog.setSize(660, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** After an upgrade to a released build, show the new version's changelog section once. */
    public void maybeShowWhatsNew() {
        if (ReleaseNotes.isDevBuild()) return;         // don't nag from the IDE or a -SNAPSHOT build
        String current = ReleaseNotes.version();
        String previous = config.lastRunVersion;
        if (current.equals(previous)) return;
        config.lastRunVersion = current;
        saveConfigQuietly();
        if (previous == null || previous.isBlank()) return;   // fresh install, not an upgrade
        String section = ReleaseNotes.sectionFor(current);
        if (section.isBlank()) return;
        JTextArea ta = new JTextArea(section);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(520, 320));
        JOptionPane.showMessageDialog(this, sp, "What's new in " + current, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Narrow every view to records mentioning {@code instanceId}, from the topology's node menu (M21.5).
     * Routed through the existing search field rather than poking {@link FilterState} directly, so the
     * filter box shows what is being filtered and the user can edit or clear it as usual — the same
     * free-text scan, which is slow on a large log by nature.
     */
    private void filterToInstance(String instanceId) {
        if (filter == null || instanceId == null) return;
        searchField.setText(instanceId);
        filter.setText(instanceId);
        status.setText("Filtered to records mentioning " + instanceId + " — clear the search box to undo.");
    }

    private void openNodeSource(String instanceId, String method) {
        sourcePanel.openInstance(instanceId, method);
        if (sideTabs != null) sideTabs.setSelectedComponent(sourcePanel);
    }

    /**
     * Accepts files dropped anywhere on the window: a {@code .graphml} loads into the Topology tab,
     * anything else opens as a log. Dropping a log + graphml pair together routes each — the
     * "here's the cycle and here's the graph it ran on" gesture.
     */
    private void installFileDrop() {
        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                try {
                    Transferable t = s.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    boolean any = false;
                    boolean droppedTopology = false;
                    for (File f : files) {
                        if (isGraphml(f.getName())) {
                            topologyPanel.load(f.toPath());
                            droppedTopology = true;
                        } else if (!any) {
                            openFile(f.toPath());   // first non-graphml is the log; extras are ignored
                            any = true;
                        }
                    }
                    if (droppedTopology && sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
                    return any || droppedTopology;
                } catch (Exception ignore) {
                    // ignore malformed drops
                }
                return false;
            }
        });
    }

    /** Routing rule for dropped files — {@code .graphml} goes to the Topology tab. */
    static boolean isGraphml(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".graphml");
    }

    private void buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open log…");
        open.addActionListener(e -> chooseFile());
        file.add(open);
        JMenuItem openS3 = new JMenuItem("Open log from S3…");
        openS3.addActionListener(e -> chooseS3());
        file.add(openS3);
        // opening lives on the File menu with the log actions, not on the Topology tab's own toolbar:
        // it is the same kind of act, and a toolbar is better spent on controls for what is already open
        JMenuItem addCsv = new JMenuItem("Add series from CSV…");
        addCsv.setToolTipText("Plot an external (timestamp, value) CSV — e.g. agent-parsed FIX data — "
                + "beside the audit-derived series. The clock domain is declared, never guessed.");
        addCsv.addActionListener(e -> addExternalSeries());
        file.add(addCsv);
        JMenuItem openGraphml = new JMenuItem("Open GraphML…");
        openGraphml.setToolTipText("Open a processor's .graphml topology");
        openGraphml.addActionListener(e -> {
            topologyPanel.chooseFile();
            if (sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
        });
        file.add(openGraphml);
        JMenuItem findGraphml = new JMenuItem("Find GraphML in source roots\u2026");
        findGraphml.setToolTipText("List the .graphml files under your source roots, ranked by how "
                + "well each fits the open log. Nothing is opened until you pick one.");
        findGraphml.addActionListener(e -> chooseDiscoveredGraph());
        file.add(findGraphml);
        file.addSeparator();
        // M35.1 — the counterparts the File menu never had. Until now the only way back to a clean
        // app was to restart it, and opening a second log left the first log's graph on screen.
        closeLogItem.setToolTipText("Close the log and everything derived from it. Named graphs, "
                + "focuses and reports are profile state and stay — they will say why they cannot resolve.");
        closeLogItem.addActionListener(e -> closeLog());
        file.add(closeLogItem);
        closeGraphItem.setToolTipText("Close the loaded .graphml topology, leaving the log open");
        closeGraphItem.addActionListener(e -> closeGraph());
        file.add(closeGraphItem);
        resetItem.setToolTipText("Close both — back to a fresh start (the project profile is kept)");
        resetItem.addActionListener(e -> resetAll());
        file.add(resetItem);
        rebuildRecentMenu();
        file.add(recentMenu);
        file.add(recentGraphmlMenu);

        // Projects are their own group: the items above open a FILE to look at, these change which
        // project's settings are in force. Appending them to the end would file "switch my whole
        // working set" next to "exit".
        file.addSeparator();
        file.add(openProjectItem());
        file.add(recentProjectsMenu);
        file.add(newProjectFromTemplateItem());
        file.add(newProjectItem());
        saveProjectAsItem.addActionListener(e -> saveProjectAs());
        saveProjectAsItem.setToolTipText("Fork these settings to another project. There is no plain "
                                         + "Save — project edits persist as you make them.");
        file.add(saveProjectAsItem);
        closeProjectItem.addActionListener(e -> closeProject());
        file.add(closeProjectItem);
        file.add(analysesMenu);          // M38.4: recall a saved analysis — the UI half of the offer
        rebuildAnalysesMenu();

        file.addSeparator();
        followMenuItem = new JCheckBoxMenuItem("Follow (tail)");
        followMenuItem.setToolTipText("Poll the open local file for newly-appended records and auto-scroll");
        followMenuItem.setEnabled(false);
        followMenuItem.addActionListener(e -> setFollowing(followMenuItem.isSelected()));
        file.add(followMenuItem);
        file.addSeparator();
        JMenuItem exportCsv = new JMenuItem("Export records (CSV)…");
        exportCsv.addActionListener(e -> exportRecords(false));
        file.add(exportCsv);
        JMenuItem exportYaml = new JMenuItem("Export records (YAML)…");
        exportYaml.addActionListener(e -> exportRecords(true));
        file.add(exportYaml);
        file.addSeparator();
        JMenuItem settings = new JMenuItem("Settings…");
        settings.addActionListener(e -> ConfigPanel.show(this, config, this::onConfigChanged, this::readerSummaries));
        file.add(settings);
        JMenuItem exportSettings = new JMenuItem("Export settings…");
        exportSettings.setToolTipText("Share your analysis setup — roots, event processors, graphs (never your API key)");
        exportSettings.addActionListener(e -> exportSettings());
        file.add(exportSettings);
        JMenuItem importSettings = new JMenuItem("Import settings…");
        importSettings.setToolTipText("Load a shared analysis setup from a .fluxtion-settings file");
        importSettings.addActionListener(e -> importSettings());
        file.add(importSettings);
        file.addSeparator();
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> onExit());
        file.add(exit);
        bar.add(file);

        bar.add(buildRecordsMenu());
        // Columns is no longer a top-level menu: it lives on the nav rail and on the table's right-click,
        // which is where you are when you notice a column is missing
        bar.add(buildThemeMenu());
        bar.add(buildAiMenu());

        JMenu help = new JMenu("Help");
        // M36.1: the start page is a STATE, and the state is "no log open" — so anyone who has ever
        // opened a log reaches it only by closing one, and a returning user whose last log reopens at
        // launch never sees it at all. This recalls it without closing anything; the page shows its own
        // way back while a log is loaded, so it stays a place you visit rather than a mode you are in.
        JMenuItem startPage = new JMenuItem("Start page");
        startPage.addActionListener(e -> showStartPage());
        help.add(startPage);
        help.addSeparator();
        JMenuItem guide = new JMenuItem("User guide");
        guide.addActionListener(e -> showHelp());
        help.add(guide);
        JMenuItem releaseNotes = new JMenuItem("Release notes");
        releaseNotes.addActionListener(e -> showReleaseNotes());
        help.add(releaseNotes);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Fluxtion Audit Log Analyser\nVersion " + ReleaseNotes.version()
                        + "\nReads machine-readable Fluxtion event-audit logs.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);
        setJMenuBar(bar);
    }

    /**
     * M43.1 — the AI menu (D-AI1: the Project panel STATES what is in force, this menu ACTS).
     *
     * <p>Everything here was reachable and almost none of it was findable: MCP setup lived in Settings
     * and on the Start page, which only appears via Help ▸ Start page — invisible mid-session, which is
     * exactly when someone thinks to connect an LLM.
     *
     * <p>Two rules hold this together. Every item OPENS the one owner of a setting or is BOUND to its
     * value, never keeping a copy (D-AI2) — settings in two places drift, which is why KnownKeys exists.
     * And an item whose precondition is missing is DISABLED WITH A REASON rather than opening a dialog
     * that explains itself after the click (D-AI3); M35 spent a milestone removing six such modals, and a
     * new menu is the likeliest place to reintroduce them. Nothing here RUNS anything (D-AI4).
     */
    private JMenu buildAiMenu() {
        JMenu ai = new JMenu("AI");

        JMenuItem connect = new JMenuItem("Connect an AI client…");
        connect.setToolTipText("Register this analyser with Claude Code, Codex or any MCP client");
        connect.addActionListener(e -> openMcpSetup());
        ai.add(connect);

        // BOUND, not copied: it reads and writes the same field Settings ▸ Assistant renders
        JCheckBoxMenuItem transport = new JCheckBoxMenuItem("Local MCP / REST enabled");
        transport.addActionListener(e -> {
            config.assistantActionsRest = transport.isSelected();
            onConfigChanged();
            refreshMcpIndicator();
        });
        ai.add(transport);

        ai.addSeparator();
        JMenuItem fluxtionKey = new JMenuItem("Fluxtion API key…");
        fluxtionKey.setToolTipText("Manage the local key file used when a processor is regenerated");
        fluxtionKey.addActionListener(e -> openFluxtionKeyDialog());
        ai.add(fluxtionKey);

        ai.addSeparator();
        JMenuItem runbooks = new JMenuItem("Runbooks…");
        runbooks.addActionListener(e -> PointerDialog.runbooks(this, config, this::onProfileEdited, projectRoot(), profileFile()));
        ai.add(runbooks);
        JMenuItem glossary = new JMenuItem("Domain glossary…");
        glossary.addActionListener(e -> PointerDialog.glossary(this, config, this::onProfileEdited, projectRoot(), profileFile()));
        ai.add(glossary);

        ai.addSeparator();
        JMenuItem exchange = new JMenuItem("Report exchange directory…");
        exchange.addActionListener(e -> ConfigPanel.show(this, config, this::onConfigChanged,
                this::readerSummaries, "Assistant"));
        ai.add(exchange);
        JMenuItem showExchange = new JMenuItem("Show exchange directory");
        showExchange.addActionListener(e -> revealPath(config.assistantExportDir));
        ai.add(showExchange);

        ai.addSeparator();
        JMenuItem docs = new JMenuItem("Working with AI (docs)");
        docs.addActionListener(e -> openDocsPage("ai-and-runbooks/"));
        ai.add(docs);

        // D-AI3: state the remedy on the item, so the reason is read BEFORE the click, not after it
        ai.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                // every decision here is AiMenuModel's; this method only paints them, so D-AI3 is
                // pinned by test rather than by whoever last edited this listener
                transport.setSelected(AiMenuModel.transportTicked(config));
                AiMenuModel.Item pointers = AiMenuModel.pointers(project.hasProject());
                for (JMenuItem item : new JMenuItem[]{runbooks, glossary}) {
                    item.setEnabled(pointers.enabled());
                    item.setToolTipText(pointers.tooltip());
                }
                AiMenuModel.Item exchange = AiMenuModel.showExchange(config);
                showExchange.setEnabled(exchange.enabled());
                showExchange.setToolTipText(exchange.tooltip());
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
        });
        return ai;
    }

    /** The profile file a pointer edit lands in — named in the dialog, because it is committed (D-AI7). */
    private Path profileFile() {
        return project.hasProject() ? project.activeFile() : null;
    }

    /** The project root pointers are stored relative to, or null when no project is open. */
    private Path projectRoot() {
        return project.hasProject()
                ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile())
                : null;
    }

    /** A profile edit: persist, then re-render every surface that states what is in force. */
    private void onProfileEdited() {
        onConfigChanged();
        refreshProjectPanel();
    }

    private void openMcpSetup() {
        McpSetupDialog.show(this, config, () -> {
            onConfigChanged();
            refreshMcpIndicator();
        }, McpSetupDialog.Target.fromPersisted(config.mcpSetupTarget, McpSetupDialog.Target.GENERIC), false);
    }

    /** One key owner, reached from the AI menu and Start Page; no key value returns from the dialog. */
    private void openFluxtionKeyDialog() {
        if (FluxtionKeyDialog.show(this, fluxtionKeyStore)) {
            if (startPanel != null) startPanel.refreshFluxtionKeyStatus();
            refreshProjectPanel();
        }
    }

    private void revealPath(String path) {
        if (path == null || path.isBlank()) return;
        try {
            java.awt.Desktop.getDesktop().open(new java.io.File(path));
        } catch (Exception ignored) {
            // the item is only enabled when the directory is configured; a failure here is not worth a modal
        }
    }

    private void openDocsPage(String page) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(
                    "https://telaminai.github.io/fluxtionauditlog-analyser/" + page));
        } catch (Exception ignored) {
        }
    }


    private void buildLayout() {
        // small minimums so the JSplitPane can move the divider freely (content min sizes were
        // otherwise pinning the panels and blocking the drag)
        tablePanel.setMinimumSize(new Dimension(100, 60));
        detailPanel.setMinimumSize(new Dimension(100, 60));

        // Search sits directly above the Records table it filters
        searchField.setToolTipText("Filter by eventToString / thread / nodeLogs (case-insensitive); combines with the other filters. Enter to remember.");
        searchField.setEnabled(false);
        searchField.setPreferredSize(new Dimension(260, searchField.getPreferredSize().height));
        searchField.setHistory(config.searchHistory);
        // Search grows to fill the width; a Clear-history button sits on the right of the same row
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 8, 0));   // vertical breathing room below search
        searchRow.add(new JLabel("Search:"), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);   // BorderLayout centre stretches the field
        JButton clearHistory = new JButton("Clear history");
        clearHistory.setToolTipText("Clear the saved search history (does not change the current search)");
        clearHistory.addActionListener(e -> clearSearchHistory());
        searchRow.add(clearHistory, BorderLayout.EAST);
        // "Records" header on top, then the Search row, then the table
        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setBorder(UiTheme.section("Records"));
        tableArea.add(searchRow, BorderLayout.NORTH);
        tableArea.add(tablePanel, BorderLayout.CENTER);
        tableArea.setMinimumSize(new Dimension(100, 80));

        // the records table shouldn't dominate: give the detail panel and right-hand tabs real space
        // M36 D-S1 / O-S2: the start page takes the LEFT COLUMN — records AND detail — while the
        // right-hand tabs stay visible, because they are the product's structure and hiding them on
        // first contact teaches nothing. Taking only the records pane was tried first and failed its
        // own acceptance test: at a normal window width the body text clipped mid-word, the third
        // action fell off the edge, and a whole section sat below a scrollbar. The detail pane has
        // nothing to say with no log either ("select a record in the table above"), so the honest
        // unit to replace is the pair.
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableArea, detailPanel);
        mainSplit.setResizeWeight(0.45);
        mainSplit.setDividerLocation(330);
        mainSplit.setContinuousLayout(true);   // live relayout while dragging
        mainSplit.setOneTouchExpandable(true);

        sideTabs = new JTabbedPane();   // right pane = outputs only (Event types moved to the left rail)
        sideTabs.addTab("Summary", summaryPanel);
        sideTabs.addTab("Source", sourcePanel);
        sideTabs.addTab("Graph", graphTabs);
        sideTabs.addTab("Topology", topologyPanel);
        reportsPanel = new ReportsPanel(
                () -> java.util.List.copyOf(config.reports),
                spec -> telamin.fluxtion.audit.analyser.analyser.report.ReportResolver.resolve(
                        spec, store == null ? null : store.index(), loadedLogName(), logProvenance,
                        findings, new java.util.HashSet<>(graphTabs.graphNames()), focusNames(),
                        filter),
                sec -> store == null
                        ? new telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.AssembledTable(
                                new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.TableData(
                                        sec.columns(), java.util.List.of(), new boolean[0],
                                        sec.rowWhen(), sec.rowWhenLabel()),
                                java.util.List.of("no log is loaded"))
                        : telamin.fluxtion.audit.analyser.analyser.report.ReportVerb
                                .assembleTable(sec, store, this::coverageForReport),
                row -> openRecordFromReport(row),
                gname -> { sideTabs.setSelectedComponent(graphTabs); graphTabs.selectGraph(gname); },
                fname -> { sideTabs.setSelectedComponent(topologyPanel); topologyPanel.recallFocus(fname); },
                snap -> snap.applyTo(filter),
                name -> exportReportPdfWithChooser(name));
        sideTabs.addTab("Reports", reportsPanel);
        reportsPanel.refresh();
        sideTabs.addTab("Analyser assistant", llmPanel);

        mainSplit.setMinimumSize(new Dimension(200, 120));
        sideTabs.setMinimumSize(new Dimension(200, 120));
        // Each tab's content has its own preferred size — the canvas asks for 640x420, a chart more — and
        // a JTabbedPane reports the SELECTED tab's preference as its own. Left alone the split re-lays out
        // to suit whichever tab is showing, so the divider walks about as you switch between them. Pinning
        // the minimums stops the content forcing a move; restoring the location below covers the rest.
        for (java.awt.Component tab : new java.awt.Component[]{
                summaryPanel, sourcePanel, graphTabs, topologyPanel, llmPanel}) {
            if (tab instanceof JComponent c) c.setMinimumSize(new Dimension(200, 120));
        }
        startPanel = new StartPanel(new StartPanel.Actions() {
            @Override public void openDemo(Path log, boolean withGraph) { openDemoLog(log, withGraph); }
            @Override public void showTab(String name) { selectTab(name); }
            @Override public void openOwnLog() { chooseFile(); }
            @Override public void openSettings() {
                ConfigPanel.show(MainFrame.this, config, MainFrame.this::onConfigChanged,
                        MainFrame.this::readerSummaries);
            }
            @Override public void openMcpSetup(McpSetupDialog.Target target) {
                McpSetupDialog.show(MainFrame.this, config, MainFrame.this::onConfigChanged, target, false);
            }
            @Override public void openFluxtionKey() { openFluxtionKeyDialog(); }
            @Override public boolean fluxtionKeyPresent() { return fluxtionKeyStore.keyPresent(); }
            @Override public void backToRecords() { syncRecordsCard(); }
        }, text -> status.setText(text));
        recordsCards.add(startPanel, "start");
        recordsCards.add(mainSplit, "table");

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, recordsCards, sideTabs);
        center.setDividerSize(9);          // constant, rather than whatever the tab's content implies
        sideTabs.addChangeListener(e -> {
            // read now, restore after the tab change has re-laid out
            int location = center.getDividerLocation();
            SwingUtilities.invokeLater(() -> {
                if (center.getDividerLocation() != location) center.setDividerLocation(location);
            });
        });
        center.setResizeWeight(0.55);
        center.setDividerLocation(630);
        center.setContinuousLayout(true);
        center.setOneTouchExpandable(true);

        // top = toolbar over filter bar; left rail = event-type checklist
        JPanel north = new JPanel(new BorderLayout());
        north.add(buildToolBar(), BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        eventFilterPanel.setPreferredSize(new Dimension(240, 200));
        // M37 (owner, 2026-08-27): the west column is DRAGGABLE, not a fixed 240px. The Project panel put
        // paths and class names in it, and a width chosen for a checklist of event names is the wrong
        // width for those. Persisted like every other layout choice (config.westWidth).
        westOuter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildWestRail(), center);
        westOuter.setResizeWeight(0.0);              // extra window width goes to the records, as before
        westOuter.setContinuousLayout(true);
        westOuter.setBorder(BorderFactory.createEmptyBorder());
        westOuter.setDividerLocation(Math.max(config.westWidth, navRail.getPreferredSize().width + 8));
        westOuter.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (westHasPanel()) config.westWidth = westOuter.getDividerLocation();   // a collapsed rail is not a choice of width
        });
        add(westOuter, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        java.awt.Color statusLine = UIManager.getColor("Component.borderColor");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        statusLine != null ? statusLine : java.awt.Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        UiTheme.status(status);
        statusBar.add(status, BorderLayout.WEST);
        statusBar.add(mcpLight, BorderLayout.CENTER);      // D-AI9: is an AI client reaching THIS window?
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(140, 14));
        statusBar.add(progress, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JToolBar buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));   // a little breathing room
        tb.add(toolButton("Open", ToolIcons.open(), "Open a log file", this::chooseFile));
        tb.add(toolButton("Open S3", ToolIcons.bucket(), "Open s3://bucket/key (via the aws CLI)", this::chooseS3));
        tb.addSeparator();
        tb.add(toolButton("Flag", ToolIcons.flag(), "Flag / unflag the selected rows (F)",
                () -> toggleFlags(tablePanel.selectedModelRows())));
        JToggleButton flaggedOnlyBtn = new JToggleButton("Flagged only", ToolIcons.funnel());
        flaggedOnlyBtn.setToolTipText("Show only flagged rows");
        flaggedOnlyBtn.setFocusable(false);
        flaggedOnlyBtn.setIconTextGap(6);
        flaggedOnlyBtn.addActionListener(e -> {
            flaggedOnly = flaggedOnlyBtn.isSelected();
            tablePanel.reFilter();
            onFilterChanged();
        });
        tb.add(flaggedOnlyBtn);
        tb.addSeparator();
        tb.add(toolButton("Next", ToolIcons.warning(), "Jump to the next anomaly — parse-error / breach / NaN (F3)",
                () -> gotoAnomaly(true)));
        tb.add(toolButton("Prev", ToolIcons.warning(), "Jump to the previous anomaly (Shift+F3)", () -> gotoAnomaly(false)));
        tb.addSeparator();
        tb.add(toolButton("Explain", ToolIcons.chat(), "Explain the selected record(s) with the LLM assistant", this::explainSelection));
        tb.add(toolButton("Export CSV", ToolIcons.download(), "Export the filtered records to CSV", () -> exportRecords(false)));
        tb.addSeparator();
        followButton = new JToggleButton("Follow", ToolIcons.play());
        followButton.setToolTipText("Tail the file: poll for newly-appended records and auto-scroll (local, heap-loaded files)");
        followButton.setFocusable(false);
        followButton.setIconTextGap(6);
        followButton.setEnabled(false);
        followButton.addActionListener(e -> setFollowing(followButton.isSelected()));
        tb.add(followButton);
        return tb;
    }

    private static JButton toolButton(String text, Icon icon, String tip, Runnable action) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setIconTextGap(6);           // icon left of the label
        b.addActionListener(e -> action.run());
        return b;
    }

    private void explainSelection() {
        if (sideTabs != null) sideTabs.setSelectedComponent(llmPanel);
        llmPanel.prepareExplain();
    }

    /** Start or stop the opt-in localhost REST transport to match {@code config.assistantActionsRest}. */
    private void applyRestServer() {
        boolean wanted = config.assistantActionsRest;
        if (wanted && actionServer == null) {
            try {
                // the server checks the header token, so the dispatcher itself is token-free; a null store
                // yields a clean "no log loaded" error rather than an NPE
                telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher d = new telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher(
                        false, null,
                        () -> {
                            if (store == null) throw new IllegalStateException("no log loaded");
                            return store.index().snapshot();
                        },
                        row -> store == null ? null : store.rawText(row),
                        actionExecutor);
                // publish the live url+token to the well-known file so an MCP client (M13) can find this
                // run's ephemeral port/token from a static config; removed again on stop/exit
                actionServer = new telamin.fluxtion.audit.analyser.analyser.net.ActionServer(d, actionToken, config.maxActionsPerReply, 10.0,
                        telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile.wellKnown());
                actionServer.start();
                llmPanel.setRestEndpoint(actionServer.url(), actionToken);
                // status bar shows only a token prefix (screenshots/screen-shares); the full token goes to
                // the console and the copy-prompt seed where it's actually needed
                String tokenHint = actionToken.substring(0, Math.min(8, actionToken.length())) + "…";
                status.setText("Assistant REST transport listening on " + actionServer.url()
                        + "  (token " + tokenHint + " — full token in console / Copy prompt)");
                System.out.println("[analyser] assistant REST: " + actionServer.url()
                        + "  X-Analyser-Token: " + actionToken);
            } catch (Exception e) {
                actionServer = null;
                status.setText("Could not start the assistant REST transport: " + rootMessage(e));
            }
        } else if (!wanted && actionServer != null) {
            actionServer.stop();
            actionServer = null;
            llmPanel.setRestEndpoint(null, null);
            status.setText("Assistant REST transport stopped.");
        }
    }

    /** Describes the loaded log so the LLM prompt can seed file access (path, shape, byte anchors). */
    private telamin.fluxtion.audit.analyser.analyser.llm.LogFileInfo currentLogFileInfo() {
        if (store == null) return null;
        long size = -1;
        if (logLocalPath != null) {
            try {
                size = java.nio.file.Files.size(Path.of(logLocalPath));
            } catch (java.io.IOException | RuntimeException ignore) {
                // size stays -1 (unknown) — the block degrades gracefully
            }
        }
        return new telamin.fluxtion.audit.analyser.analyser.llm.LogFileInfo(
                logDisplayLocation, logLocalPath, size, store.size(), store.minLogTime(), store.maxLogTime());
    }

    /** Click on the graph → select the record whose logTime is closest to the clicked time and scroll to it. */
    private void gotoNearestRecordByTime(long timeMillis) {
        if (store == null) return;
        LogIndex idx = store.index();
        int best = -1;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < idx.size(); i++) {
            Long lt = idx.logTime(i);
            if (lt == null) continue;
            long d = Math.abs(lt - timeMillis);
            if (d < bestDelta) { bestDelta = d; best = i; }
        }
        if (best < 0) return;
        if (!tablePanel.selectModelRow(best)) {
            status.setText("Nearest record at " + TimeFormat.utc(idx.logTime(best)) + " UTC is filtered out of the view.");
        }
    }

    /** Jump to the next/prev anomaly row (respects the current filter/sort); reports if there are none. */
    private void gotoAnomaly(boolean forward) {
        if (store == null) return;
        if (!tablePanel.selectNextAnomaly(forward)) {
            status.setText("No anomalies (parse-error / breach / NaN) in the filtered records.");
        }
    }

    /** M38.4: a load is in flight (log, S3, rolled set). Read off the EDT by the analysis runner, hence volatile. */
    private volatile boolean loadInFlight;

    private void setBusy(boolean busy) {
        loadInFlight = busy;
        progress.setVisible(busy);
    }

    private JPanel buildFilterBar() {
        // the time-window controls (window size, slider, pan) — search now lives above the Records table
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 6, 2, 6),
                UiTheme.section("Time range")));
        // a control cluster, not a content surface: tinted the other way so the two read as different
        // kinds of thing rather than as one continuous panel
        UiTheme.applyControlSurface(bar);
        this.filterBar = bar;

        // top row above the slider: Window (left) · showing N of M (centre).
        // Search moved to sit directly above the Records table (see buildLayout).
        windowCombo.setToolTipText("Shrink the visible time window so a short span isn't a sliver of the whole log; pan with the bar below.");
        windowCombo.setEnabled(false);
        JPanel windowCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        windowCluster.add(new JLabel("Window:"));
        windowCluster.add(windowCombo);

        showingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        UiTheme.status(showingLabel);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(showingLabel, BorderLayout.CENTER);
        top.add(windowCluster, BorderLayout.WEST);

        windowScroll.setEnabled(false);
        windowScroll.setToolTipText("Pan the visible window across the full log range.");

        JPanel timeArea = new JPanel(new BorderLayout(0, 2));
        timeArea.add(top, BorderLayout.NORTH);
        timeArea.add(timeSlider, BorderLayout.CENTER);
        timeArea.add(windowScroll, BorderLayout.SOUTH);
        bar.add(timeArea, BorderLayout.CENTER);

        windowCombo.addActionListener(e -> {
            if (syncingWindow) return;
            WindowSpan ws = (WindowSpan) windowCombo.getSelectedItem();
            if (ws != null) timeSlider.setWindowMillis(ws.millis());
            syncWindowScroll();
        });
        windowScroll.addAdjustmentListener(e -> {
            if (syncingWindow || !timeSlider.isWindowed()) return;
            int span = 1000 - windowScroll.getModel().getExtent();
            double f = span <= 0 ? 0 : windowScroll.getValue() / (double) span;
            timeSlider.setWindowStartFraction(f);
        });
        // when the slider auto-pans its window (edge-drag), keep the pan bar in step
        timeSlider.setWindowChangeListener(this::syncWindowScroll);

        // debounced text filter
        searchDebounce = new Timer(250, e -> {
            if (filter != null) filter.setText(searchField.getText());
        });
        searchDebounce.setRepeats(false);
        searchField.onTextChanged(searchDebounce::restart);
        // Enter (or picking from the dropdown) commits the term to history
        searchField.addActionListener(e -> {
            if (filter == null || searchField.isAdjusting()) return;
            String term = searchField.getText();
            filter.setText(term);
            if (!term.isBlank()) {
                config.addSearch(term);
                searchField.setHistory(config.searchHistory);
                saveConfigQuietly();
            }
        });
        return bar;
    }

    private void wireSelection() {
        tablePanel.setSelectionListener(this::onRowsSelected);
    }

    private void onRowsSelected(int[] modelRows) {
        if (store == null || modelRows.length == 0) {
            selectedRecords = List.of();
            detailPanel.clear();
            topologyPanel.showRecord(null);
            return;
        }
        List<LogRecord> records = new ArrayList<>();
        int limit = Math.min(modelRows.length, MAX_DETAIL_RECORDS);
        for (int i = 0; i < limit; i++) records.add(store.record(modelRows[i]));
        // The whole selected range feeds the LLM as context; the FINAL record is the focus that is
        // shown in the detail viewer and scrolled to in the source (records are in ascending row order).
        selectedRecords = records;
        LogRecord focus = records.get(records.size() - 1);
        detailPanel.showRecords(java.util.List.of(focus));
        detailPanel.setSelectionInfo(records.size());
        syncSourceForRecord(focus);
        // the table's selection IS the step cursor (M21.4/M21.10); skip when the move came FROM stepping
        if (!steppingSelection) {
            topologyPanel.showRecord(focus, tablePanel.viewRowOf(modelRows[limit - 1]));
        }
    }

    /**
     * Point <b>the source view the user can actually see</b> at the selected record's dispatch.
     *
     * <p>Selecting a record is not a request to change tab. There are two source viewers now — the
     * Source tab and the Topology tab's embedded pane — and syncing the wrong one is invisible while
     * syncing by switching tabs would yank the user off the graph they were reading. So: whichever is on
     * screen wins; if the topology is showing without its source pane open there is nothing visible to
     * sync, and the Source tab is updated silently so it is already right when they get there.
     */
    private void syncSourceForRecord(LogRecord focus) {
        SourcePanel embedded = topologyPanel.openSourcePane();
        java.awt.Component front = sideTabs == null ? null : sideTabs.getSelectedComponent();
        SourceTarget target = chooseSourceTarget(front == topologyPanel, front == sourcePanel,
                embedded != null);
        // the toggle governs the embedded pane only — it lives on that toolbar and describes that pane
        if (target == SourceTarget.EMBEDDED && !topologyPanel.isSourceSyncOn()) return;
        SourcePanel panel = target == SourceTarget.EMBEDDED ? embedded : sourcePanel;
        if (panel != null) panel.showDispatchFor(focus);
    }

    /** Which source viewer a record selection should update. */
    enum SourceTarget { EMBEDDED, SOURCE_TAB }

    /**
     * The routing rule, kept as a pure function so it can be reasoned about and tested without a window.
     *
     * <p>Rules, in order: a <b>visible</b> viewer always wins, because syncing one the user cannot see is
     * the same as not syncing. When the topology is in front without its source pane open there is
     * nothing visible to update, so the Source tab is updated <b>silently</b> — it is then already right
     * when they switch. Never switch tabs: selecting a record is not a request to leave the graph.
     */
    static SourceTarget chooseSourceTarget(boolean topologyShowing, boolean sourceTabShowing,
                                           boolean embeddedPaneOpen) {
        if (topologyShowing && embeddedPaneOpen) return SourceTarget.EMBEDDED;
        if (sourceTabShowing) return SourceTarget.SOURCE_TAB;
        // neither viewer is in front: prefer the pane the user deliberately opened
        return embeddedPaneOpen ? SourceTarget.EMBEDDED : SourceTarget.SOURCE_TAB;
    }

    /**
     * Files the user picked in a chooser THIS SESSION — the chooser IS the grant (M29 D-F4). The
     * external verb may read from the configured exchange directory or from this set; nothing else.
     */
    private final java.util.Set<java.nio.file.Path> sessionFileGrants = new java.util.LinkedHashSet<>();

    java.util.Set<java.nio.file.Path> sessionFileGrants() {
        return sessionFileGrants;
    }

    /** File ▸ Add series from CSV… (M29.2): declared columns/clock, loaded onto the current graph tab. */
    private void addExternalSeries() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("External series CSV");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path csv = fc.getSelectedFile().toPath().toAbsolutePath().normalize();
        sessionFileGrants.add(csv);   // the chooser IS the grant (D-F4)

        String[] header;
        try (var lines = java.nio.file.Files.lines(csv)) {
            header = lines.findFirst()
                    .map(h -> telamin.fluxtion.audit.analyser.analyser.graph.ExternalCsvLoader
                            .splitCsv(h).toArray(String[]::new))
                    .orElse(new String[0]);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read " + csv.getFileName() + ": " + ex.getMessage(),
                    "External series", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextField label = new JTextField(csv.getFileName().toString().replaceFirst("\\.csv$", ""), 14);
        JComboBox<String> timeCol = new JComboBox<>(header);
        JComboBox<String> valueCol = new JComboBox<>(header);
        if (header.length > 1) valueCol.setSelectedIndex(1);
        JComboBox<String> format = new JComboBox<>(new String[]{"epochMillis", "epochSeconds", "iso8601"});
        format.setEditable(true);   // or a DateTimeFormatter pattern
        JTextField zone = new JTextField("UTC", 10);
        JTextField offset = new JTextField("0", 6);

        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 6, 4));
        form.add(new JLabel("Legend label:"));      form.add(label);
        form.add(new JLabel("Time column:"));       form.add(timeCol);
        form.add(new JLabel("Value column:"));      form.add(valueCol);
        form.add(new JLabel("Time format:"));       form.add(format);
        form.add(new JLabel("Zone (IANA):"));       form.add(zone);
        form.add(new JLabel("Offset (ms):"));       form.add(offset);
        if (JOptionPane.showConfirmDialog(this, form, "Add series from CSV — the clock is declared, never guessed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        long offsetMs;
        try {
            offsetMs = Long.parseLong(offset.getText().trim());
        } catch (NumberFormatException ex) {
            offsetMs = 0;
        }
        var spec = new telamin.fluxtion.audit.analyser.analyser.config.GraphSpec.ExternalSpec(
                csv.toString(), label.getText().trim(),
                String.valueOf(timeCol.getSelectedItem()), String.valueOf(format.getSelectedItem()),
                zone.getText().isBlank() ? null : zone.getText().trim(),
                String.valueOf(valueCol.getSelectedItem()), offsetMs);
        GraphPanel panel = graphTabs.graphForAction(null, false);
        if (panel == null) {
            JOptionPane.showMessageDialog(this, "Open a log first — graphs live on a loaded log.",
                    "External series", JOptionPane.WARNING_MESSAGE);
            return;
        }
        var merged = new java.util.ArrayList<>(panel.externalSpecs());
        merged.removeIf(s -> s.label().equals(spec.label()));   // replace-by-label, like everything else
        merged.add(spec);
        panel.setExternal(merged);
        if (sideTabs != null) sideTabs.setSelectedComponent(graphTabs);   // show the plot it landed on
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        if (config.logFile != null && !S3Source.isS3(config.logFile)) {
            fc.setCurrentDirectory(new File(config.logFile).getParentFile());
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(fc.getSelectedFile().toPath());
        }
    }

    private void chooseS3() {
        String prefill = S3Source.isS3(config.logFile) ? config.logFile : "s3://";
        String uri = JOptionPane.showInputDialog(this,
                "S3 URI (s3://bucket/key) — uses your aws CLI credentials:", prefill);
        if (uri != null && S3Source.isS3(uri.trim())) openS3(uri.trim());
    }

    /** A project offer the agent path declined to show as a dialog — reported as DATA instead. */
    private Path pendingProjectOffer;

    /**
     * A rolled-set offer the agent path declined to show as a dialog (M35.9, the fifth instance of
     * the load-path modal): the member files, most recent last. Reported in {@code context} as
     * {@code rolledSetOffer}; cleared with the log. Opening the set is {@code open {logs: [...]}}.
     */
    private java.util.List<String> pendingRolledSetOffer;

    /** Opens a local path or an s3:// URI for a PERSON — dialogs are shown. */
    public void openLocation(String location) {
        openLocation(location, OpenRequest.HUMAN);
    }

    /**
     * Opens a local path or an s3:// URI on behalf of whoever {@code request} says asked (M35.9).
     * The request travels with the load and is read once, in {@code onLoaded}: on the socket path
     * every dialog the load would show becomes data instead, because nobody is at the screen to
     * answer it, and {@code store} would already be live behind the dialog.
     */
    public void openLocation(String location, OpenRequest request) {
        if (S3Source.isS3(location)) openS3(location, request);
        else openFile(Path.of(location), request);
    }

    /** Loads an s3:// log via the aws CLI on the background executor, for a person. */
    public void openS3(String uri) {
        openS3(uri, OpenRequest.HUMAN);
    }

    public void openS3(String uri, OpenRequest request) {
        pendingRolledSetOffer = null;   // any offer belonged to the previous open
        status.setText("Loading " + uri + " …");
        setBusy(true);
        Background.run(
                () -> {
                    try {
                        Path tmp = S3Source.fetchToFile(uri, config.awsProfile, config.awsRegion);
                        LogStore s3Store = LogStores.open(tmp, config.memoryThresholdMb);
                        var report = telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator
                                .validate(s3Store.index());
                        return new Object[]{s3Store, report};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                out -> onLoaded((LogStore) out[0], uri,
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1], request),
                err -> {
                    setBusy(false);
                    status.setText("Failed to load " + uri + ": " + rootMessage(err));
                    JOptionPane.showMessageDialog(this, rootMessage(err), "S3 load failed",
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    /** One display line per installed reader + any plugin load notes (Settings ▸ Plugins). */
    private java.util.List<String> readerSummaries() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var r : readerRegistry.readers()) {
            var caps = r.capabilities();
            out.add(r.formatId() + " — " + r.displayName() + "  (timeBase " + r.timeBase().epoch()
                    + "/" + r.timeBase().zone() + "/" + r.timeBase().source()
                    + (caps.follow() ? ", follow" : "") + (caps.byteAnchors() ? ", byteAnchors" : "")
                    + (caps.ordering() == telamin.fluxtion.audit.analyser.analyser.spi
                            .AuditLogReader.Ordering.TOTAL ? "" : ", PARTIAL ORDER") + ")");
        }
        out.addAll(readerRegistry.loadNotes());
        return out;
    }

    /** Log-source readers: the built-in YAML reader + explicitly-installed plugin jars (M31 D-P3). */
    private final telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry readerRegistry =
            new telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry();

    /** The active log's time-order validation (M30 D-R3) — clean until a load says otherwise. */
    /**
     * What the loaded log says about the producer that wrote it. Reported like the time-order report —
     * status bar and {@code context}, never a dialog: opens arrive from the socket as often as from a
     * human, and a modal in the load path is the defect M35.7 closed.
     */
    private telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics producerDiagnostics =
            telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.clean();

    private telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport timeOrderReport =
            telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean();

    /** Loads a log file on the background executor and swaps in the new model on the EDT, for a person. */
    public void openFile(Path path) {
        openFile(path, OpenRequest.HUMAN);
    }

    public void openFile(Path path, OpenRequest request) {
        pendingRolledSetOffer = null;   // any offer belonged to the previous open
        // M30 D-R5: opening a member of a rolled set OFFERS the set — never assumes it
        try {
            var siblings = telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.discoverSiblings(path);
            if (siblings.size() > 1 && request.fromActionSocket()) {
                // M35.9: the fifth load-path modal, found by threading the request. An agent opening
                // one member of a set would have hung on "Open the whole set?" — recorded as data
                // instead; the set is one `open {logs: [...]}` away and context names the files.
                var set = telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.resolve(siblings);
                pendingRolledSetOffer = set.ordered().stream()
                        .map(s -> s.file().toString()).toList();
            } else if (siblings.size() > 1) {
                var set = telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.resolve(siblings);
                StringBuilder msg = new StringBuilder("This file looks like part of a rolled set ("
                        + siblings.size() + " files). Open the whole set, in content order?\n\n");
                for (var s : set.ordered()) {
                    msg.append("  ").append(s.file().getFileName());
                    if (!s.untimed()) msg.append("   ").append(TimeFormat.utc(s.firstTime()))
                            .append(" → ").append(TimeFormat.utc(s.lastTime() == null ? s.firstTime() : s.lastTime()));
                    else msg.append("   (no timed records — position by name)");
                    msg.append('\n');
                }
                int choice = JOptionPane.showConfirmDialog(this, msg.toString(),
                        "Rolled log set found", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    openRolledSet(set, request);
                    return;
                }
            }
        } catch (Exception ignored) {
            // discovery is best-effort — a failed probe must never block opening the file itself
        }
        status.setText("Loading " + path + " …");
        setBusy(true);
        openFileWithReader(path, null, request);
    }

    /** Open via the reader registry (M31): explicit {@code format} wins; otherwise canOpen decides. */
    void openFileWithReader(Path path, String format, OpenRequest request) {
        Background.run(
                () -> {
                    try {
                        var reader = readerRegistry.readerFor(path, format);
                        if (reader == null) {
                            throw new RuntimeException(format != null
                                    ? "no installed reader has format '" + format + "' — installed: "
                                            + readerRegistry.describeReaders()
                                    : "no installed reader recognises " + path.getFileName()
                                            + " — installed: " + readerRegistry.describeReaders());
                        }
                        LogStore s = readerRegistry.open(reader, path, config.memoryThresholdMb);
                        var report = telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator
                                .validate(s.index());
                        return new Object[]{s, report};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                out -> onLoaded((LogStore) out[0], path.toString(),
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1], request),
                err -> {
                    setBusy(false);
                    status.setText("Failed to load " + path + ": " + rootMessage(err));
                    JOptionPane.showMessageDialog(this, rootMessage(err), "Load failed",
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    /** Open a resolved rolled set as one logical log (M30). */
    private void openRolledSet(telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.RollSet set,
                               OpenRequest request) {
        var files = set.ordered().stream()
                .map(telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.Sibling::file).toList();
        pendingRolledSetOffer = null;   // the set IS the answer to any offer that was pending
        status.setText("Loading rolled set (" + files.size() + " files) …");
        setBusy(true);
        Background.run(
                () -> {
                    try {
                        var s = telamin.fluxtion.audit.analyser.analyser.parse.RolledLogStore.open(
                                files, config.memoryThresholdMb);
                        var report = set.report().merged(
                                telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator
                                        .validate(s.index()));
                        return new Object[]{s, report};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                out -> onLoaded((LogStore) out[0],
                        files.get(files.size() - 1).getFileName() + " (+" + (files.size() - 1) + " rolled)",
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1], request),
                err -> {
                    setBusy(false);
                    status.setText("Failed to load rolled set: " + rootMessage(err));
                    JOptionPane.showMessageDialog(this, rootMessage(err), "Load failed",
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    // ---- M35.1 · lifecycle -----------------------------------------------------------------------

    /**
     * Close the log and everything DERIVED from it (M35.1). The owner's rule, settled in the brief:
     * <b>derived state clears, profile state survives and degrades loudly.</b>
     *
     * <p>Derived and cleared here: the store, table, filter, search, slider, event checklist,
     * summary, detail pane, flags and findings (per-file — they are model row indices), the
     * topology's execution shading and step cursor, follow, and the time-order report.
     *
     * <p>Profile state deliberately UNTOUCHED: named graph specs, named focuses, source roots,
     * saved reports, project settings. A report that can no longer resolve says so on its own —
     * {@code ReportResolver} with a null index already yields "written against X; no log is loaded"
     * and marks every anchor unresolved. That is announce-never-forbid (D-I3a), and it is why this
     * method does not go near {@code config}.
     *
     * <p>A graph the user OPENED is not touched either: it is a separate artefact they chose, and
     * {@link #closeGraph} is its counterpart. Only its shading goes. A graph the SOURCE supplied
     * (M34.1) is different — it arrived with this log and describes this log, so it goes with it
     * (review M34 F1); so does the ordering caveat, which described this source (F3).
     */
    private void closeLog() {
        setFollowing(false);
        followPath = null;
        if (store != null) store.close();
        store = null;
        tableModel = null;
        logDisplayLocation = null;
        logLocalPath = null;
        logProvenance = null;          // §E: it described THAT log, not the next one
        loggedNodeSample = java.util.Set.of();   // the sample described THAT log too
        loggedSampleScanned = 0;
        logProvenanceSource = null;
        declinedSourceGraph = null;    // review N1: that offer came with the log that just closed
        timeOrderReport = telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean();
        producerDiagnostics = telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.clean();
        flaggedRows.clear();
        findings.clear();
        flaggedOnly = false;

        filter = new FilterState();          // a fresh, unbound filter — listeners on the old one die with it
        tablePanel.setRowFilter(null);
        tablePanel.setModel(new LogTableModel(new telamin.fluxtion.audit.analyser.analyser.parse
                .HeapLogStore("")));         // an empty model, not a stale one
        detailPanel.clear();
        summaryPanel.clear();
        eventFilterPanel.clear();
        graphTabs.unbind();
        topologyPanel.clearExecution();
        topologyPanel.setOrderMeaningful(true);   // M34 review F3: "ARRIVAL ORDER" described THAT source
        topologyPanel.clearSourceGraph();         // M34 review F1: a reader's graph is log-derived state
        lastPairing = null;                // review F2: the verdict was about THIS log — with it gone the
        publishPairing();                  // graph makes no claim, and the panel's note must not keep one
        pendingProjectOffer = null;        // review F3: an offer made for a log that is no longer open
        pendingRolledSetOffer = null;      // M35.9: likewise
        if (reportsPanel != null) reportsPanel.refresh();   // re-render: anchors now say why they fail

        showingLabel.setText(" ");
        searchField.setText("");
        searchField.setEnabled(false);
        timeSlider.setRange(null, null);
        timeSlider.setHistogram(new int[0]);
        syncingWindow = true;
        windowCombo.setSelectedIndex(0);
        syncingWindow = false;
        windowCombo.setEnabled(false);
        if (followMenuItem != null) followMenuItem.setEnabled(false);
        if (followButton != null) followButton.setEnabled(false);

        status.setText("No log open" + (topologyPanel.hasGraph()
                ? " · graph " + topologyPanel.graphLabel() + " still loaded" : ""));
        updateLifecycleMenu();
        refreshProjectPanel();                                        // M37
    }

    /** Close the loaded topology, leaving the log alone (M35.1). */
    private void closeGraph() {
        topologyPanel.clearGraph();
        lastPairing = null;
        publishPairing();
        status.setText(store == null ? "No log open" : "Graph closed · " + store.size() + " records");
        updateLifecycleMenu();
        refreshProjectPanel();                                        // M37
    }

    /** Both — back to a fresh start (M35.1). Profile state still survives; this is not "new project". */
    private void resetAll() {
        closeLog();
        closeGraph();
        status.setText("Reset — no log, no graph");
    }

    /** Close items are enabled only when there is something to close. */
    private void updateLifecycleMenu() {
        // M44: the single place the processor is told what is open. Every path that opens or closes a
        // log or graph already lands here, which is why the observation hangs off it rather than being
        // hand-placed at ten call sites that would drift apart.
        noteLogState();
        noteGraphState();
        syncRecordsCard();          // M36: the start page shows exactly when there is no log
        if (closeLogItem != null) closeLogItem.setEnabled(store != null);
        if (closeGraphItem != null) closeGraphItem.setEnabled(topologyPanel.hasGraph());
        if (resetItem != null) resetItem.setEnabled(store != null || topologyPanel.hasGraph());
    }

    /**
     * @param request who asked and what they declared (M35.9) — read here and nowhere else. Every
     *                load-time side effect below takes its answer from this one immutable value, so
     *                no step can find it spent and no concurrent load can cross it.
     */
    private void onLoaded(LogStore loaded, String location,
                          telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport report,
                          OpenRequest request) {
        this.timeOrderReport = report == null
                ? telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean() : report;
        if (store != null && store != loaded) store.close();   // release the previous file's channel
        this.store = loaded;
        this.logDisplayLocation = location;
        this.logLocalPath = loaded.localFile();                 // real local file (temp file for S3)
        logProvenance = request.provenance();                   // §E: what THIS request declared
        logProvenanceSource = logProvenance == null ? null : "declared by the opener";
        // M38.3 D-C4: a project may declare environments. Consulted ONLY when nobody declared — a declared
        // value (the opener, or UP-MNG-03's server) always wins, and context says which answered.
        if (logProvenance == null && project.hasProject()) {
            Path root = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile());
            Path local = loaded.localFile() == null ? null : Path.of(loaded.localFile());
            telamin.fluxtion.audit.analyser.analyser.config.Environment
                    .match(config.environments, config.defaultEnvironment, root, local)
                    .ifPresent(m -> { logProvenance = m.environment().provenance(); logProvenanceSource = m.reason(); });
        }
        // M35.9: every load-time side effect reads the request, not a field. The field version failed
        // four times — the last when maybeOfferProject consumed the socket flag 59 lines before the
        // time-order gate read it, so a modal the socket path "suppressed" fired on every agent open.
        final boolean loadFromSocket = request.fromActionSocket();
        currentRequest = request;      // review F1: a follow rotation reloads with the SAME audience

        // M35.2 FIRST, and deliberately before maybeOfferProject(): that offer is a MODAL dialog, and
        // everything after it waits for a human — which on the agent path is nobody. `store` is
        // already assigned above, so a stale graph would otherwise be live and answerable (coverage,
        // shading, step-through) while the app sat behind a dialog nobody could see.
        // M34.2 — the ordering claim reaches the VIEW, not just `context`. Until now M34.1 plumbed
        // the flag and nothing consumed it, so the topology went on painting ordinal badges over a
        // source that never decided an order: the spike's §3 finding, still live.
        topologyPanel.setOrderMeaningful(loaded.index().totalOrder());
        // M34 review F1: a graph the PREVIOUS log's reader supplied is that log's residue, not intent —
        // judging it against this log (repairLoadedGraph) would keep it whenever the two systems are
        // similar enough, and then THIS log's own source graph could never take the slot. Only an
        // OPENED graph is judged and kept.
        if (topologyPanel.clearSourceGraph()) {
            status.setText(status.getText() + "  ·  the previous log's source-supplied graph closed with it");
        }
        repairLoadedGraph(loaded);
        offerSourceGraph(loaded);      // M34.1 — after the re-pair, so a stale graph is gone first
        maybeOfferProject(loadFromSocket);   // M20.3 — the log may sit inside a project
        flaggedRows.clear();       // flags are per-file (model row indices)
        findings.clear();
        flaggedOnly = false;
        tableModel = new LogTableModel(loaded);
        tablePanel.setModel(tableModel);
        tablePanel.setVisibleColumns(new java.util.HashSet<>(config.hiddenColumns));
        detailPanel.clear();

        // one shared filter drives the table, the event checklist, the time slider and the summary
        final LogIndex index = loaded.index();
        filter = new FilterState();
        filter.setTextSource(loaded::rawText);   // search covers eventToString, thread AND nodeLogs
        searchField.setText("");
        searchField.setEnabled(true);
        timeSlider.setRange(loaded.minLogTime(), loaded.maxLogTime());
        timeSlider.setHistogram(buildHistogram(loaded, 160));
        timeSlider.bind(filter);
        boolean hasTime = loaded.minLogTime() != null && loaded.maxLogTime() != null;
        syncingWindow = true;
        windowCombo.setSelectedIndex(0);   // "All"
        syncingWindow = false;
        windowCombo.setEnabled(hasTime);
        syncWindowScroll();
        eventFilterPanel.bind(index, filter);
        summaryPanel.bind(loaded, filter);
        // Snapshot BEFORE binding: bind() must not fire an edit (fixed in GraphTabs), but the graphs to
        // restore are the profile's, and nothing in the bind/restore sequence may be allowed to rewrite
        // config.savedGraphs in between — belt to GraphTabs' braces.
        List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec> savedGraphs = List.copyOf(config.savedGraphs);
        graphTabs.bind(loaded, filter);
        graphTabs.restore(savedGraphs);          // reopen graphs saved in the profile
        tablePanel.setRowFilter(new RowFilter<LogTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                int row = entry.getIdentifier();
                if (flaggedOnly && !flaggedRows.contains(row)) return false;
                return filter.test(index, row);
            }
        });
        filter.addListener(this::onFilterChanged);
        onFilterChanged();

        // source navigation: configure roots, then infer the EventProcessor in the background
        sourceService.configure(effectiveSourceRoots(), config.selectedEventProcessor,
                config.mavenRepos, config.searchMavenRepos);
        Background.run(() -> { sourceService.warmMavenIndex(); return null; }, r -> { }, err -> { });
        sourcePanel.setProcessors(candidateProcessors(), config.selectedEventProcessor);
        topologyPanel.setEmbeddedProcessors(candidateProcessors(), config.selectedEventProcessor);
        inferAndPopulateSource();
        refreshProjectPanel();                                        // M37: the log, its provenance, the pairing

        config.logFile = location;
        config.addRecent(location);
        rebuildRecentMenu();
        saveConfigQuietly();
        setBusy(false);
        String range = loaded.minLogTime() == null ? "no timestamps"
                : TimeFormat.utc(loaded.minLogTime()) + " → " + TimeFormat.utc(loaded.maxLogTime()) + " UTC";
        String orderWarning = timeOrderReport.isClean() ? ""
                : "  ·  ⚠ time-order violations (" + timeOrderReport.violations().size()
                        + ") — ask 'context' or see the load report";
        // What the log says about its EMITTER. Computed here, after the index is built, because two of
        // the three checks read the index and the third reads a record's text.
        producerDiagnostics = telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics
                .of(loaded.index(), loaded::rawText);
        String producerWarning = producerDiagnostics.isClean() ? ""
                : "  ·  ⚠ " + producerDiagnostics.findings().get(0).kind().name().toLowerCase(
                        java.util.Locale.ROOT).replace('_', ' ') + " — ask 'context', or hover";
        status.setText(loaded.size() + " records · " + range + " · "
                + (logProvenance != null ? logProvenance + "  (" + displayName(location) + ")"
                        : displayName(location)) + orderWarning + producerWarning);
        // the full sentence, where there is room for it — the status bar has none
        status.setToolTipText(producerDiagnostics.isClean() ? null
                : String.join("\n\n", producerDiagnostics.messages()));
        if (!timeOrderReport.isClean() && !loadFromSocket) {
            // D-R3: the report is shown, never buried — once, at load, with the evidence lines.
            // Review F5 (M35.7's species, seen live by the owner): on a socket-driven open nobody at the
            // screen asked for this log, so a modal here waits for an answer that cannot come and greets
            // whoever walks past later with a verdict about a log that may already be closed. That
            // audience gets the report where it reads: the status bar (above), 'context'.timeOrder and
            // the timeOrderNote caveat on every time-anchored verb (D-R4). Both this gate and the
            // project offer read the SAME local, captured once at the top of the load — the field
            // they used to share was already consumed by the time this line ran.
            JOptionPane.showMessageDialog(this,
                    String.join("\n", timeOrderReport.summarise()),
                    "Time-order report", JOptionPane.WARNING_MESSAGE);
        }

        // follow/tail: track this file if it's local & followable; drop follow if it isn't
        boolean followable = !S3Source.isS3(location) && loaded.supportsFollow();
        followPath = followable ? location : null;
        if (following && !followable) setFollowing(false);
        else if (following && followTimer != null) followTimer.restart();   // resume after a rotation reload
        if (followMenuItem != null) {
            followMenuItem.setEnabled(followable);
            followMenuItem.setToolTipText(followable
                    ? "Poll the open local file for newly-appended records and auto-scroll"
                    : "This source cannot follow (rolled sets and non-file containers do not tail)");
        }
        if (followButton != null) followButton.setEnabled(followable);
        updateLifecycleMenu();
    }

    /**
     * M34.1 — a source that declares its own graph offers it. It YIELDS to anything a person or agent
     * opened (GraphSource's precedence, which is M35.3's asymmetry: intent beats convenience), so a
     * chosen graph is never silently displaced by one that merely arrived with the log.
     */
    private void offerSourceGraph(LogStore loaded) {
        if (loaded.sourceGraphNote() != null) {
            // review M34 F2: the reader TRIED and failed. Recorded by the store since slice 3, read by
            // nobody until now — so an unreachable registry looked exactly like a source with no graph.
            status.setText(status.getText() + "  ·  ⚠ " + loaded.sourceGraphNote());
        }
        loaded.sourceGraph().ifPresent(g -> {
            if (topologyPanel.loadFromSource(g)) {
                status.setText(status.getText() + "  ·  graph supplied by the source ("
                        + g.nodes().size() + " nodes, " + g.provenance().name().toLowerCase(
                                java.util.Locale.ROOT) + ")");
                judgeOpenedGraph();
                updateLifecycleMenu();
            } else {
                // review N1: the offer was DECLINED because an opened graph holds the slot. The
                // precedence is right, but saying nothing means the app knows something the user
                // does not — that a second, possibly better-matched graph came with this log and
                // was passed over. D-A2 asks which graph is shown; this says which was not.
                declinedSourceGraph = g.nodes().size() + " nodes, "
                        + g.provenance().name().toLowerCase(java.util.Locale.ROOT);
                status.setText(status.getText() + "  ·  the source also offered a graph ("
                        + declinedSourceGraph + ") — yours was kept");
            }
        });
    }

    /**
     * The request that opened the log in force (review F1). Set when a load LANDS and read only by
     * the follow timer, so it re-introduces none of R1's race: nothing consumes it mid-load.
     */
    private OpenRequest currentRequest = OpenRequest.HUMAN;

    /** A source graph this log offered that an OPENED graph outranked, or null (review N1). */
    private String declinedSourceGraph;

    /** How many records to sample when deciding whether a loaded graph still applies (M35.2). */
    private static final int PAIRING_SAMPLE = 500;

    /**
     * M35.2 — a graph loaded for the PREVIOUS log must not silently survive into this one.
     *
     * <p>Sampled rather than exhaustive: a full scan is what {@code coverage} is for, and this runs on
     * every open. The first {@value #PAIRING_SAMPLE} records name the recurring nodes of any real
     * run, and the verdict carries its counts so a caller can see what was actually compared.
     */
    /**
     * M35.4 — the .graphml files under the source roots, ranked against the open log. On demand
     * only: a walk of a monorepo is not something to do on every {@code context} call, and a
     * ranking nobody asked for is a recommendation nobody can see the cost of.
     */
    private telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery.Result discoverGraphs0() {
        java.util.Set<String> logged = new java.util.LinkedHashSet<>();
        if (store != null) {
            int scan = Math.min(store.size(), PAIRING_SAMPLE);
            for (int row = 0; row < scan; row++) {
                for (var nodeLog : store.record(row).nodeLogs()) logged.add(nodeLog.instanceId());
            }
        }
        return telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery.scan(
                config.sourceRoots, logged);
    }

    /**
     * The human half of M35.4 — list the candidates and let the user pick. A dialog, deliberately:
     * the whole point of the slice is that nothing is chosen automatically, and a menu item that
     * silently loaded the best match would be the convenience that reintroduces the defect.
     */
    private void chooseDiscoveredGraph() {
        var result = discoverGraphs0();
        if (result.candidates().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    config.sourceRoots.isEmpty()
                            ? "No source roots are configured — add one in Settings, or use "
                                    + "File \u25b8 Open GraphML\u2026"
                            : "No .graphml found under the configured source roots."
                                    + (result.notes().isEmpty() ? ""
                                            : "\n\n" + String.join("\n", result.notes())),
                    "Find GraphML", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] options = result.candidates().stream()
                .map(telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery.Candidate::describe)
                .toArray(String[]::new);
        Object picked = JOptionPane.showInputDialog(this,
                (store == null ? "No log is open, so these are unranked.\n\n"
                        : "Ranked against the open log \u2014 best fit first.\n\n")
                        + "Pick a topology to open:",
                "Find GraphML in source roots", JOptionPane.QUESTION_MESSAGE, null,
                options, options[0]);
        if (picked == null) return;                       // offered, declined — nothing loaded
        for (var c : result.candidates()) {
            if (c.describe().equals(picked)) {
                topologyPanel.load(c.file());
                judgeOpenedGraph();
                updateLifecycleMenu();
                if (sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
                return;
            }
        }
    }

    /** The loaded graph judged against a log — one comparison, used by both open directions. */
    private telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing pairingAgainst(LogStore log) {
        java.util.Set<String> logged = new java.util.LinkedHashSet<>();
        int scan = Math.min(log.size(), PAIRING_SAMPLE);
        for (int row = 0; row < scan; row++) {
            for (var nodeLog : log.record(row).nodeLogs()) logged.add(nodeLog.instanceId());
        }
        var p = telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing.of(
                topologyPanel.authoredNodeIds(), logged);
        if (log.size() > PAIRING_SAMPLE) {
            // review F1: the numbers describe the SAMPLE, and the sentence must say so — "the N node(s)
            // this log writes" is a whole-log claim this method never checked
            p = new telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing(
                    p.logged(), p.matched(), p.applies(),
                    p.reason() + " (judged on the first " + PAIRING_SAMPLE + " of " + log.size() + " records)");
        }
        return p;
    }

    /**
     * M35.3 — a graph the user OPENED is judged but never closed. The asymmetry with
     * {@link #repairLoadedGraph} is the point: when a log arrives, a mismatched graph is RESIDUE
     * from the previous investigation and closing it is the safe default; when a graph arrives, it
     * is INTENT — someone asked for this processor — so a mismatch is announced and left alone.
     * Announce-never-forbid applies where there is intent to respect.
     *
     * @return the verdict, or null when there is nothing to compare
     */
    private telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing judgeOpenedGraph() {
        if (store == null || !topologyPanel.hasGraph()) {
            lastPairing = null;
            publishPairing();
            return null;
        }
        var pairing = pairingAgainst(store);
        lastPairing = pairing;
        publishPairing();
        String name = topologyPanel.graphLabel();     // may have no FILE — a source can supply one
        // review M34 F5: "you opened it deliberately" is false for a graph the SOURCE supplied — nobody
        // opened it. It is kept because it arrived with this log and is the source's own claim.
        boolean opened = topologyPanel.graphSource()
                == telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.OPENED;
        status.setText(store.size() + " records · graph " + name + (pairing.applies()
                ? " · " + pairing.reason()
                : "  ·  ⚠ " + pairing.reason() + (opened
                        ? " — kept, you opened it deliberately"
                        : " — kept, the source supplied it with this log")));
        return pairing;
    }

    /**
     * M35.2, now DECIDED by the session processor (M44.2).
     *
     * <p>What used to be here — judge the open graph against the arriving log, keep it or close it —
     * is {@code LogArrival} in the graph, replayable and audited. This method does what an adapter
     * does: refresh the evidence, hand it over, and render whatever came back.
     *
     * <p>The load itself deliberately did NOT move. It is asynchronous ({@code Background.run}) and
     * the driver is synchronous and single-in-flight by design, so making the open an effect the
     * processor requests would have meant either an asynchronous driver or a lie about when the load
     * finished. Moving the decision without the load is the honest half.
     */
    private void repairLoadedGraph(LogStore loaded) {
        refreshLoggedNodeSample();
        noteGraphState();
        noteLogState();                     // the arrival — LogArrival decides on this
        lastPairing = session == null ? null : session.processor().pairing.verdict();
        publishPairing();
    }

    /**
     * M35.6 — push the verdict onto the Topology panel, where it stays. Called wherever
     * {@code lastPairing} changes, so the panel and {@code context} can never disagree.
     */
    private void publishPairing() {
        if (!topologyPanel.hasGraph() || lastPairing == null) {
            topologyPanel.setPairingNote(null);
        } else {
            topologyPanel.setPairingNote(lastPairing.applies()
                    ? "fits this log (" + lastPairing.matched() + "/" + lastPairing.logged() + ")"
                    : "\u26a0 DOES NOT FIT THIS LOG \u2014 " + lastPairing.reason());
        }
        refreshProjectPanel();                                        // M37 D-L4: the verdict is a row
    }

    /** The most recent re-pair verdict, surfaced by {@code context} (M35.2). */
    private telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing lastPairing;

    /** Turn follow/tail mode on or off (idempotent; keeps the toolbar + menu toggles in sync). */
    private void setFollowing(boolean on) {
        if (on && (store == null || !store.supportsFollow() || followPath == null)) {
            status.setText("Follow is available for heap-loaded local files (below the memory threshold).");
            on = false;
        }
        following = on;
        if (followButton != null && followButton.isSelected() != on) followButton.setSelected(on);
        if (followMenuItem != null && followMenuItem.isSelected() != on) followMenuItem.setSelected(on);
        if (followTimer == null) {
            followTimer = new Timer(FOLLOW_POLL_MS, e -> pollFollow());
        }
        if (on) {
            followTimer.start();
            status.setText("Following " + displayName(followPath) + " — watching for new records…");
        } else {
            followTimer.stop();
        }
    }

    /** One tail poll: append any newly-completed records, or reload if the file was rotated/truncated. */
    private void pollFollow() {
        if (!following || store == null || followPath == null) return;
        int before = store.size();
        int added;
        try {
            added = store.appendFrom(Path.of(followPath));
        } catch (java.io.IOException ex) {
            status.setText("Follow read failed: " + rootMessage(ex));
            return;
        }
        if (added < 0) {                 // shrank / rotated → reload from scratch (resumes on load)
            followTimer.stop();          // avoid re-entrant reloads while the async load runs
            // review F1: carry WHO ASKED, not just what was declared — a rotation's audience is
            // whoever was there for the open that started it
            // M38.3: re-declare what the OPENER declared, not what the project supplied — the environment is
            // matched again on reload, so its provenance keeps its "project environment" source
            openFile(Path.of(followPath), OpenRequest.reload(currentRequest, currentRequest.provenance()));
            return;
        }
        if (added == 0) return;
        if (tableModel != null) tableModel.rowsAppended(before);
        Long mx = store.maxLogTime();
        if (mx != null) timeSlider.extendAbsMax(mx);
        timeSlider.setHistogram(buildHistogram(store, 160));
        onFilterChanged();
        tablePanel.scrollToLast();
        String range = store.minLogTime() == null ? "no timestamps"
                : TimeFormat.utc(store.minLogTime()) + " → " + TimeFormat.utc(store.maxLogTime()) + " UTC";
        status.setText("Following " + displayName(followPath) + " · " + store.size() + " records · " + range);
    }

    /** Record-density buckets across the log-time range, for the slider histogram. */
    private static int[] buildHistogram(LogStore store, int buckets) {
        Long min = store.minLogTime(), max = store.maxLogTime();
        if (min == null || max == null || max <= min) return new int[0];
        int[] h = new int[buckets];
        double span = max - min;
        for (int i = 0; i < store.size(); i++) {
            Long lt = store.index().logTime(i);
            if (lt == null) continue;
            int b = (int) ((lt - min) / span * (buckets - 1));
            if (b >= 0 && b < buckets) h[b]++;
        }
        return h;
    }

    /** Reflect the slider's current window span/position onto the pan scrollbar. */
    private void syncWindowScroll() {
        syncingWindow = true;
        try {
            int extent = Math.max(1, Math.min(1000, (int) Math.round(1000 * timeSlider.windowSpanFraction())));
            int value = (int) Math.round(timeSlider.windowStartFraction() * (1000 - extent));
            windowScroll.setValues(value, extent, 0, 1000);
            windowScroll.setEnabled(timeSlider.isWindowed());
            windowScroll.setBlockIncrement(Math.max(1, extent));
            windowScroll.setUnitIncrement(Math.max(1, extent / 4));
        } finally {
            syncingWindow = false;
        }
    }

    /** A selectable outer-window length for the time slider. */
    private record WindowSpan(String label, long millis) {
        static final WindowSpan[] ALL_OPTIONS = {
                new WindowSpan("All", -1),
                new WindowSpan("1 week", 7L * 24 * 3_600_000),
                new WindowSpan("1 day", 24L * 3_600_000),
                new WindowSpan("6 hours", 6L * 3_600_000),
                new WindowSpan("1 hour", 3_600_000L),
                new WindowSpan("30 min", 30L * 60_000),
                new WindowSpan("15 min", 15L * 60_000),
                new WindowSpan("5 min", 5L * 60_000),
                new WindowSpan("1 min", 60_000L),
        };

        @Override public String toString() { return label; }
    }

    private static String displayName(String location) {
        int slash = Math.max(location.lastIndexOf('/'), location.lastIndexOf('\\'));
        return slash >= 0 ? location.substring(slash + 1) : location;
    }

    /** Candidate EventProcessors: configured FQNs + those discovered in the strategy package. */
    private List<String> candidateProcessors() {
        Set<String> set = new LinkedHashSet<>(config.eventProcessorFqns);
        if (config.selectedEventProcessor != null) set.add(config.selectedEventProcessor);
        set.addAll(SourceService.discover(sourceService.resolver(), STRATEGY_PKG));
        return new ArrayList<>(set);
    }

    /** Scans a sample of records for node instanceIds and picks the best-covering processor. */
    private void inferAndPopulateSource() {
        final LogStore s = store;
        if (s == null) return;
        Background.run(
                () -> {
                    Set<String> ids = new LinkedHashSet<>();
                    int k = Math.min(s.size(), INFER_SCAN_LIMIT);
                    for (int i = 0; i < k; i++) {
                        for (NodeLog nl : s.record(i).nodeLogs()) ids.add(nl.instanceId());
                    }
                    List<String> candidates = candidateProcessors();
                    String inferred = SourceService.infer(sourceService.resolver(), candidates, ids,
                            config.selectedEventProcessor);
                    return new String[]{inferred, String.join("\n", candidates)};
                },
                result -> {
                    String inferred = result[0];
                    List<String> candidates = result[1].isEmpty() ? List.of() : List.of(result[1].split("\n"));
                    sourceService.select(inferred);
                    config.selectedEventProcessor = inferred;
                    if (inferred != null && !config.eventProcessorFqns.contains(inferred)) {
                        config.eventProcessorFqns.add(inferred);
                    }
                    sourcePanel.setProcessors(candidates, inferred);
                    topologyPanel.setEmbeddedProcessors(candidates, inferred);
                    sourcePanel.showSelectedProcessor();
                    saveConfigQuietly();
                    refreshProjectPanel();                            // M37: "selected" just changed
                },
                err -> { /* source inference is best-effort */ });
    }

    /**
     * Whether no config file existed when this frame was constructed. Captured THEN, because
     * {@code --rest} saves the config in the constructor (it persists the transport setting) and would
     * otherwise make the first run look like a second one by the time the splash timer asks — the
     * first bench run found exactly that: the note below never printed.
     */
    private boolean firstRunAtStart;

    /**
     * First run. <b>No dialog, for anybody</b> — but a process that asked for {@code --rest} is told
     * where its socket is.
     *
     * <p>Two changes met here, from opposite directions, and both were fixing the same thing. M19.7
     * suppressed the modal for {@code --rest} because a PROCESS cannot answer a Settings dialog, so an
     * agent on a fresh machine never finished starting — M35.7's species one step earlier, in the
     * startup path. M36 removed the modal outright: it announced "No configuration was found", which
     * reports the ordinary condition of a first launch as a fault; it demanded source roots and an API
     * key from someone who had not yet seen a single record; and it was a modal on exactly the surface
     * D-S1 says must have nothing to dismiss, because {@link StartPanel} — the start page — IS the
     * first run now.
     *
     * <p>So the modal is gone for the human too, and what M19.7 added stays: under {@code --rest} the
     * endpoint file is named on stdout, which is the one thing an agent actually needed from this
     * method. Keeping only the narrower fix would have left the human case still gated; keeping only
     * the wider one would have dropped the note the loop bench asserts. Settings is on the File menu
     * and one click from the start page's footer, for when there is a reason to want it.
     */
    public void showFirstRunSettingsIfNeeded() {
        if (!firstRunAtStart) return;
        if (Boolean.getBoolean(telamin.fluxtion.audit.analyser.Main.REST_PROPERTY)) {
            // M19.7 (review N2): nobody is at the screen — say where the socket is instead
            System.out.println("[analyser] first run, started with --rest: no configuration yet and no "
                    + "Settings dialog (nobody is at the screen). The REST endpoint is published in "
                    + telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile.wellKnown().path()
                    + "; source roots and processors can be set over it (source_root, open {processor}).");
        }
        // and no dialog for a human either — the start page is the first run (M36 D-S1)
    }

    /**
     * A graph was created/edited/closed (any path — the UI or the {@code graph} verb both mutate the
     * same panels). Persist now, to the right tier: sync → global write (project tier shielded by the
     * session's snapshot) → debounced project write. B-M20-3.
     */
    private void onGraphsEdited() {
        saveConfigQuietly();                       // syncs the open tabs first (see saveConfigQuietly)
        if (project != null) project.requestSave();
    }

    private void onConfigChanged() {
        sourceService.configure(effectiveSourceRoots(), config.selectedEventProcessor,
                config.mavenRepos, config.searchMavenRepos);
        Background.run(() -> { sourceService.warmMavenIndex(); return null; }, r -> { }, err -> { });
        sourcePanel.setProcessors(candidateProcessors(), config.selectedEventProcessor);
        topologyPanel.setEmbeddedProcessors(candidateProcessors(), config.selectedEventProcessor);
        sourcePanel.showSelectedProcessor();
        searchField.setHistory(config.searchHistory);   // reflect cleared/updated history
        if (reportsPanel != null) reportsPanel.refresh();   // reports are project-tier state too
        rebuildRecentMenu();
        applyRestServer();   // honour a change to the REST toggle
        saveConfigQuietly();
        // M20.2 auto-persist. Deliberately here and nowhere else: this funnel is what `source_root` and
        // `open {processor}` already go through, so scripted edits persist without a second code path.
        // Hanging this off dialog-close would silently lose every verb-driven change.
        if (project != null) project.requestSave();
        refreshProjectPanel();                                        // M37: roots and processors may have changed
        rebuildAnalysesMenu();                                        // M38.4: the profile may have gained one
    }

    // ---- settings export / import (M15) ---------------------------------------------------------

    private void exportSettings() {
        syncOpenGraphsIntoConfig();   // so exported graphs match what's on screen, not the last save
        new ExportSettingsDialog(this, config).setVisible(true);
    }

    private void importSettings() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import settings");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Shared settings (*.fluxtion-settings)", "fluxtion-settings"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();

        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read the file: " + rootMessage(ex),
                    "Import settings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
        telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.ImportPlan plan;
        try {
            syncOpenGraphsIntoConfig();   // diff/merge graphs against what's actually open
            // resolve any bundle-relative source roots — against the project root when the file is a
            // canonical .analyser/ profile, its own directory otherwise (M19.2, M35.10)
            plan = share.preview(text, config, telamin.fluxtion.audit.analyser.analyser.config
                    .ProjectProfile.baseDirFor(file.toPath()));
        } catch (telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.IncompatibleVersionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Import settings", JOptionPane.WARNING_MESSAGE);
            return;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "That doesn't look like a settings file: " + rootMessage(ex),
                    "Import settings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (plan.present().isEmpty()) {
            JOptionPane.showMessageDialog(this, "The file contains no shareable settings.",
                    "Import settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // M20.2: make the two intents explicit rather than letting one verb mean both. Merge is the
        // share-a-setup flow and stays additive; open-as-project REPLACES the project-scoped settings
        // and makes this file the active project. Conflating them is what made switching projects pile
        // one setup on top of the last.
        String[] options = {"Merge (share)", "Open as project (replace)", "Cancel"};
        int intent = JOptionPane.showOptionDialog(this,
                "Merge adds these settings to what you have now.\n\n"
                + "Open as project replaces your source roots, Maven repos, event processors, graphs\n"
                + "and hidden columns with this file's, and makes it the active project.",
                "Import settings", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (intent == 1) {
            requestProject(file.toPath(), telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "import-dialog");
            return;
        }
        if (intent != 0) return;   // cancelled, or the dialog was closed

        var selected = ImportSettingsDialog.show(this, plan, file.getName());
        if (selected == null || selected.isEmpty()) return;   // cancelled or nothing chosen

        share.apply(plan, selected, config);
        applyImportedConfig();
        status.setText("Imported settings from " + file.getName());
    }

    /** Capture the open graph tabs into {@code config.savedGraphs} (so a merge sees current state). */
    private void syncOpenGraphsIntoConfig() {
        if (store == null) return;   // no log → tabs are empty; config already holds the profile's graphs
        config.savedGraphs.clear();
        config.savedGraphs.addAll(graphTabs.specs());
    }

    /** Refresh every affected surface after an import merged into {@code config}. */
    private void applyImportedConfig() {
        onConfigChanged();   // source roots/EP/maven/search/REST + persist
        tablePanel.setVisibleColumns(new java.util.HashSet<>(config.hiddenColumns));   // View category
        if (store != null) graphTabs.restore(config.savedGraphs);   // reflect merged graphs live
    }

    private void onFilterChanged() {
        tablePanel.reFilter();
        if (store != null) {
            showingLabel.setText("showing " + tablePanel.viewRowCount() + " of " + store.size());
        } else {
            // M35.1, found by eyeballing E2/E3: the guard stopped the UPDATE but not the STALENESS,
            // so after a close this label went on claiming "showing 582 of 582" with no log open —
            // a half-cleared state, which is the one thing this milestone said must not happen
            showingLabel.setText(" ");
        }
        // a report's evidence is LIVE (D-I3): the view banner, the filter offer and the table rows
        // must move with the filter, not wait for a reselect
        if (reportsPanel != null) reportsPanel.rerender();
    }

    /**
     * Offer the project a freshly-opened log sits in.
     *
     * <p>Asked once per log per session and never for a project that is already open — the policy lives
     * in {@link telamin.fluxtion.audit.analyser.analyser.config.ProjectAutoDetect} so it can be tested
     * without a dialog. Deliberately a question rather than an action: loading a project replaces your
     * source roots and graphs, which is not something to do to someone because they opened a file.
     */
    private void maybeOfferProject(boolean fromSocket) {
        // the flag belongs to THIS open and is captured once by the caller, so a human chooser after
        // an agent open cannot inherit "do not ask" — and no second consumer can find it spent.

        Path log = logLocalPath == null ? null : Path.of(logLocalPath);
        Path offer = projectDetect.offerFor(log, project.activeFile());
        pendingProjectOffer = offer;
        if (offer == null) {
            return;
        }
        if (fromSocket) {
            // M35.7 — never block a socket-driven open on a human. The offer becomes DATA: it rides
            // the open echo and sits in `context`, so the agent (and the human reading over its
            // shoulder) know a project is available without the app freezing behind a dialog.
            status.setText(status.getText() + "  ·  project available: "
                    + (offer.getParent() == null ? offer : offer.getParent().getParent()));
            return;
        }
        Path root = offer.getParent() == null ? offer : offer.getParent().getParent();
        String name = root == null ? offer.toString() : root.getFileName().toString();
        int answer = JOptionPane.showConfirmDialog(this,
                "This log sits inside the project \"" + name + "\", which has analyser settings.\n\n"
                + "Load them? Your source roots, Maven repos, event processors, graphs and hidden\n"
                + "columns will be replaced by that project\u2019s.",
                "Load this project?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        pendingProjectOffer = null;          // review F3: asked and answered either way — no longer an offer
        if (answer != JOptionPane.YES_OPTION) {
            projectDetect.decline(log);      // asked and answered; do not ask again for this log
            return;
        }
        // M35.5's exception, and the only one: here the project is being adopted BECAUSE this log was
        // opened. Ending the session would close the log that just arrived. The exception is now CARRIED
        // as a transition kind rather than passed as an unnamed false — see TransitionKind.
        requestProject(offer, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.ADOPT_FOR_OPEN_LOG, "log-offer");
    }

    // ---- projects (M20.2) --------------------------------------------------------------------

    private JMenuItem openProjectItem() {
        JMenuItem item = new JMenuItem("Open project…");
        item.setToolTipText("Switch source roots, event processors, Maven repos, graphs and columns to "
                            + "another project. Replaces them — it does not merge.");
        item.addActionListener(e -> chooseAndOpenProject());
        return item;
    }

    private JMenuItem newProjectItem() {
        JMenuItem item = new JMenuItem("New project…");
        item.setToolTipText("Create a project profile and optionally adopt discovered source roots, skills and GraphML");
        item.addActionListener(e -> chooseAndCreateProject());
        return item;
    }

    private JMenuItem newProjectFromTemplateItem() {
        JMenuItem item = new JMenuItem("New project from template…");
        item.setToolTipText("Choose an onboarding starter from the live playground catalogue, download it safely and open its project profile");
        item.addActionListener(e -> chooseTemplateProject());
        return item;
    }

    private void chooseTemplateProject() {
        status.setText("Loading the playground template catalogue…");
        runTemplateTask("Loading the playground template catalogue…",
                () -> templateClient.catalogue(ReleaseNotes.version()),
                selection -> {
                    TemplateCatalogue.Entry chosen = TemplateProjectDialog.chooseTemplate(this, selection);
                    if (chosen == null) {
                        status.setText("Template selection cancelled");
                        return;
                    }
                    status.setText("Loading defaults for " + chosen.name() + "…");
                    runTemplateTask("Loading defaults for " + chosen.name() + "…",
                            () -> templateClient.defaults(chosen),
                            defaults -> configureTemplateDownload(chosen, defaults),
                            error -> showTemplateFailure("Could not load template defaults", error));
                },
                error -> showTemplateFailure("Could not load the template catalogue", error));
    }

    private void configureTemplateDownload(TemplateCatalogue.Entry template, TemplateClient.Defaults defaults) {
        TemplateProjectDialog.Choice choice = TemplateProjectDialog.chooseDestination(this, template, defaults);
        if (choice == null) {
            status.setText("Template download cancelled");
            return;
        }
        status.setText("Downloading and checking " + template.name() + "…");
        runTemplateTask("Downloading and safely unpacking " + template.name() + "…",
                () -> {
                    byte[] zip = templateClient.download(choice.download());
                    try {
                        return templateArchive.install(zip, choice.destination());
                    } catch (java.io.IOException e) {
                        throw new TemplateClient.Failure("could not install the starter: " + e.getMessage(), e);
                    }
                },
                installed -> openInstalledTemplate(installed, choice.referenceGuide()),
                error -> showTemplateFailure("Could not create the project", error));
    }

    private <T> void runTemplateTask(String message, java.util.function.Supplier<T> work,
                                     java.util.function.Consumer<T> onSuccess,
                                     java.util.function.Consumer<Throwable> onError) {
        TemplateProjectDialog.Progress progressDialog = TemplateProjectDialog.showProgress(this, message,
                () -> status.setText("Template operation cancelled"));
        java.util.concurrent.Future<?> task = Background.run(work, value -> {
            progressDialog.finish();
            if (!progressDialog.cancelled()) onSuccess.accept(value);
        }, error -> {
            progressDialog.finish();
            if (!progressDialog.cancelled()) onError.accept(error);
        });
        progressDialog.attach(task);
    }

    private void openInstalledTemplate(TemplateArchive.Installed installed, boolean referenceGuide) {
        // Only ONE of the catalogue's onboarding templates ships agent instructions; the rest arrive with
        // no CLAUDE.md, no AGENTS.md and no skills. Honoured here rather than in the dialog because until
        // the archive is unpacked we cannot know which kind this is — and it is never an overwrite: a
        // template that ships its own file keeps it, and the status line says so rather than staying mute.
        String guideNote = "";
        if (referenceGuide) {
            try {
                guideNote = switch (ReferenceSet.create(installed.projectRoot(),
                        NewProjectDiscovery.detectKind(installed.projectRoot()))) {
                    case WROTE -> "  ·  wrote " + ReferenceSet.FILE_NAME;
                    case ALREADY_EXISTS -> "  ·  kept the template's own " + ReferenceSet.FILE_NAME;
                    case NOTHING_AGREED -> "";
                };
            } catch (java.io.IOException e) {
                guideNote = "  ·  could not write " + ReferenceSet.FILE_NAME + ": " + e.getMessage();
            }
        }
        if (installed.profile() != null) {
            requestProject(installed.profile(), telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "template");
        } else {
            // Pre-M19 templates have no profile. Reuse day-two discovery: everything found is offered
            // unchecked and only the person's explicit selection is adopted.
            createProjectAt(installed.projectRoot());
        }
        if (!guideNote.isEmpty()) status.setText(status.getText() + guideNote);
        TemplateProjectDialog.showCommands(this, installed.projectRoot(), installed.commands());
    }

    private void showTemplateFailure(String title, Throwable error) {
        String message = rootMessage(error);
        status.setText(title + ": " + message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** Pick a project directory rather than the profile file — the file name is always the same. */
    private void chooseAndOpenProject() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open project");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Project settings (*.fluxtion-settings)", "fluxtion-settings"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File chosen = fc.getSelectedFile();
        Path file = chosen.isDirectory()
                ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(chosen.toPath())
                : chosen.toPath();
        requestProject(file, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "menu");
    }

    private void chooseAndCreateProject() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("New project — choose the project directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        createProjectAt(fc.getSelectedFile().toPath());
    }

    private void createProjectAt(Path selectedRoot) {
        Path file = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                .pathFor(selectedRoot);
        if (Files.exists(file)) {
            int keep = JOptionPane.showConfirmDialog(this,
                    "That directory already has a project.\nOpen it instead of overwriting?",
                    "Project exists", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (keep == JOptionPane.YES_OPTION) {
                requestProject(file, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "new-project-exists");
            }
            return;   // never silently replace someone's project file
        }
        Path root = selectedRoot.toAbsolutePath().normalize();
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        NewProjectDiscovery.Selection selection = NewProjectOfferDialog.show(this, offer);
        if (selection == null) return;       // the offer is a question; Cancel creates and adopts nothing
        // Creating the profile is now an effect the processor asks for, so its failure arrives as a
        // warning rather than an exception — and if it did not happen, adopting the discovery into a
        // project that does not exist would be worse than doing nothing.
        if (!requestProject(file, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.CREATE, "new-project")) {
            return;
        }
        {
            NewProjectDiscovery.apply(offer, selection, config);
            java.util.Optional<String> guideProblem = NewProjectDiscovery.writeReferenceGuide(offer, selection);
            guideProblem.ifPresent(problem -> JOptionPane.showMessageDialog(this, problem,
                    "New project", JOptionPane.WARNING_MESSAGE));
            if (!selection.sourceRoots().isEmpty() || !selection.skillPaths().isEmpty()) {
                onProfileEdited();           // one persistence funnel; the profile stores pointers, never skill text
            }
            if (selection.graph() != null) {
                topologyPanel.load(selection.graph());
                judgeOpenedGraph();
                updateLifecycleMenu();
                if (sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
            }
            status.setText("new project: " + root + "  ·  adopted " + selection.sourceRoots().size()
                    + " source root(s), " + selection.skillPaths().size() + " skill pointer(s)"
                    + (selection.createReferenceGuide() && guideProblem.isEmpty()
                        ? ", wrote " + telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet.FILE_NAME
                        : "")
                    + (selection.graph() == null ? "" : " and opened " + selection.graph().getFileName()));
            noteGraphState();
        }
    }

    private void saveProjectAs() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save project as — choose the new project directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path forked = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                    .pathFor(fc.getSelectedFile().toPath());
            project.saveAs(forked);
            // A fork adopts the new profile as active, which is a switch — and M35.5 applies to it for
            // exactly the reason it applies to any other: the settings the log was being read through
            // are now a different file's.
            requestProject(forked, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.FORK, "save-as");
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write the project: " + ex.getMessage(),
                    "Save project as", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeProject() {
        requestProject(project.activeFile(), telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.CLOSE, "menu");
    }

    // ---- M44 slice 1: session transitions are DECIDED by a Fluxtion processor -------------------

    /**
     * The session transition processor and its driver. Created lazily because it must not exist before
     * the fields its adapter performs against ({@code project}, {@code store}, {@code topologyPanel}).
     */
    private telamin.fluxtion.audit.analyser.analyser.session.SessionDriver session;

    /**
     * Whether the request in flight came from a person. It is <b>rendering</b>, not policy: the
     * processor decides that a warning is warranted and what it says, and this decides whether that
     * lands in a dialog or is handed back to a socket caller who cannot answer one (M35.7).
     */
    private boolean sessionInteractive = true;

    /** What the transition in flight actually closed, with paths — the socket echo reads it. */
    private final Map<String, Object> sessionClosed = new java.util.LinkedHashMap<>();

    /** The warning text of the transition in flight, or null. Non-null means it did not proceed. */
    private String sessionProblem;

    private telamin.fluxtion.audit.analyser.analyser.session.SessionDriver session() {
        if (session == null) {
            session = new telamin.fluxtion.audit.analyser.analyser.session.SessionDriver(
                    this::performSessionEffect);
            // The processor starts knowing nothing. Tell it what is already open, or its first
            // boundary decision would be made against an empty world.
            noteLogState();
            noteGraphState();
        }
        return session;
    }

    /**
     * <b>The one entrance to a project transition.</b> Every surface — menu, recent list, template,
     * import dialog, log-offer adoption, socket — arrives here and states its
     * {@link telamin.fluxtion.audit.analyser.analyser.session.TransitionKind}. Nothing infers intent
     * from which surface asked, because two surfaces in the same state need opposite answers.
     *
     * @return true when the transition proceeded
     */
    private boolean requestProject(Path file,
                                   telamin.fluxtion.audit.analyser.analyser.session.TransitionKind kind,
                                   String source, boolean interactive) {
        sessionInteractive = interactive;
        sessionClosed.clear();
        sessionProblem = null;
        var driver = session();
        driver.submit(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents
                .OpenProjectRequested(driver.nextOpId(), file.toString(), kind, source));
        return sessionProblem == null;
    }

    /** The interactive form — a person asked, so a failure is a dialog. */
    private boolean requestProject(Path file,
                                   telamin.fluxtion.audit.analyser.analyser.session.TransitionKind kind,
                                   String source) {
        return requestProject(file, kind, source, true);
    }

    /**
     * Translate and perform — and <b>decide nothing</b>. Every branch does what it is told, including
     * when it looks wrong; an adapter that asked "should I really close this?" would put the rule back
     * where M44 took it from.
     */
    private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result performSessionEffect(
            telamin.fluxtion.audit.analyser.analyser.session.SessionEffects effect) throws Exception {
        long opId = effect.opId();
        return switch (effect) {
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.LoadProfileEffect e -> {
                // Slice-1 honesty: ProjectSession.open reads the profile AND swaps the settings, so
                // "loaded" here means both. Splitting them is a later slice; what is already true and
                // was not before is that the log and graph close only on a load that SUCCEEDED, and
                // that the close is proven by LogClosed rather than assumed from the request.
                var r = project.open(Path.of(e.profilePath()));
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded(
                        opId, e.profilePath(), r.loaded(),
                        r.loaded() ? project.activeName() : null, 0, r.message());
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.CreateProfileEffect e -> {
                var r = project.create(Path.of(e.profilePath()));
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded(
                        opId, e.profilePath(), r.loaded(),
                        r.loaded() ? project.activeName() : null, 0, r.message());
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.ApplyProfileEffect e -> {
                applyProjectSettings();
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied(
                        opId, e.profilePath(), e.name());
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.RestoreSettingsEffect e -> {
                project.close();
                applyProjectSettings();
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored(opId);
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.CloseLogEffect e -> {
                sessionClosed.put("log", logDisplayLocation);
                closeLog();
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed(opId);
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.CloseGraphEffect e -> {
                Path graphFile = topologyPanel.loadedGraphFile();
                if (graphFile != null) sessionClosed.put("graph", graphFile.toString());
                closeGraph();
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed(opId);
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.ShowStatusEffect e -> {
                status.setText(e.text());
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown(
                        opId, "showStatus");
            }
            case telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.ShowWarningEffect e -> {
                sessionProblem = e.text();
                if (sessionInteractive) {
                    JOptionPane.showMessageDialog(this, e.text(), "Project", JOptionPane.WARNING_MESSAGE);
                }
                yield new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown(
                        opId, "showWarning");
            }
        };
    }

    /**
     * Tell the processor what is open. Called from the paths that change it and are <b>not</b> the
     * session adapter — a log opened from the File menu, a log closed from the File menu, the socket's
     * own close verbs. Inside a transition the processor learns the same facts from the typed results
     * ({@code LogClosed}), so calling this from {@code closeLog()} itself would both duplicate them and
     * re-enter the driver mid-cycle.
     *
     * <p>Scheduled for deletion with {@code LogObserved}, when the slice that moves log opening lands.
     */
    /**
     * The distinct instanceIds the open log writes, sampled once when it loads.
     *
     * <p>Cached deliberately: {@link #updateLifecycleMenu()} is the observation funnel and has ten
     * call sites, and rescanning {@value #PAIRING_SAMPLE} records on each of them would put a log
     * walk behind every menu refresh. It changes only when the log does.
     */
    private java.util.Set<String> loggedNodeSample = java.util.Set.of();
    private int loggedSampleScanned;

    private void refreshLoggedNodeSample() {
        java.util.Set<String> logged = new java.util.LinkedHashSet<>();
        int scan = 0;
        if (store != null) {
            scan = Math.min(store.size(), PAIRING_SAMPLE);
            for (int row = 0; row < scan; row++) {
                for (var nodeLog : store.record(row).nodeLogs()) logged.add(nodeLog.instanceId());
            }
        }
        loggedNodeSample = logged;
        loggedSampleScanned = scan;
    }

    private void noteLogState() {
        if (session == null || session.isDispatching()) return;
        session.submit(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogObserved(
                store != null, logDisplayLocation, logProvenance,
                loggedNodeSample, loggedSampleScanned, store == null ? 0 : store.size()));
    }

    /** As {@link #noteLogState()}, for the topology graph. */
    private void noteGraphState() {
        if (session == null || session.isDispatching()) return;
        Path graphFile = topologyPanel.loadedGraphFile();
        boolean open = topologyPanel.hasGraph();
        java.util.List<String> types = new java.util.ArrayList<>();
        if (open) {
            var full = topologyPanel.fullTopology();
            if (full != null) {
                for (var n : full.nodes()) types.add(n.simpleName());
            }
        }
        session.submit(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphObserved(
                open, graphFile == null ? null : graphFile.toString(), "OPENED",
                open ? topologyPanel.authoredNodeIds() : java.util.Set.of(), types));
    }

    /** The rendering half: make the UI reflect settings that have already been swapped. */
    private void applyProjectSettings() {
        onConfigChanged();          // source service, processors, menus, and the global save
        graphTabs.restore(config.savedGraphs);
        tablePanel.setVisibleColumns(new java.util.HashSet<>(config.hiddenColumns));
        updateProjectMenuState();
        setTitleForProject();
        updateLifecycleMenu();
        refreshProjectPanel();                                        // M37: the project, and everything it owns
    }

    /** M38.4: File ▸ Run analysis — one item per saved analysis; the rationale is the tooltip. */
    private final JMenu analysesMenu = new JMenu("Run analysis");

    private void rebuildAnalysesMenu() {
        analysesMenu.removeAll();
        analysesMenu.setEnabled(!config.analyses.isEmpty());
        analysesMenu.setToolTipText(config.analyses.isEmpty()
                ? "No saved analyses — declare them in the project profile (analysis.N.*); see the Portable context guide" : null);
        for (telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec a : config.analyses) {
            JMenuItem item = new JMenuItem(a.name() + (a.parameters().isEmpty() ? "" : "…"));
            item.setToolTipText(a.rationale().isBlank() ? a.steps().size() + " step(s)" : a.rationale());
            item.addActionListener(e -> runAnalysisFromMenu(a));
            analysesMenu.add(item);
        }
    }

    /** Ask for the parameters an analysis declares (defaults prefilled), then run it off the EDT. */
    private void runAnalysisFromMenu(telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec a) {
        Map<String, String> bind = new java.util.LinkedHashMap<>();
        if (!a.parameters().isEmpty()) {
            JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 4));
            Map<String, JTextField> fields = new java.util.LinkedHashMap<>();
            for (String p : a.parameters()) {
                form.add(new JLabel(p + ":"));
                JTextField f = new JTextField(a.defaults().getOrDefault(p, ""), 32);
                fields.put(p, f);
                form.add(f);
            }
            JPanel box = new JPanel(new BorderLayout(0, 8));
            JLabel why = new JLabel("<html><i>" + (a.rationale().isBlank() ? a.steps().size() + " steps" : a.rationale()) + "</i></html>");
            box.add(why, BorderLayout.NORTH);
            box.add(form, BorderLayout.CENTER);
            int ok = JOptionPane.showConfirmDialog(this, box, "Run analysis: " + a.name(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            fields.forEach((k, f) -> bind.put(k, f.getText()));
        }
        status.setText("Running analysis '" + a.name() + "'…");
        telamin.fluxtion.audit.analyser.analyser.core.Background.run(
                () -> actionControl.runAnalysis(a.name(), bind),
                r -> status.setText(r.ok() ? "Analysis '" + a.name() + "': " + summarise(r) : "Analysis '" + a.name() + "' — " + r.error()),
                err -> status.setText("Analysis '" + a.name() + "' failed: " + err.getMessage()));
    }

    private static String summarise(telamin.fluxtion.audit.analyser.analyser.llm.ActionResult r) {
        Object a = r.toMap().get("analysis");
        if (a instanceof Map<?, ?> m) return String.valueOf(m.get("completed")) + (m.get("stoppedAt") != null ? " — stopped at step " + m.get("stoppedAt") : "");
        return "done";
    }

    private void updateProjectMenuState() {
        rebuildAnalysesMenu();
        saveProjectAsItem.setEnabled(project.hasProject());
        closeProjectItem.setEnabled(project.hasProject());
        updateLifecycleMenu();
        fillRecent(recentProjectsMenu, config.recentProjects,
                path -> requestProject(Path.of(path), telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "recent"));
    }

    /** The window title carries the project, because "which settings am I using" is easy to lose. */
    private void setTitleForProject() {
        setTitle(project.hasProject()
                ? "Fluxtion Audit Log Analyser — " + project.activeName()
                : "Fluxtion Audit Log Analyser");
    }

    /** Write pending project edits and surface a failure once. Called by the debounce timer. */
    private void flushProject() {
        project.flush();
        String err = project.takeError();
        if (err != null) {
            status.setText(err);
        }
    }

    /**
     * Two recent lists, not one. A log and a topology are opened for different reasons and neither
     * substitutes for the other, so a single list means scrolling past logs to find a graph — and picking
     * the wrong kind silently does nothing useful.
     */
    private void rebuildRecentMenu() {
        fillRecent(recentMenu, config.recentFiles, this::openLocation);
        fillRecent(recentGraphmlMenu, config.recentGraphml, this::openGraphml);
    }

    private void fillRecent(JMenu menu, List<String> paths, java.util.function.Consumer<String> open) {
        menu.removeAll();
        if (paths.isEmpty()) {
            JMenuItem none = new JMenuItem("(none)");
            none.setEnabled(false);
            menu.add(none);
            return;
        }
        for (String p : paths) {
            JMenuItem item = new JMenuItem(p);
            item.addActionListener(e -> open.accept(p));
            menu.add(item);
        }
    }

    /** Load a topology and remember it, from wherever it was chosen — menu, recent list or a drop. */
    private void openGraphml(String path) {
        java.nio.file.Path file = java.nio.file.Path.of(path);
        if (!java.nio.file.Files.isReadable(file)) {
            JOptionPane.showMessageDialog(this, "Cannot read " + path,
                    "Open GraphML", JOptionPane.WARNING_MESSAGE);
            return;
        }
        topologyPanel.load(file);   // the load listener records it
        if (sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
    }

    /**
     * {@link telamin.fluxtion.audit.analyser.analyser.llm.AppControl} over this frame — the verbs that
     * open files and configure source roots.
     *
     * <p>An inner class rather than {@code MainFrame implements AppControl}: these methods reach the
     * filesystem, and keeping them in one named place makes the app's whole scriptable surface visible
     * at a glance instead of scattered among two hundred UI methods.
     */
    private final class AppControlAdapter implements telamin.fluxtion.audit.analyser.analyser.llm.AppControl {

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLog(String path) {
            return openLog(path, null, null);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLog(String path, String format) {
            return openLog(path, format, null);
        }

        /**
         * M35.9: the request is built HERE, from the verb's own params, and travels with the load.
         * There is no field to set before or clear after — a failed pre-check simply never builds it.
         */
        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLog(String path, String format,
                                                                               String provenance) {
            if (path == null || path.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'log' is empty");
            }
            OpenRequest request = OpenRequest.socket(provenance);   // M35.7: no modal on this path
            if (format != null && !format.isBlank()) {
                java.nio.file.Path f = java.nio.file.Path.of(path);
                if (readerRegistry.readerFor(f, format) == null) {
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                            "no installed reader has format '" + format + "' — installed: "
                                    + readerRegistry.describeReaders());
                }
                openFileWithReader(f, format, request);
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "applied",
                        java.util.Map.of("log", path, "format", format, "loading", true));
            }
            if (!S3Source.isS3(path) && !Files.isReadable(Path.of(path))) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "cannot read log '" + path + "'");
            }
            openLocation(path, request);
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            echo.put("path", path);
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "log", echo);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult discoverGraphs() {
            var result = discoverGraphs0();
            java.util.List<Map<String, Object>> found = new java.util.ArrayList<>();
            for (var c : result.candidates()) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("path", c.file().toString());
                m.put("nodes", c.nodes());
                if (c.pairing() != null) {
                    m.put("appliesToOpenLog", c.pairing().applies());
                    m.put("declaredByGraph", c.pairing().matched());
                    m.put("loggedNodes", c.pairing().logged());
                }
                found.add(m);
            }
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            echo.put("roots", java.util.List.copyOf(config.sourceRoots));
            echo.put("candidates", found);
            echo.put("ranked", store != null);
            if (store == null) {
                echo.put("note", "no log is open, so these are listed but NOT ranked — there is "
                        + "nothing to judge fit against");
            }
            if (result.truncated()) echo.put("truncated", true);
            if (!result.notes().isEmpty()) echo.put("warnings", result.notes());
            // M35.4's whole point, said where an agent will read it
            echo.put("opened", "nothing — this lists candidates; open one with open {graphml}");
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "discover", echo);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult close(String what) {
            String w = what == null ? "" : what.trim().toLowerCase(java.util.Locale.ROOT);
            boolean hadLog = store != null;
            boolean hadGraph = topologyPanel.hasGraph();
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            switch (w) {
                case "log" -> { closeLog(); echo.put("closed", "log"); }
                case "graph", "graphml" -> { closeGraph(); echo.put("closed", "graph"); }
                case "all", "both" -> { resetAll(); echo.put("closed", "all"); }
                case "project" -> {
                    // M35.8: the way back from open {project} when what was in force before it was
                    // "your own settings" — there is no path to name for that, so it needs a verb.
                    // Its echo is its own: the `kept` sentence below is FALSE here, because leaving a
                    // project is exactly the act that swaps the profile-state categories.
                    if (!project.hasProject()) {
                        return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                                "no project is open — these are already your own settings");
                    }
                    String was = project.activeName();
                    Path wasFile = project.activeFile();
                    String wasPath = wasFile.toString();
                    var before = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.snapshot(config);
                    // The processor decides that leaving a project ends the session, and the adapter
                    // records what it actually closed while doing it — so the echo below reports
                    // outcomes rather than a prediction made before the switch.
                    requestProject(wasFile, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.CLOSE, "socket", false);
                    Map<String, Object> closedEcho = sessionEndEcho();
                    var after = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.snapshot(config);
                    echo.put("closed", "project");
                    echo.put("project", was);
                    echo.put("now", "your own settings — the ones in force before any project was opened");
                    echo.put("replaced", replacedCounts(before, after));
                    echo.putAll(closedEcho);
                    echo.put("reversible", "open {project: \"" + wasPath + "\"} puts it back"
                            + (closedEcho.get("closed") instanceof Map<?, ?> c && !c.isEmpty()
                                    ? " — the settings, not the session: reopen what `closed` names" : ""));
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "applied", echo);
                }
                default -> {
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                            "close takes 'log', 'graph', 'all' or 'project', got '" + what + "'");
                }
            }
            // M26.4: say what was actually there, so a no-op close does not read as a success
            echo.put("logWasOpen", hadLog);
            echo.put("graphWasOpen", hadGraph);
            if (!hadLog && !hadGraph) echo.put("note", "nothing was open");
            echo.put("kept", "named graphs, focuses, source roots and reports are profile state and "
                    + "survive; anything that can no longer resolve says so rather than vanishing");
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "applied", echo);
        }

        /**
         * M35.8 — a route to machinery that exists: {@code project.open} never throws and
         * the session processor already closes the log and graph. What is NEW is the echo, and the echo
         * is the whole safety story: this verb APPLIES (a modal cannot be answered at the socket —
         * M35.7), so what it replaced, what it closed and how to undo it must all be in the answer.
         * The request is made NON-interactive for that reason: a failed load comes back as text.
         */
        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openProject(String path) {
            if (path == null || path.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'project' is empty");
            }
            Path given = Path.of(path.trim());
            Path file = Files.isDirectory(given)
                    ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(given)
                    : given;
            if (!Files.isRegularFile(file)) {
                // named, not thrown: the same degradation ProjectProfile.load promises, one step earlier
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "project settings not found: " + file
                                + (Files.isDirectory(given) ? " (a project directory carries "
                                + telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.CANONICAL_RELATIVE
                                + ")" : ""));
            }
            Path active = project.activeFile();
            Path target = file.toAbsolutePath().normalize();
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            if (active != null && active.toAbsolutePath().normalize().equals(target)) {
                // Re-opening the active project would flush its in-memory state OVER the file and read
                // it back — nothing changes, but the session would end for nothing. Say so instead.
                echo.put("project", project.activeName());
                echo.put("settings", active.toString());
                echo.put("alreadyActive", true);
                echo.put("note", "nothing replaced and nothing closed — this project is already in force, "
                        + "and its edits auto-save, so a reload would only read back what is live");
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "opened", echo);
            }
            String previousName = project.hasProject() ? project.activeName() : null;
            String previousPath = active == null ? null : active.toString();
            var before = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.snapshot(config);

            // M35.5's rule now lives in the processor: an EXPLICIT_SWITCH ends the session. Not
            // interactive, so a failed load is returned rather than shown — a modal cannot be answered
            // at the socket (M35.7).
            if (!requestProject(file, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "socket", false)) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(sessionProblem);
            }
            Map<String, Object> closedEcho = sessionEndEcho();
            var after = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.snapshot(config);

            echo.put("project", project.activeName());
            echo.put("settings", file.toString());
            echo.put("replaced", replacedCounts(before, after));
            echo.putAll(closedEcho);
            echo.put("previous", previousName == null ? "your own settings" : previousName);
            // review N1: "reversible" and "closed" are individually true and jointly misleading — the
            // SETTINGS come back in one call, the log and graph do not (they are named in `closed`)
            String notTheSession = closedEcho.get("closed") instanceof Map<?, ?> c && !c.isEmpty()
                    ? " — the settings, not the session: reopen what `closed` names" : "";
            echo.put("reversible", (previousPath != null
                    ? "open {project: \"" + previousPath + "\"} puts it back"
                    : "open {close: \"project\"} puts your own settings back") + notTheSession);
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "opened", echo);
        }

        /**
         * What a session boundary is about to close, WITH paths — so the answer carries what is needed
         * to reopen them, not just the fact that they went (M35.5 made the closing a rule; this makes
         * it reversible from the echo).
         */
        private Map<String, Object> sessionEndEcho() {
            // M44: this used to be called BEFORE the switch and list what was about to close — a
            // prediction, and one that would have been wrong on every path where the transition did not
            // proceed. It is now called AFTER, and reads what the adapter recorded closing.
            Map<String, Object> closed = new java.util.LinkedHashMap<>(sessionClosed);
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("closed", closed);
            out.put("closedWhy", closed.isEmpty()
                    ? "nothing was open"
                    : "a project is a session boundary (M35.5): its settings change underneath the log, "
                            + "so the log and graph go with it — reopen them inside the new project");
            return out;
        }

        /**
         * Before/after counts for every category a project owns — {@code ProjectProfile.PROJECT_SCOPED},
         * spelled out. A switch that says "project loaded" and nothing else leaves the caller to
         * discover from a failing source lookup that its three roots became one.
         */
        private Map<String, Object> replacedCounts(
                telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.Snapshot before,
                telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.Snapshot after) {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("sourceRoots", counts(before.sourceRoots().size(), after.sourceRoots().size()));
            r.put("mavenRepos", counts(before.mavenRepos().size(), after.mavenRepos().size()));
            r.put("eventProcessors", counts(before.eventProcessorFqns().size(), after.eventProcessorFqns().size()));
            Map<String, Object> sel = new java.util.LinkedHashMap<>();
            sel.put("before", blankToNull(before.selectedEventProcessor()));
            sel.put("after", blankToNull(after.selectedEventProcessor()));
            r.put("selectedEventProcessor", sel);
            r.put("namedGraphs", counts(before.savedGraphs().size(), after.savedGraphs().size()));
            r.put("namedFocuses", counts(before.namedFocuses().size(), after.namedFocuses().size()));
            r.put("reports", counts(before.reports().size(), after.reports().size()));
            r.put("hiddenColumns", counts(before.hiddenColumns().size(), after.hiddenColumns().size()));
            return r;
        }

        private static Map<String, Object> counts(int before, int after) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("before", before);
            m.put("after", after);
            return m;
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s;
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openGraphml(String path) {
            if (path == null || path.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'graphml' is empty");
            }
            Path file = Path.of(path);
            if (!Files.isReadable(file)) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "cannot read graphml '" + path + "'");
            }
            topologyPanel.load(file);
            if (!topologyPanel.hasTopology()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "not a readable Fluxtion .graphml: " + path);
            }
            if (sideTabs != null) sideTabs.setSelectedComponent(topologyPanel);
            var pairing = judgeOpenedGraph();      // M35.3 — say at once whether it fits this log
            updateLifecycleMenu();
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            echo.put("path", path);
            echo.put("nodes", topologyPanel.authoredNodeIds().size());
            if (pairing == null) {
                echo.put("pairing", store == null
                        ? "no log is open — nothing to check this graph against"
                        : "no graph loaded");
            } else {
                echo.put("appliesToOpenLog", pairing.applies());
                echo.put("loggedNodes", pairing.logged());
                echo.put("declaredByGraph", pairing.matched());
                echo.put("verdict", pairing.reason() + (pairing.applies() ? ""
                        : " — kept anyway, because you opened it deliberately (M35.3). A stale "
                                + "graph is only closed when a LOG arrives and finds it there."));
            }
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok(
                    "open", "graphml", echo);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult selectProcessor(String fqn) {
            if (fqn == null || fqn.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'processor' is empty");
            }
            if (sourceService.sourceForFqn(fqn).isEmpty()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "no source for '" + fqn + "' under the configured roots");
            }
            config.selectedEventProcessor = fqn;
            sourceService.select(fqn);
            onConfigChanged();
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok(
                    "open", "processor", Map.of("fqn", fqn));
        }

        @Override
        public List<String> sourceRoots() {
            return List.copyOf(config.sourceRoots);
        }

        /** M38.4 D-C5: bind, then run each step through the socket's own dispatcher; stop at the first failure. */
        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult runAnalysis(String name, Map<String, String> bindings) {
            telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec spec = config.analyses.stream()
                    .filter(a -> a.name().equals(name)).findFirst().orElse(null);
            if (spec == null) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("no saved analysis named '" + name
                        + "' — saved: " + config.analyses.stream().map(telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec::name).toList());
            }
            List<String> missing = spec.unbound(bindings);
            if (!missing.isEmpty()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("analysis '" + name + "' needs "
                        + missing + " — pass bind: {" + String.join(": …, ", missing) + ": …}; these parameters have no default");
            }
            // review F1: project-relative paths in open/source_root steps resolve against THIS project's root
            var bound = telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec.resolvePaths(spec.bind(bindings),
                    project.hasProject() ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile()) : null);
            var run = telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec.run(bound, step -> {
                telamin.fluxtion.audit.analyser.analyser.llm.ActionResult r =
                        actionExecutor.render(step.action(), new java.util.LinkedHashMap<>(step.params()));
                // Opening a log is ASYNCHRONOUS (the docs tell agents to read `context` after it). A sequence
                // cannot be told that: step 2 on a log step 1 has not finished loading fails with "no log is
                // loaded" — seen on the first live run. So an open step waits for its load to settle, success
                // or failure, before the next step runs.
                if (r.ok() && step.action().equals("open")
                        && (step.params().get("log") != null || step.params().get("logs") != null)) {
                    long deadline = System.currentTimeMillis() + 120_000;
                    while (loadInFlight && System.currentTimeMillis() < deadline) {
                        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                    if (loadInFlight) {
                        return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("the log did not finish loading within 120s");
                    }
                    if (store == null) {
                        return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("the log failed to load — "
                                + status.getText());
                    }
                }
                return r;
            });
            Map<String, Object> echo = new java.util.LinkedHashMap<>();
            echo.put("analysis", name);
            echo.put("rationale", spec.rationale());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (var sr : run.steps()) {
                Map<String, Object> one = new java.util.LinkedHashMap<>();
                one.put("step", sr.index());
                one.put("action", sr.action());
                one.put("ok", sr.ok());
                if (sr.error() != null) one.put("error", sr.error());
                steps.add(one);
            }
            echo.put("steps", steps);
            echo.put("completed", run.steps().stream().filter(telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec.StepResult::ok).count()
                    + "/" + bound.size() + " steps");
            if (!run.completed()) {
                echo.put("stoppedAt", run.stoppedAt());                       // review N2: which step failed…
                echo.put("skipped", bound.size() - run.steps().size());     // …and how many never ran, so the
                echo.put("note", "the steps before " + run.stoppedAt() + " HAVE changed the view; the rest did not run");   // viewer's state is knowable
            }
            refreshProjectPanel();
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "analysis", echo);
        }


        @Override
        public boolean addSourceRoot(String path) {
            if (path == null || path.isBlank()) return false;
            Path dir = Path.of(path);
            if (!Files.isDirectory(dir)) return false;
            String canonical = dir.toAbsolutePath().normalize().toString();
            if (!config.sourceRoots.contains(canonical)) config.sourceRoots.add(canonical);
            onConfigChanged();
            // Adding a root IS the statement "the code is here". Inference runs when a log is opened, so
            // a root added afterwards would otherwise leave the processor unresolved and every
            // source-navigation attempt reporting "no source mapping" with the source sitting right there.
            inferAndPopulateSource();
            return true;
        }

        @Override
        public boolean removeSourceRoot(String path) {
            if (path == null) return false;
            String canonical = Path.of(path).toAbsolutePath().normalize().toString();
            boolean removed = config.sourceRoots.remove(canonical) || config.sourceRoots.remove(path);
            if (removed) onConfigChanged();
            return removed;
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult screenshot(String path, String scope) {
            if (path == null || path.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'path' is required");
            }
            // menu:<Name> opens a top-level menu and leaves it open, so a NATIVE screen capture of the
            // returned windowBounds includes the popup. The painted fallback cannot: a Swing popup is a
            // separate layer, not part of the content pane's paint. menu:close puts it back.
            String requested = scope == null ? "window" : scope.toLowerCase(java.util.Locale.ROOT);
            if (requested.startsWith("menu:")) {
                String which = scope.substring("menu:".length());
                if ("close".equalsIgnoreCase(which)) {
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                } else {
                    javax.swing.JMenu menu = topLevelMenu(which);
                    if (menu == null) {
                        return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                                "no menu '" + which + "' — try one of " + topLevelMenuNames());
                    }
                    // the canonical way to open a menu programmatically: hand the selection manager
                    // the full path. setPopupMenuVisible alone highlights the title without laying the
                    // popup out, which looks right in the app and is empty in a capture.
                    javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                            new javax.swing.MenuElement[]{getJMenuBar(), menu, menu.getPopupMenu()});
                }
            }
            // Raise the window before reporting bounds. A native capture of those bounds photographs
            // whatever is ON SCREEN there — so any window sitting on top of the analyser lands in the
            // image, and a documentation screenshot is exactly where someone else's browser tabs must
            // never appear. CLAUDE.md rule 1 exists because a text sweep cannot see inside a PNG.
            toFront();
            requestFocus();
            try {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                    desktop.requestForeground(true);
                }
            } catch (RuntimeException ignored) {
                // headless or unsupported platform: toFront() is the best we can do
            }

            java.awt.Component target = switch (requested) {
                case "topology" -> topologyPanel;
                case "records" -> tablePanel;
                default -> getContentPane();
            };
            if (target.getWidth() <= 0 || target.getHeight() <= 0) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "nothing to capture — the window has no size yet");
            }
            try {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                        target.getWidth(), target.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = img.createGraphics();
                target.paint(g);
                g.dispose();
                Path out = Path.of(path);
                if (out.getParent() != null) Files.createDirectories(out.getParent());
                javax.imageio.ImageIO.write(img, "png", out.toFile());
                // The window's position on screen, so a caller that DOES have the OS screen-recording
                // permission can take a native capture (with the title bar) of exactly this window —
                // `screencapture -R x,y,w,h`. Painting cannot draw the title bar; the window server owns it.
                java.awt.Rectangle onScreen = new java.awt.Rectangle(getLocationOnScreen(), getSize());
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("screenshot", "wrote",
                        Map.of("path", out.toAbsolutePath().toString(),
                                "width", img.getWidth(), "height", img.getHeight(),
                                "windowBounds", Map.of("x", onScreen.x, "y", onScreen.y,
                                        "width", onScreen.width, "height", onScreen.height)));
            } catch (java.io.IOException e) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "could not write " + path + ": " + e.getMessage());
            }
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult exportFinding(
                String path, Integer recordIndex, String title, String graph, boolean withTopology) {
            return MainFrame.this.exportFinding(path, recordIndex, title, graph, withTopology);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult report(
                java.util.Map<String, Object> params, String resolvedPath) {
            return MainFrame.this.reportVerb(params, resolvedPath);
        }

        @Override

        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLogs(java.util.List<String> paths) {
            return openLogs(paths, null);
        }

        @Override
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLogs(java.util.List<String> paths,
                                                                                String provenance) {
            java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
            for (String p : paths) {
                java.nio.file.Path f = java.nio.file.Path.of(p);
                if (!java.nio.file.Files.isRegularFile(f)) {
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                            "'" + p + "' is not a readable file");
                }
                files.add(f);
            }
            try {
                var set = telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.resolve(files);
                // M35.9: this path never set the socket flag, so its time-order modal fired on agents
                openRolledSet(set, OpenRequest.socket(provenance));   // async; the echo reports what was decided NOW
                Map<String, Object> echo = new java.util.LinkedHashMap<>();
                echo.put("files", set.ordered().stream()
                        .map(s -> s.file().getFileName().toString()).toList());
                echo.put("order", "by content — each file's first timed logTime (names never order)");
                if (!set.report().isClean()) echo.put("timeOrder", set.report().summarise());
                echo.put("loading", true);
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "applied", echo);
            } catch (Exception e) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "could not resolve the set: " + e.getMessage());
            }
        }

        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult context() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();

            // the same assembly the pasted prompt uses, rendered as JSON instead of prose
            telamin.fluxtion.audit.analyser.analyser.llm.SessionFacts facts =
                    telamin.fluxtion.audit.analyser.analyser.llm.SessionFacts.of(
                            currentLogFileInfo(), config.selectedEventProcessor, sourceService,
                            telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.nodeTypes(
                                    selectedRecords, sourceService));
            Map<String, Object> log = facts.logAsMap();
            // M37: who asked. The OpenRequest carries it (M35.9); the Project panel is its first human reader
            if (!log.isEmpty()) log.put("openedBy", currentRequest.fromActionSocket() ? "action socket" : "you");
            if (!log.isEmpty()) out.put("log", log);
            // §E: absent means absent. No key at all rather than a null an agent might read as ""
            if (logProvenance != null) out.put("provenance", logProvenance);
            if (logProvenanceSource != null) out.put("provenanceSource", logProvenanceSource);   // M38.3: declared, never inferred — and by whom
            // M35.8: which settings are in force. Outside the store block — a project is open (or
            // not) whether or not a log is, and "which settings am I using" must never be a guess.
            Map<String, Object> proj = new java.util.LinkedHashMap<>();
            proj.put("active", project.hasProject());
            if (project.hasProject()) {
                proj.put("name", project.activeName());
                proj.put("settings", project.activeFile().toString());
                proj.put("root", telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                        .baseDirFor(project.activeFile()).toString());        // M37: the project's directory
            } else {
                proj.put("note", "your own settings — no project is open");
            }
            out.put("project", proj);
            if (project.hasProject()) {
                telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                        .skillsProvenance(project.activeFile()).ifPresent(value -> {
                            Map<String, Object> skills = new java.util.LinkedHashMap<>();
                            skills.put("provenance", value);
                            skills.put("from", "project declaration (inert; never a retrieval control)");
                            out.put("skills", skills);
                        });
            }
            // M19.12 / D-X3: facts this process can observe, not a claim about a future Maven JVM.
            // The credential value never enters this map; the fixed tilde path avoids leaking the local
            // account name into context or screenshots.
            Map<String, Object> fluxtionKey = new java.util.LinkedHashMap<>();
            fluxtionKey.put("canonicalFilePresent", fluxtionKeyStore.keyPresent());
            fluxtionKey.put("canonicalFile", "~/.fluxtion/fluxtion.apiKeyFile");
            fluxtionKey.put("precedenceNote", "a -Dfluxtion.apiKey system property passed to the build "
                    + "overrides this file; FLUXTION_API_KEY is not read by the builder");
            out.put("fluxtionKey", fluxtionKey);
            // M37: the graph is reported whether or not a log is open. It sat inside the store block, so
            // with the log closed and a graph "still loaded" (closeLog's own words) context disowned it —
            // the disowning defect M34.2 fixed for hasGraph(), one level up.
            {
                // D-A1a: state it BEFORE anything is derived from position. An agent stepping a
                // cycle, or reading the topology's dispatch badges, is entitled to know whether
                // that order was derived or merely observed — and must not have to infer it.
                Map<String, Object> pair = new java.util.LinkedHashMap<>();
                // M34.2: ask "is there a graph", not "is there a graph FILE" — a source-supplied
                // graph has no file, and reporting null for it disowns a graph the app is holding
                boolean gf = topologyPanel.hasGraph();
                pair.put("graph", topologyPanel.graphLabel());
                pair.put("graphSource", topologyPanel.graphSource().name());
                if (topologyPanel.graphPath() != null) pair.put("graphPath", topologyPanel.graphPath());   // M37
                if (declinedSourceGraph != null) {
                    pair.put("sourceGraphOffered", declinedSourceGraph);
                    pair.put("sourceGraphDeclined", "an OPENED graph holds the slot — a graph someone "
                            + "named outranks one that arrived with the log (M34.1 precedence)");
                }
                if (store != null && store.sourceGraphNote() != null) {
                    pair.put("sourceGraphNote", store.sourceGraphNote());   // review M34 F2
                }
                // only describe a graph that is actually there: a verdict beside "graph": null is
                // the tool asserting something about an artefact it does not have, which is the
                // defect class this milestone is about
                if (gf && lastPairing != null) {
                    pair.put("applies", lastPairing.applies());
                    pair.put("loggedNodes", lastPairing.logged());
                    pair.put("declaredByGraph", lastPairing.matched());
                    pair.put("verdict", lastPairing.reason());
                }
                // M40 (review F1): the audit verdict is a fact about the loaded GRAPH, so it belongs
                // beside the pairing, not inside the topology block — that block sits below a
                // fresh-start early return, so with only a graph open the verdict never appeared. The
                // case where it matters most is exactly that one: a graph open, no log, nothing yet run.
                var audit = topologyPanel.auditReadiness();
                // put the keys LITERALLY, not via putAll: the M37 parity test reads context()'s source
                // to prove every key the Project panel consumes is one this method puts, and a putAll
                // hides them from that check — and from anyone reading this method to learn the shape.
                pair.put("auditLogging", audit.verdict().name().toLowerCase(java.util.Locale.ROOT));
                if (audit.message() != null) pair.put("auditLoggingNote", audit.message());
                out.put("graphPairing", pair);
            }
            // M37: the processors as a LIST — configured, selected, and whether each resolves to source.
            // A dropdown shows one value at a time; the panel and the agent both need the set.
            {
                List<Map<String, Object>> procs = new ArrayList<>();
                java.util.Set<String> configured = new java.util.LinkedHashSet<>(config.eventProcessorFqns);
                for (String fqn : candidateProcessors()) {
                    Map<String, Object> one = new java.util.LinkedHashMap<>();
                    one.put("class", fqn);
                    one.put("selected", fqn.equals(config.selectedEventProcessor));
                    one.put("source", sourceService.resolver().find(fqn).isPresent() ? "found" : "not found");
                    one.put("from", configured.contains(fqn) ? (project.hasProject() ? "project" : "own settings")
                            : "discovered under a root");
                    procs.add(one);
                }
                if (!procs.isEmpty()) out.put("processors", procs);
            }
            // M38.1: runbook POINTERS — where the knowledge is, never what to do. Reported whether or not a
            // log is open; the panel renders each as a row because a pointer an agent will act on and a
            // human cannot see is precisely the shape spec-portable-context exists to avoid (D-C7).
            {
                List<Map<String, Object>> rbs = runbooksForContext();
                if (!rbs.isEmpty()) out.put("runbooks", rbs);
            }
            // M38.3: the environments the project declares, so an agent can name one when it opens a log
            if (!config.environments.isEmpty()) {
                List<Map<String, Object>> envs = new ArrayList<>();
                for (telamin.fluxtion.audit.analyser.analyser.config.Environment e : config.environments) {
                    Map<String, Object> one = new java.util.LinkedHashMap<>();
                    one.put("name", e.name());
                    one.put("provenance", e.provenance());
                    if (e.logDir() != null) one.put("logDir", e.logDir());
                    one.put("default", e.name().equals(config.defaultEnvironment));
                    envs.add(one);
                }
                out.put("environments", envs);
            }
            // M38.2: the glossary pointer — and, when the file is there, its text (D-C3: served in context).
            // Tier 1 and inert, which is why serving the CONTENT here is right where serving a runbook's
            // would be wrong: a glossary is read, a runbook is acted on.
            {
                Map<String, Object> v = vocabularyForContext();
                if (!v.isEmpty()) out.put("vocabulary", v);
            }
            // M38.4: the saved analyses — the OFFER (D-C5). Listed with their rationale and the parameters they
            // declare, so an agent can bind and recall one with open {analysis, bind}; nothing runs by itself.
            if (!config.analyses.isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec a : config.analyses) {
                    Map<String, Object> one = new java.util.LinkedHashMap<>();
                    one.put("name", a.name());
                    one.put("rationale", a.rationale());
                    List<Map<String, Object>> ps = new ArrayList<>();
                    for (String pn : a.parameters()) {
                        Map<String, Object> pm = new java.util.LinkedHashMap<>();
                        pm.put("name", pn);
                        if (a.defaults().containsKey(pn)) pm.put("default", a.defaults().get(pn));
                        ps.add(pm);
                    }
                    one.put("parameters", ps);
                    one.put("steps", a.steps().stream().map(telamin.fluxtion.audit.analyser.analyser.config.AnalysisSpec.Step::action).toList());
                    one.put("from", project.hasProject() ? "project" : "own settings");
                    list.add(one);
                }
                out.put("analyses", list);
            }
            // M38.5 D-C6: where reports are PUBLISHED — stated, never acted on. The publisher (an agent with its
            // own credentials, or a person) reads this; the analyser never writes outside its exchange directory.
            if (!config.reportDestinations.isEmpty()) {
                List<Map<String, Object>> ds = new ArrayList<>();
                for (telamin.fluxtion.audit.analyser.analyser.config.ReportDestination d : config.reportDestinations) {
                    Map<String, Object> one = new java.util.LinkedHashMap<>();
                    one.put("name", d.name());
                    one.put("location", d.location());
                    one.put("kind", d.kind().name().toLowerCase(java.util.Locale.ROOT));
                    one.put("from", project.hasProject() ? "project" : "own settings");
                    ds.add(one);
                }
                out.put("reportDestinations", ds);
            }
            // M37.6: where files LEAVE, and the reports the project holds. The exchange directory is
            // machine-tier (a path on this disk, never shared); the reports are project-tier. Both were
            // invisible outside their dialog/tab — and "exports off" is the state that made screenshot
            // fail twice on 2026-08-27 with nothing on screen saying so.
            {
                Map<String, Object> exports = new java.util.LinkedHashMap<>();
                exports.put("enabled", config.assistantExports);
                if (config.assistantExports && config.assistantExportDir != null && !config.assistantExportDir.isBlank()) {
                    exports.put("dir", config.assistantExportDir);
                }
                out.put("exports", exports);
                List<Map<String, Object>> reps = new ArrayList<>();
                for (telamin.fluxtion.audit.analyser.analyser.report.ReportSpec r : config.reports) {
                    Map<String, Object> one = new java.util.LinkedHashMap<>();
                    one.put("name", r.name());
                    one.put("title", r.title());
                    one.put("sections", r.sections() == null ? 0 : r.sections().size());
                    if (r.createdAt() != null) one.put("createdAt", r.createdAt());
                    one.put("from", project.hasProject() ? "project" : "own settings");
                    reps.add(one);
                }
                if (!reps.isEmpty()) out.put("reports", reps);
            }
            if (store != null) {
                if (pendingRolledSetOffer != null) {
                    // M35.9: the dialog the socket path did not show. The member files are named so
                    // the set is one call away — open {logs: [...]} — and the agent decides, not the app.
                    out.put("rolledSetOffer", Map.of(
                            "files", pendingRolledSetOffer,
                            "note", "this log is one member of a rolled set; opening the set is a decision, "
                                    + "so it is offered — open {logs: [...]} with these files loads it in "
                                    + "content order"));
                }
                if (pendingProjectOffer != null) {
                    // M35.7: the offer the agent path did not show as a dialog. Reported, never applied
                    // — loading a project replaces source roots, graphs and hidden columns, which is a
                    // human's decision (File ▸ Open project).
                    out.put("projectOffer", Map.of(
                            "settings", pendingProjectOffer.toString(),
                            "note", "this log sits inside a project with analyser settings; loading "
                                    + "them replaces source roots, event processors, graphs and hidden "
                                    + "columns, so it is offered and never applied automatically"));
                }
                out.put("dispatchOrder", store.index().totalOrder()
                        ? "total — position in nodeLogs IS dispatch order (derived); safe to read "
                                + "as causality"
                        : "PARTIAL — this source could not supply an order within a cycle. Position "
                                + "is arrival order, not cause. Do not read step-through or the "
                                + "topology's order badges as causality on this log.");
            }

            // M37: the source roots, BEFORE the fresh-start early return below. Until now `source` was
            // assembled last, so a socket-driven fresh start — the exact case `--rest` exists for — reported
            // no roots at all until the first log had loaded, and the Project panel drew "No source roots"
            // over roots a project had just supplied.
            Map<String, Object> source = facts.sourceAsMap();
            // each root with its tier — a flat list cannot say which roots a project brought, which are the
            // user's own, and which is the demo's transient root that a restart forgets
            List<Map<String, Object>> tiers = new ArrayList<>();
            Path projRoot = project.hasProject()
                    ? telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.baseDirFor(project.activeFile()) : null;
            for (String r : effectiveSourceRoots()) {
                Map<String, Object> one = new java.util.LinkedHashMap<>();
                one.put("path", r);
                one.put("tier", demoRoots.contains(r) ? "demo (transient)" : project.hasProject() ? "project" : "own settings");
                // M38.6 D-C9: the FORM the profile stores it in — "absolute" on a row you are about to share is
                // the whole warning, delivered before a colleague's machine delivers it as a failure
                one.put("form", telamin.fluxtion.audit.analyser.analyser.config.PathForm
                        .of(r, projRoot, config.workspaceRoot, System.getProperty("user.home")).label);
                tiers.add(one);
            }
            source.put("rootTiers", tiers);
            if (config.workspaceRoot != null && !config.workspaceRoot.isBlank()) {
                source.put("workspaceRoot", config.workspaceRoot);
                Path ws = telamin.fluxtion.audit.analyser.analyser.config.PathForm.workspaceDir(projRoot, config.workspaceRoot);
                if (ws != null) source.put("workspaceDir", ws.toString());
            }
            out.put("source", source);

            // M40.1 review F1: the topology BEFORE the fresh-start early return below. It sat after it, so with a
            // graph open and no log ever loaded — the exact case audit readiness exists for — `context` carried
            // no `topology` and therefore no verdict; only the `topology` verb's echo had it. Same trap that hid
            // `source` until M37.
            if (topologyPanel.hasTopology()) out.put("topology", topologyPanel.cursorState());

            // exactly the shape 'aggregate' takes for its own filter, so it can be passed straight back
            Map<String, Object> f = new java.util.LinkedHashMap<>();
            // Found driving M35.8 E7.0: on a FRESH start no log has ever been loaded, `filter` is still
            // null, and `context` — the first call any agent makes — threw. An app that cannot describe
            // "nothing is open" cannot be bootstrapped from the socket at all.
            if (filter == null) {
                out.put("filter", f);
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("context", "context", out);
            }
            if (filter.fromMillis() != null) f.put("from", filter.fromMillis());
            if (filter.toMillis() != null) f.put("to", filter.toMillis());
            if (filter.dimensions() != null && !filter.dimensions().isEmpty()) {
                f.put("dimensions", List.copyOf(filter.dimensions()));
            }
            if (filter.text() != null && !filter.text().isBlank()) f.put("text", filter.text());
            out.put("filter", f);
            out.put("showing", Map.of(
                    "visible", tablePanel.viewRowCount(),
                    "total", store == null ? 0 : store.size()));

            List<Map<String, Object>> selected = new ArrayList<>();
            for (LogRecord r : selectedRecords) {
                selected.add(Map.of("byteOffset", r.fileOffset(),
                        "event", String.valueOf(r.event()),
                        "logTime", r.logTime() == null ? -1L : r.logTime()));
            }
            out.put("selection", selected);

            // the user's findings so far — the highest-value thing here, and the part a pasted prompt
            // usually loses
            List<Map<String, Object>> flags = new ArrayList<>();
            for (Integer row : new java.util.TreeSet<>(flaggedRows)) {
                Map<String, Object> flag = new java.util.LinkedHashMap<>();
                flag.put("recordIndex", row);
                var finding = findings.get(row);
                if (finding != null && finding.hasNote()) flag.put("note", finding.note());
                if (finding != null && finding.hasFix()) flag.put("fix", finding.fix());
                flags.add(flag);
            }
            out.put("flags", flags);

            if (store != null && store.index().fileCount() > 1) {
                out.put("files", store.index().files());   // rolled set: offsets are file-local (M30)
            }
            if (!timeOrderReport.isClean()) {
                out.put("timeOrder", timeOrderReport.summarise());   // D-R3: agents must not discover
                // disorder by getting wrong answers from 'at'
            }
            if (!producerDiagnostics.isClean()) {
                // an agent reads the count and believes it; these are the cases where the count is a
                // lie about the file rather than a fact about the run
                out.put("producer", producerDiagnostics.messages());
            }


            List<String> graphs = graphTabs.graphNames();
            if (!graphs.isEmpty()) out.put("graphs", graphs);

            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("context", "context", out);
        }

        @Override
        public boolean showTab(String name) {
            return selectTab(name);
        }
    }

    /** Bring a right-hand tab forward by title. One implementation, used by the verb and by M36. */
    private boolean selectTab(String name) {
        if (sideTabs == null || name == null) return false;
        for (int i = 0; i < sideTabs.getTabCount(); i++) {
            if (sideTabs.getTitleAt(i).equalsIgnoreCase(name)) {
                sideTabs.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private void rememberGraphml(java.nio.file.Path file) {
        config.graphmlFile = file.toAbsolutePath().toString();
        config.addRecentGraphml(config.graphmlFile);
        rebuildRecentMenu();
        saveConfigQuietly();
    }

    /**
     * Reopen the topology that was showing at shutdown, if it is still there.
     *
     * <p>The log is already restored on start; the graph is half of the same working state, and having to
     * find it again every launch is what stops people leaving it open. Silent when the file has moved —
     * a dialog on startup about a file you may not have thought about in a week is noise, and the Topology
     * tab says plainly that nothing is loaded.
     */
    public void reopenLastGraphml() {
        String path = config.graphmlFile;
        if (path == null || path.isBlank()) return;
        java.nio.file.Path file = java.nio.file.Path.of(path);
        if (java.nio.file.Files.isReadable(file)) topologyPanel.load(file);
    }

    private void restoreBounds() {
        setPreferredSize(new Dimension(Math.max(600, config.windowW), Math.max(400, config.windowH)));
        pack();
        if (config.windowX >= 0 && config.windowY >= 0) {
            setLocation(config.windowX, config.windowY);
        } else {
            setLocationRelativeTo(null);
        }
    }

    /**
     * Quit. Every step is isolated and the exit is in a {@code finally}, because a shutdown that throws
     * half way used to leave the app <em>unquittable</em>: the exception escaped {@code windowClosing},
     * {@link System#exit} was never reached, and each further click on the close box threw again — with
     * the REST transport already stopped and the window still on screen. (Seen for real when the jar was
     * rebuilt underneath a running app, so a class not yet loaded went missing: a normal thing to do here,
     * since UI changes are verified by building and running the jar.)
     *
     * <p>So: one failing step must never cost the others, and nothing may cost the exit.
     */
    private void onExit() {
        flushProject();   // a debounce window must not eat the last edit of a session
        try {
            step(() -> { if (followTimer != null) followTimer.stop(); });
            step(() -> { if (mcpIndicatorTimer != null) mcpIndicatorTimer.stop(); });
            step(() -> { if (actionServer != null) actionServer.stop(); });
            // stop() already removes it; this also clears a file stranded by an earlier crash of ours, so a
            // clean quit never leaves a stale endpoint for an MCP client to find (M13.1)
            step(() -> telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile.wellKnown()
                    .deleteIfOwnedByThisProcess());
            step(() -> {
                config.windowX = getX();
                config.windowY = getY();
                config.windowW = getWidth();
                config.windowH = getHeight();
            });
            // remember open graphs — via the guarded sync, so quitting with NO log open can never
            // wipe the saved graphs with an empty tab set (the old raw clear+add here did exactly that)
            step(this::syncOpenGraphsIntoConfig);
            step(() -> {
                // B-M20-3: the profile gets the final state too — without this, graphs made this
                // session never reached the active project at all
                if (project != null) {
                    project.requestSave();
                    project.flush();
                }
            });
            step(this::saveConfigQuietly);   // still runs even if the steps above failed
            step(Background::shutdown);
        } finally {
            try {
                dispose();
            } catch (Throwable ignore) {
                // never let a disposal failure keep the process alive
            }
            System.exit(0);
        }
    }

    /**
     * Run one shutdown step, absorbing anything it throws. {@code Throwable}, not {@code Exception}: the
     * failure this exists for was a {@link NoClassDefFoundError}, and at exit there is nothing left to
     * protect by rethrowing.
     */
    static void step(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            System.err.println("[analyser] shutdown step failed, continuing: " + t);
        }
    }

    private void saveConfigQuietly() {
        syncOpenGraphsIntoConfig();   // never write a stale graph list (B-M20-3)
        try {
            // while a project is open the live config holds BOTH tiers; the global file must keep the
            // user's own pre-project values, or deleting a project directory would leave them with a
            // stale project's settings as their personal ones
            configStore.save(config, project == null ? null : project.globalTier());
        } catch (RuntimeException ignore) {
            // config persistence is best-effort
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }

    public AppConfig config() {
        return config;
    }
}
