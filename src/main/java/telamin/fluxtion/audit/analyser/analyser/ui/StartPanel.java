package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the records pane shows when no log is open (M36, spec-start-page).
 *
 * <p><b>It is a STATE, not a screen</b> (D-S1). No splash, no modal, nothing to dismiss: opening a
 * log replaces it and closing one brings it back. A splash is a toll gate on every launch and gets
 * muscle-memory-dismissed by week two, taking its content with it; an empty state is seen exactly
 * when it is useful and is invisible the rest of the time. This is also why the first-run
 * "no configuration was found" modal is gone — see
 * {@link MainFrame#showFirstRunSettingsIfNeeded()}.
 *
 * <p><b>Every section ends in an action</b> (D-S2), and every action runs against the demo set that
 * ships in the jar — no configuration, no server, no API key. A start page whose buttons need setup
 * first is one that lies on first contact.
 *
 * <p><b>No feature list</b> (D-S4). Three problems and three lanes, which change far more slowly
 * than the features that answer them — and this is the first thing a new user reads, so its errors
 * are the ones they carry.
 *
 * <p><b>The lanes are recognition, not a questionnaire</b> (D-S3). They are phrased as the sentence
 * the user would say, never as a question the app asks: people recognise their situation faster than
 * they classify themselves. Nothing is remembered and nothing is personalised — picking one is
 * navigation, and a wrong pick costs a click.
 *
 * <p><b>On the colour.</b> Every accent here is {@link UiTheme#accent()}, which is the blue the
 * topology already paints a hot edge with — the page is tinted, not branded. Colour is spent only
 * where it carries meaning: the primary action is the one filled shape on the page, each heading
 * gets a rule so the four sections are countable at a glance, and the lifecycle strip highlights the
 * one stage this app occupies. Nothing is coloured merely to be coloured, because on the first
 * screen a new user reads, decoration and signal are indistinguishable until they have learnt which
 * is which.
 */
public final class StartPanel extends JPanel {

    /** What the page can ask the app to do. Every one is an ordinary open, not a demo mode. */
    public interface Actions {
        /** Open a bundled demo log, optionally with the graph, and add the demo source root. */
        void openDemo(Path log, boolean withGraph);

        /** Bring a tab forward — the page hands over, it does not drive. */
        void showTab(String name);

        /** The ordinary File ▸ Open, for someone who arrived with their own log. */
        void openOwnLog();

        /**
         * File ▸ Settings. Reachable from here because the first-run modal that used to DEMAND it was
         * removed: discoverability was the one thing that gate bought, and it is cheaper to offer than
         * to insist.
         */
        void openSettings();

        /** Back to the records table, for a page raised over an open log (Help ▸ Start page). */
        void backToRecords();
    }

    private final Actions actions;
    private final Consumer<String> status;

    /** Components whose colour is theme-derived, re-resolved on a theme switch. */
    private final List<Runnable> recolour = new ArrayList<>();

    /** The "back to the records" row — present always, visible only over an open log. */
    private final JComponent returnRow;

    public StartPanel(Actions actions, Consumer<String> status) {
        super(new BorderLayout());
        this.actions = actions;
        this.status = status == null ? s -> { } : status;
        setOpaque(true);

        Box col = Box.createVerticalBox();
        col.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        returnRow = returnToRecords();
        col.add(returnRow);
        col.add(hero());

        col.add(Box.createVerticalStrut(20));
        col.add(heading("Three questions a log alone will not answer"));
        col.add(row(
                card("Why is this number what it is?",
                        "Follow one value back through the nodes that produced it.", false,
                        () -> open(DemoAssets.seriesLog(), false, "Graph")),
                card("Which nodes never ran?",
                        "Coverage over a traced run, where an absence is proof, not silence.", false,
                        () -> open(DemoAssets.tracedLog(), true, "Topology")),
                card("What did this cycle do?",
                        "Step one event through the graph, node by node.", false,
                        () -> open(DemoAssets.log(), true, "Topology"))));

        col.add(Box.createVerticalStrut(20));
        col.add(heading("Where this sits"));
        col.add(new Lifecycle());
        col.add(Box.createVerticalStrut(6));
        col.add(body("The log arrives from a build or a running server; what you find leaves as a "
                + "report, or as a change you can justify."));

        col.add(Box.createVerticalStrut(20));
        col.add(heading("Start where you are"));
        col.add(row(
                card("I am building a processor",
                        "See what the graph you wrote actually does.", false,
                        () -> open(DemoAssets.log(), true, "Topology")),
                card("Something is wrong in production",
                        "Open a log from a system you did not write.", false,
                        actions::openOwnLog),
                card("I want the numbers out of this",
                        "Chart a value over time and export it.", false,
                        () -> open(DemoAssets.seriesLog(), false, "Graph"))));

        col.add(Box.createVerticalStrut(18));
        col.add(footer());
        col.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(Fluid.column(col),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        this.scroll = scroll;
        add(scroll, BorderLayout.CENTER);
        applyColours();
    }

    private final JScrollPane scroll;

    /**
     * Show the page from the TOP.
     *
     * <p>Without this a narrow pane opens part-way down. The cards are real buttons, so focus lands on
     * one when the page is first shown, and a scroll pane chases the focused component into view —
     * scrolling the title off the top. The reader then arrives at the middle of the page with no
     * indication that they have missed anything, which is a worse failure than a clipped word: the
     * sentence that says what the app is for is simply not there.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, 0)));
    }

    /**
     * The one tinted band on the page, carrying the sentence that says what the app is and the single
     * filled action. It is a band rather than a plain heading because the first thing on a page is the
     * thing a reader will accept as the summary, so it should be visibly the summary.
     */
    private JComponent hero() {
        JPanel band = new Fluid.Panel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UiTheme.accentWash(0.10f));
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g.setColor(UiTheme.accent());          // the rule that marks the band as the summary
                g.fillRect(0, 3, 3, getHeight() - 6);
                g.dispose();
                super.paintComponent(g0);
            }
        };
        band.setOpaque(false);
        band.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        band.setAlignmentX(LEFT_ALIGNMENT);

        Box inner = Box.createVerticalBox();
        inner.setOpaque(false);
        // WRAPS rather than ellipsising: a JLabel would render this as "…see what the system act…",
        // and the one sentence on the page that says what the app is for is the worst possible thing
        // to truncate
        JTextArea title = wrapping("Read an audit log and see what the system actually did.");
        Font tf = UIManager.getFont("Label.font");
        title.setFont((tf != null ? tf : title.getFont()).deriveFont(Font.BOLD,
                (tf != null ? tf : title.getFont()).getSize2D() + 5f));
        recolour.add(() -> title.setForeground(UIManager.getColor("Label.foreground")));
        inner.add(title);
        inner.add(Box.createVerticalStrut(6));
        inner.add(body("Every event, the nodes it reached, the order they ran in, and what each one "
                + "computed — reconstructed from the log, not inferred from it."));
        inner.add(Box.createVerticalStrut(12));
        inner.add(row(card("Open the demo log",
                "A small recorded run, with its topology. Nothing to set up.", true,
                () -> open(DemoAssets.log(), true, "Topology"))));
        band.add(inner, BorderLayout.CENTER);
        return band;      // Fluid: as wide as offered, as tall as its content currently needs
    }

    private JComponent footer() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JLabel lead = new JLabel("Nothing above needs configuring. ");
        recolour.add(() -> lead.setForeground(UiTheme.mutedForeground()));
        p.add(lead);
        p.add(link("Set up source roots and an assistant", actions::openSettings));
        return p;
    }

    /**
     * The way back, shown ONLY when there is something to go back to.
     *
     * <p>Raised over an open log (Help ▸ Start page) the page would otherwise be a one-way door: the
     * records are still loaded but the table is behind the card, and nothing on screen says how to
     * return. Hidden when no log is open, because an exit that leads nowhere is worse than none —
     * it implies the reader has lost something they never had.
     */
    public void showReturnToRecords(boolean logOpen) {
        returnRow.setVisible(logOpen);
        revalidate();
        repaint();
    }

    private JComponent returnToRecords() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        p.add(link("\u2190 Back to the records", actions::backToRecords));
        p.setVisible(false);        // no log open is the common case, and then there is no way back
        return p;
    }

    /** A text-weight action: the affordance without the visual claim a button makes. */
    private JButton link(String text, Runnable go) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> go.run());
        recolour.add(() -> b.setForeground(UiTheme.accentText()));
        return b;
    }

    private void open(Path log, boolean withGraph, String tab) {
        try {
            DemoAssets.install();
        } catch (RuntimeException e) {
            // the promise is that these work on first contact; if one cannot, say why rather than
            // leaving a button that silently does nothing
            status.accept("Could not unpack the demo: " + e.getMessage());
            return;
        }
        actions.openDemo(log, withGraph);
        if (tab != null) actions.showTab(tab);
    }

    /** Section heading with a short accent rule, so the four sections are countable at a glance. */
    private JComponent heading(String text) {
        JPanel p = new Fluid.Panel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UiTheme.accent());
                // the rule spans the heading however many lines it wrapped to, so it still reads as
                // one section marker rather than a tick beside the first line
                g.fillRect(0, 1, 3, Math.max(4, getHeight() - 10));
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JTextArea l = wrapping(text);
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = l.getFont();
        l.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 11, 0, 0));
        recolour.add(() -> l.setForeground(UIManager.getColor("Label.foreground")));
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JComponent body(String text) {
        JTextArea l = wrapping(text);
        l.setFont(UIManager.getFont("Label.font"));
        recolour.add(() -> l.setForeground(UiTheme.mutedForeground()));
        return l;
    }

    /** This page's wrapped text — {@link Fluid#text}, shared with the Reports tab. */
    private static JTextArea wrapping(String text) {
        return Fluid.text(text);
    }

    /**
     * A row of actions that REFLOWS. Two earlier cuts of this both lost cards off the right-hand edge:
     * {@code FlowLayout} keeps each button's preferred width and runs the last one past the boundary,
     * and {@code GridLayout} fixes the count at three, so a narrow pane divides the width into slivers
     * too thin to read. Neither failure is visible on a wide screen, which is why both shipped as far
     * as a screenshot.
     *
     * <p>{@link CardFlow} fits as many cards per row as can be given a legible width and wraps the
     * rest — three across on a wide pane, two then one as it narrows, one per row at the bottom end.
     * The height follows from that, so the section grows downwards instead of the content vanishing
     * sideways.
     */
    private static JComponent row(JComponent... items) {
        JPanel p = new Fluid.Panel(new CardFlow());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        for (JComponent c : items) p.add(c);
        return p;
    }

    /**
     * A flow layout that WRAPS and stretches, for equal-width cards.
     *
     * <p>Swing has no such layout: {@code FlowLayout} wraps but never stretches (leaving ragged
     * trailing space), {@code GridLayout} stretches but never wraps (shrinking past legibility), and
     * {@code GridBagLayout} does neither without being told the row count in advance — which is the
     * number that has to change. So the rule is stated directly: no card is ever narrower than
     * {@link #MIN}, and whatever fits shares the width equally.
     */
    static final class CardFlow implements LayoutManager {
        /** The narrowest a card may be and still show a title and a readable line under it. */
        static final int MIN = 232;
        private static final int HGAP = 10;
        private static final int VGAP = 10;

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        private static int perRow(Container parent, int width) {
            Insets in = parent.getInsets();
            int avail = width - in.left - in.right;
            int fit = Math.max(1, (avail + HGAP) / (MIN + HGAP));
            return Math.min(fit, Math.max(1, parent.getComponentCount()));
        }

        private static int rowHeight(Container parent) {
            int h = 0;
            for (Component c : parent.getComponents()) h = Math.max(h, c.getPreferredSize().height);
            return h;
        }

        private static Dimension size(Container parent, int width) {
            int count = parent.getComponentCount();
            Insets in = parent.getInsets();
            if (count == 0) return new Dimension(in.left + in.right, in.top + in.bottom);
            int per = perRow(parent, width);
            int rows = (count + per - 1) / per;
            int h = rowHeight(parent);
            return new Dimension(width, in.top + in.bottom + rows * h + (rows - 1) * VGAP);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int w = parent.getWidth();
            if (w > 0) return size(parent, w);

            // Asked BEFORE the first layout, when the real width is not knowable. Answer with a single
            // row at natural widths and nothing else: the enclosing Box takes this height as the
            // maximum it will ever grant, so guessing HIGH here reserves space for a wrap that then
            // does not happen, and the section is followed by a band of blank page that no later
            // revalidation reclaims. Guessing low is self-correcting — the first real layout measures
            // the true width and the row grows if it must.
            Insets in = parent.getInsets();
            int natural = in.left + in.right;
            int h = 0;
            for (Component c : parent.getComponents()) {
                natural += c.getPreferredSize().width + HGAP;
                h = Math.max(h, c.getPreferredSize().height);
            }
            return new Dimension(natural, in.top + in.bottom + h);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return size(parent, MIN + in.left + in.right);
        }

        @Override
        public void layoutContainer(Container parent) {
            int count = parent.getComponentCount();
            if (count == 0) return;
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int per = perRow(parent, parent.getWidth());
            int h = rowHeight(parent);
            // width of a card in a FULL row — a partial last row keeps the same card width rather than
            // stretching its one survivor across the pane, which would read as a different kind of thing.
            // The outer min matters when the pane is narrower than one legible card: MIN is a floor we
            // would LIKE to hold, not a licence to draw outside the pane, and preferring the floor there
            // reintroduces the exact overflow this layout exists to stop.
            int cw = Math.min(avail, Math.max(MIN, (avail - (per - 1) * HGAP) / per));

            int i = 0;
            int y = in.top;
            while (i < count) {
                int n = Math.min(per, count - i);
                int x = in.left;
                for (int k = 0; k < n; k++, i++) {
                    boolean lastOfFullRow = n == per && k == n - 1;
                    int w = lastOfFullRow ? in.left + avail - x : cw;   // absorb integer-division slack
                    parent.getComponent(i).setBounds(x, y, w, h);
                    x += w + HGAP;
                }
                y += h + VGAP;
            }
        }
    }

    private Card card(String title, String why, boolean primary, Runnable go) {
        Card c = new Card(title, why, primary, go);
        recolour.add(c::repaint);
        return c;
    }

    /**
     * A two-line action. Drawn rather than styled: FlatLaf owns a JButton's background and border, and
     * fighting it per-theme is how a control ends up looking right in Dark and wrong in Light. It is
     * still a JButton, so keyboard activation, focus traversal, tooltips and accessibility are the
     * platform's, not a reimplementation.
     */
    private static final class Card extends JButton {
        private static final int PAD = 12;
        private static final int SUB_LINES = 2;

        private final String title;
        private final String why;
        private final boolean primary;

        Card(String title, String why, boolean primary, Runnable go) {
            this.title = title;
            this.why = why;
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setToolTipText(why);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> go.run());
        }

        private static Font base() {
            Font f = UIManager.getFont("Label.font");
            return f != null ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }

        private Font titleFont() {
            return base().deriveFont(Font.BOLD, base().getSize2D() + 0.5f);
        }

        private Font subFont() {
            return base().deriveFont(Font.PLAIN, base().getSize2D() - 1f);
        }

        /**
         * Height DERIVED from the two fonts, never a constant. The constant it replaces was measured
         * against a one-line description and clipped the second line of "…an absence is proof, not
         * silence." — a card is exactly as tall as a title plus the two lines it promises to show.
         */
        @Override
        public Dimension getPreferredSize() {
            int h = PAD * 2 + getFontMetrics(titleFont()).getHeight() + 3
                    + SUB_LINES * getFontMetrics(subFont()).getHeight();
            return new Dimension(220, h);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hot = getModel().isRollover() || getModel().isPressed() || isFocusOwner();
            int w = getWidth(), h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 9, 9);

            Color ink;
            Color sub;
            if (primary) {
                Color fill = UiTheme.accent();
                g.setColor(hot ? UiTheme.mix(fill, UiTheme.onAccent(), 0.14f) : fill);
                g.fill(shape);
                ink = UiTheme.onAccent();
                sub = UiTheme.mix(UiTheme.onAccent(), UiTheme.accent(), 0.32f);
            } else {
                g.setColor(hot ? UiTheme.accentWash(0.14f) : UiTheme.accentWash(0.05f));
                g.fill(shape);
                g.setColor(hot ? UiTheme.accent() : UiTheme.mix(UiTheme.surfaceEdge(),
                        UiTheme.accent(), 0.35f));
                g.draw(shape);
                ink = UiTheme.accentText();
                sub = UiTheme.mutedForeground();
            }

            int inner = w - PAD * 2;

            g.setFont(titleFont());
            g.setColor(ink);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ellipsis(fm, title, inner), PAD, PAD + fm.getAscent());

            int y = PAD + fm.getHeight() + 3;
            g.setFont(subFont());
            g.setColor(sub);
            FontMetrics fm2 = g.getFontMetrics();
            // two lines, then ellipsis — the tooltip carries the full sentence either way, so a narrow
            // column costs a word rather than costing the action
            for (String line : wrap(fm2, why, inner, SUB_LINES)) {
                g.drawString(line, PAD, y + fm2.getAscent());
                y += fm2.getHeight();
            }
            g.dispose();
        }

        private static List<String> wrap(FontMetrics fm, String text, int width, int maxLines) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(next) <= width || line.isEmpty()) {
                    line.setLength(0);
                    line.append(next);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                    if (out.size() == maxLines) break;
                }
            }
            if (out.size() < maxLines && !line.isEmpty()) out.add(line.toString());
            if (out.size() == maxLines) {
                out.set(maxLines - 1, ellipsis(fm, out.get(maxLines - 1), width));
            }
            return out;
        }

        private static String ellipsis(FontMetrics fm, String s, int width) {
            if (fm.stringWidth(s) <= width) return s;
            String cut = s;
            while (cut.length() > 1 && fm.stringWidth(cut + "…") > width) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "…";
        }
    }

    /**
     * build → deploy → ANALYSE → commit, with the one stage this app occupies filled and the rest
     * outlined. The point is placement, not process: a reader should see in one glance that the
     * analyser sits AFTER the thing ran and BEFORE the change is made, which is a claim about when it
     * is useful, and a sentence makes that claim far more slowly than a strip does.
     */
    private static final class Lifecycle extends JComponent {
        private static final String[] STAGES = {"build", "deploy", "ANALYSE", "commit"};
        private static final int HERE = 2;

        private static final int CHIP_H = 24;
        private static final int ARROW = 20;

        Lifecycle() {
            setAlignmentX(LEFT_ALIGNMENT);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                private int lastWidth = -1;

                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (getWidth() == lastWidth) return;
                    lastWidth = getWidth();
                    revalidate();      // one row or two, depending on how much width we were given
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : Integer.MAX_VALUE;
            return new Dimension(240, render(null, w));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(80, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            render(g, getWidth());
            g.dispose();
        }

        /**
         * Lay the strip out inside {@code width}, painting when {@code g} is non-null and only
         * measuring when it is null.
         *
         * <p>One method for both so the height the layout is told and the height actually drawn cannot
         * drift apart — the way they do when a paint routine wraps and a hard-coded preferred size
         * does not, which shows up as a clipped final chip.
         *
         * @return the height the strip needs at that width
         */
        private int render(Graphics2D g, int width) {
            Font base = UIManager.getFont("Label.font");
            if (base == null) base = getFont();
            if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            Font f = base.deriveFont(Font.BOLD, base.getSize2D() - 1f);
            FontMetrics fm = getFontMetrics(f);
            if (g != null) g.setFont(f);

            int x = 0;
            int y = 0;
            int rowH = CHIP_H + 6;
            for (int i = 0; i < STAGES.length; i++) {
                int cw = fm.stringWidth(STAGES[i]) + 20;
                boolean last = i == STAGES.length - 1;
                int need = cw + (last ? 0 : ARROW);
                if (x > 0 && x + need > width) {       // does not fit — carry on underneath
                    x = 0;
                    y += rowH;
                }
                if (g != null) {
                    RoundRectangle2D chip =
                            new RoundRectangle2D.Float(x, y, cw, CHIP_H, CHIP_H, CHIP_H);
                    if (i == HERE) {
                        g.setColor(UiTheme.accent());
                        g.fill(chip);
                        g.setColor(UiTheme.onAccent());
                    } else {
                        g.setColor(UiTheme.mix(UiTheme.surfaceEdge(), UiTheme.accent(), 0.20f));
                        g.draw(chip);
                        g.setColor(UiTheme.mutedForeground());
                    }
                    g.drawString(STAGES[i], x + 10,
                            y + (CHIP_H - fm.getHeight()) / 2 + fm.getAscent());
                }
                x += cw;
                if (!last) {
                    if (g != null) {
                        g.setColor(UiTheme.mutedForeground());
                        int my = y + CHIP_H / 2;
                        g.drawLine(x + 5, my, x + 15, my);
                        g.drawLine(x + 12, my - 3, x + 15, my);
                        g.drawLine(x + 12, my + 3, x + 15, my);
                    }
                    x += ARROW;
                }
            }
            return y + CHIP_H + 4;
        }
    }

    /**
     * Re-resolve every theme-derived colour. Called on construction and whenever the LAF changes:
     * anything set once at build time keeps a Dark-theme colour after a switch to Light, which is the
     * classic Swing theming bug and shows up as unreadable grey-on-grey text.
     */
    private void applyColours() {
        setBackground(UiTheme.surface());
        recolour.forEach(Runnable::run);
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (recolour != null && !recolour.isEmpty()) SwingUtilities.invokeLater(this::applyColours);
    }
}
