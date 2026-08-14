package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * The main log {@link JTable} (spec §8.2): virtual model, UTC time columns, a {@code nodeLogs} count
 * column drawn with a relative-size <b>bar</b> (so heavy cycles stand out), row tinting for anomalies,
 * and toggleable column visibility. Emits the selected rows to a listener for the detail viewer.
 */
public final class LogTablePanel extends JPanel {

    // stronger tints for contrast; dark-theme variants are dark so the light text stays readable
    private static final Color ERR_L = new Color(0xF3B0B0), ERR_D = new Color(0x6E2A2A);
    private static final Color BREACH_L = new Color(0xF3C888), BREACH_D = new Color(0x6E4A1E);
    private static final Color NAN_L = new Color(0xEDDD84), NAN_D = new Color(0x63601C);
    private static final Color FLAG_L = new Color(0xADD9B1), FLAG_D = new Color(0x255A2C);
    private static final Color NODE_BAR = new Color(9, 105, 218, 55);   // translucent blue

    private final JTable table;
    private TableRowSorter<LogTableModel> sorter;
    private Consumer<int[]> selectionListener = rows -> { };
    private IntPredicate flagged = r -> false;
    private Consumer<int[]> flagToggle = rows -> { };
    private java.util.function.IntFunction<String> noteProvider = r -> null;   // flag note per model row
    private final List<TableColumn> baseColumns = new ArrayList<>();   // all columns, model order
    private int maxNodeLogs = 1;

    public LogTablePanel() {
        super(new java.awt.BorderLayout());
        table = new JTable() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0) {
                    g.setColor(java.awt.Color.GRAY);
                    String msg = getModel().getRowCount() == 0
                            ? "No log loaded — File ▸ Open, drag a file in, or File ▸ Open from S3"
                            : "No records match the current filter";
                    g.drawString(msg, 24, 40);
                }
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int view = rowAtPoint(e.getPoint());
                if (view >= 0) {
                    String note = noteProvider.apply(convertRowIndexToModel(view));
                    if (note != null && !note.isBlank()) return note;
                }
                return super.getToolTipText(e);
            }

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row) && getModel() instanceof LogTableModel m) {
                    int mrow = convertRowIndexToModel(row);
                    boolean dark = ThemeManager.isDark();
                    if (flagged.test(mrow)) c.setBackground(dark ? FLAG_D : FLAG_L);   // bookmark wins
                    else if (m.isParseError(mrow)) c.setBackground(dark ? ERR_D : ERR_L);
                    else if (m.hasBreach(mrow)) c.setBackground(dark ? BREACH_D : BREACH_L);
                    else if (m.hasNaN(mrow)) c.setBackground(dark ? NAN_D : NAN_L);
                    else c.setBackground(getBackground());
                }
                return c;
            }
        };
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int[] viewRows = table.getSelectedRows();
            int[] modelRows = new int[viewRows.length];
            for (int i = 0; i < viewRows.length; i++) modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
            selectionListener.accept(modelRows);
        });
        add(new JScrollPane(table), java.awt.BorderLayout.CENTER);
        // the "Records" section header lives on the wrapping area (above the Search row) — see MainFrame
        javax.swing.ToolTipManager.sharedInstance().registerComponent(table);   // enable per-row note tooltips

        // 'F' toggles a bookmark/flag on the selected rows
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "toggle-flag");
        table.getActionMap().put("toggle-flag", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int[] view = table.getSelectedRows();
                int[] model = new int[view.length];
                for (int i = 0; i < view.length; i++) model[i] = table.convertRowIndexToModel(view[i]);
                flagToggle.accept(model);
            }
        });
    }

    public void setFlagTester(IntPredicate flagged) {
        this.flagged = flagged == null ? r -> false : flagged;
    }

    public void setFlagToggle(Consumer<int[]> toggle) {
        this.flagToggle = toggle == null ? rows -> { } : toggle;
    }

    /** Supplies a hover note per model row (assistant flag notes); shown as the cell tooltip. */
    public void setNoteProvider(java.util.function.IntFunction<String> provider) {
        this.noteProvider = provider == null ? r -> null : provider;
    }

    public void setModel(LogTableModel model) {
        table.setModel(model);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        maxNodeLogs = Math.max(1, model.store().index().maxNodeLogsCount());
        installRenderers();
        sizeColumns();
        // capture the full column set (model order) so columns can be hidden/shown later
        baseColumns.clear();
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) baseColumns.add(cm.getColumn(i));
    }

    /** Show only the columns whose names are not in {@code hidden} (model order preserved). */
    public void setVisibleColumns(Set<String> hidden) {
        if (baseColumns.isEmpty()) return;
        TableColumnModel cm = table.getColumnModel();
        while (cm.getColumnCount() > 0) cm.removeColumn(cm.getColumn(0));
        for (TableColumn col : baseColumns) {
            if (!hidden.contains(String.valueOf(col.getHeaderValue()))) cm.addColumn(col);
        }
    }

    public void setRowFilter(RowFilter<? super LogTableModel, ? super Integer> f) {
        if (sorter != null) sorter.setRowFilter(f);
    }

    public void reFilter() {
        if (sorter != null) sorter.sort();
    }

    public int viewRowCount() {
        return table.getRowCount();
    }

    public int[] selectedModelRows() {
        int[] view = table.getSelectedRows();
        int[] model = new int[view.length];
        for (int i = 0; i < view.length; i++) model[i] = table.convertRowIndexToModel(view[i]);
        return model;
    }

    public void repaintRows() {
        table.repaint();
    }

    /** Scroll so the last (newest) view row is visible — used by follow/tail mode. */
    public void scrollToLast() {
        int rows = table.getRowCount();
        if (rows > 0) table.scrollRectToVisible(table.getCellRect(rows - 1, 0, true));
    }

    /**
     * Select and scroll to a model row (assistant {@code goto}). Returns false if the row is currently
     * filtered out of the view (so the caller can say so). Call on the EDT.
     */
    public boolean selectModelRow(int modelRow) {
        if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) return false;
        int view = table.convertRowIndexToView(modelRow);
        if (view < 0) return false;               // hidden by the current filter
        table.setRowSelectionInterval(view, view);
        table.scrollRectToVisible(table.getCellRect(view, 0, true));
        return true;
    }

    public void setSelectionListener(Consumer<int[]> listener) {
        this.selectionListener = listener == null ? rows -> { } : listener;
    }

    private void installRenderers() {
        DefaultTableCellRenderer time = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof Long l ? TimeFormat.utc(l) : "");
            }
        };
        setRenderer(LogTableModel.COL_EVENT_TIME, time);
        setRenderer(LogTableModel.COL_LOG_TIME, time);
        setRenderer(LogTableModel.COL_END_TIME, time);
        setRenderer(LogTableModel.COL_NODE_LOGS, new NodeBarRenderer());
    }

    private void setRenderer(int col, TableCellRenderer r) {
        table.getColumnModel().getColumn(col).setCellRenderer(r);
    }

    private void sizeColumns() {
        int[] widths = {160, 160, 90, 150, 170, 380, 150, 110, 160};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    public JTable table() {
        return table;
    }

    /**
     * Selects the next (or previous) row — in current view order, starting from the selection — whose
     * record is an anomaly (parse-error / breach / NaN), wrapping around. Returns false if none exist.
     */
    public boolean selectNextAnomaly(boolean forward) {
        if (!(table.getModel() instanceof LogTableModel m)) return false;
        int rows = table.getRowCount();
        if (rows == 0) return false;
        int cur = table.getSelectedRow();
        int start = cur < 0 ? (forward ? -1 : rows) : cur;
        for (int step = 1; step <= rows; step++) {
            int v = ((forward ? start + step : start - step) % rows + rows) % rows;
            int mrow = table.convertRowIndexToModel(v);
            if (m.isParseError(mrow) || m.hasBreach(mrow) || m.hasNaN(mrow)) {
                table.setRowSelectionInterval(v, v);
                table.scrollRectToVisible(table.getCellRect(v, 0, true));
                return true;
            }
        }
        return false;
    }

    /** Renders the nodeLogs count as "N nodes" over a bar whose width is N relative to the max. */
    private final class NodeBarRenderer extends DefaultTableCellRenderer {
        private double ratio;

        NodeBarRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            int n = v instanceof Integer i ? i : 0;
            ratio = maxNodeLogs > 0 ? (double) n / maxNodeLogs : 0;
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setText(n == 0 ? "" : n + (n == 1 ? " node" : " nodes"));
            return c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (ratio > 0) {
                g.setColor(NODE_BAR);
                g.fillRect(0, 0, Math.max(2, (int) Math.round(getWidth() * ratio)), getHeight());
            }
            boolean opaque = isOpaque();
            setOpaque(false);          // let super paint the text without re-filling the background
            super.paintComponent(g);
            setOpaque(opaque);
        }
    }
}
