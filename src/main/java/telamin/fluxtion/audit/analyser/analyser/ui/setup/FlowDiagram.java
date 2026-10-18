package telamin.fluxtion.audit.analyser.analyser.ui.setup;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Auth;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.ui.ThemeManager;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * M77-5 (spec-ai-setup-wizard.md §6.1a, OD-77.14–.21): <i>Where data goes</i> — a small picture under the SELECTED
 * Screen 1 card showing where data goes for that choice. Big shapes, few words; the card's sentence stays.
 *
 * <p>The picture is DATA first: {@link #model(AiSetupState, Option)} is a pure function of the shown option and the
 * published state (no Swing, no clock). The painter and the accessible description both read that one model, so a
 * screen reader hears what is drawn (★ T26). {@link #setSnapshot} only stores and repaints: it decides nothing (rule 9);
 * there is no node, fact or effect of its own. Every word is {@link AiSetupLines}' {@code DIAGRAM_*}; every colour a
 * {@link DiagramPalette} token. Java2D shapes and {@code drawString} only.
 *
 * <p><b>Two-way and one-way</b> (the owner's definition): Claude inside is two-way — a right-click Ask reaches it, and it
 * works the product back. Your own client is one-way — it pulls from the product, but nothing reaches it from a
 * right-click: you copy and paste. Amber (Full, caution) is drawn exactly when the posture is Full AND the picture draws
 * Claude. <i>Both</i> draws two rows, each with its own product icon (OD-77.21).
 */
public final class FlowDiagram extends JComponent {

    /** The option a picture shows (Screen 1's four cards). */
    public enum Option {
        NO_AI, CLIENT, INSIDE, BOTH;

        /** The card for a choice, or null for a choice with no picture (none yet, or the in-app assistant). */
        public static Option of(Choice c) {
            if (c == null) return null;
            return switch (c) {
                case NO_AI -> NO_AI;
                case CLIENT -> CLIENT;
                case INSIDE -> INSIDE;
                case BOTH -> BOTH;
                case NONE, IN_APP -> null;
            };
        }

        boolean drawsClaude() {
            return this == INSIDE || this == BOTH;
        }

        boolean drawsClient() {
            return this == CLIENT || this == BOTH;
        }
    }

    /** External access as §8.2's {@code externalAccess} states it. */
    public enum Access { OFF, CONFIRMING, ON, CLOSED_FOR_SESSION, OTHER_INSTANCE }

    public enum NodeId { ANALYSER, CLAUDE, CLIENT, CLAUDE_SERVICE, CLIENT_SERVICE }

    /** AUTO: the program does it (solid). BY_HAND: you do it (dashed). NOT_ON: not possible now (dotted). */
    public enum Line { AUTO, BY_HAND, NOT_ON }

    /** NORMAL, ACCENT (Full's amber), MUTED (a path that is not on). */
    public enum Tone { NORMAL, ACCENT, MUTED }

    /** An arrow's chip: its icon and its word. */
    public enum Chip { ASK, POINTS, YOU_COPY, OFF, CLOSED, ANOTHER_WINDOW, WHAT_IT_READS }

    /** A drawn node. {@code row} is 0 except for Both's client row (1), which has its own product icon. */
    public record Node(NodeId id, int row, String label, String shortLabel, String caption, String shortCaption) {
        boolean local() {
            return id != NodeId.CLAUDE_SERVICE && id != NodeId.CLIENT_SERVICE;
        }
    }

    /**
     * A drawn arrow: from → to in a row, its line, tone and chip (with its short form below 400 px), an optional second
     * word under it ({@code extra}: Full's "+ your own tools"), and the sentence a screen reader says for it.
     */
    public record Edge(NodeId from, NodeId to, int row, Line line, Tone tone, Chip chip, String chipText,
                       String chipShort, String extra, String extraShort, String spoken) {
        boolean outbound() {
            return to == NodeId.CLAUDE_SERVICE || to == NodeId.CLIENT_SERVICE;
        }
    }

    /** The picture as data: what the painter draws and the accessible description says. */
    public record Model(Option option, boolean amber, List<Node> nodes, List<Edge> edges, String sent, String summary,
                        boolean usageLink) {
        public Model {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }

        public int rows() {
            int r = 0;
            for (Node n : nodes) r = Math.max(r, n.row() + 1);
            return Math.max(1, r);
        }

        public Node node(NodeId id, int row) {
            for (Node n : nodes) if (n.id() == id && n.row() == row) return n;
            return null;
        }

        /** The text equivalent: the summary, each arrow's sentence, then the sent line. */
        public List<String> text() {
            List<String> out = new ArrayList<>();
            out.add(summary);
            for (Edge e : edges) out.add(e.spoken());
            out.add(sent);
            return out;
        }

        public String description() {
            return String.join(" ", text());
        }

        /** Every word the picture can draw (both lengths). */
        public List<String> drawnWords() {
            List<String> out = new ArrayList<>(List.of(AiSetupLines.DIAGRAM_THIS_COMPUTER, sent));
            if (option == Option.NO_AI) out.add(AiSetupLines.DIAGRAM_NO_AI);
            if (amber) out.add(AiSetupLines.DIAGRAM_FULL_TAG);
            for (Node n : nodes) {
                for (String s : new String[]{n.label(), n.shortLabel(), n.caption(), n.shortCaption()}) {
                    if (s != null) out.add(s);
                }
            }
            for (Edge e : edges) {
                for (String s : new String[]{e.chipText(), e.chipShort(), e.extra(), e.extraShort()}) {
                    if (s != null) out.add(s);
                }
            }
            return out;
        }
    }

    // ---- the model (pure) --------------------------------------------------------------------------------------------

    /**
     * The picture for {@code shown} under the published state: it reads the posture, external access and the sign-in
     * check's method (OD-77.16), nothing else. Pure.
     */
    public static Model model(AiSetupState s, Option shown) {
        AiSetupState st = s == null ? AiSetupState.IDLE : s;
        return model(shown, st.posture(), externalAccess(st), st.signedIn());
    }

    /**
     * External access as the state publishes it. {@link AiSetupState} gains {@code externalAccess} with Screen 2b
     * (M77-3, §8.2); until then this state carries no access verdict, so the picture draws the path as not on — the
     * client's arrow then always carries its word, and the sent line is true either way.
     */
    static Access externalAccess(AiSetupState s) {
        return Access.OFF;
    }

    /** The pure core: every option × posture × access × sign-in (★ T25 walks it). */
    public static Model model(Option shown, Posture posture, Access access, Auth signedIn) {
        Option o = shown == null ? Option.NO_AI : shown;
        boolean full = posture == Posture.FULL;
        boolean amber = full && o.drawsClaude();
        Access a = access == null ? Access.OFF : access;
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        int row = 0;
        nodes.add(analyser(row));
        if (o.drawsClaude()) {
            String bill = AiSetupLines.diagramBill(signedIn);
            nodes.add(new Node(NodeId.CLAUDE, row, AiSetupLines.DIAGRAM_CLAUDE, AiSetupLines.DIAGRAM_CLAUDE,
                    full ? AiSetupLines.DIAGRAM_CLAUDE_FULL : AiSetupLines.DIAGRAM_CLAUDE_SAFE,
                    full ? AiSetupLines.DIAGRAM_CLAUDE_FULL_SHORT : AiSetupLines.DIAGRAM_CLAUDE_SAFE_SHORT));
            nodes.add(new Node(NodeId.CLAUDE_SERVICE, row, AiSetupLines.DIAGRAM_CLAUDE_SERVICE,
                    AiSetupLines.DIAGRAM_CLAUDE_SERVICE, bill, bill));
            Tone t = amber ? Tone.ACCENT : Tone.NORMAL;
            edges.add(new Edge(NodeId.ANALYSER, NodeId.CLAUDE, row, Line.AUTO, t, Chip.ASK, AiSetupLines.DIAGRAM_ASK,
                    AiSetupLines.DIAGRAM_ASK, null, null, AiSetupLines.DIAGRAM_SAY_ASK));
            edges.add(new Edge(NodeId.CLAUDE, NodeId.ANALYSER, row, Line.AUTO, t, Chip.POINTS,
                    AiSetupLines.DIAGRAM_POINTS, AiSetupLines.DIAGRAM_POINTS, null, null,
                    full ? AiSetupLines.DIAGRAM_SAY_CLAUDE_POINTS_FULL : AiSetupLines.DIAGRAM_SAY_CLAUDE_POINTS));
            String sends = full ? AiSetupLines.DIAGRAM_SAY_CLAUDE_SENDS_FULL : AiSetupLines.DIAGRAM_SAY_CLAUDE_SENDS;
            if (bill != null) sends += " " + AiSetupLines.diagramSayBill(bill);
            edges.add(new Edge(NodeId.CLAUDE, NodeId.CLAUDE_SERVICE, row, Line.AUTO, Tone.NORMAL, Chip.WHAT_IT_READS,
                    AiSetupLines.DIAGRAM_WHAT_IT_READS, AiSetupLines.DIAGRAM_WHAT_IT_READS_SHORT,
                    full ? AiSetupLines.DIAGRAM_OWN_TOOLS : null, full ? AiSetupLines.DIAGRAM_OWN_TOOLS_SHORT : null,
                    sends));
            row++;
        }
        if (o.drawsClient()) {
            if (row > 0) nodes.add(analyser(row));          // OD-77.21: Both's client row has its own product icon
            nodes.add(new Node(NodeId.CLIENT, row, AiSetupLines.DIAGRAM_CLIENT, AiSetupLines.DIAGRAM_CLIENT_SHORT,
                    null, null));
            nodes.add(new Node(NodeId.CLIENT_SERVICE, row, AiSetupLines.DIAGRAM_CLIENT_SERVICE,
                    AiSetupLines.DIAGRAM_CLIENT_SERVICE_SHORT, null, null));
            edges.add(switch (a) {
                case ON -> new Edge(NodeId.CLIENT, NodeId.ANALYSER, row, Line.AUTO, Tone.NORMAL, Chip.POINTS,
                        AiSetupLines.DIAGRAM_POINTS, AiSetupLines.DIAGRAM_POINTS, null, null,
                        AiSetupLines.DIAGRAM_SAY_CLIENT_POINTS);
                case OFF, CONFIRMING -> new Edge(NodeId.CLIENT, NodeId.ANALYSER, row, Line.NOT_ON, Tone.MUTED, Chip.OFF,
                        AiSetupLines.DIAGRAM_OFF, AiSetupLines.DIAGRAM_OFF, null, null,
                        AiSetupLines.DIAGRAM_SAY_CLIENT_OFF);
                case CLOSED_FOR_SESSION -> new Edge(NodeId.CLIENT, NodeId.ANALYSER, row, Line.NOT_ON, Tone.MUTED,
                        Chip.CLOSED, AiSetupLines.DIAGRAM_CLOSED, AiSetupLines.DIAGRAM_CLOSED, null, null,
                        AiSetupLines.DIAGRAM_SAY_CLIENT_CLOSED);
                case OTHER_INSTANCE -> new Edge(NodeId.CLIENT, NodeId.ANALYSER, row, Line.NOT_ON, Tone.MUTED,
                        Chip.ANOTHER_WINDOW, AiSetupLines.DIAGRAM_ANOTHER_WINDOW,
                        AiSetupLines.DIAGRAM_ANOTHER_WINDOW_SHORT, null, null,
                        AiSetupLines.DIAGRAM_SAY_CLIENT_OTHER_WINDOW);
            });
            // the one-way rule: nothing reaches your client from the product — you carry it
            edges.add(new Edge(NodeId.ANALYSER, NodeId.CLIENT, row, Line.BY_HAND, Tone.NORMAL, Chip.YOU_COPY,
                    AiSetupLines.DIAGRAM_YOU_COPY, AiSetupLines.DIAGRAM_YOU_COPY, null, null,
                    AiSetupLines.DIAGRAM_SAY_YOU_COPY));
            edges.add(new Edge(NodeId.CLIENT, NodeId.CLIENT_SERVICE, row, Line.AUTO, Tone.NORMAL, Chip.WHAT_IT_READS,
                    AiSetupLines.DIAGRAM_WHAT_IT_READS, AiSetupLines.DIAGRAM_WHAT_IT_READS_SHORT, null, null,
                    AiSetupLines.DIAGRAM_SAY_CLIENT_SENDS));
        }
        String sent = switch (o) {
            case NO_AI -> AiSetupLines.DIAGRAM_SENT_NO_AI;
            case CLIENT -> AiSetupLines.DIAGRAM_SENT_CLIENT;
            case INSIDE -> AiSetupLines.diagramSentInside(full);
            case BOTH -> AiSetupLines.DIAGRAM_SENT_BOTH;
        };
        String summary = switch (o) {
            case NO_AI -> AiSetupLines.DIAGRAM_SUMMARY_NO_AI;
            case CLIENT -> AiSetupLines.DIAGRAM_SUMMARY_CLIENT;
            case INSIDE -> AiSetupLines.DIAGRAM_SUMMARY_INSIDE;
            case BOTH -> AiSetupLines.DIAGRAM_SUMMARY_BOTH;
        };
        return new Model(o, amber, nodes, edges, sent, summary, o.drawsClaude());
    }

    private static Node analyser(int row) {
        return new Node(NodeId.ANALYSER, row, AiSetupLines.DIAGRAM_PRODUCT, AiSetupLines.DIAGRAM_PRODUCT_SHORT, null,
                null);
    }

    // ---- the component ----------------------------------------------------------------------------------------------

    private AiSetupState state = AiSetupState.IDLE;
    private Option shown = Option.NO_AI;

    public FlowDiagram() {
        setOpaque(false);
        setFocusable(false);
    }

    /** Store what to render, and repaint. Decides nothing (rule 9): the picture is {@link #model()} of these. */
    public void setSnapshot(AiSetupState s, Option option) {
        state = s == null ? AiSetupState.IDLE : s;
        shown = option == null ? Option.NO_AI : option;
        revalidate();                       // Both is two rows: a new height
        repaint();
    }

    /** The picture now on screen, as data. */
    public Model model() {
        return model(state, shown);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.LABEL;
                }

                @Override public String getAccessibleName() {
                    return AiSetupLines.DIAGRAM_NAME;
                }

                @Override public String getAccessibleDescription() {
                    return model().description();
                }
            };
        }
        return accessibleContext;
    }

    // ---- what the last paint drew (the test hooks of ★ T26 and ★ T27) -----------------------------------------------

    /** An arrow as painted: its ends, row and line. */
    public record PaintedArrow(NodeId from, NodeId to, int row, Line line) {
    }

    /** A run of text as painted, its ink and what it sits on. */
    public record Run(String text, Rectangle2D bounds, Color ink, Color under) {
    }

    /** A line, icon or edge as painted, its colour and what it sits on; {@code exempt} for the off line (§6.1a). */
    public record Mark(String what, Color ink, Color under, boolean exempt) {
    }

    private final List<PaintedArrow> arrows = new ArrayList<>();
    private final List<Run> runs = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    List<PaintedArrow> paintedArrows() {
        return List.copyOf(arrows);
    }

    List<Run> paintedRuns() {
        return List.copyOf(runs);
    }

    List<Mark> paintedMarks() {
        return List.copyOf(marks);
    }

    /** Text cut by the component's edge, text over text, text over an icon. */
    List<String> layoutProblems() {
        return List.copyOf(problems);
    }

    // ---- sizes (from Label.font) ------------------------------------------------------------------------------------

    /** A row's words under its icons: a label, and a caption when a node in the row has one. */
    static final int PAD = 6, TOP = 34, LABEL_H = 34, LABEL_ONLY_H = 20, ROW_GAP = 14, CAPTION_GAP = 10;

    private static Font base() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : f;
    }

    private static Font small() {
        return base().deriveFont(base().getSize2D() - 2f);
    }

    private static Font label() {
        return base().deriveFont(Font.BOLD, base().getSize2D() - 1f);
    }

    private static Font cap() {
        return base().deriveFont(base().getSize2D() - 1f);
    }

    /** Below 400 px the short labels (§6.1a). */
    boolean narrow() {
        return getWidth() < 400;
    }

    static int tile(int w) {
        return Math.max(38, Math.min(60, w / 9));
    }

    private int widthForLayout() {
        Container p = getParent();
        if (p != null && p.getWidth() > 0) {
            Insets in = p.getInsets();
            return p.getWidth() - in.left - in.right;
        }
        return getWidth() > 0 ? getWidth() : 560;
    }

    /** The height the picture needs at {@code w}. */
    public int heightFor(int w) {
        Model m = model();
        int lh = getFontMetrics(cap()).getHeight();
        return PAD + boundaryHeight(m, tile(w)) + CAPTION_GAP + wrap(m.sent(), getFontMetrics(cap()), w - 2 * PAD).size() * lh
                + PAD;
    }

    /** The words' height under row {@code row}'s icons. */
    private static int labelHeight(Model m, int row) {
        for (Node n : m.nodes()) if (n.row() == row && n.caption() != null) return LABEL_H;
        return LABEL_ONLY_H;
    }

    /** The top of row {@code row}'s icons, from the boundary's top. */
    private static int rowTop(Model m, int row, int t) {
        int y = TOP;
        for (int r = 0; r < row; r++) y += t + labelHeight(m, r) + ROW_GAP;
        return y;
    }

    private static int boundaryHeight(Model m, int t) {
        int last = m.rows() - 1;
        return rowTop(m, last, t) + t + labelHeight(m, last) + 6;
    }

    @Override
    public Dimension getPreferredSize() {
        int w = widthForLayout();
        return new Dimension(w, heightFor(w));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private final Set<Integer> reflowed = new HashSet<>();

    /** A new width may need another height (the WrapLine lesson): lay out again, once per width. */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (width > 0 && reflowed.add(width) && heightFor(width) != height) SwingUtilities.invokeLater(this::revalidate);
    }

    // ---- painting ---------------------------------------------------------------------------------------------------

    private Model painting;
    private boolean dark;
    private Rectangle boundary;
    private final java.util.Map<String, Rectangle> icons = new java.util.LinkedHashMap<>();
    private int[] xs = new int[3];

    private Color c(DiagramPalette t) {
        return t.color(dark);
    }

    private Color fg() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : (dark ? Color.LIGHT_GRAY : Color.BLACK);
    }

    /** What the picture is painted on: the first opaque ancestor's background. */
    private Color panel() {
        for (Component p = getParent(); p != null; p = p.getParent()) {
            if (p.isOpaque() && p.getBackground() != null) return p.getBackground();
        }
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : Color.WHITE;
    }

    private Color under(double x, double y) {
        return boundary != null && boundary.contains(x, y) ? c(DiagramPalette.BOUNDARY_WASH) : panel();
    }

    private static String key(NodeId id, int row) {
        return id + "#" + row;
    }

    private Rectangle icon(NodeId id, int row) {
        return icons.get(key(id, row));
    }

    private String[] labels(Node n) {
        boolean shortly = narrow();
        return new String[]{shortly ? n.shortLabel() : n.label(), shortly ? n.shortCaption() : n.caption()};
    }

    private void layout(int w, Model m, Graphics2D g) {
        icons.clear();
        int t = tile(w);
        // the product's label is wider than its icon: the first column moves in so the label clears the boundary
        String productLabel = narrow() ? AiSetupLines.DIAGRAM_PRODUCT_SHORT : AiSetupLines.DIAGRAM_PRODUCT;
        int left = Math.max(8, (g.getFontMetrics(label()).stringWidth(productLabel) - t) / 2 + 6);
        int gap = (w - 2 * PAD - left - 8 - 3 * t) / 2;
        xs = new int[]{PAD + left, PAD + left + t + gap, PAD + left + 2 * (t + gap)};
        for (Node n : m.nodes()) {
            int col = switch (n.id()) {
                case ANALYSER -> 0;
                case CLAUDE, CLIENT -> 1;
                case CLAUDE_SERVICE, CLIENT_SERVICE -> 2;
            };
            int y = PAD + rowTop(m, n.row(), t);
            icons.put(key(n.id(), n.row()), new Rectangle(xs[col], y, t, t));
        }
        // the boundary holds the local icons AND their words, so no label straddles its edge
        double right = xs[1] + t + gap * 0.18;
        for (Node n : m.nodes()) {
            if (!n.local()) continue;
            Rectangle r = icon(n.id(), n.row());
            for (String s : labels(n)) {
                if (s == null) continue;
                Font f = s.equals(labels(n)[0]) ? label() : small();
                double half = g.getFontMetrics(f).stringWidth(s) / 2.0;
                right = Math.max(right, Math.min(w - PAD, r.getCenterX() + half) + 6);
            }
        }
        boundary = new Rectangle(PAD, PAD, (int) Math.ceil(right) - PAD, boundaryHeight(m, t));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            paintPicture(g);
        } finally {
            g.dispose();
        }
    }

    private void paintPicture(Graphics2D g) {
        arrows.clear();
        runs.clear();
        marks.clear();
        problems.clear();
        dark = ThemeManager.isDark();
        painting = model();
        int w = getWidth();
        layout(w, painting, g);
        int t = tile(w);

        RoundRectangle2D box = new RoundRectangle2D.Double(boundary.x, boundary.y, boundary.width, boundary.height, 14, 14);
        g.setColor(c(DiagramPalette.BOUNDARY_WASH));
        g.fill(box);
        g.setColor(c(DiagramPalette.BOUNDARY_EDGE));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
        g.draw(box);
        marks.add(new Mark("boundary", c(DiagramPalette.BOUNDARY_EDGE), panel(), false));
        text(g, AiSetupLines.DIAGRAM_THIS_COMPUTER, small(), c(DiagramPalette.ANALYSER_INK), boundary.x + 10,
                boundary.y + 15);

        for (Edge e : painting.edges()) drawEdge(g, e);
        for (Node n : painting.nodes()) {
            Rectangle r = icon(n.id(), n.row());
            drawIcon(g, n, r);
            String[] l = labels(n);
            Color ink = ink(n.id());
            center(g, l[0], label(), ink, r.getCenterX(), r.y + r.height + 15);
            if (l[1] != null) center(g, l[1], small(), fg(), r.getCenterX(), r.y + r.height + 29);
        }
        if (painting.option() == Option.NO_AI) {
            // No AI: a cloud with a no-sign — the shape, not the colour, says nothing goes there
            Rectangle a = icon(NodeId.ANALYSER, 0);
            Rectangle r = new Rectangle(xs[2], a.y, t, t);
            cloud(g, r, c(DiagramPalette.SERVICE_INK), c(DiagramPalette.SERVICE_FILL), "no-AI cloud");
            noSign(g, r.getCenterX(), r.getCenterY() + t * 0.05, t * 0.30);
            icons.put("NO_AI", r);
            center(g, AiSetupLines.DIAGRAM_NO_AI, label(), fg(), r.getCenterX(), r.y + r.height + 15);
        }
        FontMetrics fm = g.getFontMetrics(cap());
        double y = PAD + boundary.height + CAPTION_GAP + fm.getAscent();
        for (String s : wrap(painting.sent(), fm, w - 2 * PAD)) {
            text(g, s, cap(), fg(), PAD, y);
            y += fm.getHeight();
        }
    }

    private Color ink(NodeId id) {
        return switch (id) {
            case ANALYSER -> c(DiagramPalette.ANALYSER_INK);
            case CLAUDE -> c(DiagramPalette.CLAUDE_INK);
            case CLIENT -> c(DiagramPalette.CLIENT_INK);
            case CLAUDE_SERVICE, CLIENT_SERVICE -> c(DiagramPalette.SERVICE_INK);
        };
    }

    private Color fill(NodeId id) {
        return switch (id) {
            case ANALYSER -> c(DiagramPalette.ANALYSER_FILL);
            case CLAUDE -> c(DiagramPalette.CLAUDE_FILL);
            case CLIENT -> c(DiagramPalette.CLIENT_FILL);
            case CLAUDE_SERVICE, CLIENT_SERVICE -> c(DiagramPalette.SERVICE_FILL);
        };
    }

    private Color lineOf(Edge e) {
        if (e.line() == Line.NOT_ON) return c(DiagramPalette.OFF_LINE);
        if (e.line() == Line.BY_HAND) return c(DiagramPalette.BY_HAND_LINE);
        if (e.outbound()) return c(DiagramPalette.SERVICE_LINE);
        if (e.from() == NodeId.CLIENT) return c(DiagramPalette.CLIENT_LINE);
        return e.tone() == Tone.ACCENT ? c(DiagramPalette.FULL_AMBER_LINE) : c(DiagramPalette.TWO_WAY_LINE);
    }

    private Color textOf(Edge e) {
        if (e.line() == Line.NOT_ON) return c(DiagramPalette.OFF_TEXT);
        if (e.line() == Line.BY_HAND) return c(DiagramPalette.BY_HAND_TEXT);
        if (e.outbound()) return fg();
        if (e.from() == NodeId.CLIENT) return c(DiagramPalette.CLIENT_TEXT);
        return e.tone() == Tone.ACCENT ? c(DiagramPalette.FULL_AMBER_TEXT) : c(DiagramPalette.TWO_WAY_TEXT);
    }

    // ---- arrows -----------------------------------------------------------------------------------------------------

    private void drawEdge(Graphics2D g, Edge e) {
        Rectangle a = icon(e.from(), e.row()), b = icon(e.to(), e.row());
        if (a == null || b == null) return;
        boolean paired = false, first = true;
        List<Edge> all = painting.edges();
        for (Edge o : all) {
            if (o.row() == e.row() && o.from() == e.to() && o.to() == e.from()) {
                paired = true;
                if (all.indexOf(o) < all.indexOf(e)) first = false;
            }
        }
        double y = a.getCenterY() + (paired ? (first ? -9 : 9) : 0);
        double x1, x2;
        if (a.x < b.x) {
            x1 = a.getMaxX() + 6;
            x2 = b.x - 6;
        } else {
            x1 = a.x - 6;
            x2 = b.getMaxX() + 6;
        }
        Color line = lineOf(e);
        float sw = e.tone() == Tone.ACCENT ? 2.6f : 1.6f;
        float[] dash = switch (e.line()) {
            case AUTO -> null;
            case BY_HAND -> new float[]{7f, 5f};
            case NOT_ON -> new float[]{2f, 4f};
        };
        g.setStroke(dash == null ? new BasicStroke(sw)
                : new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
        g.setColor(line);
        g.draw(new Line2D.Double(x1, y, x2, y));
        arrowHead(g, x1, y, x2, y, line);
        arrows.add(new PaintedArrow(e.from(), e.to(), e.row(), e.line()));
        boolean exempt = e.line() == Line.NOT_ON;
        marks.add(new Mark("line " + e.from() + "→" + e.to(), line, under((x1 + x2) / 2, y), exempt));
        if (e.outbound()) marks.add(new Mark("line " + e.from() + "→" + e.to() + " outside", line, panel(), exempt));

        boolean shortly = narrow();
        String word = shortly ? e.chipShort() : e.chipText();
        double mx = e.outbound() ? (boundary.getMaxX() + x2) / 2 : (x1 + x2) / 2;
        double cy = paired ? (first ? y - 15 : y + 15) : y - 15;
        Font f = small();
        FontMetrics fm = g.getFontMetrics(f);
        int lead = e.chip() == Chip.OFF ? 14 : 11;
        double chipW = lead + 7 + fm.stringWidth(word);
        double ix = mx - chipW / 2 + 7;
        switch (e.chip()) {
            case ASK -> mouse(g, ix, cy, line);
            case YOU_COPY -> clipboard(g, ix, cy, line);
            case POINTS -> target(g, ix, cy, line);
            case CLOSED -> padlock(g, ix, cy, line);
            case OFF -> toggleOff(g, ix + 2, cy, line);
            case ANOTHER_WINDOW -> twoWindows(g, ix, cy, line);
            case WHAT_IT_READS -> page(g, ix, cy, line);
        }
        marks.add(new Mark("chip icon " + e.chip(), line, under(ix, cy), exempt));
        double tx = ix + lead;
        g.setColor(under(tx, cy));
        g.fill(new Rectangle2D.Double(tx - 1, cy - fm.getAscent() / 2.0 - 2, fm.stringWidth(word) + 3, fm.getAscent() + 3));
        Color tc = textOf(e);
        text(g, word, f, tc, tx, cy + fm.getAscent() / 2.0 - 1);
        String extra = shortly ? e.extraShort() : e.extra();
        if (extra != null) text(g, extra, f, tc, ix - 6, y + 15 + fm.getAscent() / 2.0 - 1);
    }

    private static void arrowHead(Graphics2D g, double x1, double y1, double x2, double y2, Color c) {
        double ang = Math.atan2(y2 - y1, x2 - x1), len = 8, spread = Math.toRadians(24);
        Path2D p = new Path2D.Double();
        p.moveTo(x2, y2);
        p.lineTo(x2 - len * Math.cos(ang - spread), y2 - len * Math.sin(ang - spread));
        p.lineTo(x2 - len * Math.cos(ang + spread), y2 - len * Math.sin(ang + spread));
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    // ---- icons (pure Java2D) ----------------------------------------------------------------------------------------

    private void drawIcon(Graphics2D g, Node n, Rectangle r) {
        Color ink = ink(n.id()), fill = fill(n.id());
        marks.add(new Mark("icon " + n.id() + " on its fill", ink, fill, false));
        marks.add(new Mark("icon " + n.id() + " on its ground", ink, under(r.getCenterX(), r.getCenterY()), false));
        switch (n.id()) {
            case ANALYSER -> window(g, r, ink, fill);
            case CLAUDE -> {
                bubble(g, r, ink, fill);
                if (painting.amber()) fullTag(g, r);
                else shield(g, r.getMaxX() - r.width * 0.12, r.getMaxY() - r.height * 0.18, r.width * 0.17, ink, fill);
            }
            case CLIENT -> terminal(g, r, ink, fill);
            case CLAUDE_SERVICE, CLIENT_SERVICE -> cloud(g, r, ink, fill, null);
        }
    }

    private void window(Graphics2D g, Rectangle r, Color c, Color fill) {
        double inset = r.height * 0.1;
        RoundRectangle2D rr = new RoundRectangle2D.Double(r.x, r.y + inset, r.width, r.height - 2 * inset, 6, 6);
        g.setColor(fill);
        g.fill(rr);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(rr);
        double top = rr.getY() + Math.min(10, r.width * 0.16);
        g.draw(new Line2D.Double(r.x, top, r.x + r.width, top));
        double bx = r.x + r.width * 0.14, by = rr.getMaxY() - r.width * 0.14, bw = r.width * 0.09, unit = r.width * 0.8;
        double[] hs = {0.18, 0.32, 0.24, 0.4};
        for (int i = 0; i < hs.length; i++) {
            g.fill(new Rectangle2D.Double(bx + i * bw * 1.6, by - unit * hs[i], bw, unit * hs[i]));
        }
    }

    private void bubble(Graphics2D g, Rectangle r, Color c, Color fill) {
        double x = r.x + r.width * 0.04, y = r.y + r.height * 0.1, w = r.width * 0.92, h = r.height * 0.62;
        Area a = new Area(new RoundRectangle2D.Double(x, y, w, h, 12, 12));
        Path2D tail = new Path2D.Double();
        tail.moveTo(x + w * 0.22, y + h - 1);
        tail.lineTo(x + w * 0.14, y + h + r.height * 0.2);
        tail.lineTo(x + w * 0.44, y + h - 1);
        a.add(new Area(tail));
        g.setColor(fill);
        g.fill(a);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f));
        g.draw(a);
        double d = Math.max(3, r.width * 0.09);
        for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Double(x + w * (0.27 + i * 0.23) - d / 2, y + h / 2 - d / 2, d, d));
    }

    private void fullTag(Graphics2D g, Rectangle r) {
        Font f = base().deriveFont(Font.BOLD, base().getSize2D() - 3f);
        FontMetrics fm = g.getFontMetrics(f);
        String s = AiSetupLines.DIAGRAM_FULL_TAG;
        double w = fm.stringWidth(s) + 6, h = fm.getAscent() + 2;
        double x = r.getMaxX() - w + 2, y = r.getMaxY() - h - 1;
        Color tag = c(DiagramPalette.FULL_AMBER_TAG), on = c(DiagramPalette.ON_FULL_AMBER_TAG);
        g.setColor(tag);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 4, 4));
        g.setFont(f);
        g.setColor(on);
        g.drawString(s, (float) (x + 3), (float) (y + fm.getAscent()));
        Rectangle2D b = new Rectangle2D.Double(x, y, w, h);
        if (b.getMaxX() > getWidth()) problems.add("clipped by the component edge: \"" + s + "\"");
        runs.add(new Run(s, b, on, tag));
        marks.add(new Mark("Full tag on its ground", tag, c(DiagramPalette.BOUNDARY_WASH), false));
    }

    private void terminal(Graphics2D g, Rectangle r, Color c, Color fill) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(r.x, r.y + r.height * 0.12, r.width, r.height * 0.76, 6, 6);
        g.setColor(fill);
        g.fill(rr);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(rr);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double x = r.x + r.width * 0.2, y = r.y + r.height * 0.36, s = r.width * 0.13;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + s, y + s);
        p.lineTo(x, y + 2 * s);
        g.draw(p);
        g.draw(new Line2D.Double(x + s * 1.8, y + 2 * s, x + s * 3.6, y + 2 * s));
    }

    private void cloud(Graphics2D g, Rectangle r, Color c, Color fill, String mark) {
        double x = r.x, y = r.y + r.height * 0.18, w = r.width, h = r.height * 0.62;
        Area a = new Area(new Ellipse2D.Double(x, y + h * 0.35, w * 0.45, h * 0.6));
        a.add(new Area(new Ellipse2D.Double(x + w * 0.2, y, w * 0.5, h * 0.75)));
        a.add(new Area(new Ellipse2D.Double(x + w * 0.5, y + h * 0.25, w * 0.5, h * 0.7)));
        a.add(new Area(new Rectangle2D.Double(x + w * 0.2, y + h * 0.6, w * 0.6, h * 0.35)));
        g.setColor(fill);
        g.fill(a);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(a);
        if (mark != null) {
            marks.add(new Mark(mark + " on its fill", c, fill, false));
            marks.add(new Mark(mark + " on its ground", c, panel(), false));
        }
    }

    private void shield(Graphics2D g, double cx, double cy, double s, Color c, Color fill) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - s);
        p.lineTo(cx + s * 0.85, cy - s * 0.6);
        p.quadTo(cx + s * 0.8, cy + s * 0.6, cx, cy + s);
        p.quadTo(cx - s * 0.8, cy + s * 0.6, cx - s * 0.85, cy - s * 0.6);
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(p);
        g.draw(new Line2D.Double(cx - s * 0.35, cy, cx - s * 0.05, cy + s * 0.3));
        g.draw(new Line2D.Double(cx - s * 0.05, cy + s * 0.3, cx + s * 0.4, cy - s * 0.3));
    }

    private void mouse(Graphics2D g, double cx, double cy, Color c) {
        double w = 10, h = 14;
        RoundRectangle2D body = new RoundRectangle2D.Double(cx - w / 2, cy - h / 2, w, h, 9, 9);
        g.setColor(under(cx, cy));
        g.fill(body);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(body);
        Area right = new Area(new Rectangle2D.Double(cx, cy - h / 2, w / 2, h * 0.4));
        right.intersect(new Area(body));
        g.fill(right);
        g.draw(new Line2D.Double(cx, cy - h / 2, cx, cy - h / 2 + h * 0.4));
        g.draw(new Line2D.Double(cx - w / 2, cy - h / 2 + h * 0.4, cx + w / 2, cy - h / 2 + h * 0.4));
    }

    private void target(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(under(cx, cy));
        g.fill(new Ellipse2D.Double(cx - 7, cy - 7, 14, 14));
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Double(cx - 6.5, cy - 6.5, 13, 13));
        g.fill(new Ellipse2D.Double(cx - 2.5, cy - 2.5, 5, 5));
    }

    private void clipboard(Graphics2D g, double cx, double cy, Color c) {
        RoundRectangle2D b = new RoundRectangle2D.Double(cx - 5.5, cy - 6, 11, 14, 3, 3);
        g.setColor(under(cx, cy));
        g.fill(b);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(b);
        g.fill(new RoundRectangle2D.Double(cx - 3, cy - 8, 6, 4, 2, 2));
    }

    private void page(Graphics2D g, double cx, double cy, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx - 5, cy - 7);
        p.lineTo(cx + 2, cy - 7);
        p.lineTo(cx + 5, cy - 4);
        p.lineTo(cx + 5, cy + 7);
        p.lineTo(cx - 5, cy + 7);
        p.closePath();
        g.setColor(under(cx, cy));
        g.fill(p);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(p);
        for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(cx - 3, cy - 2 + i * 3, cx + 3, cy - 2 + i * 3));
    }

    private void padlock(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(under(cx, cy));
        g.fill(new Rectangle2D.Double(cx - 6, cy - 8, 12, 16));
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Arc2D.Double(cx - 3.5, cy - 7, 7, 8, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(cx - 3.5, cy - 3, cx - 3.5, cy - 1));
        g.draw(new Line2D.Double(cx + 3.5, cy - 3, cx + 3.5, cy - 1));
        g.fill(new RoundRectangle2D.Double(cx - 5.5, cy - 1, 11, 8, 2, 2));
    }

    private void toggleOff(Graphics2D g, double cx, double cy, Color c) {
        RoundRectangle2D b = new RoundRectangle2D.Double(cx - 9, cy - 5, 18, 10, 10, 10);
        g.setColor(under(cx, cy));
        g.fill(b);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(b);
        g.fill(new Ellipse2D.Double(cx - 8, cy - 4, 8, 8));
    }

    private void twoWindows(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(under(cx, cy));
        g.fill(new Rectangle2D.Double(cx - 6, cy - 7, 13, 14));
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new RoundRectangle2D.Double(cx - 6, cy - 6, 8, 7, 2, 2));
        g.draw(new RoundRectangle2D.Double(cx - 2, cy - 1, 8, 7, 2, 2));
    }

    private void noSign(Graphics2D g, double cx, double cy, double r) {
        Color c = c(DiagramPalette.NO_SIGN);
        g.setColor(c);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.draw(new Line2D.Double(cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7));
        marks.add(new Mark("no-sign", c, c(DiagramPalette.SERVICE_FILL), false));
    }

    // ---- text -------------------------------------------------------------------------------------------------------

    private void center(Graphics2D g, String s, Font f, Color c, double cx, double baseline) {
        FontMetrics fm = g.getFontMetrics(f);
        double x = cx - fm.stringWidth(s) / 2.0;
        x = Math.max(PAD, Math.min(x, getWidth() - PAD - fm.stringWidth(s)));
        text(g, s, f, c, x, baseline);
    }

    private void text(Graphics2D g, String s, Font f, Color c, double x, double baseline) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D r = new Rectangle2D.Double(x, baseline - fm.getAscent(), fm.stringWidth(s), fm.getAscent() + fm.getDescent());
        if (r.getMinX() < 0 || r.getMaxX() > getWidth() || r.getMinY() < 0 || r.getMaxY() > getHeight()) {
            problems.add("clipped by the component edge: \"" + s + "\"");
        }
        for (Run o : runs) {
            if (o.bounds().intersects(r)) problems.add("text over text: \"" + o.text() + "\" / \"" + s + "\"");
        }
        for (var en : icons.entrySet()) {
            if (en.getValue().intersects(r)) problems.add("text over the icon " + en.getKey() + ": \"" + s + "\"");
        }
        g.drawString(s, (float) x, (float) baseline);
        runs.add(new Run(s, r, c, under(r.getCenterX(), r.getCenterY())));
    }

    /** Greedy word wrap at {@code width}. */
    static List<String> wrap(String s, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
