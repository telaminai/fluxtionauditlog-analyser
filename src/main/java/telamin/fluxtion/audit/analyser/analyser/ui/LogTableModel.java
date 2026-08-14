package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.table.AbstractTableModel;

/**
 * Virtual table model backed entirely by the {@link LogIndex} — every column is index-resident, so
 * scrolling never parses node-logs (spec §8.2). Node-logs are shown only in the detail viewer.
 *
 * <p>Columns: eventTime, logTime, groupingId, event, callback, eventToString, thread, nodeLogs,
 * endTime. Display rules (improvements.md): for {@code ExportFunctionAuditEvent} the {@code event}
 * cell is blank (it is noise — the callback carries the meaning) and {@code eventToString} shows the
 * short {@code DeclaringClass.callback} instead of the full signature; {@code eventToString} is
 * treated as optional elsewhere.
 */
public final class LogTableModel extends AbstractTableModel {

    public static final int COL_EVENT_TIME = 0;
    public static final int COL_LOG_TIME = 1;
    public static final int COL_GROUPING = 2;
    public static final int COL_EVENT = 3;
    public static final int COL_CALLBACK = 4;
    public static final int COL_EVENT_TO_STRING = 5;
    public static final int COL_THREAD = 6;
    public static final int COL_NODE_LOGS = 7;
    public static final int COL_END_TIME = 8;

    private static final String EXPORT_FN = "ExportFunctionAuditEvent";

    private static final String[] COLS = {
            "eventTime", "logTime", "groupingId", "event", "callback", "eventToString", "thread", "nodeLogs", "endTime"
    };

    /** Column names in model order (for the show/hide menu). */
    public static java.util.List<String> columnNames() {
        return java.util.List.of(COLS);
    }

    private final LogStore store;
    private final LogIndex index;

    public LogTableModel(LogStore store) {
        this.store = store;
        this.index = store.index();
    }

    public LogStore store() {
        return store;
    }

    /** Notify the table that rows {@code [oldSize, current)} were appended (follow/tail mode). */
    public void rowsAppended(int oldSize) {
        int now = getRowCount();
        if (now > oldSize) fireTableRowsInserted(oldSize, now - 1);
    }

    @Override public int getRowCount() { return index.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int c) { return COLS[c]; }

    @Override
    public Class<?> getColumnClass(int c) {
        return switch (c) {
            case COL_EVENT_TIME, COL_LOG_TIME, COL_END_TIME -> Long.class;
            case COL_NODE_LOGS -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        return switch (col) {
            case COL_EVENT_TIME -> index.eventTime(row);
            case COL_LOG_TIME -> index.logTime(row);
            case COL_GROUPING -> index.groupingId(row);
            case COL_EVENT -> displayEvent(row);
            case COL_CALLBACK -> nz(index.callback(row));
            case COL_EVENT_TO_STRING -> displayEventToString(row);
            case COL_THREAD -> nz(index.thread(row));
            case COL_NODE_LOGS -> index.nodeLogsCount(row);
            case COL_END_TIME -> index.endTime(row);
            default -> null;
        };
    }

    /** Blank for ExportFunctionAuditEvent, otherwise the raw event type. */
    private String displayEvent(int row) {
        String event = index.event(row);
        return EXPORT_FN.equals(event) ? "" : nz(event);
    }

    /** Short {@code DeclaringClass.callback} for export-function events, else the raw eventToString. */
    private String displayEventToString(int row) {
        String event = index.event(row);
        if (EXPORT_FN.equals(event)) {
            String callback = index.callback(row);
            String declaring = simpleName(index.declaringType(row));
            if (callback != null && declaring != null) return declaring + "." + callback;
            if (callback != null) return callback;
        }
        return nz(index.eventToString(row));
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return null;
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // anomaly cues (index-resident, spec §8.2 / U2.5)
    public boolean isParseError(int row) { return index.parseError(row); }
    public boolean hasNaN(int row) { return index.hasNaN(row); }
    public boolean hasBreach(int row) { return index.hasBreach(row); }
}
