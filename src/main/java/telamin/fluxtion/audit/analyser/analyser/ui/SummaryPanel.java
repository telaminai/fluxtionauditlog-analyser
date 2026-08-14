package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.summary.SummaryBuilder;
import telamin.fluxtion.audit.analyser.analyser.summary.SummaryRow;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Set;

/**
 * Summary of the filtered records grouped by event dimension (spec §8.6): count + log-time span +
 * rate. Rebuilds whenever the shared {@link FilterState} changes; clicking a row cross-filters the
 * whole app to that dimension.
 */
public final class SummaryPanel extends JPanel {

    private final SummaryTableModel model = new SummaryTableModel();
    private final JTable table = new JTable(model);

    private LogStore store;
    private FilterState filter;

    public SummaryPanel() {
        super(new BorderLayout());
        setBorder(UiTheme.pad());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        // A left-click only selects (no filter side-effect); right-click a row to filter by its dimension.
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybePopup(e); }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void bind(LogStore store, FilterState filter) {
        this.store = store;
        this.filter = filter;
        filter.addListener(this::rebuild);
        rebuild();
    }

    private void rebuild() {
        if (store == null || filter == null) return;
        model.setRows(SummaryBuilder.build(store.index(), filter));
    }

    /** Right-click a summary row → filter the app by its event dimension (only / add / remove). */
    private void maybePopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger() || filter == null) return;
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) return;
        table.setRowSelectionInterval(viewRow, viewRow);
        String dim = model.rows.get(table.convertRowIndexToModel(viewRow)).dimension();
        String label = dim.isEmpty() ? "(none)" : dim;
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem only = new javax.swing.JMenuItem("Filter to \"" + label + "\"");
        only.addActionListener(a -> filter.setDimensions(Set.of(dim)));
        javax.swing.JMenuItem add = new javax.swing.JMenuItem("Add \"" + label + "\" to filter");
        add.addActionListener(a -> { Set<String> s = effectiveDims(); s.add(dim); applyDims(s); });
        javax.swing.JMenuItem remove = new javax.swing.JMenuItem("Remove \"" + label + "\" from filter");
        remove.addActionListener(a -> { Set<String> s = effectiveDims(); s.remove(dim); applyDims(s); });
        menu.add(only);
        menu.add(add);
        menu.add(remove);
        menu.show(table, e.getX(), e.getY());
    }

    /** The current selection as a concrete set (all dimensions when the filter is "all"). */
    private Set<String> effectiveDims() {
        Set<String> dims = filter.dimensions();
        return dims != null ? new java.util.HashSet<>(dims) : allDims();
    }

    private Set<String> allDims() {
        Set<String> all = new java.util.HashSet<>();
        for (SummaryRow r : model.rows) all.add(r.dimension());
        return all;
    }

    /** Apply a dimension set, collapsing "everything selected" back to the null (all) sentinel. */
    private void applyDims(Set<String> s) {
        Set<String> all = allDims();
        filter.setDimensions(s.equals(all) ? null : s);
    }

    private static final class SummaryTableModel extends AbstractTableModel {
        private static final String[] COLS = {"event dimension", "count", "first (UTC)", "last (UTC)", "span", "rate/min"};
        private List<SummaryRow> rows = List.of();

        void setRows(List<SummaryRow> r) {
            this.rows = r;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 1 ? Long.class : String.class; }

        @Override
        public Object getValueAt(int r, int c) {
            SummaryRow row = rows.get(r);
            return switch (c) {
                case 0 -> row.dimension().isEmpty() ? "(none)" : row.dimension();
                case 1 -> row.count();
                case 2 -> TimeFormat.utc(row.firstLog());
                case 3 -> TimeFormat.utc(row.lastLog());
                case 4 -> TimeFormat.duration(row.spanMillis());
                case 5 -> String.format("%.1f", row.ratePerMinute());
                default -> null;
            };
        }
    }
}
