package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds one or more named {@link GraphPanel}s behind an open-graph selector so different comparisons can be viewed
 * (spec §8.7). Each graph binds to the same shared store + filter, so all react to the global filter.
 *
 * <p>Graphs are <b>named</b>: rename from the More menu. Names
 * persist in the profile and make a graph addressable through the assistant {@code graph} action
 * (spec-assistant-actions §4.3).
 */
public final class GraphTabs extends JPanel {

    // Keep JTabbedPane's established selection/lifecycle model while suppressing its wrapping tab strip.
    // The combo is the sole visible selector; the hidden tabs still own each GraphPanel exactly once.
    private final JTabbedPane tabs = new JTabbedPane() {
        @Override public void updateUI() {
            super.updateUI();
            setUI(new BasicTabbedPaneUI() {
                @Override protected int calculateTabAreaHeight(int placement, int runs, int height) { return 0; }
                @Override protected void paintTabArea(java.awt.Graphics g, int placement, int selected) { }
            });
        }
    };
    private final JComboBox<GraphPanel> graphSelector = new JComboBox<>();
    private final JMenuItem renameItem = new JMenuItem("Rename…");
    private final JMenuItem closeItem = new JMenuItem("Close graph");
    private final JMenuItem deleteItem = new JMenuItem("Delete chart");
    private boolean syncingSelector;
    private LogStore store;
    private FilterState filter;
    private int counter;
    private java.util.function.LongConsumer timeClickHandler = t -> { };   // plot click → scroll table there
    private java.util.function.IntConsumer markerClickHandler = r -> { };  // marker click → select record (M32)
    /** Told after any persistable graph change (see B-M20-3); quiet while {@link #restore} rebuilds. */
    private Runnable changeListener = () -> { };
    private boolean restoring;
    private java.util.function.Supplier<List<GraphSpec>> savedDefinitions = List::of;
    private String definitionRefusal;
    private final javax.swing.JTextArea definitionNotice = new javax.swing.JTextArea();
    private final javax.swing.JScrollPane definitionNoticeScroll = new javax.swing.JScrollPane(definitionNotice);
    private final List<javax.swing.AbstractButton> editingButtons = new ArrayList<>();
    /** Shown only while definitions are withheld: the way out that is not "close the app and edit a file". */
    private final JButton repairButton = new JButton("Repair names…");
    /**
     * M68.7 (owner, Q4): the charts' statement that the file behind the log changed after it was read. One banner
     * above every chart tab, so a chart opened after the verdict is under it too. It holds no verdict: the frame sets
     * it from the session snapshot, beside the table's (M68.5). G14's pass condition lands on this surface.
     */
    private final javax.swing.JLabel identityBanner = new javax.swing.JLabel() {
        /** Review O2: the warning colour follows the theme — recomputed whenever the look and feel is updated. */
        @Override public void updateUI() {
            super.updateUI();
            setForeground(UiTheme.warnForeground());
        }
    };

    /**
     * What the charts say for a file-identity verdict, or null for none. WHEN is the table's rule
     * ({@link LogTablePanel#identityBannerText}), so the surfaces cannot disagree about whether the file changed;
     * this says only what that means for a chart.
     */
    static String identityBannerText(String verdict, String reason) {
        if (LogTablePanel.identityBannerText(verdict, reason) == null) return null;
        // Review O1: the verdict and the recovery lead, so a narrow pane still shows a complete sentence; the reason
        // follows, and the whole note is the tooltip.
        return "⚠ Charts not verified against the file on disk — reopen the log to redraw them · "
                + (reason == null ? "the file behind this log changed after it was read" : reason);
    }

    /** Show {@code note} above the charts, or hide the banner for null. Call on the EDT. */
    public void setIdentityNote(String note) {
        identityBanner.setText(note == null ? "" : note);
        identityBanner.setToolTipText(note);
        identityBanner.setVisible(note != null);
        revalidate();
    }

    /** The banner's text, or null while it is hidden. */
    String identityNote() {
        return identityBanner.isVisible() ? identityBanner.getText() : null;
    }
    private Runnable repairHandler = () -> { };

    public void setRepairHandler(Runnable handler) {
        this.repairHandler = handler == null ? () -> { } : handler;
    }

    /** Visible for tests: the control a person uses to resolve ambiguity in place. */
    JButton repairButton() {
        return repairButton;
    }

    /** Definitions are unambiguous again; drop the refusal so the next restore can bind them. */
    public void clearRefusal() {
        definitionRefusal = null;
        showDefinitionNotice();
    }

    /** No definition is selected or repaired when its name is ambiguous. */
    public void refuseDefinitions(String reason) {
        definitionRefusal = java.util.Objects.requireNonNull(reason);
        clearGraphs();
        showDefinitionNotice();
    }

    public String definitionRefusal() {
        return definitionRefusal;
    }

    private void showDefinitionNotice() {
        definitionNotice.setText(definitionRefusal == null ? "" : definitionRefusal);
        definitionNoticeScroll.setVisible(definitionRefusal != null);
        // Owner decision 2026-09-24: only DELETE is withheld while definitions are ambiguous. New graph,
        // Rename and Close act on open tabs, nothing is persisted while a refusal stands, and blocking all
        // chart work punished people whose profiles were made ambiguous by a shipped release.
        editingButtons.forEach(button -> button.setEnabled(definitionRefusal == null));
        repairButton.setVisible(definitionRefusal != null);
        revalidate();
        repaint();
    }

    public void setSavedDefinitions(java.util.function.Supplier<List<GraphSpec>> definitions) {
        savedDefinitions = definitions == null ? List::of : definitions;
    }

    public boolean hasDefinition(String name) {
        return name != null && takenNames().contains(name.trim());
    }

    /**
     * Is this name held by a SAVED definition that is currently withheld? R13-4b: {@link #hasDefinition}
     * answers "taken", which includes open tabs — so a chart the assistant had just created counted as
     * withheld and could not be edited again. Only the saved definitions are actually being withheld.
     */
    public boolean isWithheldDefinition(String name) {
        if (name == null || definitionRefusal == null) return false;
        String target = name.trim();
        for (GraphSpec g : savedDefinitions.get()) {
            if (target.equals(g.name())) return true;
        }
        return false;
    }


    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    private void fireChanged() {
        if (!restoring) changeListener.run();
    }

    public GraphTabs() {
        super(new BorderLayout());
        JPanel bar = new JPanel(new BorderLayout(4, 0));
        JButton add = new JButton("New graph");
        editingButtons.add(deleteItem);   // only Delete is withheld while definitions are ambiguous
        repairButton.setVisible(false);
        repairButton.setToolTipText("Resolve the duplicate chart names holding these definitions back");
        repairButton.addActionListener(e -> repairHandler.run());
        add.addActionListener(e -> addGraph());
        renameItem.addActionListener(e -> promptRename(tabs.getSelectedIndex()));
        closeItem.addActionListener(e -> closeCurrent());
        deleteItem.addActionListener(e -> deleteCurrent());
        // the two buttons say which is which, because one of them is unrecoverable
        closeItem.setToolTipText("Close the graph and keep the chart — reopen it from the Project panel");
        deleteItem.setToolTipText("Remove the chart's definition from the project, including its notes. Cannot be undone");
        JPopupMenu actions = new JPopupMenu();
        actions.add(renameItem);
        actions.add(closeItem);
        actions.addSeparator();
        actions.add(deleteItem);
        JButton more = new JButton("More ▾");
        more.setToolTipText("Rename, close or delete the selected graph");
        more.setComponentPopupMenu(actions);
        more.addActionListener(e -> actions.show(more, 0, more.getHeight()));
        graphSelector.setToolTipText("Select an open graph; closed charts can be reopened from Project");
        graphSelector.getAccessibleContext().setAccessibleName("Open graph");
        graphSelector.setMaximumRowCount(16);
        graphSelector.setPreferredSize(new Dimension(140, graphSelector.getPreferredSize().height));
        graphSelector.setRenderer((ListCellRenderer<? super GraphPanel>) (list, value, index, selected, focus) -> {
            JLabel label = (JLabel) new javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(value == null ? "No open graph" : displayTitle(value));
            return label;
        });
        graphSelector.addActionListener(e -> {
            if (!syncingSelector && graphSelector.getSelectedItem() instanceof GraphPanel panel)
                tabs.setSelectedComponent(panel);
        });
        tabs.addChangeListener(e -> syncSelectorSelection());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        controls.add(add);
        controls.add(more);
        controls.add(repairButton);
        bar.add(graphSelector, BorderLayout.CENTER);
        bar.add(controls, BorderLayout.EAST);
        identityBanner.setVisible(false);
        identityBanner.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        north.add(identityBanner, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        definitionNotice.setEditable(false);
        definitionNotice.setLineWrap(true);
        definitionNotice.setWrapStyleWord(true);
        definitionNotice.setRows(4);
        definitionNoticeScroll.setVisible(false);
        add(definitionNoticeScroll, BorderLayout.SOUTH);
        setBorder(UiTheme.section("Graphs"));

        syncSelectorSelection();
    }

    /** The selector mirrors the tab model; choosing a graph is view navigation, not a saved chart edit. */
    private void syncSelectorSelection() {
        syncingSelector = true;
        try {
            if (graphSelector.getItemCount() != tabs.getTabCount()) {
                graphSelector.removeAllItems();
                for (int i = 0; i < tabs.getTabCount(); i++)
                    graphSelector.addItem((GraphPanel) tabs.getComponentAt(i));
            }
            graphSelector.setSelectedItem(tabs.getSelectedComponent());
            graphSelector.repaint();
            renameItem.setEnabled(tabs.getSelectedIndex() >= 0);
            closeItem.setEnabled(tabs.getTabCount() > 1);
            deleteItem.setEnabled(definitionRefusal == null && tabs.getSelectedIndex() >= 0);
        } finally {
            syncingSelector = false;
        }
    }

    /** Rebind to a freshly loaded log: drop existing graphs and start with one. */
    public void bind(LogStore store, FilterState filter) {
        this.store = store;
        this.filter = filter;
        clearGraphs();
        counter = 0;
        // The placeholder tab a fresh binding opens is STRUCTURAL, not a user edit — so it must not be
        // echoed to the change listener. Since B-M20-3 that listener persists the open tabs into the
        // profile, and MainFrame.onLoaded assigns the store BEFORE binding, so this one fireChanged()
        // overwrote a project's saved graphs with ["Graph 1"] a line before restore() read them back.
        // Every release since 1.1 lost a project's graphs on the first log open (ledger, 2026-08-26).
        boolean was = restoring;   // review 2026-08-27: restore the caller's value, never clear it
        restoring = true;
        try {
            addGraph();
        } finally {
            restoring = was;
        }
    }

    /** Handler invoked with the UTC time under a plot click (wired to scroll the table to the nearest record). */
    public void setTimeClickHandler(java.util.function.LongConsumer handler) {
        this.timeClickHandler = handler == null ? t -> { } : handler;
    }

    public void setMarkerClickHandler(java.util.function.IntConsumer handler) {
        this.markerClickHandler = handler == null ? r -> { } : handler;
    }

    private GraphPanel newPanel() {
        if (store == null || filter == null) return null;
        GraphPanel panel = new GraphPanel();
        panel.bind(store, filter);
        panel.setOnTimeClick(timeClickHandler);
        panel.setOnMarkerClick(markerClickHandler);
        panel.setFlagRugSource(flagRugSource);
        return panel;
    }

    /** Flagged rows → note, from MainFrame (M32.6); fanned to every panel, current and future. */
    private java.util.function.Supplier<java.util.Map<Integer, String>> flagRugSource;

    public void setFlagRugSource(java.util.function.Supplier<java.util.Map<Integer, String>> source) {
        this.flagRugSource = source;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.setFlagRugSource(source);
        }
    }

    /** The open log grew (follow — M65 D-F1): every chart re-extracts, each through its own debounce. */
    public void onRecordsAppended() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.onRecordsAppended();
        }
    }

    /** Flags changed: every chart's rug refreshes from its cached extraction. */
    public void refreshFlagRug() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.refreshFlagRug();
        }
    }

    private GraphPanel addGraph() {
        return addGraph(null);   // default "Graph N"
    }

    private static final String PIN = "📌";

    /**
     * Add and select a graph (blank/null name → default "Graph N"). Returns null when unbound,
     * when definitions are refused, or when an explicit name is already taken.
     */
    public GraphPanel addGraph(String name) {
        // Owner decision 2026-09-24: a NEW chart is allowed while definitions are withheld — an ambiguous
        // profile must not stop unrelated work. Reopening a WITHHELD one is still refused, by the name
        // check below, which is what "withheld definitions must not acquire unsaved edits" actually
        // protects. Nothing created here is persisted while a refusal stands: syncOpenGraphsIntoConfig
        // returns early, so the ambiguous definitions are left exactly as they are on disk.
        if (name != null && !name.isBlank()
                && (graphNamed(name.trim()) != null || (!restoring && hasDefinition(name)))) return null;
        GraphPanel panel = newPanel();
        if (panel == null) return null;
        panel.setGraphName((name == null || name.isBlank()) ? nextFreeDefaultName() : name.trim());
        panel.setOnPinChanged(() -> refreshTabTitle(panel));   // 📌 indicator tracks the pin state
        panel.setOnMutation(this::fireChanged);                // B-M20-3: edits persist as you make them
        panel.onNotesChanged(this::fireChanged);               // interactive note pins/edits too
        tabs.addTab(displayTitle(panel), panel);
        tabs.setSelectedComponent(panel);
        syncSelectorSelection();
        fireChanged();
        return panel;
    }

    /**
     * "Graph N" for the lowest N that nothing already answers to — a tab OR a closed definition.
     *
     * <p>f6e8d7e0: the counter alone was not enough. {@code doRestore} resets it to 0 and skips closed charts,
     * so after a reload "New graph" would hand out a name a closed, annotated chart still held, and the
     * name-keyed merge would then replace that chart with the empty new one.
     */
    String nextFreeDefaultName() {
        java.util.Set<String> taken = takenNames();
        do {
            counter++;
        } while (taken.contains("Graph " + counter));
        return "Graph " + counter;
    }

    /** The display title = the logical name with a 📌 prefix when pinned (name stays clean for persistence). */
    private static String displayTitle(GraphPanel panel) {
        return (panel.isPinned() ? PIN + " " : "") + panel.graphName();
    }

    private void refreshTabTitle(GraphPanel panel) {
        int i = indexOf(panel);
        if (i >= 0) {
            tabs.setTitleAt(i, displayTitle(panel));
            tabs.setToolTipTextAt(i, panel.isPinned()
                    ? "Pinned to a fixed window — click 📌 to unpin and follow the filter" : null);
            graphSelector.repaint();
        }
    }

    private int indexOf(GraphPanel panel) {
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) == panel) return i;
        return -1;
    }

    /** Logical names of the open graphs, in tab order. */
    public List<String> graphNames() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) out.add(gp.graphName());
        }
        return out;
    }

    /** Logical name of the currently selected graph, or null when none. */
    public String selectedGraphName() {
        return tabs.getSelectedComponent() instanceof GraphPanel gp ? gp.graphName() : null;
    }

    /** Select the named graph's tab (M33.4 — a report's chart section navigates here). No-op if absent. */
    public boolean selectGraph(String name) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp && gp.graphName().equals(name)) {
                tabs.setSelectedIndex(i);
                return true;
            }
        }
        return false;   // so a caller can SAY it found nothing rather than appear to have done something
    }

    /**
     * Add a raw series to a graph: {@code name} null/blank → the currently selected graph; a known
     * name → that graph; an unknown name → a new graph with that name. Selects the target tab.
     */
    public void addSeriesTo(String name, telamin.fluxtion.audit.analyser.analyser.graph.GraphKey key) {
        GraphPanel target;
        if (name == null || name.isBlank()) {
            target = tabs.getSelectedComponent() instanceof GraphPanel gp ? gp : addGraph();
        } else {
            target = graphForAction(name, false);
        }
        if (target == null) return;
        target.addKeys(List.of(key));
        tabs.setSelectedComponent(target);
    }

    /** The graph with logical name {@code name}, or null — for the assistant {@code graph} action. */
    public GraphPanel graphNamed(String name) {
        if (name == null) return null;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp && name.equals(gp.graphName())) return gp;
        }
        return null;
    }

    /**
     * Resolve the target graph for a {@code graph} action: reuse the named graph when it exists and a new
     * tab wasn't requested, else create one (named if a name was given). Refuses a new tab whose name is already saved (returns null).
     */
    public GraphPanel graphForAction(String name, boolean newTab) {
        String target = name == null ? null : name.trim();
        if (!newTab) {
            GraphPanel existing = graphNamed(target);
            if (existing != null) return existing;
            for (GraphSpec saved : savedDefinitions.get()) {
                if (java.util.Objects.equals(saved.name(), target) && openSaved(saved)) return graphNamed(target);
            }
        }
        return addGraph(target);
    }

    /** Rename the selected graph (used by the rename button). */
    public void renameSelected(String name) {
        renameAt(tabs.getSelectedIndex(), name);
    }

    /** Rename the graph named {@code from} to {@code to} (assistant action — explicit target, no selection). */
    public boolean renameNamed(String from, String to) {
        if (from == null || to == null || to.isBlank()) return false;
        GraphPanel gp = graphNamed(from);
        return gp != null && rename(gp, to);
    }

    private void renameAt(int i, String name) {
        if (i >= 0 && tabs.getComponentAt(i) instanceof GraphPanel gp) rename(gp, name);
    }

    /**
     * Rename one chart, keeping the stored definition with it.
     *
     * <p>f6e8d7e0: this used to change the tab title and nothing else. Since the name IS the identity, the
     * definition under the OLD name was then orphaned — kept by the merge as a closed ghost that could
     * never be reopened — and renaming onto a name something else already held silently merged the two
     * into one. Both are refused or repaired here, not in the merge, which cannot see intent.
     */
    boolean rename(GraphPanel gp, String name) {
        if (name == null || name.isBlank()) return false;
        if (SpotlightTarget.chartNameProblem(name) != null) return false;   // M68.6: callers refuse first, and say why
        String to = name.trim();
        String from = gp.graphName();
        if (to.equals(from)) return true;                 // nothing to do, and not a collision with itself
        if (takenNames().contains(to)) return false;
        gp.setGraphName(to);
        placeholders.remove(gp);   // a placeholder someone named is a chart they meant to keep
        refreshTabTitle(gp);
        renameListener.accept(from, to);   // move the stored definition BEFORE the list is persisted
        fireChanged();
        return true;
    }

    private void promptRename(int i) {
        if (i < 0 || i >= tabs.getTabCount() || !(tabs.getComponentAt(i) instanceof GraphPanel gp)) return;
        String name = JOptionPane.showInputDialog(this, "Graph name:", gp.graphName());
        // M68.6 first, so the person is told the RIGHT reason: main's collision message would otherwise be shown for a
        // name refused by the address grammar
        String problem = name == null || name.isBlank() ? null : SpotlightTarget.chartNameProblem(name);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, "Not renamed: " + problem, "Graph name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (name != null && !name.isBlank() && !rename(gp, name)) {
            JOptionPane.showMessageDialog(this, "A chart with that name already exists, including closed charts.",
                    "Name already used", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Name + series + formulas + pinned window of every open graph, for persistence. */
    public List<GraphSpec> specs() {
        List<GraphSpec> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) {
                if (isEmptyPlaceholder(gp)) continue;   // PR #51 review: see deleteConfirmed
                var notes = gp.notes();
                List<GraphSpec.NoteSpec> noteSpecs = new ArrayList<>();
                for (var n : notes.notes()) {
                    noteSpecs.add(new GraphSpec.NoteSpec(n.atMillis(), n.text(), n.series()));
                }
                out.add(new GraphSpec(gp.graphName(), gp.seriesSpecs(), gp.exprSpecs(),
                        gp.pinnedFrom(), gp.pinnedTo(), gp.caption(),
                        notes.explanation(), noteSpecs, new ArrayList<>(gp.axes().rightSeries()),
                        gp.guides(), gp.bandSpecs(), gp.externalSpecs(), gp.markerSpecs(),
                        gp.styleName()));
            }
        }
        return out;
    }

    /** Rebuild graphs (names + series + formulas + pin) from saved specs (used when a profile is restored). */
    public void restore(List<GraphSpec> saved) {
        telamin.fluxtion.audit.analyser.analyser.config.SavedGraphMerge.requireUniqueNames(saved);
        boolean resuming = definitionRefusal != null;
        definitionRefusal = null;
        showDefinitionNotice();
        if (saved == null || store == null || (saved.isEmpty() && !resuming)) return;
        boolean was = restoring;   // rebuilding from persisted state is not a user edit — don't echo it back
        restoring = true;
        try {
            doRestore(saved);
        } finally {
            restoring = was;
        }
    }

    /**
     * 35eeb320: open ONE saved chart and select it — what the Project panel's Open does for a chart that is
     * not currently a tab. A chart already open is selected rather than rebuilt, so Open never discards
     * edits made since the profile was written. Returns false when there is nothing to open.
     */
    public boolean openSaved(GraphSpec spec) {
        if (spec == null || store == null || definitionRefusal != null) return false;
        GraphPanel existing = graphNamed(spec.name());
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return true;
        }
        boolean was = restoring;   // building from persisted state is not a user edit
        restoring = true;
        boolean opened;
        try {
            GraphPanel panel = addGraph(spec.name());
            opened = panel != null;
            if (opened) {
                applySpec(panel, spec);
                tabs.setSelectedComponent(panel);
            }
        } finally {
            restoring = was;
        }
        // f6e8d7e0: reopening IS a change to persisted state — since 38ecc7f3 the open/closed flag is durable, so
        // a reopen that never asks to be saved sticks only if some later unrelated edit happens to write.
        // Fired outside the guard above, which exists to suppress the REBUILD, not the outcome.
        if (opened) fireChanged();
        return opened;
    }

    private void doRestore(List<GraphSpec> saved) {
        clearGraphs();
        counter = 0;
        for (GraphSpec g : saved) {
            // 38ecc7f3: a chart closed in an earlier session stays a DEFINITION and does not reopen as a tab.
            // It is still listed in the Project panel, and its Open reopens it through openSaved.
            if (!g.open()) continue;
            GraphPanel panel = addGraph(g.name());
            if (panel == null) continue;
            applySpec(panel, g);
        }
        if (tabs.getTabCount() == 0) addGraph();
    }

    /** Everything a {@link GraphSpec} says, onto a panel — the one place a saved chart is rebuilt. */
    private void applySpec(GraphPanel panel, GraphSpec g) {
        panel.setCaption(g.note());
        if (g.series() != null) panel.addSpecs(g.series());
        for (GraphSpec.ExprSpec ex : g.exprs()) {
            panel.addExpr(ex.label(), ex.expr(), resolveOf(ex.resolve()));
        }
        if (g.isPinned()) panel.pin(g.from(), g.to());
        if (!g.guides().isEmpty()) panel.setGuides(g.guides());
        if (!g.bands().isEmpty()) panel.setBands(g.bands());
        if (!g.external().isEmpty()) panel.setExternal(g.external());   // async reload; D-F5 notes on failure
        if (!g.markers().isEmpty()) panel.setMarkers(g.markers());
        // the reading of the chart, restored with it
        var notes = new telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes(
                g.explanation(), g.notes().stream()
                .map(n -> new telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note(
                        n.at(), n.text(), n.series()))
                .toList());
        if (!notes.isEmpty()) panel.setNotes(notes);
        if (!g.rightAxis().isEmpty()) {
            panel.setAxes(new telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment(
                    g.rightAxis()));
        }
        // 35eeb320: last, so the style the profile declared survives everything added above
        panel.setStyleByName(g.style());
    }

    private static SeriesExtractor.Resolve resolveOf(String s) {
        try {
            return s == null ? SeriesExtractor.Resolve.LOCF : SeriesExtractor.Resolve.valueOf(s);
        } catch (IllegalArgumentException e) {
            return SeriesExtractor.Resolve.LOCF;
        }
    }

    /**
     * Drop the log-derived plots (M35.1). The graph SPECS are profile state and live in the config —
     * they are restored by {@link #restore} on the next load, so closing a log loses no definition,
     * only the data that was extracted from it.
     */
    public void unbind() {
        clearGraphs();
        this.store = null;
        this.filter = null;
        counter = 0;
    }

    private void clearGraphs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.unbind();
        }
        tabs.removeAll();
        syncSelectorSelection();
    }

    /**
     * Close the tab. 38ecc7f3: this KEEPS the chart's definition — the profile still lists it, the Project
     * panel still shows it, and its Open reopens it. Removing a chart for good is {@link #deleteCurrent()},
     * a separate action that says so and asks first.
     */
    private void closeCurrent() {
        if (tabs.getTabCount() <= 1) return;   // keep at least one
        int i = tabs.getSelectedIndex();
        if (i < 0) return;
        if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.unbind();
        tabs.removeTabAt(i);
        syncSelectorSelection();
        fireChanged();
    }

    /**
     * Close a chart by NAME, keeping its definition — the non-destructive neighbour of
     * {@link #deleteNamed}, which the desktop has had all along as "Close graph".
     *
     * <p>PR #51 review, on #50: over the socket, delete was the ONLY way to get a chart off the
     * screen, and it is irreversible. That is what made the irreversibility bite — not that delete
     * exists, but that an assistant tidying up had no gentler option and the reply's own advice
     * ("close a chart instead to put it away") named something it could not do.
     *
     * <p>The tab strip keeps at least one tab, so closing the last chart is refused rather than
     * silently leaving a blank placeholder in its place.
     *
     * <p>Refused, too, while definitions are refused: nothing is saved then, so a close could not keep
     * anything. The desktop's Close stays enabled under a refusal (owner, 2026-09-24) and says nothing;
     * this verb promises "kept", so it must not be able to break the promise.
     *
     * @return null on success, otherwise why it was refused
     */
    public String closeNamed(String name) {
        if (name == null || name.isBlank()) return "close needs a chart name";
        GraphPanel gp = graphNamed(name.trim());
        if (gp == null) return "no open chart named '" + name.trim() + "' — open charts: " + graphNames();
        // PR #51 review: nothing is saved while a refusal stands (MainFrame.syncOpenGraphsIntoConfig returns
        // early), so every chart open now was made since the refusal and lives only as its tab. Closing one
        // would discard it — the reply would call that "kept". Refuse, in the refusal's own words.
        if (definitionRefusal != null) {
            return "'" + name.trim() + "' is not saved — no chart is while this stands: " + definitionRefusal
                    + " Closing it would discard it; repair the names first, or delete it if discarding is meant.";
        }
        if (tabs.getTabCount() <= 1) {
            return "'" + name.trim() + "' is the only open chart and the strip keeps one; its definition is "
                    + "already saved, so there is nothing to close it FOR";
        }
        gp.unbind();
        tabs.removeTabAt(indexOf(gp));
        syncSelectorSelection();
        fireChanged();
        return null;
    }

    /** Told the NAME of a chart the person deleted, so the owner of the config can drop its definition. */
    private java.util.function.Consumer<String> deleteListener = name -> { };

    public void setDeleteListener(java.util.function.Consumer<String> listener) {
        this.deleteListener = listener == null ? name -> { } : listener;
    }

    /** Told (from, to) when a chart is renamed, so the stored definition is renamed rather than orphaned. */
    private java.util.function.BiConsumer<String, String> renameListener = (from, to) -> { };

    public void setRenameListener(java.util.function.BiConsumer<String, String> listener) {
        this.renameListener = listener == null ? (from, to) -> { } : listener;
    }

    /**
     * Every chart name the PROJECT knows, including definitions that are closed and therefore not tabs.
     *
     * <p>f6e8d7e0: a chart is identified by its name, but this class could only see the open tabs. A closed
     * definition reserved nothing, so a generated "Graph N" or a rename could land on top of one and the
     * name-keyed merge would then overwrite it — destroying an annotated chart nobody named.
     */
    private java.util.function.Supplier<java.util.Set<String>> knownNames = java.util.Set::of;

    public void setKnownNames(java.util.function.Supplier<java.util.Set<String>> names) {
        this.knownNames = names == null ? java.util.Set::of : names;
    }

    /** Names that are spoken for: open tabs plus the project's closed definitions. Visible for tests. */
    java.util.Set<String> takenNames() {
        java.util.Set<String> taken = new java.util.HashSet<>(graphNames());
        java.util.Set<String> known = knownNames.get();
        if (known != null) taken.addAll(known);
        return taken;
    }

    /**
     * 38ecc7f3 — remove the chart's DEFINITION, not just its tab. Destructive and unrecoverable (a chart
     * carries its explanation and pinned notes, which is the part worth keeping), so it confirms first and
     * names the chart in the question. Close is the non-destructive neighbour.
     */
    /**
     * Asks the person whether to delete the named chart. Replaceable so a test can answer it: the real one
     * is a modal {@link JOptionPane}, which cannot run headless, and leaving it hard-wired meant the one
     * branch that must change NOTHING — Cancel — was verified by nothing at all.
     */
    private java.util.function.Predicate<String> confirmDelete = this::askWhetherToDelete;

    void setConfirmDelete(java.util.function.Predicate<String> ask) {
        this.confirmDelete = ask == null ? this::askWhetherToDelete : ask;
    }

    private boolean askWhetherToDelete(String name) {
        return JOptionPane.showConfirmDialog(this,
                "Delete the chart \"" + name + "\"?\n\n"
                        + "This removes its definition from the project — series, formulas, notes and the\n"
                        + "explanation written on it. It cannot be undone.\n\n"
                        + "To put it away without losing it, use Close graph instead.",
                "Delete chart", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    private void deleteCurrent() {
        int i = tabs.getSelectedIndex();
        if (i < 0 || i >= tabs.getTabCount() || !(tabs.getComponentAt(i) instanceof GraphPanel gp)) return;
        String name = gp.graphName();
        if (!confirmDelete.test(name)) return;   // Cancel changes nothing at all
        // Asking may run a nested event loop (the real dialog does), so the index captured above can be
        // stale by now — a tab may have closed, been renamed or been replaced under it. Re-resolve the
        // PANEL and re-check its name, or a confirmed delete lands on a chart nobody was asked about.
        int current = indexOf(gp);
        if (current >= 0 && name.equals(gp.graphName())) deleteConfirmed(current);
    }

    /** The Delete button's whole path, from the question to the consequence — for tests to drive. */
    void deleteSelected() {
        deleteCurrent();
    }

    /**
     * #50 — delete a chart by NAME, with no dialog: the socket's caller has already decided.
     *
     * <p>Charts accumulate in a profile exactly the way reports did before #23. An investigation leaves
     * throwaways behind — a probe to check an expression resolves, a variant to compare two window pins
     * — and they persist, reopen with the project, and sit in the open-graph selector indistinguishable from the
     * chart that carries the finding. Until this there was a Delete button and no verb, so an assistant
     * could create a chart and never clear it up.
     *
     * <p>A CLOSED chart still holds a definition, and deleting one of those has to work too — otherwise
     * "delete" would mean "delete only if you can see it", and the name would stay taken.
     *
     * @return false when no chart, open or saved, has that name
     */
    public boolean deleteNamed(String name) {
        if (name == null || name.isBlank()) return false;
        String target = name.trim();
        GraphPanel open = graphNamed(target);
        if (open != null) {
            deleteConfirmed(indexOf(open));
            return true;
        }
        if (!hasDefinition(target)) return false;
        deleteListener.accept(target);   // the saved definition, which is all a closed chart is
        fireChanged();
        return true;
    }

    /**
     * The delete itself, once a person has confirmed it — separated from the modal dialog so a test can
     * reach it. f6e8d7e0: a {@code JOptionPane} cannot run headless, so leaving this inside
     * {@link #deleteCurrent()} left the one destructive path in the app untestable.
     */
    void deleteConfirmed(int i) {
        if (i < 0 || i >= tabs.getTabCount() || !(tabs.getComponentAt(i) instanceof GraphPanel gp)) return;
        String name = gp.graphName();
        gp.unbind();
        tabs.removeTabAt(i);
        syncSelectorSelection();
        // f6e8d7e0: drop the definition FIRST. This used to run after the fallback below, and addGraph ends in
        // fireChanged() — a save — so the list was persisted while the deleted chart was still in it and a
        // fresh placeholder had just taken a name a CLOSED chart still held. The name-keyed merge then
        // overwrote that closed chart's definition with the empty placeholder: deleting one chart destroyed
        // a different one the dialog never named. The comment that used to sit here claimed this ordering
        // while the code did the opposite.
        deleteListener.accept(name);
        if (tabs.getTabCount() == 0) {
            // safe now: the name is free and the definition is gone. PR #51 review: the selector keeps one graph, so
            // deleting the LAST chart opens a blank one — and that blank tab used to be saved and reported as a chart
            // that "remains". Over the socket, an assistant clearing its probe charts deleted "probe" and was told
            // "remaining: [Graph 2]", deleted that and got "[Graph 3]", and each one landed in the profile: the
            // accumulation #50 exists to stop. A placeholder is not a chart until someone puts something on it.
            GraphPanel placeholder = addGraph();
            if (placeholder != null) placeholders.add(placeholder);
        }
        fireChanged();
    }

    /**
     * Blank tabs opened only because the last chart was deleted. Held by identity; one leaves this set's meaning
     * the moment it is renamed or given anything to show (see {@link #isEmptyPlaceholder}).
     */
    private final java.util.Set<GraphPanel> placeholders =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** A placeholder nobody has used: not saved, and not a chart that "remains" after a delete. */
    boolean isEmptyPlaceholder(GraphPanel gp) {
        return placeholders.contains(gp)
                && gp.seriesSpecs().isEmpty() && gp.exprSpecs().isEmpty() && gp.externalSpecs().isEmpty()
                && gp.markerSpecs().isEmpty() && gp.guides().isEmpty() && gp.bandSpecs().isEmpty()
                && gp.notes().isEmpty();
    }

    /** The charts a delete leaves behind: every open chart except an unused placeholder. */
    public List<String> chartsThatRemain() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp && !isEmptyPlaceholder(gp)) out.add(gp.graphName());
        }
        return out;
    }
}
