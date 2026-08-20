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

    private static final Color NARRATIVE = new Color(0x6741D9);
    private static final Color WARN = new Color(0xB45309);
    private static final Color WARN_BG = new Color(0xFFF4E2);
    private static final Color HIGHLIGHT_BG = new Color(0xFFE9E6);

    private final Supplier<List<ReportSpec>> reports;
    private final Function<ReportSpec, ReportResolver.Resolution> resolve;
    private final Function<ReportSpec.SectionSpec, ReportVerb.AssembledTable> assembleTable;
    private final IntConsumer gotoRecord;
    private final Consumer<String> openGraph;
    private final Consumer<String> openFocus;
    private final Consumer<FilterSnapshot> applyFilter;

    private final DefaultListModel<String> names = new DefaultListModel<>();
    private final JList<String> list = new JList<>(names);
    private final JPanel detail = new JPanel();

    public ReportsPanel(Supplier<List<ReportSpec>> reports,
                        Function<ReportSpec, ReportResolver.Resolution> resolve,
                        Function<ReportSpec.SectionSpec, ReportVerb.AssembledTable> assembleTable,
                        IntConsumer gotoRecord, Consumer<String> openGraph,
                        Consumer<String> openFocus, Consumer<FilterSnapshot> applyFilter) {
        super(new BorderLayout());
        this.reports = reports;
        this.resolve = resolve;
        this.assembleTable = assembleTable;
        this.gotoRecord = gotoRecord;
        this.openGraph = openGraph;
        this.openFocus = openFocus;
        this.applyFilter = applyFilter;

        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderSelected();
        });
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), new JScrollPane(detail));
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

    private void renderSelected() {
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

        JLabel title = new JLabel(spec.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        detail.add(title);
        if (spec.fingerprint() != null) {
            detail.add(muted("written against " + spec.fingerprint().describe()
                    + " · view: " + spec.filter().describe()));
        }
        detail.add(Box.createVerticalStrut(6));

        // D-I3a: announce FIRST, and the filter difference is an OFFER
        if (res.fingerprintMismatch() != null) {
            detail.add(banner("THIS IS NOT THE LOG THE REPORT WAS WRITTEN AGAINST",
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
                JPanel box = calloutBox(new Color(0xB45309), new Color(0xFFF8EC));
                box.add(bold("What is wrong — record #" + f.recordIndex()));
                box.add(wrapped(f.note()));
                if (f.hasFix()) {
                    box.add(bold("Suggested fix"));
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
        for (List<String> row : a.table().rows()) model.addRow(row.toArray());
        JTable t = new JTable(model);
        boolean[] hot = a.table().highlighted();
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                if (!sel) {
                    c.setBackground(hot != null && row < hot.length && hot[row]
                            ? HIGHLIGHT_BG : tb.getBackground());
                }
                return c;
            }
        });
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane sp = new JScrollPane(t);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setPreferredSize(new java.awt.Dimension(10,
                Math.min(260, 40 + a.table().rows().size() * t.getRowHeight())));
        detail.add(sp);
        if (s.rowWhen() != null) {
            // D-I8 on screen exactly as on the page: the emphasis carries its reason
            detail.add(muted((s.rowWhenLabel() == null ? "highlighted" : s.rowWhenLabel())
                    + " — rows where " + s.rowWhen()));
        }
        for (String note : a.notes()) detail.add(muted(note));
    }

    // ---- small pieces --------------------------------------------------------------------------

    private JPanel banner(String heading, String body, String actionLabel, Runnable action) {
        JPanel box = calloutBox(WARN, WARN_BG);
        box.add(bold(heading));
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
        JPanel box = calloutBox(NARRATIVE, new Color(0xF6F3FE));
        JLabel label = bold(ReportRenderer.NARRATIVE_LABEL);
        label.setForeground(NARRATIVE);
        box.add(label);
        box.add(wrapped(text));
        return box;
    }

    private JPanel calloutBox(Color accent, Color bg) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(bg);
        box.setOpaque(true);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    private static JLabel bold(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JTextArea wrapped(String s) {
        JTextArea t = new JTextArea(s);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setOpaque(false);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        return t;
    }

    private static JLabel muted(String s) {
        JLabel l = new JLabel("<html>" + s.replace("<", "&lt;") + "</html>");
        l.setForeground(new Color(0x707880));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JButton link(String label, Runnable go) {
        JButton b = new JButton(label);
        b.addActionListener(e -> go.run());
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
}
