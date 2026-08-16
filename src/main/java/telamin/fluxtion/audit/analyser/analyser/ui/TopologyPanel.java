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

    private Path loadedFrom;
    private Consumer<String> nodeActivated = id -> { };

    /** The nodeLogs of the record the table has selected — the cycle being stepped through. */
    private List<NodeLog> cycle = List.of();
    /** Walks the cycle; S3 widens it from this record to the whole filtered sequence. */
    private StepCursor cursor = StepCursor.over(List.of());

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
        prevStep.setToolTipText("Previous node in dispatch order");
        nextStep.setToolTipText("Next node in dispatch order");
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
        cycle = record == null ? List.of() : record.nodeLogs();
        List<String> order = new ArrayList<>(cycle.size());
        for (NodeLog n : cycle) order.add(n.instanceId());
        List<String> entries = record == null ? List.of()
                : List.copyOf(EntryPointResolver.resolve(
                        canvas.topology(), record.event(), record.eventToString()));
        canvas.setDispatch(order, entries,
                record != null && AuditTrace.tracesEveryInvocation(record.nodeLogs()));
        cursor = record == null ? StepCursor.over(List.of()) : StepCursor.over(List.of(record));
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
        if (cycle.isEmpty()) return;
        boolean moved = delta > 0 ? cursor.next() : cursor.prev();
        if (!moved) return;
        syncCursor();
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
        if (id != null) canvas.select(id);
        status.setText(cursor.atEntry()
                ? cursor.positionLabel() + describeEntry()
                : cursor.positionLabel() + "  ·  " + cursor.rowSummary()
                  + (canvas.topology().contains(id) ? "" : "   [not in this topology]"));
        updateStepControls();
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
