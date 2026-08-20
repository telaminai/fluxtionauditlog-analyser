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
    private final AppConfig config;

    private final LogTablePanel tablePanel = new LogTablePanel();
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
        actionExecutor.bind(topologyPanel, new AppControlAdapter());
        actionExecutor.bindExportPolicy(() -> config);   // B1: file-writing verbs are opt-in + confined
        actionExecutor.setReadGrants(this::sessionFileGrants);   // M29 D-F4: the chooser is the grant
        readerRegistry.loadPlugins(java.nio.file.Path.of(
                System.getProperty("user.home"), ".fluxtion-analyser", "plugins"));
        actionExecutor.setTimeOrderNote(() -> timeOrderReport.isClean() ? null
                : "time order is violated in this log — time-anchored answers may be approximate; "
                        + "see 'context'.timeOrder");   // M30 D-R4
        llmPanel.bind(() -> config, () -> selectedRecords, sourceService::selectedFqn,
                this::currentLogFileInfo, () -> store, actionExecutor, sourceService);
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
        topologyPanel.onTopologyLoaded(this::rememberGraphml);
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
            west.revalidate();
            west.repaint();
        });
        // the same column checkboxes as the menu, one click from the table instead of up in the menu bar
        rail.addAction("Columns", () -> {
            JPopupMenu popup = new JPopupMenu();
            for (java.awt.Component item : buildColumnsMenu().getMenuComponents()) popup.add(item);
            popup.show(rail, rail.getWidth(), 0);
        });
        rail.addGap();

        west.add(rail, BorderLayout.WEST);
        west.add(eventFilterPanel, BorderLayout.CENTER);
        return west;
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
                store.index(), loadedLogName());
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
                    .assembleTable(spec.sections().get(sectionIdx), store);
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
                spec, store.index(), loadedLogName(), findings,
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
                            .assembleTable(s, store);
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
                pictures.add(new telamin.fluxtion.audit.analyser.analyser.report.FindingReport.Picture(
                        "Where it sits in the processor",
                        "The whole graph with that cycle lit. What stayed grey is what this event did "
                                + "not reach — which is the evidence for anything of the form "
                                + "\"the check never fired\".",
                        views.wholeGraph()));
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
        rebuildRecentMenu();
        file.add(recentMenu);
        file.add(recentGraphmlMenu);

        // Projects are their own group: the items above open a FILE to look at, these change which
        // project's settings are in force. Appending them to the end would file "switch my whole
        // working set" next to "exit".
        file.addSeparator();
        file.add(openProjectItem());
        file.add(recentProjectsMenu);
        file.add(newProjectItem());
        saveProjectAsItem.addActionListener(e -> saveProjectAs());
        saveProjectAsItem.setToolTipText("Fork these settings to another project. There is no plain "
                                         + "Save — project edits persist as you make them.");
        file.add(saveProjectAsItem);
        closeProjectItem.addActionListener(e -> closeProject());
        file.add(closeProjectItem);

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

        JMenu help = new JMenu("Help");
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
                        spec, store == null ? null : store.index(), loadedLogName(), findings,
                        new java.util.HashSet<>(graphTabs.graphNames()), focusNames(), filter),
                sec -> store == null
                        ? new telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.AssembledTable(
                                new telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer.TableData(
                                        sec.columns(), java.util.List.of(), new boolean[0],
                                        sec.rowWhen(), sec.rowWhenLabel()),
                                java.util.List.of("no log is loaded"))
                        : telamin.fluxtion.audit.analyser.analyser.report.ReportVerb.assembleTable(sec, store),
                row -> tablePanel.selectModelRow(row),
                gname -> { sideTabs.setSelectedComponent(graphTabs); graphTabs.selectGraph(gname); },
                fname -> { sideTabs.setSelectedComponent(topologyPanel); topologyPanel.recallFocus(fname); },
                snap -> snap.applyTo(filter));
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
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainSplit, sideTabs);
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
        add(buildWestRail(), BorderLayout.WEST);
        add(center, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        java.awt.Color statusLine = UIManager.getColor("Component.borderColor");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        statusLine != null ? statusLine : java.awt.Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        UiTheme.status(status);
        statusBar.add(status, BorderLayout.WEST);
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

    private void setBusy(boolean busy) {
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

    /** Opens a local path or an s3:// URI. */
    public void openLocation(String location) {
        if (S3Source.isS3(location)) openS3(location);
        else openFile(Path.of(location));
    }

    /** Loads an s3:// log via the aws CLI on the background executor. */
    public void openS3(String uri) {
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
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1]),
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
                    + (caps.follow() ? ", follow" : "") + (caps.byteAnchors() ? ", byteAnchors" : "") + ")");
        }
        out.addAll(readerRegistry.loadNotes());
        return out;
    }

    /** Log-source readers: the built-in YAML reader + explicitly-installed plugin jars (M31 D-P3). */
    private final telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry readerRegistry =
            new telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry();

    /** The active log's time-order validation (M30 D-R3) — clean until a load says otherwise. */
    private telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport timeOrderReport =
            telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean();

    /** Loads a log file on the background executor and swaps in the new model on the EDT. */
    public void openFile(Path path) {
        // M30 D-R5: opening a member of a rolled set OFFERS the set — never assumes it
        try {
            var siblings = telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.discoverSiblings(path);
            if (siblings.size() > 1) {
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
                    openRolledSet(set);
                    return;
                }
            }
        } catch (Exception ignored) {
            // discovery is best-effort — a failed probe must never block opening the file itself
        }
        status.setText("Loading " + path + " …");
        setBusy(true);
        openFileWithReader(path, null);
    }

    /** Open via the reader registry (M31): explicit {@code format} wins; otherwise canOpen decides. */
    void openFileWithReader(Path path, String format) {
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
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1]),
                err -> {
                    setBusy(false);
                    status.setText("Failed to load " + path + ": " + rootMessage(err));
                    JOptionPane.showMessageDialog(this, rootMessage(err), "Load failed",
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    /** Open a resolved rolled set as one logical log (M30). */
    private void openRolledSet(telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.RollSet set) {
        var files = set.ordered().stream()
                .map(telamin.fluxtion.audit.analyser.analyser.parse.RollSetResolver.Sibling::file).toList();
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
                        (telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport) out[1]),
                err -> {
                    setBusy(false);
                    status.setText("Failed to load rolled set: " + rootMessage(err));
                    JOptionPane.showMessageDialog(this, rootMessage(err), "Load failed",
                            JOptionPane.ERROR_MESSAGE);
                });
    }

    private void onLoaded(LogStore loaded, String location) {
        onLoaded(loaded, location, telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean());
    }

    private void onLoaded(LogStore loaded, String location,
                          telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport report) {
        this.timeOrderReport = report == null
                ? telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean() : report;
        if (store != null && store != loaded) store.close();   // release the previous file's channel
        this.store = loaded;
        this.logDisplayLocation = location;
        this.logLocalPath = loaded.localFile();                 // real local file (temp file for S3)
        maybeOfferProject();       // M20.3 — the log may sit inside a project we could configure from
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
        graphTabs.bind(loaded, filter);
        graphTabs.restore(config.savedGraphs);   // reopen graphs saved in the profile
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
        sourceService.configure(config.sourceRoots, config.selectedEventProcessor,
                config.mavenRepos, config.searchMavenRepos);
        Background.run(() -> { sourceService.warmMavenIndex(); return null; }, r -> { }, err -> { });
        sourcePanel.setProcessors(candidateProcessors(), config.selectedEventProcessor);
        inferAndPopulateSource();

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
        status.setText(loaded.size() + " records · " + range + " · " + displayName(location) + orderWarning);
        if (!timeOrderReport.isClean()) {
            // D-R3: the report is shown, never buried — once, at load, with the evidence lines
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
    }

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
            openFile(Path.of(followPath));
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
                    sourcePanel.showSelectedProcessor();
                    saveConfigQuietly();
                },
                err -> { /* source inference is best-effort */ });
    }

    /**
     * First run (no config file saved yet): open the Settings dialog so the user can point the app at
     * source roots, an EventProcessor and an LLM before anything else. Saving creates the file, so
     * this shows exactly once.
     */
    public void showFirstRunSettingsIfNeeded() {
        if (configStore.exists()) return;
        JOptionPane.showMessageDialog(this,
                "Welcome! No configuration was found, so Settings will open now.\n"
                        + "Set your Java source roots (and optionally an LLM API key) to get the most\n"
                        + "out of source navigation and the assistant — everything can be changed later\n"
                        + "via File → Settings.",
                "First run", JOptionPane.INFORMATION_MESSAGE);
        ConfigPanel.show(this, config, this::onConfigChanged, this::readerSummaries);
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
        sourceService.configure(config.sourceRoots, config.selectedEventProcessor,
                config.mavenRepos, config.searchMavenRepos);
        Background.run(() -> { sourceService.warmMavenIndex(); return null; }, r -> { }, err -> { });
        sourcePanel.setProcessors(candidateProcessors(), config.selectedEventProcessor);
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
            // resolve any bundle-relative source roots against the imported file's own directory (M19.2)
            plan = share.preview(text, config, file.toPath().toAbsolutePath().getParent());
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
            applyProjectResult(project.open(file.toPath()));
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
    private void maybeOfferProject() {
        Path log = logLocalPath == null ? null : Path.of(logLocalPath);
        Path offer = projectDetect.offerFor(log, project.activeFile());
        if (offer == null) {
            return;
        }
        Path root = offer.getParent() == null ? offer : offer.getParent().getParent();
        String name = root == null ? offer.toString() : root.getFileName().toString();
        int answer = JOptionPane.showConfirmDialog(this,
                "This log sits inside the project \"" + name + "\", which has analyser settings.\n\n"
                + "Load them? Your source roots, Maven repos, event processors, graphs and hidden\n"
                + "columns will be replaced by that project\u2019s.",
                "Load this project?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            projectDetect.decline(log);      // asked and answered; do not ask again for this log
            return;
        }
        applyProjectResult(project.open(offer));
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
        item.setToolTipText("Start an empty project profile. Settings you edit from here save to it.");
        item.addActionListener(e -> chooseAndCreateProject());
        return item;
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
        applyProjectResult(project.open(file));
    }

    private void chooseAndCreateProject() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("New project — choose the project directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                .pathFor(fc.getSelectedFile().toPath());
        if (Files.exists(file)) {
            int keep = JOptionPane.showConfirmDialog(this,
                    "That directory already has a project.\nOpen it instead of overwriting?",
                    "Project exists", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (keep == JOptionPane.YES_OPTION) {
                applyProjectResult(project.open(file));
            }
            return;   // never silently replace someone's project file
        }
        try {
            applyProjectResult(project.create(file));
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not create the project: " + ex.getMessage(),
                    "New project", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveProjectAs() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save project as — choose the new project directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            project.saveAs(telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile
                    .pathFor(fc.getSelectedFile().toPath()));
            afterProjectChange("project forked to " + project.activeName());
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write the project: " + ex.getMessage(),
                    "Save project as", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeProject() {
        String was = project.activeName();
        project.close();
        afterProjectChange("closed project " + was + " — back to your own settings");
    }

    private void applyProjectResult(
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.LoadResult result) {
        if (!result.loaded()) {
            JOptionPane.showMessageDialog(this, result.message(), "Project",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        afterProjectChange(result.message());
    }

    /**
     * Everything that has to happen when the project-scoped settings have been swapped underneath the
     * running app: rebuild what reads them, refresh the menus, persist the pointer, and say so.
     */
    private void afterProjectChange(String note) {
        onConfigChanged();          // source service, processors, menus, and the global save
        graphTabs.restore(config.savedGraphs);
        tablePanel.setVisibleColumns(new java.util.HashSet<>(config.hiddenColumns));
        updateProjectMenuState();
        setTitleForProject();
        status.setText(note);
    }

    private void updateProjectMenuState() {
        saveProjectAsItem.setEnabled(project.hasProject());
        closeProjectItem.setEnabled(project.hasProject());
        fillRecent(recentProjectsMenu, config.recentProjects,
                path -> applyProjectResult(project.open(Path.of(path))));
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
            if (path == null || path.isBlank()) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("'log' is empty");
            }
            if (!S3Source.isS3(path) && !Files.isReadable(Path.of(path))) {
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                        "cannot read log '" + path + "'");
            }
            openLocation(path);
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok(
                    "open", "log", Map.of("path", path));
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
            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok(
                    "open", "graphml", Map.of("path", path));
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
        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLog(String path, String format) {
            java.nio.file.Path f = java.nio.file.Path.of(path);
            if (format != null && !format.isBlank()) {
                if (readerRegistry.readerFor(f, format) == null) {
                    return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(
                            "no installed reader has format '" + format + "' — installed: "
                                    + readerRegistry.describeReaders());
                }
                openFileWithReader(f, format);
                return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("open", "applied",
                        java.util.Map.of("log", path, "format", format, "loading", true));
            }
            return openLog(path);
        }

        public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult openLogs(java.util.List<String> paths) {
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
                openRolledSet(set);   // async load; the echo reports what was decided NOW
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
            if (!log.isEmpty()) out.put("log", log);

            // exactly the shape 'aggregate' takes for its own filter, so it can be passed straight back
            Map<String, Object> f = new java.util.LinkedHashMap<>();
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

            if (topologyPanel.hasTopology()) out.put("topology", topologyPanel.cursorState());

            List<String> graphs = graphTabs.graphNames();
            if (!graphs.isEmpty()) out.put("graphs", graphs);

            out.put("source", facts.sourceAsMap());

            return telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("context", "context", out);
        }

        @Override
        public boolean showTab(String name) {
            if (sideTabs == null || name == null) return false;
            for (int i = 0; i < sideTabs.getTabCount(); i++) {
                if (sideTabs.getTitleAt(i).equalsIgnoreCase(name)) {
                    sideTabs.setSelectedIndex(i);
                    return true;
                }
            }
            return false;
        }
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
