package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.EntryPointResolver;
import telamin.fluxtion.audit.analyser.analyser.topology.FocusStack;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
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
import javax.swing.JComponent;
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
    private final JButton prevRecord = new JButton("◀◀");
    private final JButton nextRecord = new JButton("▶▶");
    private final JButton playButton = new JButton("▶ Play");
    /** Autoplay: one step per tick, on the EDT, so it drives exactly the same path as pressing ↓. */
    private final javax.swing.Timer autoplay = new javax.swing.Timer(700, e -> autoplayTick());

    private final TopologyIndex index = new TopologyIndex(null, null);
    private final javax.swing.JCheckBox scaffoldingBox = new javax.swing.JCheckBox("Scaffolding", false);
    private final javax.swing.JButton focusButton = new javax.swing.JButton("Focus");
    /**
     * H4: bound "all routes" to {@link TopologyFocus#ROUTE_HOP_BOUND} hops when it would otherwise be
     * most of the graph. On by default; unticking gives the unbounded answer. A checkbox rather than a
     * fifth scope so the verb surface (scope: node|neighbours|routes|all) stays as published.
     */
    private final javax.swing.JCheckBox boundRoutesBox =
            new javax.swing.JCheckBox("≤" + TopologyFocus.ROUTE_HOP_BOUND + " hops", true);
    /** The last routes answer, when the current scope is ROUTES — so the status can say if it was bounded. */
    private TopologyFocus.RouteScope lastRoutes;
    private SourcePanel embeddedSource;
    // Processor choices mirrored into the embedded (split-view) source pane, remembered so a pane
    // bound before the first refresh is still seeded (its dropdown is otherwise never populated).
    private java.util.List<String> lastProcessorFqns = java.util.List.of();
    private String lastSelectedProcessor;
    private final javax.swing.JToggleButton sourceButton = new javax.swing.JToggleButton("Source");
    private final javax.swing.JToggleButton syncButton = new javax.swing.JToggleButton("Sync", true);
    private final javax.swing.JSplitPane graphSplit =
            new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, true);
    private int dividerAt;
    private javax.swing.JSlider spacingSlider;
    private javax.swing.JSlider textSlider;
    private Runnable displayPrefsChanged = () -> { };

    /** The graph as loaded. The canvas shows a filtered VIEW of this; classification always uses this. */
    private ProcessorTopology fullTopology = ProcessorTopology.empty();
    /** Clicked nodes — more than one when Cmd/Ctrl-clicked. */
    private final java.util.LinkedHashSet<String> selection = new java.util.LinkedHashSet<>();
    private TopologyFocus.Scope scope = TopologyFocus.Scope.NODE;
    /** Focus as a nesting FILTER (M27): the top context is the world; dimming never exits it. */
    private FocusStack focusStack = new FocusStack(null);
    /** Clickable breadcrumb of the context stack; empty (hidden) at the full graph. */
    private final javax.swing.JPanel crumbBar = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
    /** Named focuses (M27.3): project-tier storage owned by the config; the panel only reads/writes it. */
    private java.util.function.Supplier<java.util.List<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>>
            namedFocuses = java.util.List::of;
    private Runnable focusesChanged = () -> { };
    private final javax.swing.JButton focusPicker = new javax.swing.JButton("Focuses ▾");
    /** What the last recall resolved — mismatch honesty for the status line and the verb echo. */
    private String lastRecallNote = "";
    /** The node set currently laid out — a change to it invalidates the saved zoom and pan. */
    private java.util.Set<String> shownNodes = java.util.Set.of();

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
        bar.add(button("Fit", () -> { canvas.fitToView(); displayPrefsChanged.run(); }));
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
        focusButton.setToolTipText("Filter the view to the selection's scope (F). The filtered graph "
                + "becomes the whole graph — click a node to explore within it; Esc steps back out, "
                + "Show all returns to the full graph.");
        focusButton.addActionListener(e -> pushFocus());
        bar.add(focusButton);
        boundRoutesBox.setToolTipText("When 'all routes' from a node would cover more than half of a large "
                + "graph — a sink everything feeds — keep it to " + TopologyFocus.ROUTE_HOP_BOUND
                + " hops each way. Untick for every route, however many.");
        boundRoutesBox.addActionListener(e -> applyView());
        bar.add(boundRoutesBox);
        focusPicker.setToolTipText("Named focuses — saved filter contexts for this project. Recalling one "
                + "replaces the current context stack; the rationale says why the view exists.");
        focusPicker.addActionListener(e -> showFocusPicker());
        bar.add(focusPicker);
        bar.add(button("Show all", this::showAll));
        crumbBar.setOpaque(false);
        bar.add(crumbBar);
        sourceButton.setToolTipText("Show the source beside the graph (Enter on a selected node)");
        sourceButton.setEnabled(false);
        sourceButton.addActionListener(e -> showSourcePane(sourceButton.isSelected()));
        bar.add(sourceButton);
        syncButton.setToolTipText("Source pane follows what you click and step through. Off, it stays "
                + "where you put it — Enter and the right-click menu still navigate.");
        syncButton.setFocusable(false);
        syncButton.addActionListener(e -> displayPrefsChanged.run());
        bar.add(syncButton);
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
        graphSplit.setLeftComponent(canvas);
        graphSplit.setRightComponent(null);       // the source pane appears only once asked for
        graphSplit.setResizeWeight(0.62);
        graphSplit.setBorder(null);
        releaseArrowKeys(graphSplit);
        add(graphSplit, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        canvas.onNodeActivated(id -> nodeActivated.accept(id));
        canvas.onNodeSelected(this::describeSelection);
        canvas.onNodeClicked(this::onNodeClicked);
        canvas.onViewChanged(() -> displayPrefsChanged.run());
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
        keys.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "open-source");
        actions.put("open-source", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (canvas.selected() != null) openSource(canvas.selected());
            }
        });
        keys.put(javax.swing.KeyStroke.getKeyStroke('F'), "focus-push");
        keys.put(javax.swing.KeyStroke.getKeyStroke('f'), "focus-push");
        actions.put("focus-push", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                pushFocus();
            }
        });
        keys.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "focus-pop");
        actions.put("focus-pop", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                popFocus();
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
     * Back to the FULL graph, undimmed: pops every focus context, then clears selection and shading.
     * This is the filter's exit. Clicking empty canvas clears only dimming/selection and stays inside
     * the current context — filter and dimming never share an exit gesture (M27).
     */
    private void showAll() {
        focusStack.popToFull();
        clearHighlights();
        refreshCrumbs();
    }

    /** Filter the view to the current selection's scope: the context becomes the whole graph (M27). */
    private void pushFocus() {
        if (selection.isEmpty()) return;
        java.util.Set<String> ids = scopedIds();
        String label = (selection.size() == 1 ? selection.iterator().next() : selection.size() + " nodes")
                + " · " + scope.label();
        if (!focusStack.push(ids, label)) return;
        selection.clear();
        scope = TopologyFocus.Scope.NODE;   // the cycle restarts inside the new, smaller world
        canvas.select(null);
        applyView(false);                   // a different world is a different layout — reframe
        refreshCrumbs();
    }

    /** Step back out one context level (Esc). */
    private void popFocus() {
        if (!focusStack.pop()) return;
        applyView(false);
        refreshCrumbs();
    }

    /** Rebuild the clickable breadcrumb: All (62) ▸ hedge path (12) ▸ … ; hidden at the full graph. */
    private void refreshCrumbs() {
        crumbBar.removeAll();
        if (!focusStack.atFull()) {
            java.util.List<FocusStack.Context> levels = focusStack.contextsOldestFirst();
            crumbBar.add(crumbButton("All (" + fullTopology.nodeCount() + ")", 0));
            for (int i = 0; i < levels.size(); i++) {
                crumbBar.add(new javax.swing.JLabel("▸"));
                FocusStack.Context c = levels.get(i);
                crumbBar.add(crumbButton(c.label() + " (" + c.ids().size() + ")", i + 1));
            }
        }
        crumbBar.revalidate();
        crumbBar.repaint();
    }

    // ---- named focuses (M27.3) -----------------------------------------------------------------

    public void bindNamedFocuses(
            java.util.function.Supplier<java.util.List<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec>> supplier,
            Runnable onChanged) {
        this.namedFocuses = supplier == null ? java.util.List::of : supplier;
        this.focusesChanged = onChanged == null ? () -> { } : onChanged;
    }

    /**
     * Save the current context as a named focus (replace-by-name). Returns an error message, or null.
     * The full graph is refused: a focus that admits everything filters nothing and is never what was
     * meant — apply a focus first, then name it.
     */
    public String saveFocusAs(String name, String rationale) {
        if (name == null || name.isBlank()) return "'saveFocusAs' needs a name";
        if (fullTopology.isEmpty()) return "no topology is loaded";
        if (focusStack.atFull()) return "nothing to save — the full graph is not a focus; apply one first";
        java.util.List<String> ids = java.util.List.copyOf(focusStack.world());
        java.util.List<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec> list = namedFocuses.get();
        String trimmed = name.trim();
        list.removeIf(f -> f.name().equals(trimmed));
        list.add(new telamin.fluxtion.audit.analyser.analyser.config.FocusSpec(trimmed,
                rationale == null ? "" : rationale.trim(), ids));
        focusesChanged.run();
        setStatus("saved focus '" + trimmed + "' (" + ids.size() + " nodes)");
        return null;
    }

    /**
     * Recall a named focus: pops to the full graph and pushes the named context (a named focus is an
     * absolute view — predictable regardless of what was open). Ids missing from this topology are
     * surfaced, never silently dropped: a partial resolve usually means a different build.
     * Returns an error message, or null.
     */
    public String recallFocus(String name) {
        lastRecallNote = "";
        if (name == null || name.isBlank()) return "'focus' needs a name";
        telamin.fluxtion.audit.analyser.analyser.config.FocusSpec spec = namedFocuses.get().stream()
                .filter(f -> f.name().equals(name.trim())).findFirst().orElse(null);
        if (spec == null) {
            java.util.List<String> known = namedFocuses.get().stream()
                    .map(telamin.fluxtion.audit.analyser.analyser.config.FocusSpec::name).toList();
            return "no focus named '" + name + "'" + (known.isEmpty() ? "" : " — available: " + known);
        }
        java.util.List<String> resolved = spec.nodeIds().stream().filter(fullTopology::contains).toList();
        if (resolved.isEmpty()) {
            return "focus '" + spec.name() + "': none of its " + spec.nodeIds().size()
                    + " nodes exist in this topology — a different build?";
        }
        focusStack.popToFull();
        focusStack.push(resolved, spec.name());
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        canvas.select(null);
        applyView(false);
        refreshCrumbs();
        int missing = spec.nodeIds().size() - resolved.size();
        if (missing > 0) {
            lastRecallNote = missing + " of " + spec.nodeIds().size()
                    + " nodes are not in this topology — the focus may be from a different build";
            setStatus("focus '" + spec.name() + "': " + lastRecallNote);
        } else {
            setStatus("focus '" + spec.name() + "' (" + resolved.size() + " nodes)"
                    + (spec.rationale().isBlank() ? "" : " — " + spec.rationale()));
        }
        return null;
    }

    /** The mismatch note from the last {@link #recallFocus}, or "" — for the verb echo. */
    public String lastRecallNote() {
        return lastRecallNote;
    }

    private void showFocusPicker() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem save = new javax.swing.JMenuItem("Save focus as…");
        save.setEnabled(!focusStack.atFull());
        save.setToolTipText(focusStack.atFull() ? "Apply a focus first — the full graph is not a focus" : null);
        save.addActionListener(e -> {
            javax.swing.JTextField nameField = new javax.swing.JTextField(18);
            javax.swing.JTextField whyField = new javax.swing.JTextField(28);
            javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 4, 4));
            form.add(new javax.swing.JLabel("Name:"));
            form.add(nameField);
            form.add(new javax.swing.JLabel("Why this view exists (shown in the picker):"));
            form.add(whyField);
            if (javax.swing.JOptionPane.showConfirmDialog(this, form, "Save focus",
                    javax.swing.JOptionPane.OK_CANCEL_OPTION) == javax.swing.JOptionPane.OK_OPTION) {
                String err = saveFocusAs(nameField.getText(), whyField.getText());
                if (err != null) setStatus(err);
            }
        });
        menu.add(save);
        java.util.List<telamin.fluxtion.audit.analyser.analyser.config.FocusSpec> list = namedFocuses.get();
        if (!list.isEmpty()) {
            menu.addSeparator();
            for (var f : list) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(f.name() + " (" + f.nodeIds().size() + ")");
                item.setToolTipText(f.rationale().isBlank() ? null : f.rationale());
                item.addActionListener(e -> {
                    String err = recallFocus(f.name());
                    if (err != null) setStatus(err);
                });
                menu.add(item);
            }
            javax.swing.JMenu delete = new javax.swing.JMenu("Delete");
            for (var f : list) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(f.name());
                item.addActionListener(e -> {
                    namedFocuses.get().removeIf(x -> x.name().equals(f.name()));
                    focusesChanged.run();
                    setStatus("deleted focus '" + f.name() + "'");
                });
                delete.add(item);
            }
            menu.addSeparator();
            menu.add(delete);
        }
        menu.show(focusPicker, 0, focusPicker.getHeight());
    }

    private javax.swing.JButton crumbButton(String text, int depth) {
        javax.swing.JButton b = new javax.swing.JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusable(false);
        b.setToolTipText(depth == 0 ? "Back to the full graph" : "Back to this context");
        b.addActionListener(e -> {
            focusStack.popTo(depth);
            applyView(false);
            refreshCrumbs();
        });
        return b;
    }

    /**
     * Back to the plain graph: nothing selected, nothing focused, and <b>no cycle shading</b>, so every
     * node is at full strength.
     *
     * <p>Dropping the cycle shading as well is the point. Selection dimming and execution dimming look
     * the same on screen, so clearing only the selection leaves a graph that is still half-faded and
     * gives no way to see the whole thing plainly. Stepping restores the shading on the next keypress,
     * so nothing is lost — {@link #stepBy} re-applies the cycle when it finds it cleared.
     */
    private void clearHighlights() {
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        canvas.select(null);
        shadingCleared = true;
        canvas.setDispatch(List.of(), List.of(), false);
        canvas.clearCursor();
        applyView();
    }

    /** True while the cycle shading has been deliberately dropped; the next step brings it back. */
    private boolean shadingCleared;
    /** The record last shaded, by identity — so a repeated notification is not mistaken for a change. */
    private LogRecord lastShownRecord;
    /** Looks up the finding for a record in the filtered view; null when nothing supplies findings. */
    private java.util.function.IntFunction<telamin.fluxtion.audit.analyser.analyser.report.Finding>
            findingProvider;

    /**
     * Stop a {@link javax.swing.JSplitPane} eating the arrow keys.
     *
     * <p>Its look-and-feel binds Up/Down/Left/Right to move the divider, in the
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} map. Key lookup walks <em>up</em> from the focused
     * component, so the split — sitting between the canvas and this panel — is consulted first and the
     * step keys never fire. Adding the split for the source pane silently broke stepping this way.
     *
     * <p>Shadowing with a name no {@code ActionMap} defines makes {@code processKeyBinding} return false
     * and continue up the hierarchy, rather than consuming the key. The divider still moves by dragging,
     * and F6/F8 still work.
     */
    private static void releaseArrowKeys(JComponent component) {
        javax.swing.InputMap keys = component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        for (String arrow : new String[]{"UP", "DOWN", "LEFT", "RIGHT"}) {
            keys.put(javax.swing.KeyStroke.getKeyStroke(arrow), "topology-arrow-passthrough");
        }
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

    /** Ask for a {@code .graphml} and load it. Public because the File menu owns opening, not the tab. */
    public void chooseFile() {
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

    /**
     * The zoom/pan to restore once a topology is loaded. Held rather than applied, because loading a
     * graph fits it to the view — applying the saved transform first would simply be overwritten. It is
     * used once and then forgotten, so a later load fits normally instead of jumping to where a different
     * graph happened to be scrolled.
     */
    public void setSavedView(double zoom, double panX, double panY, String orientation) {
        this.pendingZoom = zoom;
        this.pendingPanX = panX;
        this.pendingPanY = panY;
        if ("LEFT_RIGHT".equals(orientation)) {
            canvas.setOrientation(LayeredLayout.Orientation.LEFT_RIGHT);
            orientationButton.setText("Top→down");
        }
    }

    public double zoom() {
        return canvas.zoom();
    }

    public double panX() {
        return canvas.panX();
    }

    public double panY() {
        return canvas.panY();
    }

    public String orientationName() {
        return canvas.orientation().name();
    }

    private double pendingZoom;
    private double pendingPanX;
    private double pendingPanY;

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
    /**
     * Drop the loaded graph entirely (M35.1) — the counterpart {@link #load} never had. Also clears
     * everything DERIVED from it, because a graph-shaped hole with live shading in it is exactly the
     * half-cleared state this milestone exists to prevent.
     */
    public void clearGraph() {
        loadedFrom = null;
        graphSource = telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE;
        pairingPart = null;
        fullTopology = telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology.empty();
        focusStack = new FocusStack(fullTopology);
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        cursor = StepCursor.over(java.util.List.of());
        canvas.setClassificationTopology(fullTopology);
        index.setTopology(fullTopology);
        refreshCrumbs();
        applyView(false);
        setStatus("No graph loaded — open a .graphml to see the topology.");
    }

    /**
     * Drop only what the LOG contributed (M35.1): the per-record execution shading and the step
     * cursor. The graph itself is a separate artefact with its own lifetime — closing a log must not
     * silently discard a topology the user opened deliberately.
     */
    public void clearExecution() {
        cursor = StepCursor.over(java.util.List.of());
        applyView(true);
    }

    public Path loadedGraphFile() {
        return loadedFrom;
    }

    /**
     * Is a graph loaded AT ALL? (M34.2) — {@link #loadedGraphFile} answers "from which file", and a
     * source-supplied graph has none by design, so callers that ask the file question to mean
     * "is there a graph" understate what the app is holding. That is the mirror of the defect M35
     * spent itself on: not claiming a graph it lacks, but disowning one it has.
     */
    public boolean hasGraph() {
        return graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE;
    }

    /** M37: the file the graph was OPENED from, absolute — null for a source-supplied graph, which has none. */
    public String graphPath() {
        return loadedFrom == null ? null : loadedFrom.toAbsolutePath().normalize().toString();
    }

    /** How to name the loaded graph — its file, or where the source said it came from. */
    public String graphLabel() {
        if (loadedFrom != null) return loadedFrom.getFileName().toString();
        return hasGraph() ? graphSource.describe : null;
    }

    /**
     * M34.2 — tell the view whether position within a cycle means anything. Drives the ordinal badge
     * and the step-through wording together, so the picture and the words cannot disagree. Reset to
     * true when the log closes: the caveat described THAT source (review M34 F3).
     */
    public void setOrderMeaningful(boolean meaningful) {
        this.orderMeaningful = meaningful;
        canvas.setOrderMeaningful(meaningful);
        renderStatus();
    }

    private boolean orderMeaningful = true;

    /** Where the loaded graph came from (M34.1) — NONE until something loads one. */
    private telamin.fluxtion.audit.analyser.analyser.topology.GraphSource graphSource =
            telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE;

    public telamin.fluxtion.audit.analyser.analyser.topology.GraphSource graphSource() {
        return graphSource;
    }

    /**
     * Drop a graph the SOURCE supplied, leaving one somebody OPENED alone (review M34 F1). A reader's
     * graph is derived from its log — when that log closes or another arrives, keeping it is the app
     * describing the previous log's structure over the current one's records.
     *
     * @return true if a source-supplied graph was cleared
     */
    public boolean clearSourceGraph() {
        if (graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.READER_DECLARED
                && graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.READER_INFERRED) {
            return false;
        }
        clearGraph();
        return true;
    }

    /**
     * Load a graph the SOURCE supplied (M34.1). Yields to anything a person or agent opened — the
     * precedence lives in {@link telamin.fluxtion.audit.analyser.analyser.topology.GraphSource}, and
     * the asymmetry is M35.3's: intent beats convenience.
     *
     * @return true if it took the slot
     */
    public boolean loadFromSource(telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.SourceGraph g) {
        var candidate = telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.of(g.provenance());
        if (!graphSource.replacedBy(candidate)) return false;
        fullTopology = telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology.of(
                g.nodes(), g.edges());
        loadedFrom = null;                       // it came from the log, not a file the user can point at
        graphSource = candidate;
        focusStack = new FocusStack(fullTopology);
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        cursor = StepCursor.over(java.util.List.of());
        canvas.setClassificationTopology(fullTopology);
        index.setTopology(fullTopology);
        refreshCrumbs();
        applyView(false);
        setStatus(fullTopology.nodeCount() + " nodes, " + fullTopology.edgeCount() + " edges · "
                + candidate.describe
                + (candidate.supportsCoverage() ? ""
                        : " — coverage cannot find a dead node in a graph built from what ran"));
        return true;
    }

    public void load(Path file) {
        ProcessorTopology topology = GraphMlParser.parse(file);
        if (topology.isEmpty()) {
            setStatus("Could not read a topology from " + file.getFileName()
                           + " — is it a Fluxtion .graphml?");
            return;
        }
        loadedFrom = file;
        graphSource = telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.OPENED;
        fullTopology = topology;
        focusStack = new FocusStack(topology);
        refreshCrumbs();
        selection.clear();
        scope = TopologyFocus.Scope.NODE;
        canvas.setClassificationTopology(fullTopology);
        // the FULL graph: the index is how you reach a node the filters have hidden
        index.setTopology(fullTopology);
        applyView(false);
        if (pendingZoom > 0) {
            canvas.setViewState(pendingZoom, pendingPanX, pendingPanY);
            pendingZoom = 0;                        // restore once; later loads fit as usual
        }
        setStatus(summary(topology, file));
        topologyLoaded.accept(file);
    }

    /**
     * The click cycle (M22.2). Clicking the <b>same</b> node again widens the scope one step; clicking a
     * different one starts over at that node; Cmd/Ctrl-click adds to or removes from the selection, and
     * leaves the scope where it is so a multi-node scope can be built up.
     *
     * <p>Package-private: this IS the mouse path (the canvas listener delegates straight here), and the
     * escalation cycle broke once without any test able to see it — {@code TopologyClickEscalationTest}
     * drives it headlessly.
     */
    void onNodeClicked(String id, boolean additive) {
        if (id == null) {
            clearHighlights();
            return;
        }
        if (additive) {
            if (!selection.remove(id)) selection.add(id);
        } else if (selection.size() == 1 && selection.contains(id)) {
            scope = scope.next();
        } else {
            selection.clear();
            selection.add(id);
            // Inside a context the scope is a width the user chose — clicking another node means
            // "show me THAT one at this width". At the full graph, resetting is harmless because
            // nothing is filtered, so the cycle starts fresh as it should.
            if (focusStack.atFull()) {
                scope = TopologyFocus.Scope.NODE;
            }
        }
        applyView();
        syncSourceTo(id);
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
        // P2 (review): scopedIds() is the ONLY writer of lastRoutes, and it is skipped when nothing is
        // selected — so without this the topology echo goes on reporting scopeBounded/scopeNote for a
        // selection that no longer exists. The status line was already safe (it returns early on an
        // empty selection); the agent-facing surface was not, which is the half that matters.
        if (selection.isEmpty()) lastRoutes = null;
        java.util.Set<String> scoped = selection.isEmpty() ? null : scopedIds();

        // the WORLD is the focus context (M27): hiding comes from the filter stack, dimming from the
        // selection's scope — two different statements, never conflated again
        java.util.Set<String> visible = TopologyFocus.visible(fullTopology, scaffoldingBox.isSelected(),
                focusStack.atFull() ? null : focusStack.world());
        // A different node set is a different LAYOUT, so the old zoom and pan address coordinates that no
        // longer exist — keeping them leaves the user staring at empty space where the graph used to be.
        // Preserve the view only while the visible set is unchanged; otherwise reframe.
        boolean sameNodes = visible.equals(shownNodes);
        shownNodes = java.util.Set.copyOf(visible);
        canvas.setClassificationTopology(fullTopology);
        canvas.setTopology(fullTopology.subgraph(visible), keepView && sameNodes);
        canvas.setEmphasis(scoped == null ? java.util.Set.of() : scoped);
        canvas.setSelectedNodes(selection);
        index.setSelection(selection);

        // the canvas cleared its shading when the graph changed — put the current cycle back, unless the
        // user has just asked to see the plain graph
        if (!shadingCleared && !cycle.isEmpty() && cursor.record() != null) {
            showCycleOf(cursor.record());
            canvas.setCursor(cursor.currentInstanceId(), cursor.steppedSoFar(), cursor.atEntry());
        }
        updateScopeLabel(scoped);
        refreshStatus();
    }

    /**
     * The status line is assembled from independent parts, each owned by a different concern: what is
     * happening, where the step cursor is, what is selected, and what the filters are hiding.
     *
     * <p>They live here rather than in the toolbar because a toolbar is for <b>controls</b>. Readouts
     * mixed in among buttons make it hard to find either — the position label and the scope label were
     * two moving strings between a slider and a play button — and they also make the toolbar's width
     * jump as the text changes.
     */
    private void setStatus(String text) {
        statusBase = text == null ? " " : text;
        renderStatus();
    }

    /** Re-render after a filter change, keeping whatever the line was saying. */
    private void refreshStatus() {
        renderStatus();
    }

    private void renderStatus() {
        StringBuilder sb = new StringBuilder(statusBase == null ? " " : statusBase);
        appendPart(sb, stepPart);
        appendPart(sb, scopePart);
        appendPart(sb, pairingPart);      // M35.6 — persistent, because it qualifies everything below
        if (!orderMeaningful) {
            appendPart(sb, "⚠ ARRIVAL ORDER, NOT DISPATCH ORDER — this source declares no order "
                    + "within a cycle, so position here is not causality");
        }
        if (!graphSource.supportsCoverage()
                && graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE) {
            // M34.2: on a graph INFERRED from what ran, every node logged by construction, so
            // "did not run" and "not on this path" can never appear — and their absence reads as
            // "nothing was missed". That is coverage's 100% wearing a different hat.
            appendPart(sb, "⚠ this graph was " + graphSource.describe
                    + " — every node here ran by construction, so \"did not run\" and \"not on this "
                    + "path\" cannot appear and their absence proves nothing");
        }
        sb.append(viewNote());
        status.setText(sb.toString());
    }

    /**
     * M35.6 — whether this graph fits the open log, stated HERE and permanently.
     *
     * <p>The main window's status bar already says it at load time, and that was not enough: it is
     * written by 32 call sites, so the pairing evaporates on the next filter change. Yet this panel
     * is exactly where a mismatched graph does its damage — the execution shading, the step-through
     * order and the coverage figures are all derived from it. So the qualification lives beside the
     * thing it qualifies, and it survives every other status update because {@link #renderStatus}
     * composes it in rather than overwriting.
     *
     * <p>Null or blank clears it — a graph with no log to judge against makes no claim either way.
     */
    public void setPairingNote(String note) {
        this.pairingPart = note == null || note.isBlank() ? null : note;
        renderStatus();
    }

    private String pairingPart;

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (sb.length() > 0 && !sb.toString().isBlank()) sb.append("   ·   ");
        sb.append(part.strip());
    }

    private String statusBase = " ";
    private String stepPart = "";
    private String scopePart = "";

    /**
     * Whether the loaded processor can write an audit log at all (M40) — a fact about the GRAPH, so it
     * is answerable with no log open, which is the whole point: the failure it catches produces nothing
     * to examine afterwards.
     */
    public telamin.fluxtion.audit.analyser.analyser.topology.AuditReadiness auditReadiness() {
        return telamin.fluxtion.audit.analyser.analyser.topology.AuditReadiness.of(
                hasTopology() ? fullTopology : null);
    }

    /**
     * The selection's scope in the current world, honouring the routes bound (H4). ROUTES goes through
     * {@link FocusStack#routesInWorld} so the answer carries WHETHER it was bounded; the other scopes are
     * unchanged.
     */
    private java.util.Set<String> scopedIds() {
        if (scope == TopologyFocus.Scope.ROUTES) {
            lastRoutes = focusStack.routesInWorld(selection, boundRoutesBox.isSelected());
            return lastRoutes.ids();
        }
        lastRoutes = null;
        return focusStack.expandInWorld(selection, scope);
    }

    private void updateScopeLabel(java.util.Set<String> scoped) {
        if (selection.isEmpty()) {
            scopePart = "";
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(selection.size() == 1 ? selection.iterator().next()
                        : selection.size() + " selected")
          .append(" · ").append(scope.label());
        if (lastRoutes != null && lastRoutes.bounded()) {
            // H4: say that the answer was bounded, by how much, and what the unbounded one would be —
            // a scope that quietly showed less than its name promised would be the opposite of a focus
            sb.append(" within ").append(lastRoutes.hops()).append(" hops — all routes would be ")
              .append(lastRoutes.unboundedSize()).append(" of ").append(focusStack.world().size())
              .append(" nodes; untick '≤").append(TopologyFocus.ROUTE_HOP_BOUND).append(" hops' for all");
        }
        sb.append(" · ").append(scoped == null ? 0 : scoped.size()).append(" node(s)");
        sb.append(" · click again to widen");
        if (!focusStack.atFull()) sb.append(" · within ").append(focusStack.world().size()).append("-node context");
        scopePart = sb.toString();
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
        // boundary honesty (M27): a context must never quietly misrepresent a propagation as contained.
        // The shading is computed on the full graph; if the shown cycle ran through nodes this context
        // cannot show, say so in words right where the person is reading.
        if (!focusStack.atFull() && !cycle.isEmpty()) {
            java.util.Set<String> ran = new java.util.LinkedHashSet<>();
            for (NodeLog n : cycle) ran.add(n.instanceId());
            int outside = focusStack.outsideWorld(ran).size();
            if (outside > 0) parts.add(outside + " node(s) of this cycle ran OUTSIDE this view");
        }
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

    /**
     * Open a node's source <b>beside the graph</b> rather than in the Source tab.
     *
     * <p>Reading a dispatch means holding two things at once — which node ran, and what its method does —
     * and the Source tab is a sibling of this one, so going there hides the thing you navigated from.
     * The embedded pane keeps both on screen with a divider you can drag. It falls back to the Source tab
     * only when no source service has been bound.
     */
    /** Select a node and widen its scope one step — the same as two clicks, for scripted screenshots. */
    public void selectForDocs(String id) {
        onNodeClicked(id, false);
        onNodeClicked(id, false);
    }

    // ---- control surface (assistant verbs; see llm.AppControl) -------------------------------------

    /** Select a node, or clear the selection with null. Does not change the scope. */
    public void selectNode(String id) {
        if (id == null) {
            clearHighlights();
            return;
        }
        selection.clear();
        selection.add(id);
        // the same scope rule the mouse path uses — inside a context the width is a deliberate choice
        // and survives a new selection; at the full graph the cycle starts fresh. Two paths with two
        // rules is how a scripted session and a hand-driven one stop agreeing.
        if (focusStack.atFull()) {
            scope = TopologyFocus.Scope.NODE;
        }
        canvas.select(id);
        canvas.centreOn(id);
        applyView();
        // the scripted path must behave like the mouse path, or a verb-driven session and a hand-driven
        // one diverge — which is exactly what makes scripted screenshots stop matching reality
        syncSourceTo(id);
    }

    /**
     * The application nodes this topology declares — scaffolding excluded.
     *
     * <p>The denominator for node coverage. Framework nodes are left out on purpose: they are not the
     * author's code and reporting them as uncovered would be noise in the one report whose value depends
     * on people reading every line of it.
     */
    private java.util.function.Function<String, java.util.Optional<String>> sourceResolver;

    /**
     * FQN → source text, or null when no source is configured. M40.2b's coverage denominator needs it
     * to prove a node cannot log; with no resolver every node stays counted, which is the safe way to
     * be wrong.
     */
    public java.util.function.Function<String, java.util.Optional<String>> sourceResolver() {
        return sourceResolver;
    }

    /** The whole graph, for callers that need node KINDS as well as ids (M40.2's coverage denominator). */
    public telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology fullTopology() {
        return fullTopology;
    }

    public java.util.Set<String> authoredNodeIds() {
        return telamin.fluxtion.audit.analyser.analyser.topology.Scaffolding.authoredNodes(fullTopology);
    }

    /** The kind and class of a node, for a report that needs to explain what it is naming. */
    public telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology.Node nodeInfo(String id) {
        return fullTopology.node(id);
    }

    public boolean hasNode(String id) {
        return id != null && fullTopology.contains(id);
    }

    /** Set the scope directly, rather than by repeated clicking. */
    public void setScope(TopologyFocus.Scope newScope) {
        if (newScope == null) return;
        scope = newScope;
        applyView();
    }

    /**
     * Lift or restore the routes hop bound from the action socket (review P1).
     *
     * <p>The bound shipped as a checkbox, on by default, and the verb path runs through it — so
     * {@code topology {scope: "routes"}} started returning a three-hop answer with no way for the
     * caller to ask for the whole thing. The echo said the unbounded answer was "one untick away in
     * the Topology toolbar", which is a remedy only a human at a keyboard can perform; offering it to
     * a process is the defect M35.7 and review N2 both name.
     *
     * <p>The CHECKBOX stays the single source of truth rather than a parallel field, so the two
     * surfaces cannot disagree about what is being shown — and a human watching the screen sees the
     * box change when an agent lifts the bound, which is the honest outcome.
     */
    public void setRouteBound(boolean bounded) {
        if (boundRoutesBox.isSelected() == bounded) return;
        boundRoutesBox.setSelected(bounded);
        applyView();
    }

    public void setFocus(boolean on) {
        if (on) {
            pushFocus();
        } else {
            focusStack.popToFull();
            applyView(false);
            refreshCrumbs();
        }
    }

    /** Step back out of focus contexts: one level, or all of them. The verb's {@code pop}. */
    public void popFocus(boolean toFull) {
        if (toFull) {
            focusStack.popToFull();
        } else if (!focusStack.pop()) {
            return;
        }
        applyView(false);
        refreshCrumbs();
    }

    public void setScaffoldingVisible(boolean on) {
        scaffoldingBox.setSelected(on);
        applyView();
    }

    /**
     * Show or hide the source pane. Showing it with a node selected also <b>opens that node</b> — asking
     * for the source view is asking to see some source, and an empty pane beside the graph answers
     * nothing.
     */
    public void setSourcePaneVisible(boolean on) {
        if (on && !selection.isEmpty()) {
            openSource(selection.iterator().next());
            return;
        }
        showSourcePane(on);
    }

    public void setOrientation(LayeredLayout.Orientation orientation) {
        if (orientation == null || canvas.orientation() == orientation) return;
        toggleOrientation();
    }

    public void fit() {
        canvas.fitToView();
    }

    public void clearView() {
        clearHighlights();
    }

    /** Move the step cursor by {@code n} rows; negative steps back. Returns how many steps were taken. */
    public int step(int n) {
        int taken = 0;
        for (int i = 0; i < Math.abs(n); i++) {
            boolean before = cursor.atEntry();
            int record = cursor.recordIndex();
            stepBy(n > 0 ? 1 : -1);
            if (cursor.recordIndex() == record && cursor.atEntry() == before && cursor.rowCount() > 0
                    && !canStillMove(n)) {
                break;
            }
            taken++;
        }
        return taken;
    }

    private boolean canStillMove(int direction) {
        return direction > 0 ? cursor.canNext() : cursor.canPrev();
    }

    public void moveToRecord(int index) {
        if (cursor.isEmpty()) return;
        shadingCleared = false;
        syncing = true;
        try {
            cursor.moveToRecord(index);
            showCycleOf(cursor.record());
            recordChanged.accept(cursor.recordIndex());
        } finally {
            syncing = false;
        }
        syncCursor();
    }

    /** A machine-readable snapshot of where the cursor is — the echo an assistant verb returns. */
    public java.util.Map<String, Object> cursorState() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("recordIndex", cursor.recordIndex());
        out.put("rowIndex", cursor.rowIndex());
        // M34.2 — an agent reads the echo, not the canvas. The suppressed badge is invisible to it,
        // so the qualification has to travel in the data or the agent will read rowIndex as order.
        out.put("orderMeaningful", orderMeaningful);
        out.put("graphSource", graphSource.name());
        // M40: the audit verdict lives beside graphPairing in `context`, NOT here — it is a fact about
        // the loaded graph rather than about the step cursor, and one fact deserves one home. It was
        // briefly in both after two sessions fixed review F1 from different ends.
        //   (the reviewer's half of that fix — hoisting this whole block above context()'s fresh-start
        //    early return — is kept: it repairs every topology fact on a fresh start, not just this one)
        if (!graphSource.supportsCoverage()
                && graphSource != telamin.fluxtion.audit.analyser.analyser.topology.GraphSource.NONE) {
            out.put("executionCaveat", "this graph was " + graphSource.describe + ": every node in "
                    + "it ran by construction, so an absence of \"did not run\" nodes is not "
                    + "evidence that nothing was missed");
        }
        if (!orderMeaningful) {
            out.put("orderCaveat", "this source declares no order within a cycle: rowIndex is "
                    + "ARRIVAL order, not dispatch order. Do not read it as causality.");
        }
        out.put("atEntry", cursor.atEntry());
        out.put("rowCount", cursor.rowCount());
        out.put("position", cursor.positionLabel());
        out.put("currentNode", cursor.currentInstanceId());
        out.put("selected", List.copyOf(selection));
        out.put("scope", scope.name().toLowerCase(java.util.Locale.ROOT));
        out.put("routeBound", boundRoutesBox.isSelected());   // readable state, not only a consequence
        if (lastRoutes != null && lastRoutes.bounded()) {
            // H4: an agent reads the echo, not the checkbox — a scope that covers less than its name
            // says must say so in the data too
            out.put("scopeBounded", lastRoutes.hops());
            out.put("scopeNote", "'routes' was bounded to " + lastRoutes.hops() + " hops because all routes "
                    + "would cover " + lastRoutes.unboundedSize() + " of " + focusStack.world().size()
                    + " nodes — a sink's routes are the graph. For every route, call again with "
                    + "topology {routeBound: false}; the '≤" + TopologyFocus.ROUTE_HOP_BOUND
                    + " hops' box in the Topology toolbar is the same switch.");
        }
        out.put("focus", !focusStack.atFull());
        out.put("context", focusStack.breadcrumb());
        out.put("contextDepth", focusStack.depth());
        out.put("scaffolding", scaffoldingBox.isSelected());
        out.put("syncSource", syncButton.isSelected());
        out.put("visibleNodes", canvas.topology().nodeCount());
        out.put("totalNodes", fullTopology.nodeCount());
        out.put("callout", canvas.isCalloutVisible());
        // echo the finding actually on screen, so a caller that has just written one can confirm the
        // graph is showing it rather than assume it
        var finding = canvas.callout();
        if (finding != null && !finding.isEmpty()) {
            java.util.Map<String, Object> f = new java.util.LinkedHashMap<>();
            if (finding.hasNote()) f.put("note", finding.note());
            if (finding.hasFix()) f.put("fix", finding.fix());
            out.put("finding", f);
        }
        return out;
    }

    /**
     * Whether the source pane <b>follows</b> selection and stepping.
     *
     * <p>Off, the pane is yours: it stays where you put it and only an explicit navigation — Enter, the
     * right-click menu, a double-click in the index — moves it. That distinction is the point. Automatic
     * tracking is what you want while exploring, and exactly what you do not want while reading one
     * method and clicking around the graph to work out what calls it.
     */
    public boolean isSourceSyncOn() {
        return syncButton.isSelected();
    }

    public void setSourceSync(boolean on) {
        syncButton.setSelected(on);
    }

    /**
     * Follow a node into the source, but only if tracking is on and the pane is already open. It never
     * opens the pane: having a panel appear because you clicked a box would be the view rearranging
     * itself under you, which is what the toggle exists to prevent.
     */
    private void syncSourceTo(String instanceId) {
        if (instanceId == null || !syncButton.isSelected() || openSourcePane() == null) return;
        openSource(instanceId);
    }

    /** The embedded source viewer when it is on screen, else null — the caller decides what that means. */
    public SourcePanel openSourcePane() {
        return embeddedSource != null && graphSplit.getRightComponent() == embeddedSource
                ? embeddedSource : null;
    }

    /** Open a node's source in the embedded pane, from outside the panel. */
    public void openSourceFor(String instanceId) {
        openSource(instanceId);
    }

    private void openSource(String instanceId) {
        if (embeddedSource != null) {
            showSourcePane(true);
            // Fill the processor half on FIRST use only — Split exists to show the call site and the
            // method together, and half of it sitting empty defeats the point. Re-navigating it on every
            // sync would yank its scroll back to the class declaration each time you clicked a node,
            // which is precisely the flicker tracking is supposed to avoid.
            if (!embeddedSource.hasProcessorOpen()) embeddedSource.showSelectedProcessor();
            // An EVENT node has no class of its own in the graph — what you want to see is where the
            // processor dispatches it, which is its handleEvent overload. Sending it down the node path
            // instead just reports "no source mapping", which is true and unhelpful.
            ProcessorTopology.Node node = fullTopology.node(instanceId);
            if (node != null && node.kind() == ProcessorTopology.Kind.EVENT) {
                embeddedSource.openEventHandler(node.simpleName());
            } else {
                embeddedSource.openInstance(instanceId, null);
            }
            return;
        }
        if (sourceOpener != null) sourceOpener.accept(instanceId, null);
    }

    /**
     * Give the topology its own source viewer. Shares the caller's {@link SourceService}, so the
     * processor selection and source roots are the ones already configured — not a second set.
     */
    public void bindSource(SourceService service) {
        if (service == null || embeddedSource != null) return;
        embeddedSource = new SourcePanel();
        embeddedSource.bind(service);
        embeddedSource.setMinimumSize(new java.awt.Dimension(220, 80));
        // Seed the dropdown if choices were already published before the pane existed.
        if (!lastProcessorFqns.isEmpty()) embeddedSource.setProcessors(lastProcessorFqns, lastSelectedProcessor);
        sourceButton.setEnabled(true);
    }

    /**
     * Mirror the event-processor choices into the embedded split-view source pane's dropdown. The main
     * Source tab and this pane are separate {@link SourcePanel} instances; without this call the
     * embedded dropdown is never populated (its selected processor still works via the shared
     * SourceService, but the list to switch between them stays empty).
     */
    public void setEmbeddedProcessors(java.util.List<String> fqns, String selected) {
        lastProcessorFqns = fqns == null ? java.util.List.of() : fqns;
        lastSelectedProcessor = selected;
        if (embeddedSource != null) embeddedSource.setProcessors(lastProcessorFqns, selected);
    }

    private void showSourcePane(boolean show) {
        if (embeddedSource == null) return;
        sourceButton.setSelected(show);
        boolean wasShowing = graphSplit.getRightComponent() == embeddedSource;
        if (show) {
            if (graphSplit.getRightComponent() != embeddedSource) {
                graphSplit.setRightComponent(embeddedSource);
                // half and half the first time; after that whatever the user dragged it to
                graphSplit.setDividerLocation(dividerAt > 0 ? dividerAt : Math.max(240, getWidth() / 2));
            }
        } else {
            if (graphSplit.getRightComponent() != null) dividerAt = graphSplit.getDividerLocation();
            graphSplit.setRightComponent(null);
        }
        graphSplit.revalidate();
        graphSplit.repaint();
        // Re-fit ONLY when the pane actually appeared or disappeared. Fitting unconditionally meant that
        // with Sync on, every node click ran openSource -> showSourcePane(true) -> fitToView, throwing
        // away the zoom the user had set. The canvas only needs reframing when its width changed.
        if (wasShowing != show) {
            javax.swing.SwingUtilities.invokeLater(canvas::fitToView);
        }
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

        // Selecting a DIFFERENT record is a new cycle and re-shades. Being told about the SAME one again
        // is not a user action at all — the table re-fires its selection on a re-filter, on a repaint, on
        // being handed back focus — and treating that as "shade it again" is what made Show all appear to
        // work only sometimes: the clear was undone by an event the user never caused.
        boolean sameRecord = record != null && record == lastShownRecord;
        lastShownRecord = record;
        if (!sameRecord) shadingCleared = false;

        cycle = record == null ? List.of() : record.nodeLogs();
        List<String> order = new ArrayList<>(cycle.size());
        for (NodeLog n : cycle) order.add(n.instanceId());
        List<String> entries = record == null ? List.of()
                : List.copyOf(EntryPointResolver.resolve(
                        fullTopology, record.event(), record.eventToString()));
        if (!shadingCleared) {
            canvas.setDispatch(order, entries,
                    record != null && AuditTrace.tracesEveryInvocation(record.nodeLogs()));
        }
        if (record == null) {
            cursor = StepCursor.over(List.of());
        } else if (recordSource != null && recordSource.size() > 0) {
            cursor = new StepCursor(recordSource);
            cursor.moveToRecord(Math.max(0, filteredIndex));
        } else {
            cursor = StepCursor.over(List.of(record));
        }
        updateStepControls();
        refreshCallout();
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
        if (shadingCleared) {
            // the user cleared the view, then asked to step again: restore the cycle before moving, or
            // the first keypress would silently do half of what the second one does
            shadingCleared = false;
            showCycleOf(cursor.record());
        }
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
        shadingCleared = false;
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
        if (shadingCleared) {                        // asking for the cycle is asking for it to be shaded
            shadingCleared = false;
            showCycleOf(cursor.record());
        }
        cursor.moveToRecord(cursor.recordIndex());   // back to the entry: the whole cycle, nothing current
        canvas.select(null);
        syncCursor();
    }

    /** Push the cursor position into the canvas and the status line. */
    private void syncCursor() {
        refreshCallout();
        canvas.setCursor(cursor.currentInstanceId(), cursor.steppedSoFar(), cursor.atEntry());
        String id = cursor.currentInstanceId();
        rowChanged.accept(id, occurrenceOfCurrentRow());
        if (id != null) canvas.select(id);
        syncSourceTo(id);
        setStatus(cursor.atEntry()
                ? cursor.positionLabel() + describeEntry()
                : cursor.positionLabel() + "  ·  " + cursor.rowSummary()
                  + (fullTopology.contains(id) ? "" : "   [not in this topology]"));
        updateStepControls();
    }

    /**
     * Put the current record's finding on the canvas.
     *
     * <p>Pull, not push. The panel asks "is there a finding for the record I am showing?" every time the
     * cursor moves, rather than being told when one is written — so a flag added while the topology is on
     * screen, a flag added from the assistant, and a flag that was already there all arrive by the same
     * path. A push would need three call sites and would eventually be missing one.
     */
    private void refreshCallout() {
        canvas.setCallout(findingProvider == null ? null : findingProvider.apply(cursor.recordIndex()));
    }

    /** Re-read the finding for the record on screen — call after one is written, edited or cleared. */
    public void refreshFinding() {
        refreshCallout();
    }

    /**
     * Two pictures of one cycle, for a report.
     *
     * @param trace      only the nodes this event actually reached — the answer to "what ran?"
     * @param wholeGraph the entire processor with those nodes lit — the answer to "and what didn't?"
     */
    public record CycleViews(java.awt.image.BufferedImage trace,
                             java.awt.image.BufferedImage wholeGraph,
                             String wholeNote) {
        public CycleViews(java.awt.image.BufferedImage trace, java.awt.image.BufferedImage wholeGraph) {
            this(trace, wholeGraph, null);
        }
    }

    /**
     * Above this many nodes the "whole graph" report picture is not a picture of anything (polish H6):
     * checked against a 309-node graph, the estate fitted to a 1200×800 frame rendered at 8% zoom as
     * a grey horizontal band with the lit nodes as specks. So beyond this size the second view shows the
     * cycle's nodes AND THEIR NEIGHBOURS — the unlit nodes adjacent to the path, which is where "the
     * check never fired" is actually visible — and the caption counts what was left out.
     */
    static final int REPORT_WHOLE_GRAPH_MAX = 60;

    /**
     * Render the cycle for a report, <b>offscreen</b>.
     *
     * <p>Deliberately not a screenshot of this panel. Capturing the live view meant the report inherited
     * whatever zoom, pan and toolbar happened to be on screen — usually a focused subgraph adrift in a
     * field of empty canvas, because the user had zoomed in on one node. It also meant the act of
     * exporting could only be made to look right by changing what the user was looking at.
     *
     * <p>A detached canvas costs one layout pass and leaves this panel untouched, so the export is
     * side-effect free and the picture is framed for the page rather than for the window.
     *
     * <p>Two views because they answer different questions, and the second is the one people forget to
     * ask. The trace shows what ran. The whole graph shows what that cycle <em>didn't</em> reach — which
     * is exactly the evidence for "the stock check never fired" or "this node is downstream of the wrong
     * thing". A trace alone cannot show an absence.
     */
    public CycleViews renderCycleViews(LogRecord record, int width, int height) {
        if (!hasTopology() || record == null) {
            return new CycleViews(null, null);
        }
        List<NodeLog> logs = record.nodeLogs();
        List<String> order = new ArrayList<>(logs.size());
        for (NodeLog n : logs) order.add(n.instanceId());
        List<String> entries = List.copyOf(
                EntryPointResolver.resolve(fullTopology, record.event(), record.eventToString()));
        boolean traced = AuditTrace.tracesEveryInvocation(logs);

        java.util.Set<String> touched = new java.util.LinkedHashSet<>();
        for (String id : order) if (fullTopology.contains(id)) touched.add(id);
        for (String id : entries) if (fullTopology.contains(id)) touched.add(id);

        // the trace view is meaningless with nothing in it — a record whose nodes are all from a
        // different build would otherwise render an empty box captioned "the cycle"
        java.awt.image.BufferedImage trace = touched.isEmpty() ? null
                : paintOffscreen(fullTopology.subgraph(touched), order, entries, traced,
                        java.util.Set.of(), width, height);

        // honour the scaffolding toggle: a bird's-eye view padded with framework nodes is harder to read,
        // and the user has already said whether they want to see them
        java.util.Set<String> all =
                TopologyFocus.visible(fullTopology, scaffoldingBox.isSelected(), null);
        java.util.Set<String> shown = all;
        String note = null;
        if (all.size() > REPORT_WHOLE_GRAPH_MAX && !touched.isEmpty()) {
            // H6: the estate would be a grey band. Show the path and what sits beside it instead.
            shown = new java.util.LinkedHashSet<>();
            for (String id : TopologyFocus.expand(fullTopology, touched, TopologyFocus.Scope.NEIGHBOURS)) {
                if (all.contains(id)) shown.add(id);
            }
            int hidden = all.size() - shown.size();
            note = "The processor has " + all.size() + " nodes — too many to read in one picture — so this "
                    + "shows the cycle's nodes and their immediate neighbours (" + shown.size() + " nodes); "
                    + "+" + hidden + " nodes not shown. Grey nodes here are the ones next to the path "
                    + "that this event did not reach.";
        }
        java.awt.image.BufferedImage whole = paintOffscreen(
                fullTopology.subgraph(shown), order, entries, traced, touched, width, height);

        return new CycleViews(trace, whole, note);
    }

    /**
     * How far a report picture may magnify to fill its frame. Bounded rather than unlimited: a two-node
     * cycle scaled until it fits looks like a diagram of something important, and the reader deserves to
     * see that it is two boxes.
     */
    private static final double REPORT_MAX_ZOOM = 2.2;

    /** A canvas that is never added to this window: configured, sized, fitted, painted, discarded. */
    private java.awt.image.BufferedImage paintOffscreen(
            ProcessorTopology shown, List<String> order, List<String> entries, boolean traced,
            java.util.Set<String> emphasis, int width, int height) {
        TopologyCanvas off = new TopologyCanvas();
        off.setSpacing(spacingPercent() / 100.0);
        off.setLabelSize(textSize());
        off.setOrientation(canvas.orientation());
        // classification stays pinned to the FULL graph, exactly as on screen: what the log establishes
        // about a node must not change with how much of the graph a picture happens to show
        off.setClassificationTopology(fullTopology);
        off.setTopology(shown);
        // H5: the page has no hover. A label that elides to "Category…" on screen is recoverable there
        // and meaningless in a PDF, so the exported picture's boxes grow to fit their labels.
        off.fitNodeWidthToLabels();
        off.setDispatch(order, entries, traced);
        off.setEmphasis(emphasis);
        off.setSize(width, height);
        off.doLayout();
        // fill the frame: a report's picture has a fixed box and nothing else can use the space, so the
        // on-screen 1:1 ceiling would leave a four-node trace as a stamp in the middle of a white page
        off.fitToView(REPORT_MAX_ZOOM);

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        off.paint(g);
        g.dispose();
        return img;
    }

    /**
     * Where the callout's text comes from: the record's flag note and suggested fix, looked up by the
     * cursor's position in the <b>filtered</b> view. The caller owns that translation, because it is the
     * only layer that knows how the table maps view rows to records.
     */
    public void setFindingProvider(
            java.util.function.IntFunction<telamin.fluxtion.audit.analyser.analyser.report.Finding> provider) {
        this.findingProvider = provider;
        refreshCallout();
    }

    /** Hide or show the callout without discarding the finding — it is still there when toggled back. */
    public void setCalloutVisible(boolean visible) {
        canvas.setCalloutVisible(visible);
    }

    public boolean isCalloutVisible() {
        return canvas.isCalloutVisible();
    }

    /** The finding currently drawn, or null — so a report export takes exactly what is on screen. */
    public telamin.fluxtion.audit.analyser.analyser.report.Finding shownFinding() {
        return canvas.callout();
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
        stepPart = stepping ? headerText(records) : "";
        renderStatus();
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
            sb.append(orderMeaningful ? "step " : "logged ")
                    .append(cursor.rowIndex() + 1).append(" / ").append(rows);
        }
        return sb.toString();
    }

    /**
     * Wire the node tooltip to class documentation. Takes a resolver from FQN to source text — the panel
     * caches per class, because a tooltip fires on every hover and reading a file each time would make
     * moving the mouse across a graph do filesystem work.
     */
    public void setSourceResolver(java.util.function.Function<String, java.util.Optional<String>> resolver) {
        this.sourceResolver = resolver;
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
        // the embedded viewer holds its own theme-derived colours; MainFrame only knew about the Source
        // tab's panel, so this one kept the previous theme's palette after a switch
        if (embeddedSource != null) embeddedSource.refresh();
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
        displayPrefsChanged.run();
    }
}
