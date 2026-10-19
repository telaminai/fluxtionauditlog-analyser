package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffExport;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.Change;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.DiffRow;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;

/**
 * Side-by-side diff of two records (spec §13): a table of {@code instanceId.key} with each record's
 * value, changed/added/removed rows highlighted so what moved between two cycles is obvious.
 */
public final class RecordDiffDialog {

    private RecordDiffDialog() {
    }

    /**
     * M74 P2 (§5.5, R F5): the dialog's own binding — the two model rows, the log identity and generation at open — so
     * its Ask button composes Q2 from THIS, never from the live selection, and a reload while it is open makes its draft
     * refused at Send.
     */
    public record DiffContext(int indexA, int indexB, String logIdentity, long generation) {
    }

    public static void show(Component parent, LogRecord a, LogRecord b, String labelA, String labelB) {
        show(parent, a, b, labelA, labelB, null, null, null);
    }

    /** With an Ask entrance: {@code ask} receives the key names the two records differ in (at most 30, names only). */
    public static void show(Component parent, LogRecord a, LogRecord b, String labelA, String labelB, DiffContext context,
                            java.util.function.Consumer<java.util.List<String>> ask, String askLabel) {
        List<DiffRow> rows = DiffBuilder.diff(a, b);
        long diffs = rows.stream().filter(DiffRow::isDifference).count();

        // SF-2: the cells are two records' values and the column names their labels — the log's text
        JTable table = UiText.plainCells(new JTable(new DiffTableModel(rows, labelA, labelB)));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(320);
        table.getColumnModel().getColumn(2).setPreferredWidth(320);
        DiffRenderer r = UiText.plain(new DiffRenderer(rows));
        for (int c = 0; c < table.getColumnCount(); c++) table.getColumnModel().getColumn(c).setCellRenderer(r);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Record diff",
                java.awt.Dialog.ModalityType.MODELESS);
        dialog.setLayout(new BorderLayout());
        dialog.add(UiText.label("  " + diffs + " difference(s) — changed / removed / added highlighted"),
                BorderLayout.NORTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        javax.swing.JPanel exportBar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        exportBar.add(new JLabel("Export:"));
        exportBar.add(exportButton("CSV", () -> DiffExport.toCsv(rows, labelA, labelB), "record-diff.csv", dialog));
        exportBar.add(exportButton("JSON", () -> DiffExport.toJson(rows, labelA, labelB), "record-diff.json", dialog));
        javax.swing.JButton pdf = new javax.swing.JButton("PDF");
        pdf.addActionListener(e -> saveBytes(dialog, "record-diff.pdf",
                telamin.fluxtion.audit.analyser.analyser.diff.TextPdf.render(
                        "Record diff: " + labelA + " vs " + labelB,
                        DiffExport.toTextLines(rows, labelA, labelB))));
        exportBar.add(pdf);
        if (context != null && ask != null) {
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (DiffRow row : rows) {
                if (row.isDifference() && keys.size() < 30 && !keys.contains(row.key())) keys.add(row.key());
            }
            javax.swing.JButton askButton = UiText.button(askLabel == null ? "Ask" : askLabel);
            askButton.setToolTipText(UiText.tooltip("Drafts a question naming records #" + context.indexA() + " and #"
                    + context.indexB() + " and the keys they differ in; nothing is sent until you press Send"));
            askButton.addActionListener(e -> ask.accept(java.util.List.copyOf(keys)));
            exportBar.add(askButton);
        }
        dialog.add(exportBar, BorderLayout.SOUTH);

        dialog.setSize(1040, 640);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static javax.swing.JButton exportButton(String label, java.util.function.Supplier<String> content,
                                                    String suggestedName, Component parent) {
        javax.swing.JButton b = UiText.button(label);
        b.addActionListener(e -> saveText(parent, suggestedName, content.get()));
        return b;
    }

    private static void saveText(Component parent, String name, String content) {
        java.io.File f = chooseSave(parent, name);
        if (f == null) return;
        try {
            java.nio.file.Files.writeString(f.toPath(), content);
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(parent, UiText.inert("Could not save: " + ex.getMessage()),
                    "Export", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void saveBytes(Component parent, String name, byte[] content) {
        java.io.File f = chooseSave(parent, name);
        if (f == null) return;
        try {
            java.nio.file.Files.write(f.toPath(), content);
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(parent, UiText.inert("Could not save: " + ex.getMessage()),
                    "Export", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private static java.io.File chooseSave(Component parent, String name) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setSelectedFile(new java.io.File(name));
        return fc.showSaveDialog(parent) == javax.swing.JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }

    private static final class DiffTableModel extends AbstractTableModel {
        private final List<DiffRow> rows;
        private final String[] cols;

        DiffTableModel(List<DiffRow> rows, String labelA, String labelB) {
            this.rows = rows;
            this.cols = new String[]{"instanceId.key", labelA, labelB};
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            DiffRow d = rows.get(row);
            return switch (col) {
                case 0 -> d.key();
                case 1 -> d.displayA() == null ? "" : d.displayA();
                case 2 -> d.displayB() == null ? "" : d.displayB();
                default -> "";
            };
        }
    }

    private static final class DiffRenderer extends DefaultTableCellRenderer {
        private static final Color CHANGED = new Color(0xFFF3C4);
        private static final Color ONLY_A = new Color(0xFFD7D5);
        private static final Color ONLY_B = new Color(0xD7F0D9);
        private final List<DiffRow> rows;

        DiffRenderer(List<DiffRow> rows) {
            this.rows = rows;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            if (!sel) {
                Change ch = rows.get(t.convertRowIndexToModel(row)).change();
                c.setBackground(switch (ch) {
                    case CHANGED -> CHANGED;
                    case ONLY_A -> ONLY_A;
                    case ONLY_B -> ONLY_B;
                    default -> t.getBackground();
                });
            }
            return c;
        }
    }
}
