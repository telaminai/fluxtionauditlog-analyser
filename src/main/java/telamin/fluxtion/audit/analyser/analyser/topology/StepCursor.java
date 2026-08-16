package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.ArrayList;
import java.util.List;

/**
 * One cursor walking the log at two depths: record → its {@code nodeLogs} rows → next record → its rows.
 *
 * <p>A single Next/Prev drives both, so there is one notion of "where am I" rather than a record
 * selection and a separate step index that can disagree. Entering a record is itself a stop — the
 * <b>entry</b> position — which is where the topology marks how the cycle got in before any node is
 * highlighted.
 *
 * <p><b>Rows are not nodes.</b> A row is one {@code nodeLogs} entry, and the same {@code instanceId} may
 * occupy several rows of one record — a node that fired twice. Each row is its own step and the node
 * lights again; deduping would hide a real event.
 *
 * <p><b>What a row means depends on the audit regime</b> (see {@link AuditTrace}), and the cursor's own
 * labelling says which, because "row 3 of 8" invites reading 8 as "the nodes that ran":
 * <ul>
 *   <li><b>untraced</b> — rows are only the nodes that <em>logged</em>; others ran silently or may have;</li>
 *   <li><b>traced</b> — rows are every invocation, so stepping is exact.</li>
 * </ul>
 *
 * <p>No Swing: the view renders this, and the awkward parts — regime wording, roll-over between records,
 * duplicate rows — are testable against real fixtures without a display.
 */
public final class StepCursor {

    /** The record sequence being walked — the <b>filtered</b> view, so stepping honours the shared filter. */
    public interface RecordSource {
        int size();

        /** The record at {@code index} in filtered order. */
        LogRecord record(int index);
    }

    /** Position within a record: {@link #ENTRY} is "in this cycle, before its first row". */
    public static final int ENTRY = -1;

    private final RecordSource records;
    private int recordIndex;
    private int rowIndex = ENTRY;

    public StepCursor(RecordSource records) {
        this.records = records;
    }

    /** Convenience over a fixed list — used by tests and by anything holding records already. */
    public static StepCursor over(List<LogRecord> records) {
        return new StepCursor(new RecordSource() {
            @Override public int size() {
                return records.size();
            }

            @Override public LogRecord record(int index) {
                return records.get(index);
            }
        });
    }

    // ---- position ---------------------------------------------------------------------------------

    public boolean isEmpty() {
        return records.size() == 0;
    }

    public int recordIndex() {
        return recordIndex;
    }

    /** {@link #ENTRY} when sitting at the record's entry point, else the 0-based row. */
    public int rowIndex() {
        return rowIndex;
    }

    public boolean atEntry() {
        return rowIndex == ENTRY;
    }

    public LogRecord record() {
        return isEmpty() ? null : records.record(clampRecord(recordIndex));
    }

    public List<NodeLog> rows() {
        LogRecord record = record();
        return record == null ? List.of() : record.nodeLogs();
    }

    public int rowCount() {
        return rows().size();
    }

    /** The row under the cursor, or {@code null} at the entry position. */
    public NodeLog currentRow() {
        List<NodeLog> rows = rows();
        return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex) : null;
    }

    /** The node the cursor is on, or {@code null} at the entry position. */
    public String currentInstanceId() {
        NodeLog row = currentRow();
        return row == null ? null : row.instanceId();
    }

    /**
     * Instance ids stepped through in this cycle so far, in order and <b>including repeats</b>. The view
     * accumulates these within a cycle and clears them when the cursor rolls into the next record.
     */
    public List<String> steppedSoFar() {
        List<NodeLog> rows = rows();
        List<String> out = new ArrayList<>(Math.max(0, rowIndex + 1));
        for (int i = 0; i <= rowIndex && i < rows.size(); i++) out.add(rows.get(i).instanceId());
        return out;
    }

    /** True when this record's audit covers every invocation, so stepping is exact. */
    public boolean traced() {
        return AuditTrace.tracesEveryInvocation(rows());
    }

    /** Where this cycle entered the graph, per {@link EntryPointResolver}; empty when unresolved. */
    public List<String> entryPoints(ProcessorTopology topology) {
        LogRecord record = record();
        if (record == null || topology == null) return List.of();
        return List.copyOf(EntryPointResolver.resolve(topology, record.event(), record.eventToString()));
    }

    // ---- movement ---------------------------------------------------------------------------------

    public boolean canNext() {
        return !isEmpty() && (rowIndex + 1 < rowCount() || recordIndex + 1 < records.size());
    }

    public boolean canPrev() {
        return !isEmpty() && (rowIndex >= 0 || recordIndex > 0);
    }

    /** Advance one step: to the next row, or into the next record's entry when the rows run out. */
    public boolean next() {
        if (isEmpty()) return false;
        if (rowIndex + 1 < rowCount()) {
            rowIndex++;
            return true;
        }
        if (recordIndex + 1 < records.size()) {
            recordIndex++;
            rowIndex = ENTRY;               // entering a cycle is its own stop: the entry point
            return true;
        }
        return false;
    }

    /** Retreat one step: the log is complete in both directions, so entry rolls back to the previous
     *  record's <em>last</em> row rather than its entry. */
    public boolean prev() {
        if (isEmpty()) return false;
        if (rowIndex >= 0) {
            rowIndex--;                      // row 0 → ENTRY
            return true;
        }
        if (recordIndex > 0) {
            recordIndex--;
            rowIndex = rowCount() - 1;
            return true;
        }
        return false;
    }

    /** Jump to a record's entry — what selecting a row in the table does. */
    public void moveToRecord(int index) {
        if (isEmpty()) return;
        recordIndex = clampRecord(index);
        rowIndex = ENTRY;
    }

    private int clampRecord(int index) {
        return Math.max(0, Math.min(records.size() - 1, index));
    }

    // ---- labelling --------------------------------------------------------------------------------

    /**
     * Position wording for the status line. Names the regime, because "row 3 / 8" alone invites reading
     * 8 as "the nodes that ran" — which is only true when the record is traced.
     */
    public String positionLabel() {
        if (isEmpty()) return "no records";
        int total = rowCount();
        if (atEntry()) {
            return total == 0
                    ? "entry · no node logged in this cycle"
                    : "entry · " + total + (traced() ? " invocation(s)" : " logged row(s)");
        }
        return traced()
                ? "invocation " + (rowIndex + 1) + " / " + total
                : "row " + (rowIndex + 1) + " / " + total + " (logged nodes)";
    }

    /** The current row's values, summarised for the status line; empty at the entry position. */
    public String rowSummary() {
        NodeLog row = currentRow();
        if (row == null) return "";
        StringBuilder sb = new StringBuilder(row.instanceId());
        if (!row.entries().isEmpty()) {
            sb.append("  ·  ");
            for (int i = 0; i < row.entries().size(); i++) {
                if (i > 0) sb.append(", ");
                KV kv = row.entries().get(i);
                sb.append(kv.key()).append('=').append(kv.rawValue());
            }
        }
        return sb.toString();
    }
}
