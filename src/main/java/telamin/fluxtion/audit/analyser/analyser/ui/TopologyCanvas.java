package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.topology.LayeredLayout;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;
import telamin.fluxtion.audit.analyser.analyser.topology.TopologyLayout;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Paints a {@link TopologyLayout} (M21.3): the processor graph, pan/zoom/hover/select, theme-aware.
 *
 * <p>Owns no layout. Geometry is computed once by {@link LayeredLayout} when the topology changes and
 * cached; repaint only transforms and draws, which is what keeps panning smooth on a graph of a few
 * hundred nodes. Off-screen boxes are culled, so cost tracks what you can see rather than graph size.
 *
 * <p>Screen = world × {@code scale} + offset. Everything interactive works in world coordinates and
 * converts at the edges, so zoom never accumulates error in the model.
 */
public final class TopologyCanvas extends JPanel {

    /**
     * Labels appear once a box is at least this wide <em>on screen</em>. Keyed to rendered pixels, not to
     * the zoom factor: what decides whether a name is readable is how many pixels it has, and a zoom
     * threshold gets that wrong the moment the node size changes.
     */
    private static final double LABEL_MIN_BOX_PX = 54;
    private static final double MIN_SCALE = 0.08;
    private static final double MAX_SCALE = 4.0;

    private ProcessorTopology topology = ProcessorTopology.empty();
    private TopologyLayout layout = TopologyLayout.empty();
    private LayeredLayout.Config config = LayeredLayout.Config.defaults();

    private double scale = 1.0;
    private double offsetX;
    private double offsetY;

    private String hoveredId;
    private String selectedId;

    /** Ids that <b>logged</b> in the shown cycle, in dispatch order; empty when no record is shown. */
    private List<String> dispatch = List.of();
    /** id → its first position in {@link #dispatch}, for the ordinal badge. */
    private java.util.Map<String, Integer> firedAt = java.util.Map.of();
    /** Where this cycle entered the graph, if the record says. */
    private List<String> entryPoints = List.of();
    /** id → what the log lets us claim about it this cycle. Empty when no record is shown. */
    private java.util.Map<String, ProcessorTopology.Execution> execution = java.util.Map.of();
    /** The node the step cursor is on, or null (at a record's entry, or no cursor). */
    private String cursorNode;
    /** Nodes stepped through in this cycle so far, in order and including repeats. */
    private List<String> steppedNodes = List.of();
    /** True when the cursor sits at the record's entry, before any row. */
    private boolean cursorAtEntry;

    private Point dragOrigin;
    private double dragOffsetX;
    private double dragOffsetY;

    private Consumer<String> nodeSelected = id -> { };
    private Consumer<String> nodeActivated = id -> { };
    private java.util.function.BiConsumer<String, java.awt.Point> contextMenu = (id, at) -> { };

    private record Point(int x, int y) { }

    public TopologyCanvas() {
        setOpaque(true);
        setPreferredSize(new Dimension(640, 420));
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                dragOrigin = new Point(e.getX(), e.getY());
                dragOffsetX = offsetX;
                dragOffsetY = offsetY;
                if (maybeShowMenu(e)) return;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    TopologyLayout.NodeBox hit = boxAt(e.getX(), e.getY());
                    String id = hit == null ? null : hit.id();
                    // Cmd on macOS, Ctrl elsewhere (plain Ctrl-click is the popup trigger on macOS),
                    // Shift as a second spelling everywhere
                    int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
                    boolean additive = (e.getModifiersEx() & menuMask) != 0 || e.isShiftDown();
                    if (id != null) {
                        select(id);
                        nodeClicked.accept(id, additive);
                    } else {
                        // A press on the background is also how a pan starts, and a pan must not be read
                        // as "clear everything". Held until release, and dropped if the mouse moves.
                        pendingBackgroundClick = true;
                        pendingAdditive = additive;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null) return;
                pendingBackgroundClick = false;   // this is a pan, not a click
                offsetX = dragOffsetX + (e.getX() - dragOrigin.x());
                offsetY = dragOffsetY + (e.getY() - dragOrigin.y());
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOrigin = null;
                setCursor(Cursor.getDefaultCursor());
                maybeShowMenu(e);   // popup trigger fires on press on some platforms, release on others
                if (pendingBackgroundClick) {
                    pendingBackgroundClick = false;
                    select(null);
                    nodeClicked.accept(null, pendingAdditive);
                } else {
                    viewChanged.run();      // the drag that just ended was a pan
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                TopologyLayout.NodeBox hit = boxAt(e.getX(), e.getY());
                String id = hit == null ? null : hit.id();
                if (!java.util.Objects.equals(id, hoveredId)) {
                    hoveredId = id;
                    setCursor(id == null ? Cursor.getDefaultCursor()
                            : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredId != null) {
                    hoveredId = null;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(e.getX(), e.getY(), Math.pow(1.1, -e.getPreciseWheelRotation()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    // ---- model ------------------------------------------------------------------------------------

    /** Lay out and show a topology. Fits to the view, because an unfitted graph opens off-screen. */
    public void setTopology(ProcessorTopology topology) {
        setTopology(topology, false);
    }

    /**
     * @param keepView keep the current zoom/pan and selection. Set when the change is a <b>filter</b>
     *                 rather than a new graph: re-fitting on every scaffolding or focus toggle throws
     *                 away where the user had navigated to, which makes the filters unusable for the
     *                 exploring they exist to support.
     */
    public void setTopology(ProcessorTopology topology, boolean keepView) {
        double scale0 = scale;
        double ox = offsetX;
        double oy = offsetY;
        String keptSelection = selectedId;

        this.topology = topology == null ? ProcessorTopology.empty() : topology;
        this.layout = LayeredLayout.layout(this.topology, config);
        this.hoveredId = null;
        this.selectedId = null;
        if (keepView) {
            scale = scale0;
            offsetX = ox;
            offsetY = oy;
            if (keptSelection != null && this.topology.contains(keptSelection)) selectedId = keptSelection;
            repaint();
        } else {
            fitToView();
        }
    }

    /**
     * The graph execution is classified against, when the <b>shown</b> graph is a filtered view of it.
     *
     * <p>Classification must not depend on what is currently visible. {@code classifyCycle} reasons from
     * parents and reachability — hiding a node would change the answer for its neighbours, so toggling
     * scaffolding off could turn a RAN_SILENTLY into a MAY_HAVE_RUN and the view would quietly claim
     * something different about the same log. What is drawn is a display choice; what the log establishes
     * is not.
     */
    public void setClassificationTopology(ProcessorTopology full) {
        this.classifyAgainst = full;
    }

    private ProcessorTopology classifyAgainst;

    private boolean pendingBackgroundClick;
    private boolean pendingAdditive;
    private final java.util.Set<String> emphasis = new java.util.LinkedHashSet<>();
    /** The nodes actually clicked. Distinct from {@link #emphasis}, which is what their scope reaches. */
    private final java.util.Set<String> selectedNodes = new java.util.LinkedHashSet<>();

    public ProcessorTopology topology() {
        return topology;
    }

    /** Re-space the layout. Re-runs the layout, keeping zoom and pan so the view does not jump. */
    public void setSpacing(double factor) {
        double scale0 = scale;
        double ox = offsetX;
        double oy = offsetY;
        config = config.withSpacing(factor);
        layout = LayeredLayout.layout(topology, config);
        scale = scale0;
        offsetX = ox;
        offsetY = oy;
        repaint();
    }

    /**
     * Label point size. Kept independent of zoom: labels that scale with zoom read well while zoomed in
     * and become unreadable when zoomed out, which is when you most need to know what you are looking at.
     */
    public void setLabelSize(float points) {
        this.labelPoints = Math.max(7f, Math.min(22f, points));
        repaint();
    }

    public float labelSize() {
        return labelPoints;
    }

    private float labelPoints = 11f;

    public void setOrientation(LayeredLayout.Orientation orientation) {
        config = config.withOrientation(orientation);
        layout = LayeredLayout.layout(topology, config);
        fitToView();
    }

    public LayeredLayout.Orientation orientation() {
        return config.orientation();
    }

    LayeredLayout.Config config() {
        return config;
    }

    // ---- step-through (M21.4) ---------------------------------------------------------------------

    /**
     * Show a cycle: the ids that <b>wrote audit output</b>, in dispatch order.
     *
     * <p>Everything else is classified by {@link ProcessorTopology#classifyCycle}, not simply dimmed.
     * A node with no audit entry may still have executed — nodes log only if they write audit output,
     * and only at the level in force — so the view distinguishes "ran, said nothing" from "might have
     * run" from "not on this path" instead of implying the log is a complete record of execution.
     */
    public void setDispatch(List<String> dispatchOrder) {
        setDispatch(dispatchOrder, List.of());
    }

    /**
     * As {@link #setDispatch(List)}, plus where the cycle entered the graph — which lets a branch that
     * executed while logging nothing show as unknown rather than as unrelated to the event.
     */
    public void setDispatch(List<String> dispatchOrder, List<String> entryPoints) {
        setDispatch(dispatchOrder, entryPoints, false);
    }

    /**
     * As above, plus whether the record traces every invocation — when it does, absence from the log is
     * proof a node did not run, and the view says so instead of hedging.
     */
    public void setDispatch(List<String> dispatchOrder, List<String> entryPoints, boolean traced) {
        this.dispatch = dispatchOrder == null ? List.of() : List.copyOf(dispatchOrder);
        this.entryPoints = entryPoints == null ? List.of() : List.copyOf(entryPoints);
        java.util.Map<String, Integer> ordinals = new java.util.LinkedHashMap<>();
        for (int i = 0; i < this.dispatch.size(); i++) {
            ordinals.putIfAbsent(this.dispatch.get(i), i);
        }
        this.firedAt = ordinals;
        ProcessorTopology basis = classifyAgainst != null ? classifyAgainst : topology;
        this.execution = this.dispatch.isEmpty() && this.entryPoints.isEmpty()
                ? java.util.Map.of()
                : basis.classifyCycle(this.dispatch, this.entryPoints, traced);
        this.cursorNode = null;
        this.steppedNodes = List.of();
        this.cursorAtEntry = true;
        repaint();
    }

    /** What the log lets us claim about this node in the shown cycle; null when no cycle is shown. */
    public ProcessorTopology.Execution executionOf(String id) {
        return execution.get(id);
    }

    /**
     * Place the step cursor (M21.10 S2). Painted <b>over</b> the execution shading, never instead of it:
     * the cursor says where you are, the shading says what the log establishes, and losing the second to
     * show the first would undo the fix this view exists for.
     *
     * @param currentId  the node under the cursor, or null at the record's entry
     * @param stepped    nodes already stepped in this cycle, in order (repeats kept)
     * @param atEntry    true when sitting at the entry, before any row
     */
    public void setCursor(String currentId, List<String> stepped, boolean atEntry) {
        this.cursorNode = currentId;
        this.steppedNodes = stepped == null ? List.of() : List.copyOf(stepped);
        this.cursorAtEntry = atEntry;
        repaint();
    }

    /** Clear the cursor overlay, leaving the execution shading alone. */
    /**
     * Nodes to keep at full strength, dimming the rest — the focus scope, shown <b>without</b> hiding
     * anything. Dimming rather than hiding is the honest default while exploring: a node you cannot see
     * is easily read as a node that is not there, and the topology's whole job is telling those apart.
     * The Focus toggle is what actually removes them.
     */
    public void setEmphasis(java.util.Collection<String> ids) {
        emphasis.clear();
        if (ids != null) emphasis.addAll(ids);
        repaint();
    }

    /**
     * The clicked nodes. Marked <b>positively</b> — a brighter fill and a heavy accent ring — rather than
     * by dimming everything else: "what did I select" should be answerable by looking at the selection,
     * not by comparing the whole graph against itself.
     */
    public void setSelectedNodes(java.util.Collection<String> ids) {
        selectedNodes.clear();
        if (ids != null) selectedNodes.addAll(ids);
        repaint();
    }

    public java.util.Set<String> emphasis() {
        return java.util.Set.copyOf(emphasis);
    }

    public void clearCursor() {
        setCursor(null, List.of(), false);
    }

    public String cursorNode() {
        return cursorNode;
    }

    public List<String> dispatch() {
        return dispatch;
    }

    private boolean showingCycle() {
        return !dispatch.isEmpty();
    }

    /**
     * Whether this edge could have carried dispatch <b>in the cycle being shown</b> — both ends ran.
     *
     * <p>Highlighting every edge touching the current node is right when no cycle is on screen, and wrong
     * the moment one is: an edge from a node that did not run is drawn as though the event came in that
     * way. Stepping into {@code quotePublisher} on an order cycle lit its {@code QuoteControl} edge, which
     * says an operator called the service — the one thing that definitely did not happen.
     *
     * <p>{@link ProcessorTopology.Execution#MAY_HAVE_RUN} is deliberately excluded. It means the log does
     * not say, and a highlighted arrow is an assertion.
     */
    private boolean carriedDispatch(String source, String target) {
        if (!showingCycle()) return true;
        return ran(source) && ran(target);
    }

    private boolean ran(String id) {
        ProcessorTopology.Execution state = execution.get(id);
        return state == ProcessorTopology.Execution.LOGGED
               || state == ProcessorTopology.Execution.RAN_SILENTLY;
    }

    /** Called on every left click: {@code (id, additive)} — additive means add to / remove from a set. */
    public void onNodeClicked(java.util.function.BiConsumer<String, Boolean> listener) {
        this.nodeClicked = listener == null ? (id, add) -> { } : listener;
    }

    private java.util.function.BiConsumer<String, Boolean> nodeClicked = (id, add) -> { };

    /** Called with the selected node id, or {@code null} when the selection is cleared. */
    public void onNodeSelected(Consumer<String> listener) {
        this.nodeSelected = listener == null ? id -> { } : listener;
    }

    /** Called on double-click — the hook M21.5 hangs "go to source" on. */
    public void onNodeActivated(Consumer<String> listener) {
        this.nodeActivated = listener == null ? id -> { } : listener;
    }

    /** Called with the node id and the click point when a context menu is requested on a node. */
    public void onNodeContextMenu(java.util.function.BiConsumer<String, java.awt.Point> listener) {
        this.contextMenu = listener == null ? (id, at) -> { } : listener;
    }

    /** Selects the node under a popup-trigger click and asks for a menu. True if it handled the event. */
    private boolean maybeShowMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) return false;
        TopologyLayout.NodeBox hit = boxAt(e.getX(), e.getY());
        if (hit == null) return false;
        select(hit.id());
        contextMenu.accept(hit.id(), e.getPoint());
        return true;
    }

    public String selected() {
        return selectedId;
    }

    public void select(String id) {
        if (java.util.Objects.equals(id, selectedId)) return;
        selectedId = id;
        repaint();
        nodeSelected.accept(id);
    }

    // ---- view -------------------------------------------------------------------------------------

    /** Scale and centre so the whole graph is visible with a small margin. */
    public void fitToView() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || layout.isEmpty()) {
            scale = 1.0;
            offsetX = offsetY = 0;
            repaint();
            return;
        }
        double margin = 24;
        double sx = (w - 2 * margin) / Math.max(1, layout.width());
        double sy = (h - 2 * margin) / Math.max(1, layout.height());
        scale = clamp(Math.min(sx, sy), MIN_SCALE, 1.0);   // never zoom past 1:1 just to fill space
        offsetX = (w - layout.width() * scale) / 2;
        offsetY = (h - layout.height() * scale) / 2;
        repaint();
    }

    /**
     * Pan so a node sits in the middle of the view, without changing the zoom.
     *
     * <p>Zoom is left alone deliberately: picking a name from the index is a request to <em>go there</em>,
     * not to change how much of the graph you can see, and a jump that also rescales loses the context
     * you had. Already-visible nodes are still centred, so the gesture behaves the same wherever the node
     * happens to be.
     */
    public void centreOn(String id) {
        if (id == null || layout.isEmpty()) return;
        TopologyLayout.NodeBox box = layout.box(id);
        if (box == null) return;               // filtered out of the current view
        offsetX = getWidth() / 2.0 - (box.x() + box.width() / 2) * scale;
        offsetY = getHeight() / 2.0 - (box.y() + box.height() / 2) * scale;
        repaint();
    }

    public double zoom() {
        return scale;
    }

    public double panX() {
        return offsetX;
    }

    public double panY() {
        return offsetY;
    }

    /** Restore a saved zoom and pan. Ignored when the values are not a usable view. */
    public void setViewState(double zoom, double panX, double panY) {
        if (zoom <= 0 || Double.isNaN(zoom) || Double.isNaN(panX) || Double.isNaN(panY)) return;
        scale = clamp(zoom, MIN_SCALE, MAX_SCALE);
        offsetX = panX;
        offsetY = panY;
        repaint();
    }

    /**
     * Told when the user has changed the view — after a zoom, or when a pan finishes. Deliberately not
     * during a drag: a pan fires continuously, and persisting on every pixel would write the config file
     * hundreds of times for one gesture.
     */
    public void onViewChanged(Runnable listener) {
        this.viewChanged = listener == null ? () -> { } : listener;
    }

    private Runnable viewChanged = () -> { };

    public void zoomIn() {
        zoomAt(getWidth() / 2, getHeight() / 2, 1.2);
    }

    public void zoomOut() {
        zoomAt(getWidth() / 2, getHeight() / 2, 1 / 1.2);
    }

    /** Zoom about a screen point, so the thing under the cursor stays under the cursor. */
    private void zoomAt(int px, int py, double factor) {
        double next = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        if (next == scale) return;
        double worldX = (px - offsetX) / scale;
        double worldY = (py - offsetY) / scale;
        scale = next;
        offsetX = px - worldX * scale;
        offsetY = py - worldY * scale;
        repaint();
        viewChanged.run();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private TopologyLayout.NodeBox boxAt(int px, int py) {
        return layout.at((px - offsetX) / scale, (py - offsetY) / scale);
    }

    /**
     * Supplies a node's class documentation for the tooltip, keyed by class name. Optional: without it the
     * tooltip is unchanged, which is what happens when no source root reaches the class.
     */
    public void setDocLookup(java.util.function.Function<ProcessorTopology.Node, String> lookup) {
        this.docLookup = lookup;
    }

    private java.util.function.Function<ProcessorTopology.Node, String> docLookup;

    @Override
    public String getToolTipText(MouseEvent e) {
        TopologyLayout.NodeBox box = boxAt(e.getX(), e.getY());
        if (box == null) return null;
        ProcessorTopology.Node node = topology.node(box.id());
        if (node == null) return null;
        String className = node.className() == null ? "" : "<br>" + node.className();
        String claim = "";
        ProcessorTopology.Execution ran = execution.get(box.id());
        if (ran != null) claim = "<br><br>" + describe(ran);
        String doc = "";
        if (docLookup != null) {
            String summary = docLookup.apply(node);
            // the class javadoc says what the node IS; the execution claim says what it DID this cycle.
            // Both belong here and neither substitutes for the other.
            if (summary != null && !summary.isBlank()) {
                doc = "<br><br>" + escape(summary);
            }
        }
        return "<html><b>" + node.id() + "</b>" + className
               + "<br><i>" + node.kind().name().toLowerCase().replace('_', ' ') + "</i>"
               + doc + claim + "</html>";
    }

    /** The tooltip is HTML, and a javadoc summary can legitimately contain angle brackets. */
    private static String escape(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        // wrap: a long first sentence otherwise makes a tooltip as wide as the screen
        return "<div width=\"320\">" + safe + "</div>";
    }

    /** Plain words for a claim — the colour alone must never be what tells a user this. */
    public static String describe(ProcessorTopology.Execution ran) {
        return switch (ran) {
            case LOGGED -> "logged audit output in this cycle";
            case RAN_SILENTLY -> "ran, but logged nothing — something it feeds did log";
            case MAY_HAVE_RUN -> "may have run — it logged nothing, and the log cannot say either way";
            case OFF_PATH -> "not on this event's path";
            case DID_NOT_RUN -> "did not run — this log records every node invocation";
        };
    }

    // ---- painting ---------------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        boolean dark = ThemeManager.isDark();
        // one shared content surface with the record-detail and source panels: the graph is a document,
        // and at 0xF6F8FA it sat within a shade of FlatLaf's light panel grey and lost its own edge
        Color canvas = UiTheme.surface();
        Color edge = dark ? new Color(0x545D68) : new Color(0x9AA5B1);
        Color edgeHot = dark ? new Color(0x6CB6FF) : new Color(0x1F6FEB);
        Color text = dark ? new Color(0xC9D1D9) : new Color(0x24292F);
        Color muted = dark ? new Color(0x8B949E) : new Color(0x6E7781);

        g.setColor(canvas);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (layout.isEmpty()) {
            g.setColor(muted);
            String msg = "No topology loaded — open the processor's .graphml to see the node graph.";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            g.dispose();
            return;
        }

        Rectangle2D visible = visibleWorldRect();
        paintEdges(g, visible, edge, edgeHot);
        paintNodes(g, visible, dark, text, muted);
        paintHud(g, muted);
        g.dispose();
    }

    /** The world rectangle currently on screen — everything outside it is skipped. */
    private Rectangle2D visibleWorldRect() {
        return new Rectangle2D.Double(
                -offsetX / scale, -offsetY / scale, getWidth() / scale, getHeight() / scale);
    }

    private void paintEdges(Graphics2D g, Rectangle2D visible, Color normal, Color hot) {
        String focus = selectedId != null ? selectedId : hoveredId;
        g.setStroke(new BasicStroke((float) Math.max(1, 1.1 * scale)));
        for (TopologyLayout.EdgePath path : layout.edges()) {
            boolean incident = focus != null
                               && (focus.equals(path.source()) || focus.equals(path.target()))
                               && carriedDispatch(path.source(), path.target());
            List<TopologyLayout.Point> pts = path.points();
            if (!incident && !touches(visible, pts)) continue;

            // Stop the line at the box border rather than its centre. Drawn to the centre, the last
            // segment — and the arrowhead on it — is painted over by the node and the graph loses all
            // sense of direction.
            TopologyLayout.Point first = trimToBox(layout.box(path.source()), pts.get(0), pts.get(1));
            TopologyLayout.Point last = trimToBox(
                    layout.box(path.target()), pts.get(pts.size() - 1), pts.get(pts.size() - 2));

            Path2D.Double shape = new Path2D.Double();
            shape.moveTo(sx(first.x()), sy(first.y()));
            for (int i = 1; i < pts.size() - 1; i++) {
                shape.lineTo(sx(pts.get(i).x()), sy(pts.get(i).y()));
            }
            shape.lineTo(sx(last.x()), sy(last.y()));
            g.setColor(incident ? hot : normal);
            g.draw(shape);
            paintArrowHead(g, pts.get(pts.size() - 2), last);
        }
    }

    /**
     * Where the line from {@code centre} toward {@code toward} leaves {@code box}. Falls back to the
     * centre when there is no box (a bend point, or an edge naming a node the document never declared).
     */
    private static TopologyLayout.Point trimToBox(TopologyLayout.NodeBox box,
                                                  TopologyLayout.Point centre,
                                                  TopologyLayout.Point toward) {
        if (box == null) return centre;
        double cx = box.centerX();
        double cy = box.centerY();
        double dx = toward.x() - cx;
        double dy = toward.y() - cy;
        if (dx == 0 && dy == 0) return centre;
        double halfW = box.width() / 2;
        double halfH = box.height() / 2;
        double tx = dx == 0 ? Double.MAX_VALUE : halfW / Math.abs(dx);
        double ty = dy == 0 ? Double.MAX_VALUE : halfH / Math.abs(dy);
        double t = Math.min(tx, ty);
        return new TopologyLayout.Point(cx + dx * t, cy + dy * t);
    }

    private boolean touches(Rectangle2D visible, List<TopologyLayout.Point> pts) {
        for (TopologyLayout.Point p : pts) {
            if (visible.contains(p.x(), p.y())) return true;
        }
        return false;
    }

    /** A small filled triangle at the target end, so dispatch direction is readable at a glance. */
    private void paintArrowHead(Graphics2D g, TopologyLayout.Point from, TopologyLayout.Point to) {
        double angle = Math.atan2(to.y() - from.y(), to.x() - from.x());
        // kept close to constant on screen: direction has to stay readable when zoomed out, which is
        // exactly when a head scaled with the graph would vanish
        double size = 9 * Math.max(0.85, Math.min(1.3, scale));
        double tipX = sx(to.x());
        double tipY = sy(to.y());
        Path2D.Double head = new Path2D.Double();
        head.moveTo(tipX, tipY);
        head.lineTo(tipX - size * Math.cos(angle - Math.PI / 7), tipY - size * Math.sin(angle - Math.PI / 7));
        head.lineTo(tipX - size * Math.cos(angle + Math.PI / 7), tipY - size * Math.sin(angle + Math.PI / 7));
        head.closePath();
        g.fill(head);
    }

    private void paintNodes(Graphics2D g, Rectangle2D visible, boolean dark, Color text, Color muted) {
        boolean labels = config().nodeWidth() * scale >= LABEL_MIN_BOX_PX;
        g.setFont(getFont().deriveFont(labelPoints));
        FontMetrics fm = g.getFontMetrics();

        for (TopologyLayout.NodeBox box : layout.boxes()) {
            if (!visible.intersects(box.x(), box.y(), box.width(), box.height())) continue;   // cull

            ProcessorTopology.Node node = topology.node(box.id());
            boolean isSelected = box.id().equals(selectedId) || selectedNodes.contains(box.id());
            boolean isHovered = box.id().equals(hoveredId);
            // in the scope but not itself clicked — reached BY the selection rather than part of it
            boolean inScope = !emphasis.isEmpty() && emphasis.contains(box.id()) && !isSelected;

            Integer ordinal = firedAt.get(box.id());
            boolean fired = ordinal != null;
            boolean isCurrentStep = box.id().equals(cursorNode);
            boolean isStepped = !isCurrentStep && steppedNodes.contains(box.id());
            boolean isEntry = cursorAtEntry && entryPoints.contains(box.id());
            ProcessorTopology.Execution ran = execution.get(box.id());
            // only "no reason to think dispatch came near it" recedes; a silent node that demonstrably
            // ran, or might have, stays fully legible
            // Two dimming reasons exist and they must not compound. Execution shading answers "what did
            // this cycle do"; scope shading answers "what is in my selection". A node that RAN is the one
            // thing the cycle view exists to show, so a selection made earlier must not fade it out —
            // otherwise the node with the green ring and the ordinal badge is drawn as though it were
            // barely there. Evidence outranks the selection; entry points too, for the same reason.
            boolean hasCycleEvidence = ran == ProcessorTopology.Execution.LOGGED
                                       || ran == ProcessorTopology.Execution.RAN_SILENTLY
                                       || isEntry;
            boolean outOfScope = !emphasis.isEmpty() && !emphasis.contains(box.id()) && !hasCycleEvidence;
            boolean dimmed = outOfScope || showingCycle() && (ran == ProcessorTopology.Execution.OFF_PATH
                                            || ran == ProcessorTopology.Execution.DID_NOT_RUN);

            double x = sx(box.x());
            double y = sy(box.y());
            double w = box.width() * scale;
            double h = box.height() * scale;
            java.awt.Shape shape = shapeFor(node, x, y, w, h);

            Color fill = fillFor(node, dark);
            g.setColor(dimmed ? fade(fill, dark)
                    : isSelected ? towards(fill, accent(dark), dark ? 0.34 : 0.20)
                    : fill);
            g.fill(shape);

            Color border = isSelected ? accent(dark)
                    : fired ? firedBorder(dark)
                    : ran == ProcessorTopology.Execution.RAN_SILENTLY ? ranSilentlyBorder(dark)
                    : dimmed ? fade(borderFor(dark, false), dark)
                    : borderFor(dark, isHovered);
            g.setColor(border);
            // MAY_HAVE_RUN is drawn dashed: the log genuinely does not say whether dispatch got here, and
            // a solid box would state more than the evidence does
            g.setStroke(ran == ProcessorTopology.Execution.MAY_HAVE_RUN && !isSelected
                    ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                            new float[]{5f, 4f}, 0f)
                    : new BasicStroke(isSelected ? 2.4f : fired ? 1.8f
                            : ran == ProcessorTopology.Execution.RAN_SILENTLY ? 1.6f
                            : isHovered ? 1.8f : 1f));
            g.draw(shape);

            if (fired && labels) paintOrdinal(g, x, y, ordinal + 1, dark);
            if (inScope) paintHalo(g, x, y, w, h, dark, HaloStyle.SCOPE);
            if (isSelected) paintHalo(g, x, y, w, h, dark, HaloStyle.SELECTION);
            if (isEntry) paintHalo(g, x, y, w, h, dark, HaloStyle.ENTRY);
            if (isStepped) paintHalo(g, x, y, w, h, dark, HaloStyle.TRAIL);
            if (isCurrentStep) paintHalo(g, x, y, w, h, dark, HaloStyle.CURRENT);

            if (!labels) continue;
            String title = node == null ? box.id() : node.simpleName();
            String subtitle = box.id();
            g.setColor(dimmed ? fade(text, dark) : text);
            drawClipped(g, fm, title, x + 8 * scale,
                    y + h / 2 - (subtitle.equals(title) ? -fm.getAscent() / 2.0 : 1), w - 16 * scale);
            if (!subtitle.equals(title)) {
                g.setColor(dimmed ? fade(muted, dark) : muted);
                drawClipped(g, fm, subtitle, x + 8 * scale, y + h / 2 + fm.getHeight() - 2, w - 16 * scale);
            }
        }
    }

    /** Draw text, eliding with an ellipsis rather than spilling out of the box. */
    private void drawClipped(Graphics2D g, FontMetrics fm, String s, double x, double y, double maxWidth) {
        if (maxWidth <= 8) return;
        String out = s;
        if (fm.stringWidth(out) > maxWidth) {
            while (out.length() > 1 && fm.stringWidth(out + "…") > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            out = out + "…";
        }
        g.drawString(out, (float) x, (float) y);
    }

    private enum HaloStyle { CURRENT, TRAIL, ENTRY, SELECTION, SCOPE }

    /**
     * Cursor halos, drawn <b>outside</b> the node so it keeps its own execution border and fill.
     * Recolouring the border would hide what the log establishes in order to show where you are
     * standing — two different questions, both wanted at once while stepping.
     */
    private void paintHalo(Graphics2D g, double x, double y, double w, double h, boolean dark,
                           HaloStyle style) {
        double pad = switch (style) {
            case TRAIL -> 2.5;
            case SELECTION -> 3;
            case SCOPE -> 2;
            default -> 4;
        };
        RoundRectangle2D.Double halo = new RoundRectangle2D.Double(
                x - pad, y - pad, w + 2 * pad, h + 2 * pad, 10 * scale + pad, 10 * scale + pad);
        g.setColor(style == HaloStyle.TRAIL ? steppedHalo(dark)
                : style == HaloStyle.SCOPE ? fade2(accent(dark), dark, 0.6)
                : accent(dark));
        g.setStroke(switch (style) {
            case CURRENT -> new BasicStroke(2.6f);
            case SELECTION -> new BasicStroke(3f);
            case SCOPE -> new BasicStroke(1.6f);
            case TRAIL -> new BasicStroke(1.4f);
            case ENTRY -> new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{6f, 4f}, 0f);
        });
        g.draw(halo);
    }

    /** Trailing-halo colour: the accent, muted toward the canvas so "current" stays dominant. */
    private static Color steppedHalo(boolean dark) {
        return fade2(accent(dark), dark, 0.55);
    }

    private static Color fade2(Color c, boolean dark, double keep) {
        // one shared content surface with the record-detail and source panels: the graph is a document,
        // and at 0xF6F8FA it sat within a shade of FlatLaf's light panel grey and lost its own edge
        Color canvas = UiTheme.surface();
        return new Color(
                (int) (c.getRed() * keep + canvas.getRed() * (1 - keep)),
                (int) (c.getGreen() * keep + canvas.getGreen() * (1 - keep)),
                (int) (c.getBlue() * keep + canvas.getBlue() * (1 - keep)));
    }

    /**
     * <b>Shape carries the kind</b>, not just colour: an event is a stadium (something arriving), an
     * exported service a hexagon (a surface something calls into), everything that computes a rounded
     * rectangle. Shape survives greyscale, projectors and colour-blindness, which fill alone does not —
     * and the three roles read at a glance even when the labels are too small to draw.
     */
    private static java.awt.Shape shapeFor(ProcessorTopology.Node node, double x, double y,
                                           double w, double h) {
        ProcessorTopology.Kind kind = node == null ? ProcessorTopology.Kind.UNKNOWN : node.kind();
        return switch (kind) {
            case EVENT -> new RoundRectangle2D.Double(x, y, w, h, h, h);            // stadium
            case EXPORT_SERVICE -> hexagon(x, y, w, h);
            default -> new RoundRectangle2D.Double(x, y, w, h, Math.min(10, h / 4), Math.min(10, h / 4));
        };
    }

    /** A flat-topped hexagon: distinct from both the stadium and the rectangle at any size. */
    private static java.awt.Shape hexagon(double x, double y, double w, double h) {
        double notch = Math.min(h / 2, w / 5);
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + notch, y);
        p.lineTo(x + w - notch, y);
        p.lineTo(x + w, y + h / 2);
        p.lineTo(x + w - notch, y + h);
        p.lineTo(x + notch, y + h);
        p.lineTo(x, y + h / 2);
        p.closePath();
        return p;
    }

    /**
     * Node kinds are distinguished by fill too, so the graph's shape reads before any label does.
     *
     * <p>The plain {@code NODE} fill has to carry the most weight and had the least: white on a near-white
     * canvas, and one hex step from the canvas in dark mode, so an ordinary compute node — the commonest
     * thing in any graph — was a border floating on the background. It is now a distinct slate in both
     * themes. The tinted kinds are unchanged; they were never the problem.
     */
    private static Color fillFor(ProcessorTopology.Node node, boolean dark) {
        ProcessorTopology.Kind kind = node == null ? ProcessorTopology.Kind.UNKNOWN : node.kind();
        return switch (kind) {
            case EVENT -> dark ? new Color(0x4B4526) : new Color(0xFFF8C5);
            case EVENT_HANDLER -> dark ? new Color(0x24455C) : new Color(0xDDF4FF);
            case EXPORT_SERVICE -> dark ? new Color(0x40355C) : new Color(0xF3E8FF);
            case NODE -> dark ? new Color(0x39424F) : new Color(0xE9EEF4);
            case UNKNOWN -> dark ? new Color(0x4A525E) : new Color(0xDDE2E8);
        };
    }

    /**
     * The border has to stay visible against the fill it outlines. In dark mode the old value was darker
     * than the (now lighter) fills, so raising the fills for contrast against the canvas would have made
     * the outline vanish into them — the two colours have to move together.
     */
    private static Color borderFor(boolean dark, boolean hovered) {
        if (hovered) return dark ? new Color(0xADB6C0) : new Color(0x57606A);
        return dark ? new Color(0x616C7A) : new Color(0xB6BFC9);
    }

    /** Blend {@code from} toward {@code to} by {@code amount} — used to tint a selected node's fill. */
    private static Color towards(Color from, Color to, double amount) {
        double a = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) (from.getRed() * (1 - a) + to.getRed() * a),
                (int) (from.getGreen() * (1 - a) + to.getGreen() * a),
                (int) (from.getBlue() * (1 - a) + to.getBlue() * a));
    }

    private static Color accent(boolean dark) {
        return dark ? new Color(0x6CB6FF) : new Color(0x1F6FEB);
    }

    /** Ring around a node that wrote audit output this cycle — the only observed state. */
    private static Color firedBorder(boolean dark) {
        return dark ? new Color(0x3FB950) : new Color(0x1A7F37);
    }

    /** Ran but said nothing: inferred with certainty from a logged descendant, so shown plainly. */
    private static Color ranSilentlyBorder(boolean dark) {
        return dark ? new Color(0x8B949E) : new Color(0x57606A);
    }

    /** Pull a colour toward the canvas so a node that never fired recedes without disappearing. */
    private static Color fade(Color c, boolean dark) {
        // one shared content surface with the record-detail and source panels: the graph is a document,
        // and at 0xF6F8FA it sat within a shade of FlatLaf's light panel grey and lost its own edge
        Color canvas = UiTheme.surface();
        double keep = 0.16;   // out-of-scope must recede, not merely soften
        return new Color(
                (int) (c.getRed() * keep + canvas.getRed() * (1 - keep)),
                (int) (c.getGreen() * keep + canvas.getGreen() * (1 - keep)),
                (int) (c.getBlue() * keep + canvas.getBlue() * (1 - keep)));
    }

    /** The node's position in dispatch order, in a small badge on its corner. */
    private void paintOrdinal(Graphics2D g, double x, double y, int ordinal, boolean dark) {
        double r = 9 * Math.max(0.8, Math.min(1.2, scale));
        double cx = x + r * 0.65;
        double cy = y + r * 0.65;
        g.setColor(firedBorder(dark));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - r / 2, cy - r / 2, r, r));
        g.setColor(dark ? new Color(0x0D1117) : Color.WHITE);
        java.awt.Font previous = g.getFont();
        g.setFont(previous.deriveFont(java.awt.Font.BOLD, (float) Math.max(8, r * 0.9)));
        FontMetrics fm = g.getFontMetrics();
        String s = String.valueOf(ordinal);
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + fm.getAscent() / 2.5));
        g.setFont(previous);
    }

    /** Corner readout: what you are looking at and how far in, plus the cycle legend when one is shown. */
    private void paintHud(Graphics2D g, Color muted) {
        g.setColor(muted);
        g.setFont(getFont().deriveFont(labelPoints));
        String hud = topology.nodeCount() + " nodes · " + topology.edgeCount() + " edges · "
                     + layout.layerCount() + " layers · " + Math.round(scale * 100) + "%";
        g.drawString(hud, 10, getHeight() - 10);
        if (showingCycle()) paintLegend(g);
    }

    /**
     * The legend is not decoration. Without it the greyed boxes read as "did not run", which the log does
     * not say — so the three claims are spelled out on screen wherever a cycle is shown.
     */
    private void paintLegend(Graphics2D g) {
        boolean dark = ThemeManager.isDark();
        boolean complete = execution.containsValue(ProcessorTopology.Execution.DID_NOT_RUN);
        String[] labels = complete
                ? new String[]{dispatch.size() + " ran", "did not run"}
                : new String[]{dispatch.size() + " logged", "ran, logged nothing",
                        "may have run", "not on this path"};
        Color[] colours = complete
                ? new Color[]{firedBorder(dark), fade(ranSilentlyBorder(dark), dark)}
                : new Color[]{firedBorder(dark), ranSilentlyBorder(dark),
                        ranSilentlyBorder(dark), fade(ranSilentlyBorder(dark), dark)};
        FontMetrics fm = g.getFontMetrics();
        int y = 18;
        int x = 12;
        for (int i = 0; i < labels.length; i++) {
            g.setColor(colours[i]);
            g.setStroke(!complete && i == 2
                    ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                            new float[]{4f, 3f}, 0f)
                    : new BasicStroke(i == 0 ? 1.8f : 1.4f));
            g.draw(new RoundRectangle2D.Double(x, y - 8, 16, 11, 4, 4));
            g.setColor(dark ? new Color(0x8B949E) : new Color(0x6E7781));
            g.drawString(labels[i], x + 22, y + 1);
            x += 22 + fm.stringWidth(labels[i]) + 16;
        }
    }

    private double sx(double worldX) {
        return worldX * scale + offsetX;
    }

    private double sy(double worldY) {
        return worldY * scale + offsetY;
    }
}
