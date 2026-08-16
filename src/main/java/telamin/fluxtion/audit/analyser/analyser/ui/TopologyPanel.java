package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.EntryPointResolver;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;
import telamin.fluxtion.audit.analyser.analyser.topology.StepCursor;

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

    private StepCursor.RecordSource recordSource;
    private Path loadedFrom;
    private Consumer<String> nodeActivated = id -> { };

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
        prevStep.setToolTipText("Previous step  [   (rows, then the previous record)");
        nextStep.setToolTipText("Next step  ]   (rows, then the next record)");
        prevStep.addActionListener(e -> stepBy(-1));
        nextStep.addActionListener(e -> stepBy(1));
        wholeCycle.addActionListener(e -> showWholeCycle());
        bar.add(prevStep);
        bar.add(nextStep);
        bar.add(wholeCycle);
        bar.add(stepLabel);
        bar.add(Box.createHorizontalGlue());
        updateStepControls();

        UiTheme.status(status);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(2, UiTheme.PAD, 2, UiTheme.PAD));
        south.add(status, BorderLayout.CENTER);

        add(bar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        canvas.onNodeActivated(id -> nodeActivated.accept(id));
        canvas.onNodeSelected(this::describeSelection);
        canvas.onNodeContextMenu(this::showNodeMenu);
        installStepKeys();
    }

    /**
     * [ and ] step. F3/Shift+F3 stay anomaly-jump — overloading them would make one key mean two kinds
     * of "next" depending on focus, which is worse than a second pair.
     */
    private void installStepKeys() {
        javax.swing.InputMap keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap actions = getActionMap();
        keys.put(javax.swing.KeyStroke.getKeyStroke(']'), "step-next");
        keys.put(javax.swing.KeyStroke.getKeyStroke('['), "step-prev");
        actions.put("step-next", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepBy(1); }
        });
        actions.put("step-prev", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepBy(-1); }
        });
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

    public boolean hasTopology() {
        return !canvas.topology().isEmpty();
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

    /** Load a topology from a {@code .graphml}; a bad file reports rather than throwing. */
    public void load(Path file) {
        ProcessorTopology topology = GraphMlParser.parse(file);
        if (topology.isEmpty()) {
            status.setText("Could not read a topology from " + file.getFileName()
                           + " — is it a Fluxtion .graphml?");
            return;
        }
        loadedFrom = file;
        canvas.setTopology(topology);
        status.setText(summary(topology, file));
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
        ProcessorTopology.Match match = canvas.topology().match(logInstanceIds);
        status.setText((loadedFrom == null ? "" : loadedFrom.getFileName() + " — ") + match.describe());
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
        cycle = record == null ? List.of() : record.nodeLogs();
        List<String> order = new ArrayList<>(cycle.size());
        for (NodeLog n : cycle) order.add(n.instanceId());
        List<String> entries = record == null ? List.of()
                : List.copyOf(EntryPointResolver.resolve(
                        canvas.topology(), record.event(), record.eventToString()));
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
            status.setText(hasTopology() && loadedFrom != null
                    ? summary(canvas.topology(), loadedFrom) : " ");
            return;
        }
        long unknown = order.stream().filter(id -> !canvas.topology().contains(id)).distinct().count();
        status.setText(describeEvent(record) + " — " + order.size()
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
                List.copyOf(EntryPointResolver.resolve(canvas.topology(), record.event(), record.eventToString())),
                AuditTrace.tracesEveryInvocation(record.nodeLogs()));
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
        status.setText(cursor.atEntry()
                ? cursor.positionLabel() + describeEntry()
                : cursor.positionLabel() + "  ·  " + cursor.rowSummary()
                  + (canvas.topology().contains(id) ? "" : "   [not in this topology]"));
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
        List<String> entries = cursor.entryPoints(canvas.topology());
        return entries.isEmpty() ? "  ·  entry point not resolved from this record"
                : "  ·  entered at " + String.join(", ", entries);
    }

    private void updateStepControls() {
        boolean stepping = !cycle.isEmpty();
        prevStep.setEnabled(stepping && cursor.canPrev());
        nextStep.setEnabled(stepping && cursor.canNext());
        wholeCycle.setEnabled(stepping && !cursor.atEntry());
        stepLabel.setText(stepping ? "  " + cursor.positionLabel() : "  no record selected");
    }

    private void describeSelection(String id) {
        if (id == null) {
            if (hasTopology() && loadedFrom != null) status.setText(summary(canvas.topology(), loadedFrom));
            return;
        }
        ProcessorTopology topology = canvas.topology();
        ProcessorTopology.Node node = topology.node(id);
        if (node == null) return;
        var ran = canvas.executionOf(id);
        status.setText(id
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
