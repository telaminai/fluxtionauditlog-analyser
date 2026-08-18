package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds one or more named {@link GraphPanel}s in tabs so different comparisons can be viewed side by side
 * (spec §8.7). Each graph binds to the same shared store + filter, so all react to the global filter.
 *
 * <p>Graphs are <b>named</b> (the tab title): rename via the button or a double-click on the tab. Names
 * persist in the profile and make a graph addressable through the assistant {@code graph} action
 * (spec-assistant-actions §4.3).
 */
public final class GraphTabs extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();
    private LogStore store;
    private FilterState filter;
    private int counter;
    private java.util.function.LongConsumer timeClickHandler = t -> { };   // plot click → scroll table there
    /** Told after any persistable graph change (see B-M20-3); quiet while {@link #restore} rebuilds. */
    private Runnable changeListener = () -> { };
    private boolean restoring;

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    private void fireChanged() {
        if (!restoring) changeListener.run();
    }

    public GraphTabs() {
        super(new BorderLayout());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton add = new JButton("New graph");
        JButton rename = new JButton("Rename…");
        JButton close = new JButton("Close graph");
        add.addActionListener(e -> addGraph());
        rename.addActionListener(e -> promptRename(tabs.getSelectedIndex()));
        close.addActionListener(e -> closeCurrent());
        bar.add(add);
        bar.add(rename);
        bar.add(close);
        add(bar, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setBorder(UiTheme.section("Graphs"));

        // double-click a tab to rename it
        tabs.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int i = tabs.indexAtLocation(e.getX(), e.getY());
                    if (i >= 0) promptRename(i);
                }
            }
        });
    }

    /** Rebind to a freshly loaded log: drop existing graphs and start with one. */
    public void bind(LogStore store, FilterState filter) {
        this.store = store;
        this.filter = filter;
        clearGraphs();
        counter = 0;
        addGraph();
    }

    /** Handler invoked with the UTC time under a plot click (wired to scroll the table to the nearest record). */
    public void setTimeClickHandler(java.util.function.LongConsumer handler) {
        this.timeClickHandler = handler == null ? t -> { } : handler;
    }

    private GraphPanel newPanel() {
        if (store == null || filter == null) return null;
        GraphPanel panel = new GraphPanel();
        panel.bind(store, filter);
        panel.setOnTimeClick(timeClickHandler);
        return panel;
    }

    private GraphPanel addGraph() {
        return addGraph(null);   // default "Graph N"
    }

    private static final String PIN = "📌";

    /** Add a graph with the given name (blank/null → default "Graph N"); selects it. */
    public GraphPanel addGraph(String name) {
        GraphPanel panel = newPanel();
        if (panel == null) return null;
        counter++;   // keep the counter ahead so later default names never collide with a chosen name
        panel.setGraphName((name == null || name.isBlank()) ? "Graph " + counter : name.trim());
        panel.setOnPinChanged(() -> refreshTabTitle(panel));   // 📌 indicator tracks the pin state
        panel.setOnMutation(this::fireChanged);                // B-M20-3: edits persist as you make them
        panel.onNotesChanged(this::fireChanged);               // interactive note pins/edits too
        tabs.addTab(displayTitle(panel), panel);
        tabs.setSelectedComponent(panel);
        fireChanged();
        return panel;
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
     * tab wasn't requested, else create one (named if a name was given). Never returns null once bound.
     */
    public GraphPanel graphForAction(String name, boolean newTab) {
        if (!newTab) {
            GraphPanel existing = graphNamed(name);
            if (existing != null) return existing;
        }
        return addGraph(name);
    }

    /** Rename the selected graph (used by the rename button). */
    public void renameSelected(String name) {
        renameAt(tabs.getSelectedIndex(), name);
    }

    /** Rename the graph named {@code from} to {@code to} (assistant action — explicit target, no selection). */
    public boolean renameNamed(String from, String to) {
        if (from == null || to == null || to.isBlank()) return false;
        GraphPanel gp = graphNamed(from);
        if (gp == null) return false;
        gp.setGraphName(to.trim());
        refreshTabTitle(gp);
        fireChanged();
        return true;
    }

    private void renameAt(int i, String name) {
        if (i >= 0 && name != null && !name.isBlank() && tabs.getComponentAt(i) instanceof GraphPanel gp) {
            gp.setGraphName(name.trim());
            refreshTabTitle(gp);
            fireChanged();
        }
    }

    private void promptRename(int i) {
        if (i < 0 || !(tabs.getComponentAt(i) instanceof GraphPanel gp)) return;
        String name = JOptionPane.showInputDialog(this, "Graph name:", gp.graphName());
        renameAt(i, name);
    }

    /** Name + series + formulas + pinned window of every open graph, for persistence. */
    public List<GraphSpec> specs() {
        List<GraphSpec> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) {
                var notes = gp.notes();
                List<GraphSpec.NoteSpec> noteSpecs = new ArrayList<>();
                for (var n : notes.notes()) {
                    noteSpecs.add(new GraphSpec.NoteSpec(n.atMillis(), n.text(), n.series()));
                }
                out.add(new GraphSpec(gp.graphName(), gp.seriesSpecs(), gp.exprSpecs(),
                        gp.pinnedFrom(), gp.pinnedTo(), gp.caption(),
                        notes.explanation(), noteSpecs, new ArrayList<>(gp.axes().rightSeries()),
                        gp.guides(), gp.bandSpecs(), gp.externalSpecs()));
            }
        }
        return out;
    }

    /** Rebuild graphs (names + series + formulas + pin) from saved specs (used when a profile is restored). */
    public void restore(List<GraphSpec> saved) {
        if (saved == null || saved.isEmpty() || store == null) return;
        restoring = true;   // rebuilding from persisted state is not a user edit — don't echo it back
        try {
            doRestore(saved);
        } finally {
            restoring = false;
        }
    }

    private void doRestore(List<GraphSpec> saved) {
        clearGraphs();
        counter = 0;
        for (GraphSpec g : saved) {
            GraphPanel panel = addGraph(g.name());
            if (panel == null) continue;
            panel.setCaption(g.note());
            if (g.series() != null) panel.addSpecs(g.series());
            for (GraphSpec.ExprSpec ex : g.exprs()) {
                panel.addExpr(ex.label(), ex.expr(), resolveOf(ex.resolve()));
            }
            if (g.isPinned()) panel.pin(g.from(), g.to());
            if (!g.guides().isEmpty()) panel.setGuides(g.guides());
            if (!g.bands().isEmpty()) panel.setBands(g.bands());
            if (!g.external().isEmpty()) panel.setExternal(g.external());   // async reload; D-F5 notes on failure
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
        }
        if (tabs.getTabCount() == 0) addGraph();
    }

    private static SeriesExtractor.Resolve resolveOf(String s) {
        try {
            return s == null ? SeriesExtractor.Resolve.LOCF : SeriesExtractor.Resolve.valueOf(s);
        } catch (IllegalArgumentException e) {
            return SeriesExtractor.Resolve.LOCF;
        }
    }

    private void clearGraphs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.unbind();
        }
        tabs.removeAll();
    }

    private void closeCurrent() {
        if (tabs.getTabCount() <= 1) return;   // keep at least one
        int i = tabs.getSelectedIndex();
        if (i < 0) return;
        if (tabs.getComponentAt(i) instanceof GraphPanel gp) gp.unbind();
        tabs.removeTabAt(i);
        fireChanged();
    }
}
