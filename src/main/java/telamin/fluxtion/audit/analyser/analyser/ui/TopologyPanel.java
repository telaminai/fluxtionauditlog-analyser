package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.EntryPointResolver;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;
import telamin.fluxtion.audit.analyser.analyser.topology.Scaffolding;
import telamin.fluxtion.audit.analyser.analyser.topology.StepCursor;
import telamin.fluxtion.audit.analyser.analyser.topology.TopologyFocus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Topology tab (M21.3): a {@link TopologyCanvas} plus the controls around it.
 *
 * <p>Loading is deliberately explicit for now — pick the processor's {@code .graphml}. Resolving it
 * automatically from the configured source roots, and from a linked server, are M21.5 and M21.6; doing
 * the simple thing first keeps this slice about whether the rendering is any good.
 */
public final class TopologyPanel extends JPanel {

    private final TopologyCanvas canvas = new TopologyCanvas();
    private final JLabel status = new JLabel(" ");
    private final JButton orientationButton = new JButton("Left→right");
    private final JButton prevStep = new JButton("◀");
    private final JButton nextStep = new JButton("▶");
    private final JButton wholeCycle = new JButton("Whole cycle");
    private final JLabel stepLabel = new JLabel(" ");
    private final JButton prevRecord = new JButton("◀◀");
    private final JButton nextRecord = new JButton("▶▶");
    private final JButton playButton = new JButton("▶ Play");
    /** Autoplay: one step per tick, on the EDT, so it drives exactly the same path as pressing ↓. */
    private final javax.swing.Timer autoplay = new javax.swing.Timer(700, e -> autoplayTick());

    private final TopologyIndex index = new TopologyIndex(null, null);
    private final javax.swing.JCheckBox scaffoldingBox = new javax.swing.JCheckBox("Scaffolding", false);
    private final javax.swing.JToggleButton focusButton = new javax.swing.JToggleButton("Focus");
    private final JLabel scopeLabel = new JLabel(" ");
    private javax.swing.JSlider spacingSlider;
    private javax.swing.JSlider textSlider;
    private Runnable displayPrefsChanged = () -> { };

    /** The graph as loaded. The canvas shows a filtered VIEW of this; classification always uses this. */
    private ProcessorTopology fullTopology = ProcessorTopology.empty();
    /** Clicked nodes — more than one when Cmd/Ctrl-clicked. */
    private final java.util.LinkedHashSet<String> selection = new java.util.LinkedHashSet<>();
    private TopologyFocus.Scope scope = TopologyFocus.Scope.NODE;

    private StepCursor.RecordSource recordSource;
    private Path loadedFrom;
    private Consumer<String> nodeActivated = id -> { };
    /** Told whenever a topology loads successfully, from any entry point — menu, recent list or drop. */
    private Consumer<Path> topologyLoaded = f -> { };

    /** The nodeLogs of the record the table has selected — the cycle being stepped through. */
    private List<NodeLog> cycle = List.of();
    /** Walks the filtered record sequence and the rows within each record. */
    private StepCursor cursor = StepCursor.over(List.of());
    /** Told when the cursor rolls into a different record, so the table can follow. */
    private java.util.function.IntConsumer recordChanged = index -> { };
    /** Told the current row so the detail viewer can highlight it: (instanceId, occurrence). */
    private java.util.function.BiConsumer<String, Integer> rowChanged = (id, n) -> { };
    /** Guards the table ⇄ cursor loop. */
    private boolean syncing;

    // Collaborators, all of them things the app already does; the topology only routes to them (M21.5).
    private java.util.function.BiConsumer<String, String> sourceOpener;
    private DetailPanel.GraphTargets graphTargets;
    private Consumer<String> filterAction;

    public TopologyPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("Open .graphml…", this::chooseFile));
        bar.addSeparator();
        bar.add(button("Fit", canvas::fitToView));
        bar.add(button("+", canvas::zoomIn));
        bar.add(button("−", canvas::zoomOut));
        bar.addSeparator();
        orientationButton.addActionListener(e -> toggleOrientation());
        bar.add(orientationButton);
        bar.addSeparator();
        scaffoldingBox.setToolTipText("Show the nodes Fluxtion adds to every graph — context, clock, "
                + "dispatcher, audit and service plumbing. Off by default: they are most of the graph "
                + "and none of your application.");
        scaffoldingBox.addActionListener(e -> applyView());
        bar.add(scaffoldingBox);
        focusButton.setToolTipText("Show only the selection and its scope (F). Off, the scope is dimmed "
                + "rather than hidden.");
        focusButton.addActionListener(e -> applyView());
        bar.add(focusButton);
        bar.add(button("Show all", this::showAll));
        UiTheme.status(scopeLabel);
        bar.add(scopeLabel);
        bar.addSeparator();
        bar.add(new JLabel(" spacing "));
        spacingSlider = slider(25, 400, 100, "Space the layers and siblings further apart",
                v -> { canvas.setSpacing(v / 100.0); displayPrefsChanged.run(); });
        bar.add(spacingSlider);
        bar.add(new JLabel(" text "));
        textSlider = slider(7, 22, 11, "Label point size — independent of zoom, so labels stay readable "
                        + "when you zoom out",
                v -> { canvas.setLabelSize(v); displayPrefsChanged.run(); });
        bar.add(textSlider);
        bar.addSeparator();
        prevStep.setToolTipText("Previous step  ↑   (rows, then back into the previous record)");
        nextStep.setToolTipText("Next step  ↓   (this record's rows, then on to the next record)");
        prevStep.addActionListener(e -> stepBy(-1));
        nextStep.addActionListener(e -> stepBy(1));
        wholeCycle.addActionListener(e -> showWholeCycle());
        prevRecord.setToolTipText("Previous record — skip the rest of this cycle");
        nextRecord.setToolTipText("Next record — skip the rest of this cycle");
        prevRecord.addActionListener(e -> moveRecord(-1));
        nextRecord.addActionListener(e -> moveRecord(1));
        playButton.setToolTipText("Step automatically until the end of the log");
        playButton.addActionListener(e -> togglePlay());
        autoplay.setInitialDelay(0);
        bar.add(prevRecord);
        bar.add(prevStep);
        bar.add(playButton);
        bar.add(nextStep);
        bar.add(nextRecord);
        bar.add(wholeCycle);
        bar.add(stepLabel);
        bar.add(Box.createHorizontalGlue());
        updateStepControls();

        UiTheme.status(status);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(2, UiTheme.PAD, 2, UiTheme.PAD));
        south.add(status, BorderLayout.CENTER);

        add(bar, BorderLayout.NORTH);
        canvas.setBorder(BorderFactory.createLineBorder(UiTheme.surfaceEdge()));
        // the index is a child of the canvas, so it floats over the drawing rather than taking width
        // from it; the bottom inset clears the "N nodes · N edges" HUD line the canvas paints there
        canvas.setLayout(new BorderLayout());
        JPanel overlay = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        overlay.setOpaque(false);
        overlay.setBorder(BorderFactory.createEmptyBorder(0, 4, 22, 4));
        overlay.add(index);
        canvas.add(overlay, BorderLayout.SOUTH);
        add(canvas, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        canvas.onNodeActivated(id -> nodeActivated.accept(id));
        canvas.onNodeSelected(this::describeSelection);
        canvas.onNodeClicked(this::onNodeClicked);
        index.setSelectHandler(id -> {
            onNodeClicked(id, false);
            // picked by name rather than found on screen, so bring it into view — otherwise the only
            // feedback for a node that is off screen is a status line changing
            canvas.centreOn(id);
        });
        index.setOpenSourceHandler(this::openSource);
        canvas.onNodeContextMenu(this::showNodeMenu);
        installStepKeys();
    }

    /**
     * <b>Down / Up step</b> — one key walks the whole log: into this record's rows, then on to the next
     * record. Arrows because that is what "next thing down the list" already means everywhere else; the
     * bracket keys stay bound as aliases but nothing advertises them.
     *
     * <p>Bound on the panel, so they only fire while focus is inside the Topology tab and the records
     * table keeps its own arrow behaviour. F3/Shift+F3 stay anomaly-jump: one key meaning two kinds of
     * "next" depending on focus is worse than two pairs.
     */
    private void installStepKeys() {
        javax.swing.InputMap keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap actions = getActionMap();
        keys.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "step-next");
        keys.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "step-prev");
        keys.put(javax.swing.KeyStroke.getKeyStroke('F'), "focus-toggle");
        keys.put(javax.swing.KeyStroke.getKeyStroke('f'), "focus-toggle");
        actions.put("focus-toggle", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                focusButton.setSelected(!focusButton.isSelected());
                applyView();
            }
        });
        keys.put(javax.swing.KeyStroke.getKeyStroke(']'), "step-next");
        keys.put(javax.swing.KeyStroke.getKeyStroke('['), "step-prev");
        actions.put("step-next", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepBy(1); }
        });
        actions.put("step-prev", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepBy(-1); }
        });
    }

    /** A compact toolbar slider that reports only when the drag settles, so layout does not re-run per pixel. */
    private javax.swing.JSlider slider(int min, int max, int value, String tip,
                                       java.util.function.IntConsumer onChange) {
        javax.swing.JSlider s = new javax.swing.JSlider(min, max, value);
        s.setToolTipText(tip);
        s.setPreferredSize(new java.awt.Dimension(90, s.getPreferredSize().height));
        s.setMaximumSize(new java.awt.Dimension(90, s.getPreferredSize().height));
        s.setFocusable(false);
        s.addChangeListener(e -> {
            // re-laying out a 300-node graph on every intermediate value makes the drag feel broken;
            // the label size is cheap enough to follow live
            if (!s.getValueIsAdjusting() || max <= 22) onChange.accept(s.getValue());
        });
        return s;
    }

    /**
     * Back to the whole graph, undimmed: clears the selection and the focus. Clicking empty canvas does
     * the same, but only if you know it does — an explicit way out matters more than an implicit one when
     * the view can hide most of the graph.
     */
    private void showAll() {
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        focusButton.setSelected(false);
        canvas.select(null);
        applyView();
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Double-click on a node — wired to source navigation in M21.5. */
    public void onNodeActivated(Consumer<String> listener) {
        this.nodeActivated = listener == null ? id -> { } : listener;
    }

    public TopologyCanvas canvas() {
        return canvas;
    }

    /** Whether a graph is loaded — asked of the graph, not of the filtered view, which can be empty. */
    public boolean hasTopology() {
        return !fullTopology.isEmpty();
    }

    // ---- loading ----------------------------------------------------------------------------------

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open processor GraphML");
        chooser.setFileFilter(new FileNameExtensionFilter("GraphML (*.graphml, *.xml)", "graphml", "xml"));
        if (loadedFrom != null && loadedFrom.getParent() != null) {
            chooser.setCurrentDirectory(loadedFrom.getParent().toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().toPath());
        }
    }

    /** Apply saved display preferences. Silent — restoring a setting is not a change to report back. */
    public void setDisplayPrefs(int spacingPercent, int textSize) {
        Runnable saved = displayPrefsChanged;
        displayPrefsChanged = () -> { };
        try {
            spacingSlider.setValue(spacingPercent);
            textSlider.setValue(textSize);
            canvas.setSpacing(spacingPercent / 100.0);
            canvas.setLabelSize(textSize);
        } finally {
            displayPrefsChanged = saved;
        }
    }

    public int spacingPercent() {
        return spacingSlider.getValue();
    }

    public int textSize() {
        return textSlider.getValue();
    }

    /** Told when the user moves either display slider, so the caller can persist it. */
    public void onDisplayPrefsChanged(Runnable listener) {
        this.displayPrefsChanged = listener == null ? () -> { } : listener;
    }

    public void onTopologyLoaded(Consumer<Path> listener) {
        this.topologyLoaded = listener == null ? f -> { } : listener;
    }

    /** Load a topology from a {@code .graphml}; a bad file reports rather than throwing. */
    public void load(Path file) {
        ProcessorTopology topology = GraphMlParser.parse(file);
        if (topology.isEmpty()) {
            setStatus("Could not read a topology from " + file.getFileName()
                           + " — is it a Fluxtion .graphml?");
            return;
        }
        loadedFrom = file;
        fullTopology = topology;
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        canvas.setClassificationTopology(fullTopology);
        // the FULL graph: the index is how you reach a node the filters have hidden
        index.setTopology(fullTopology);
        applyView(false);
        setStatus(summary(topology, file));
        topologyLoaded.accept(file);
    }

    /**
     * The click cycle (M22.2). Clicking the <b>same</b> node again widens the scope one step; clicking a
     * different one starts over at that node; Cmd/Ctrl-click adds to or removes from the selection, and
     * leaves the scope where it is so a multi-node scope can be built up.
     */
    private void onNodeClicked(String id, boolean additive) {
        if (id == null) {
            selection.clear();
            scope = TopologyFocus.Scope.NODE;
        } else if (additive) {
            if (!selection.remove(id)) selection.add(id);
        } else if (selection.size() == 1 && selection.contains(id)) {
            scope = scope.next();
        } else {
            selection.clear();
            selection.add(id);
            scope = TopologyFocus.Scope.NODE;
        }
        applyView();
    }

    private void applyView() {
        applyView(true);
    }

    /**
     * Rebuild the shown graph from the two filters. The canvas is given a <b>subgraph</b>, so a hidden
     * node's edges go with it and the view never draws a dependency that runs through something absent;
     * classification stays pinned to the full graph so what the log establishes does not change with what
     * is on screen.
     */
    private void applyView(boolean keepView) {
        java.util.Set<String> scoped = selection.isEmpty()
                ? null
                : TopologyFocus.expand(fullTopology, selection, scope);
        boolean focusing = focusButton.isSelected() && scoped != null;

        java.util.Set<String> visible =
                TopologyFocus.visible(fullTopology, scaffoldingBox.isSelected(), focusing ? scoped : null);
        canvas.setClassificationTopology(fullTopology);
        canvas.setTopology(fullTopology.subgraph(visible), keepView);
        // focusing already removes everything else, so dimming on top of it would say the same thing twice
        canvas.setEmphasis(focusing || scoped == null ? java.util.Set.of() : scoped);
        canvas.setSelectedNodes(selection);
        index.setSelection(selection);

        // the canvas cleared its shading when the graph changed — put the current cycle back
        if (!cycle.isEmpty() && cursor.record() != null) {
            showCycleOf(cursor.record());
            canvas.setCursor(cursor.currentInstanceId(), cursor.steppedSoFar(), cursor.atEntry());
        }
        updateScopeLabel(scoped);
        refreshStatus();
    }

    /**
     * All status writes go through here so the "what is hidden" note cannot be dropped by whichever
     * caller last set the text — and there are eight of them, each about something else.
     */
    private void setStatus(String text) {
        statusBase = text == null ? " " : text;
        status.setText(statusBase + viewNote());
    }

    /** Re-render the status line after a filter change, keeping whatever it was saying. */
    private void refreshStatus() {
        status.setText(statusBase + viewNote());
    }

    private String statusBase = " ";

    private void updateScopeLabel(java.util.Set<String> scoped) {
        StringBuilder sb = new StringBuilder("  ");
        if (!selection.isEmpty()) {
            sb.append(selection.size() == 1 ? selection.iterator().next()
                            : selection.size() + " selected")
              .append("  ·  ").append(scope.label())
              .append("  ·  ").append(scoped == null ? 0 : scoped.size()).append(" node(s)");
            if (!focusButton.isSelected()) sb.append("  ·  click again to widen");
        }
        scopeLabel.setText(sb.toString());
    }

    /**
     * What the current filters are keeping off screen, appended to the status line.
     *
     * <p>It belongs on the status line rather than only next to its checkbox because it is a statement
     * about <b>what you are looking at</b>. Half the nodes being absent is the single most misleading
     * thing this view can do quietly, and someone reading the graph is looking at the graph, not at the
     * toolbar they set ten minutes ago.
     */
    private String viewNote() {
        if (fullTopology.isEmpty()) return "";
        int shown = canvas.topology().nodeCount();
        int hidden = fullTopology.nodeCount() - shown;
        if (hidden <= 0) return "";
        List<String> parts = new ArrayList<>(2);
        if (!scaffoldingBox.isSelected()) {
            int scaffolding = Scaffolding.count(fullTopology);
            if (scaffolding > 0) parts.add(scaffolding + " scaffolding node(s) hidden");
        }
        int byFocus = hidden - (scaffoldingBox.isSelected() ? 0 : Scaffolding.count(fullTopology));
        if (byFocus > 0) parts.add(byFocus + " outside the focus");
        if (parts.isEmpty()) parts.add(hidden + " node(s) hidden");
        return "   [" + String.join(", ", parts) + "]";
    }

    private String summary(ProcessorTopology topology, Path file) {
        return file.getFileName() + " — " + topology.nodeCount() + " nodes, "
               + topology.edgeCount() + " edges, " + topology.roots().size() + " roots";
    }

    /**
     * Report how well the loaded topology matches the log on screen. The point of surfacing this is that
     * a topology from a different build renders perfectly and misleads silently.
     */
    public void checkAgainstLog(Collection<String> logInstanceIds) {
        if (!hasTopology()) return;
        // matched against the whole graph: a build mismatch is a fact about the file, and hiding
        // scaffolding must not turn a mismatch into a clean bill of health
        ProcessorTopology.Match match = fullTopology.match(logInstanceIds);
        setStatus((loadedFrom == null ? "" : loadedFrom.getFileName() + " — ") + match.describe());
    }

    // ---- cross-view wiring (M21.5) ----------------------------------------------------------------

    /** {@code (instanceId, method)} → open that node's source. Same opener the detail viewer uses. */
    public void setInstanceSourceOpener(java.util.function.BiConsumer<String, String> opener) {
        this.sourceOpener = opener;
        canvas.onNodeActivated(id -> openSource(id));   // double-click is the shortcut for the menu item
    }

    /** The same {@link DetailPanel.GraphTargets} the detail viewer plots through — not a second path. */
    public void setGraphTargets(DetailPanel.GraphTargets targets) {
        this.graphTargets = targets;
    }

    /**
     * Walk the <b>filtered</b> record sequence, so stepping honours the shared filter like every other
     * view. Passing null falls back to stepping within the selected record only.
     */
    public void setRecordSource(StepCursor.RecordSource source) {
        this.recordSource = source;
    }

    /** Called with the filtered-view index when stepping moves into a different record. */
    public void onRecordChanged(java.util.function.IntConsumer listener) {
        this.recordChanged = listener == null ? index -> { } : listener;
    }

    /** Called with (instanceId, occurrence) for the row under the cursor; (null, 0) at an entry. */
    public void onRowChanged(java.util.function.BiConsumer<String, Integer> listener) {
        this.rowChanged = listener == null ? (id, n) -> { } : listener;
    }

    /** Move the cursor to a record in the filtered view without re-entering the table's listener. */
    public void cursorToRecord(int filteredIndex) {
        if (syncing) return;
        cursor.moveToRecord(filteredIndex);
        syncCursor();
    }

    /** Narrow the shared filter to a node — routed through the app's existing text filter. */
    public void setFilterAction(Consumer<String> action) {
        this.filterAction = action;
    }

    private void openSource(String instanceId) {
        if (sourceOpener != null) sourceOpener.accept(instanceId, null);
    }

    /**
     * The node menu. Every item hands off to something that already exists — source navigation, the
     * graph tabs, the shared filter — so the topology adds routes, not parallel implementations.
     */
    private void showNodeMenu(String instanceId, java.awt.Point at) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem source = new JMenuItem("Open source");
        source.setEnabled(sourceOpener != null);
        source.addActionListener(e -> openSource(instanceId));
        menu.add(source);

        List<KV> graphable = graphableEntries(instanceId);
        JMenu graph = new JMenu("Graph");
        graph.setEnabled(graphTargets != null && !graphable.isEmpty());
        for (KV kv : graphable) {
            JMenuItem item = new JMenuItem(kv.key() + "  (" + kv.rawValue() + ")");
            item.addActionListener(e -> graphTargets.addSeries(null, instanceId, kv.key()));
            graph.add(item);
        }
        if (graphable.isEmpty()) {
            JMenuItem none = new JMenuItem(cycle.isEmpty()
                    ? "select a record first" : "no numeric values in this cycle");
            none.setEnabled(false);
            graph.add(none);
        }
        menu.add(graph);

        JMenuItem filter = new JMenuItem("Filter records to this node");
        filter.setEnabled(filterAction != null);
        filter.addActionListener(e -> filterAction.accept(instanceId));
        menu.add(filter);

        menu.addSeparator();
        JMenuItem copy = new JMenuItem("Copy instance id");
        copy.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(instanceId), null));
        menu.add(copy);

        menu.show(canvas, at.x, at.y);
    }

    /**
     * The keys of this node worth plotting, taken from the cycle on screen. Graphable means what it means
     * everywhere else in the app — {@link KV#graphValue()} decides, so a number buried in a
     * {@code toString()} is text here too.
     */
    List<KV> graphableEntries(String instanceId) {
        List<KV> out = new ArrayList<>();
        for (NodeLog node : cycle) {
            if (!node.instanceId().equals(instanceId)) continue;
            for (KV kv : node.entries()) {
                if (kv.graphValue().isPresent() && out.stream().noneMatch(k -> k.key().equals(kv.key()))) {
                    out.add(kv);
                }
            }
        }
        return out;
    }

    // ---- step-through (M21.4) ---------------------------------------------------------------------

    /**
     * Show the cycle for the record the table has selected. Deliberately driven by the existing
     * selection rather than a cursor of its own: two sources of truth for "which record are we on" is
     * the failure this design avoids (spec-graph-replay §6).
     */
    public void showRecord(LogRecord record) {
        showRecord(record, -1);
    }

    /**
     * Show the cycle for the selected record. {@code filteredIndex} is its position in the table's
     * filtered view, so stepping can continue into neighbouring records; -1 confines it to this record.
     */
    public void showRecord(LogRecord record, int filteredIndex) {
        if (!syncing && autoplay.isRunning()) stopPlay();   // a manual selection wins over the timer
        cycle = record == null ? List.of() : record.nodeLogs();
        List<String> order = new ArrayList<>(cycle.size());
        for (NodeLog n : cycle) order.add(n.instanceId());
        List<String> entries = record == null ? List.of()
                : List.copyOf(EntryPointResolver.resolve(
                        fullTopology, record.event(), record.eventToString()));
        canvas.setDispatch(order, entries,
                record != null && AuditTrace.tracesEveryInvocation(record.nodeLogs()));
        if (record == null) {
            cursor = StepCursor.over(List.of());
        } else if (recordSource != null && recordSource.size() > 0) {
            cursor = new StepCursor(recordSource);
            cursor.moveToRecord(Math.max(0, filteredIndex));
        } else {
            cursor = StepCursor.over(List.of(record));
        }
        updateStepControls();
        if (record == null) {
            setStatus(hasTopology() && loadedFrom != null
                    ? summary(fullTopology, loadedFrom) : " ");
            return;
        }
        long unknown = order.stream().filter(id -> !fullTopology.contains(id)).distinct().count();
        setStatus(describeEvent(record) + " — " + order.size()
                       + (AuditTrace.tracesEveryInvocation(record.nodeLogs()) ? " node(s) ran" : " node(s) logged")
                       + (unknown > 0 && hasTopology()
                               ? "  ·  " + unknown + " not in this topology (different build?)" : ""));
    }

    private String describeEvent(LogRecord record) {
        String event = record.event() == null ? "event" : record.event();
        return record.eventToString() == null ? event : event;
    }

    private void stepBy(int delta) {
        if (cursor.isEmpty()) return;
        int before = cursor.recordIndex();
        boolean moved = delta > 0 ? cursor.next() : cursor.prev();
        if (!moved) return;
        if (cursor.recordIndex() != before) {
            // rolled into another cycle: re-shade for the new record, then let the table follow
            syncing = true;
            try {
                showCycleOf(cursor.record());
                recordChanged.accept(cursor.recordIndex());
            } finally {
                syncing = false;
            }
        }
        syncCursor();
    }

    /** Re-shade the canvas for a record reached by stepping (not by table selection). */
    private void showCycleOf(telamin.fluxtion.audit.analyser.analyser.model.LogRecord record) {
        if (record == null) return;
        cycle = record.nodeLogs();
        List<String> order = new ArrayList<>(cycle.size());
        for (NodeLog n : cycle) order.add(n.instanceId());
        canvas.setDispatch(order,
                List.copyOf(EntryPointResolver.resolve(fullTopology, record.event(), record.eventToString())),
                AuditTrace.tracesEveryInvocation(record.nodeLogs()));
    }

    /**
     * Jump a whole record, rather than walking its remaining rows. On a market-data log most cycles are
     * the same three nodes, so stepping row by row to reach the next interesting event is not navigation
     * — it is scrolling.
     */
    private void moveRecord(int delta) {
        if (cursor.isEmpty()) return;
        int target = cursor.recordIndex() + delta;
        if (target < 0 || (recordSource != null && target >= recordSource.size())) return;
        syncing = true;
        try {
            cursor.moveToRecord(target);
            showCycleOf(cursor.record());
            recordChanged.accept(cursor.recordIndex());
        } finally {
            syncing = false;
        }
        syncCursor();
    }

    private void togglePlay() {
        if (autoplay.isRunning()) {
            stopPlay();
        } else if (!cursor.isEmpty() && cursor.canNext()) {
            autoplay.start();
            playButton.setText("❚❚ Pause");
        }
    }

    private void stopPlay() {
        autoplay.stop();
        playButton.setText("▶ Play");
        updateStepControls();
    }

    /** One autoplay tick. Stops at the end of the log rather than silently sitting on the last row. */
    private void autoplayTick() {
        if (cursor.isEmpty() || !cursor.canNext()) {
            stopPlay();
            return;
        }
        stepBy(1);
    }

    private void showWholeCycle() {
        cursor.moveToRecord(cursor.recordIndex());   // back to the entry: the whole cycle, nothing current
        canvas.select(null);
        syncCursor();
    }

    /** Push the cursor position into the canvas and the status line. */
    private void syncCursor() {
        canvas.setCursor(cursor.currentInstanceId(), cursor.steppedSoFar(), cursor.atEntry());
        String id = cursor.currentInstanceId();
        rowChanged.accept(id, occurrenceOfCurrentRow());
        if (id != null) canvas.select(id);
        setStatus(cursor.atEntry()
                ? cursor.positionLabel() + describeEntry()
                : cursor.positionLabel() + "  ·  " + cursor.rowSummary()
                  + (fullTopology.contains(id) ? "" : "   [not in this topology]"));
        updateStepControls();
    }

    /** How many times the cursor's node has already appeared in this cycle — a node can log twice. */
    private int occurrenceOfCurrentRow() {
        String id = cursor.currentInstanceId();
        if (id == null) return 0;
        int seen = -1;
        List<String> stepped = cursor.steppedSoFar();
        for (String s : stepped) {
            if (s.equals(id)) seen++;
        }
        return Math.max(0, seen);
    }

    private String describeEntry() {
        List<String> entries = cursor.entryPoints(fullTopology);
        return entries.isEmpty() ? "  ·  entry point not resolved from this record"
                : "  ·  entered at " + String.join(", ", entries);
    }

    private void updateStepControls() {
        boolean stepping = !cycle.isEmpty();
        prevStep.setEnabled(stepping && cursor.canPrev());
        nextStep.setEnabled(stepping && cursor.canNext());
        wholeCycle.setEnabled(stepping && !cursor.atEntry());
        int records = recordSource == null ? 0 : recordSource.size();
        prevRecord.setEnabled(stepping && cursor.recordIndex() > 0);
        nextRecord.setEnabled(stepping && cursor.recordIndex() + 1 < records);
        playButton.setEnabled(stepping && (autoplay.isRunning() || cursor.canNext()));
        stepLabel.setText(stepping ? "  " + headerText(records) : "  no record selected");
    }

    /**
     * The compact position: {@code event 8 / 10 · step 2 / 5}. Deliberately terse — the full, regime-aware
     * wording ("row 2 / 5 (logged nodes)") stays in the status line, where there is room to say what a row
     * means. Shortening it here and leaving it long there would be two claims about the same thing.
     */
    private String headerText(int records) {
        StringBuilder sb = new StringBuilder();
        if (records > 1) {
            sb.append("event ").append(cursor.recordIndex() + 1).append(" / ").append(records).append("  ·  ");
        }
        int rows = cursor.rowCount();
        if (cursor.atEntry()) {
            sb.append("entry");
            if (rows > 0) sb.append("  ·  ").append(rows).append(rows == 1 ? " row" : " rows");
        } else {
            sb.append("step ").append(cursor.rowIndex() + 1).append(" / ").append(rows);
        }
        return sb.toString();
    }

    /**
     * Wire the node tooltip to class documentation. Takes a resolver from FQN to source text — the panel
     * caches per class, because a tooltip fires on every hover and reading a file each time would make
     * moving the mouse across a graph do filesystem work.
     */
    public void setSourceResolver(java.util.function.Function<String, java.util.Optional<String>> resolver) {
        java.util.Map<String, String> cache = new java.util.HashMap<>();
        canvas.setDocLookup(node -> {
            String fqn = node.className();
            if (resolver == null || fqn == null || fqn.isBlank()) return null;
            return cache.computeIfAbsent(fqn, key -> resolver.apply(key)
                    .map(src -> telamin.fluxtion.audit.analyser.analyser.source.Javadoc.summary(
                            telamin.fluxtion.audit.analyser.analyser.source.Javadoc.forType(
                                    src, simpleNameOf(key))))
                    .orElse(""));      // "" is a cached MISS; null would retry the file on every hover
        });
    }

    private static String simpleNameOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    /** Re-apply theme-derived chrome. The canvas paints its own surface, but its edge is set once. */
    public void refreshTheme() {
        canvas.setBorder(BorderFactory.createLineBorder(UiTheme.surfaceEdge()));
        index.applyTheme();
        repaint();
    }

    private void describeSelection(String id) {
        if (id == null) {
            if (hasTopology() && loadedFrom != null) setStatus(summary(fullTopology, loadedFrom));
            return;
        }
        ProcessorTopology topology = fullTopology;
        ProcessorTopology.Node node = topology.node(id);
        if (node == null) return;
        var ran = canvas.executionOf(id);
        setStatus(id
                       + (node.className() == null ? "" : "  ·  " + node.className())
                       + "  ·  fed by " + topology.parentsOf(id).size()
                       + ", feeds " + topology.childrenOf(id).size()
                       + (ran == null ? "" : "  ·  " + TopologyCanvas.describe(ran)));
    }

    private void toggleOrientation() {
        boolean topDown = canvas.orientation() == LayeredLayout.Orientation.TOP_DOWN;
        canvas.setOrientation(topDown
                ? LayeredLayout.Orientation.LEFT_RIGHT
                : LayeredLayout.Orientation.TOP_DOWN);
        orientationButton.setText(topDown ? "Top→down" : "Left→right");
    }
}
