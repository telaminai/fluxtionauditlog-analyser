package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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
        canvas.setDispatch(order);
        updateStepControls();
        if (record == null) {
            status.setText(hasTopology() && loadedFrom != null
                    ? summary(canvas.topology(), loadedFrom) : " ");
            return;
        }
        long unknown = order.stream().filter(id -> !canvas.topology().contains(id)).distinct().count();
        status.setText(describeEvent(record) + " — " + order.size() + " node(s) fired"
                       + (unknown > 0 && hasTopology()
                               ? "  ·  " + unknown + " not in this topology (different build?)" : ""));
    }

    private String describeEvent(LogRecord record) {
        String event = record.event() == null ? "event" : record.event();
        return record.eventToString() == null ? event : event;
    }

    private void stepBy(int delta) {
        if (cycle.isEmpty()) return;
        int current = canvas.step();
        int next = current < 0 ? (delta > 0 ? 0 : cycle.size() - 1) : current + delta;
        next = Math.max(0, Math.min(cycle.size() - 1, next));
        canvas.setStep(next);
        canvas.select(cycle.get(next).instanceId());
        describeStep(next);
        updateStepControls();
    }

    private void showWholeCycle() {
        canvas.setStep(-1);
        canvas.select(null);
        updateStepControls();
    }

    /** What this node held at this point in the cycle — the "what did it hold" half of stepping. */
    private void describeStep(int index) {
        NodeLog node = cycle.get(index);
        StringBuilder sb = new StringBuilder(node.instanceId());
        if (!node.entries().isEmpty()) {
            sb.append("  ·  ");
            for (int i = 0; i < node.entries().size(); i++) {
                if (i > 0) sb.append(", ");
                KV kv = node.entries().get(i);
                sb.append(kv.key()).append('=').append(kv.rawValue());
            }
        }
        if (!canvas.topology().contains(node.instanceId())) {
            sb.append("   [not in this topology]");
        }
        status.setText(sb.toString());
    }

    private void updateStepControls() {
        boolean stepping = !cycle.isEmpty();
        prevStep.setEnabled(stepping);
        nextStep.setEnabled(stepping);
        wholeCycle.setEnabled(stepping && canvas.step() >= 0);
        stepLabel.setText(!stepping ? "  no record selected"
                : canvas.step() < 0 ? "  " + cycle.size() + " nodes fired"
                : "  " + (canvas.step() + 1) + " / " + cycle.size());
    }

    private void describeSelection(String id) {
        if (id == null) {
            if (hasTopology() && loadedFrom != null) status.setText(summary(canvas.topology(), loadedFrom));
            return;
        }
        ProcessorTopology topology = canvas.topology();
        ProcessorTopology.Node node = topology.node(id);
        if (node == null) return;
        status.setText(id
                       + (node.className() == null ? "" : "  ·  " + node.className())
                       + "  ·  fed by " + topology.parentsOf(id).size()
                       + ", feeds " + topology.childrenOf(id).size());
    }

    private void toggleOrientation() {
        boolean topDown = canvas.orientation() == LayeredLayout.Orientation.TOP_DOWN;
        canvas.setOrientation(topDown
                ? LayeredLayout.Orientation.LEFT_RIGHT
                : LayeredLayout.Orientation.TOP_DOWN);
        orientationButton.setText(topDown ? "Top→down" : "Left→right");
    }
}
