package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportVerb;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The Reports tab (spec-investigation-reports M33.4, §C): a report is a NAVIGATION SURFACE, not just
 * an output — each evidence section is clickable through to the thing it references, because the
 * report's whole claim is that its claims can be checked.
 *
 * <p>The D-I3a rendering rule lives here for the screen the way {@link ReportRenderer} carries it for
 * the PDF: a fingerprint mismatch banners BEFORE the sections; a filter difference is an OFFER — a
 * button that applies the stored context — never an automatic act (M20.5/D-R5's pattern).
 *
 * <p>Swing, so untested by rule 4; every decision with logic in it lives in the report package and is
 * pinned there.
 */
public final class ReportsPanel extends JPanel {

    /**
     * Theme-derived colours, computed at render time so a theme switch re-renders correctly. The PDF
     * stays deliberately light (a document prints); the PANEL lives inside a themed app, and a light
     * "paper" block under the dark theme's light-gray text is unreadable — accents keep their hue,
     * backgrounds are a tint of the accent over the theme's own panel colour, and body text is always
     * the theme's foreground.
     */
    private record Theme(boolean dark, Color panelBg, Color fg, Color mutedFg,
                         Color warn, Color narrative, Color problem, Color fix, Color highlightBg) {
        static Theme current() {
            Color bg = javax.swing.UIManager.getColor("Panel.background");
            if (bg == null) bg = Color.WHITE;
            boolean dark = (bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114) < 128;
            Color fg = javax.swing.UIManager.getColor("Label.foreground");
            if (fg == null) fg = dark ? new Color(0xDDE1E6) : new Color(0x1F2A44);
            return new Theme(dark, bg, fg,
                    dark ? new Color(0x9AA4AE) : new Color(0x57606A),
                    dark ? new Color(0xE8A657) : new Color(0xB45309),
                    dark ? new Color(0xB197FC) : new Color(0x6741D9),
                    dark ? new Color(0xE8A657) : new Color(0xB45309),
                    dark ? new Color(0x69DB7C) : new Color(0x15803D),
                    dark ? mix(new Color(0xE03131), bg, 0.75f) : new Color(0xFFE9E6));
        }

        /** A callout background: mostly the theme's own panel, a whiff of the accent. */
        Color tint(Color accent) {
            return mix(accent, panelBg, dark ? 0.82f : 0.88f);
        }
    }

    private static Color mix(Color a, Color b, float towardB) {
        float t = Math.max(0f, Math.min(1f, towardB));
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    private Theme theme = Theme.current();

    private final Supplier<List<ReportSpec>> reports;
    private final Function<ReportSpec, ReportResolver.Resolution> resolve;
    private final Function<ReportSpec.SectionSpec, ReportVerb.AssembledTable> assembleTable;
    private final IntConsumer gotoRecord;
    private final Consumer<String> openGraph;
    private final Consumer<String> openFocus;
    private final Consumer<FilterSnapshot> applyFilter;
    private final Consumer<String> exportPdf;

    private final DefaultListModel<String> names = new DefaultListModel<>();
    private final JList<String> list = new JList<>(names);
    private final JPanel detail = new JPanel();

    public ReportsPanel(Supplier<List<ReportSpec>> reports,
                        Function<ReportSpec, ReportResolver.Resolution> resolve,
                        Function<ReportSpec.SectionSpec, ReportVerb.AssembledTable> assembleTable,
                        IntConsumer gotoRecord, Consumer<String> openGraph,
                        Consumer<String> openFocus, Consumer<FilterSnapshot> applyFilter,
                        Consumer<String> exportPdf) {
        super(new BorderLayout());
        this.reports = reports;
        this.resolve = resolve;
        this.assembleTable = assembleTable;
        this.gotoRecord = gotoRecord;
        this.openGraph = openGraph;
        this.openFocus = openFocus;
        this.applyFilter = applyFilter;
        this.exportPdf = exportPdf;

        // the agent exports with report {path}; the human gets the same door as a button — the two
        // surfaces must stay in parity, or one side's report is not quite the other's
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton export = new JButton("Export PDF…");
        export.addActionListener(e -> {
            String name = list.getSelectedValue();
            if (name != null) exportPdf.accept(name);
        });
        bar.add(export);
        add(bar, BorderLayout.NORTH);

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderSelected();
        });
        // Fluid.column: the detail pane reflows to the width it is GIVEN. Without it the scroll pane
        // sizes this view to its own preferred width, so one long unwrapped line — the "written
        // against …" provenance line is the usual culprit — widens the entire report and everything
        // below it is laid out to match, off the right-hand edge and behind a horizontal scrollbar.
        JScrollPane detailScroll = new JScrollPane(Fluid.column(detail),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), detailScroll);
        split.setDividerLocation(180);
        add(split, BorderLayout.CENTER);
    }

    /** Rebuild the list from the store, keeping the selection where it survives. */
    public void refresh() {
        String selected = list.getSelectedValue();
        names.clear();
        for (ReportSpec r : reports.get()) names.addElement(r.name());
        if (selected != null && names.contains(selected)) {
            list.setSelectedValue(selected, true);
        } else if (!names.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            renderSelected();
        }
    }

    /** Re-render the open report (the filter changed, the log changed — the evidence is live). */
    public void rerender() {
        renderSelected();
    }

    /** Select one report by name (the verb reveals what it just built, like the graph verb does). */
    public void select(String name) {
        if (names.contains(name)) list.setSelectedValue(name, true);
    }

    private void renderSelected() {
        theme = Theme.current();                   // the theme can change between renders
        detail.removeAll();
        String name = list.getSelectedValue();
        ReportSpec spec = null;
        for (ReportSpec r : reports.get()) if (r.name().equals(name)) spec = r;
        if (spec == null) {
            detail.add(muted("No reports yet — the assistant builds one with "
                    + "report {name, sections}, and it appears here."));
            detail.revalidate();
            detail.repaint();
            return;
        }
        ReportResolver.Resolution res = resolve.apply(spec);

        JTextArea title = Fluid.text(spec.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        detail.add(title);
        if (spec.fingerprint() != null) {
            detail.add(muted("written against " + spec.fingerprint().describe()
                    + " · view: " + spec.filter().describe()));
        }
        detail.add(Box.createVerticalStrut(6));

        // D-I3a: announce FIRST, and the filter difference is an OFFER
        if (res.fingerprintMismatch() != null) {
            detail.add(banner(ReportResolver.fingerprintHeading(res.fingerprintMismatch()),
                    res.fingerprintMismatch(), null, null));
        }
        if (res.filterDifference() != null) {
            final ReportSpec s = spec;
            detail.add(banner("VIEW", res.filterDifference(),
                    "Apply the authored view", () -> {
                        applyFilter.accept(s.filter());
                        renderSelected();
                    }));
        }
        if (res.summary() != null) {
            detail.add(banner("UNRESOLVED", res.summary(), null, null));
        }
        if (!spec.notes().isBlank()) {
            detail.add(narrative(spec.notes()));
        }

        for (int i = 0; i < spec.sections().size(); i++) {
            detail.add(Box.createVerticalStrut(8));
            section(spec.sections().get(i), res.sections().get(i));
        }
        detail.add(Box.createVerticalGlue());
        detail.revalidate();
        detail.repaint();
    }

    private void section(ReportSpec.SectionSpec s, ReportResolver.SectionResolution r) {
        if (!r.resolved()) {
            detail.add(banner("DID NOT RESOLVE", r.reason(), null, null));
            return;
        }
        switch (s.kind()) {
            case NARRATIVE -> detail.add(narrative(s.text()));
            case FINDING -> {
                var f = r.finding();
                JPanel box = calloutBox(theme.problem(), theme.tint(theme.problem()));
                JTextArea wrong = bold("What is wrong — record #" + f.recordIndex());
                wrong.setForeground(theme.problem());
                box.add(wrong);
                box.add(wrapped(f.note()));
                if (f.hasFix()) {
                    JTextArea fixHeading = bold("Suggested fix");
                    fixHeading.setForeground(theme.fix());
                    box.add(fixHeading);
                    box.add(wrapped(f.fix()));
                }
                box.add(link("open record #" + f.recordIndex(),
                        () -> gotoRecord.accept(f.recordIndex())));
                detail.add(box);
            }
            case RECORD -> detail.add(link("record #" + s.recordIndex()
                            + (s.file() == null ? "" : " · " + s.file()),
                    () -> gotoRecord.accept(s.recordIndex())));
            case CHART -> detail.add(link("open graph '" + s.ref() + "'",
                    () -> openGraph.accept(s.ref())));
            case TOPOLOGY -> detail.add(link("apply focus '" + s.ref() + "'",
                    () -> openFocus.accept(s.ref())));
            case SERIES -> detail.add(muted("series: " + s.call()));
            case TABLE -> table(s);
        }
        if (r.warning() != null) {
            detail.add(banner("WARNING", r.warning(), null, null));
        }
    }

    private void table(ReportSpec.SectionSpec s) {
        ReportVerb.AssembledTable a = assembleTable.apply(s);
        var cols = a.table().columns();
        String[] headings = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) headings[i] = cols.get(i).heading();
        DefaultTableModel model = new DefaultTableModel(headings, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (List<String> row : a.table().rows()) {
            // the DECLARED formats apply on screen exactly as in the PDF (D-I8, one rule): a raw
            // epoch in a column declared "time" would be two renderings of one declaration
            Object[] cells = new Object[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                cells[i] = ReportRenderer.formatCell(i < row.size() ? row.get(i) : "",
                        cols.get(i).format());
            }
            model.addRow(cells);
        }
        JTable t = new JTable(model);
        boolean[] hot = a.table().highlighted();
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                if (!sel) {
                    c.setBackground(hot != null && row < hot.length && hot[row]
                            ? theme.highlightBg() : tb.getBackground());
                }
                return c;
            }
        });
        List<Integer> rowRecords = a.rowRecords();
        if (!rowRecords.isEmpty()) {
            t.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int row = t.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < rowRecords.size() && rowRecords.get(row) != null
                            && rowRecords.get(row) >= 0) {
                        gotoRecord.accept(rowRecords.get(row));
                    }
                }
            });
        }
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane sp = new JScrollPane(t);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        java.awt.Dimension size = new java.awt.Dimension(10,
                Math.min(260, 40 + a.table().rows().size() * t.getRowHeight()));
        sp.setPreferredSize(size);
        // a preferred height alone is only half the instruction: the maximum is still unbounded, so a
        // vertical BoxLayout hands the table whatever space is spare and a three-row table is drawn
        // with a screenful of empty grid under it
        sp.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, size.height));
        detail.add(sp);
        if (a.table().rows().isEmpty()) {
            String reason = a.table().emptyReason();
            detail.add(muted(reason == null ? "no rows" : "no rows — " + reason));
        }
        if (a.table().rowWhen() != null) {
            // D-I8 on screen exactly as on the page: the emphasis carries its reason. The rule is read
            // from the ASSEMBLED table, not the section — a rowWhen refused for a record-less source
            // (D-T4) reaches TableData as null, and printing the section's rule over a table that never
            // evaluated it would be a label claiming an emphasis that never ran.
            detail.add(muted((a.table().rowWhenLabel() == null ? "highlighted" : a.table().rowWhenLabel())
                    + " — rows where " + a.table().rowWhen()));
        }
        if (a.table().scalarLine() != null && !a.table().scalarLine().isBlank()) {
            detail.add(muted(a.table().scalarLine()));
        }
        for (String note : a.notes()) detail.add(muted(note));
    }

    // ---- small pieces --------------------------------------------------------------------------

    private JPanel banner(String heading, String body, String actionLabel, Runnable action) {
        JPanel box = calloutBox(theme.warn(), theme.tint(theme.warn()));
        JTextArea h = bold(heading);
        h.setForeground(theme.warn());
        box.add(h);
        box.add(wrapped(body));
        if (actionLabel != null) {
            JButton b = new JButton(actionLabel);
            b.addActionListener(e -> action.run());
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(b);
        }
        return box;
    }

    private JPanel narrative(String text) {
        JPanel box = calloutBox(theme.narrative(), theme.tint(theme.narrative()));
        JTextArea label = bold(ReportRenderer.NARRATIVE_LABEL);
        label.setForeground(theme.narrative());
        box.add(label);
        box.add(wrapped(text));
        return box;
    }

    private JPanel calloutBox(Color accent, Color bg) {
        // Fluid.Panel: as tall as its content, no taller. A plain JPanel has an unbounded maximum
        // height, and a vertical BoxLayout hands spare space to whatever will take it — which turned
        // a two-line narrative into a tinted block half the pane deep
        JPanel box = new Fluid.Panel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(bg);
        box.setOpaque(true);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    /**
     * A small bold heading — wrapping, for the same reason {@link #muted} does. The longest string
     * this panel draws is a heading: "THIS IS NOT THE LOG THE REPORT WAS WRITTEN AGAINST".
     */
    private static JTextArea bold(String s) {
        JTextArea t = Fluid.text(s);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        return t;
    }

    private JTextArea wrapped(String s) {
        JTextArea t = Fluid.text(s);
        t.setForeground(theme.fg());
        return t;
    }

    /**
     * Secondary text — and it WRAPS.
     *
     * <p>This was a {@code <html>} {@link JLabel}, which does not: it lays the whole string on one
     * line and reports that line's full width as its preferred size. A report's muted lines are its
     * longest — the "written against <provenance> · N record(s) · <range>" header, a {@code rowWhen}
     * rule quoted back, an unresolved-anchor reason naming a file — so the single widest of them set
     * the width of the entire report.
     */
    private JTextArea muted(String s) {
        JTextArea t = Fluid.text(s);
        t.setForeground(theme.mutedFg());
        return t;
    }

    private JButton link(String label, Runnable go) {
        JButton b = new JButton(label);
        b.addActionListener(e -> go.run());
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
}
