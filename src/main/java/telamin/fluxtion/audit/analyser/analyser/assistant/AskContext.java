package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.util.List;
import java.util.Set;

/**
 * M74 P2 (spec-claude-code-provider.md §5.5): what the frame knows when a menu opens — the RENDERING input of the
 * {@link AskCatalogue} (which items to show, enabled or not, and what a draft names). It is never a decision: whether a
 * composed draft may be sent is decided by the session nodes at Send, from the draft's binding.
 *
 * <p>Every field is a POINTER (an index, an id, a name, a time, a count) or a flag. There is deliberately no field for a
 * value, a payload, a note's words or a finding's prose: a template cannot interpolate what the context cannot carry.
 *
 * @param mode             which assistant the item drafts for (its label prefix)
 * @param selected         the selected record indexes (model rows), in order
 * @param flagged          the flagged subset of all records, by index
 * @param current          the current record index, or null
 * @param recordCount      how many records the open log has (0 when none is open)
 * @param logName          the open log's file NAME (never a path), or null
 * @param logIdentity      the open log's identity digest, or null
 * @param generation       the open log's generation (0 when none is open)
 * @param cycleNodeIds     the node ids that logged in the current record's cycle (Q1)
 * @param graphName        the open graph's name, or null
 * @param nodeId           the topology node under the pointer, or null
 * @param nodePosition     that node's 1-based position in the graph, for a node id that is not an identifier
 * @param nodeLogged       the analyser's own coverage reading of that node for the current record, or null
 * @param chartName        the chart under the pointer, or null
 * @param seriesExpr       its series expression, or null
 * @param pointTime        the point's time (epoch ms), or null
 * @param pointRecord      the record nearest the point, or null
 * @param noteNumber       the chart note's NUMBER on the chart (never its words), or null
 * @param rangeFrom        a selected time range's start (epoch ms), or null
 * @param rangeTo          its end, or null
 * @param rangeFirst       the first record in that range, or null
 * @param rangeLast        the last record in that range, or null
 * @param rangeCount       how many records it holds, or null
 * @param diffKeys         the key names two diffed records differ in (the diff dialog's own context)
 * @param inProcessActions whether the in-app assistant may run actions in-process (it can then follow "read it")
 */
public record AskContext(Mode mode, List<Integer> selected, Set<Integer> flagged, Integer current, int recordCount,
                         String logName, String logIdentity, long generation, List<String> cycleNodeIds,
                         String graphName, String nodeId, int nodePosition, Boolean nodeLogged,
                         String chartName, String seriesExpr, Long pointTime, Integer pointRecord, Integer noteNumber,
                         Long rangeFrom, Long rangeTo, Integer rangeFirst, Integer rangeLast, Integer rangeCount,
                         List<String> diffKeys, boolean inProcessActions) {

    /** The assistant a draft is for: its label prefix and its pop-up words. */
    public enum Mode { CLAUDE_CODE, IN_APP }

    public AskContext {
        mode = mode == null ? Mode.IN_APP : mode;
        selected = List.copyOf(selected == null ? List.of() : selected);
        flagged = Set.copyOf(flagged == null ? Set.of() : flagged);
        cycleNodeIds = List.copyOf(cycleNodeIds == null ? List.of() : cycleNodeIds);
        diffKeys = List.copyOf(diffKeys == null ? List.of() : diffKeys);
    }

    /** Nothing open and nothing under the pointer. */
    public static AskContext empty(Mode mode) {
        return builder(mode).build();
    }

    public boolean logOpen() {
        return logIdentity != null && generation > 0;
    }

    public static Builder builder(Mode mode) {
        return new Builder(mode);
    }

    public Builder toBuilder() {
        Builder b = new Builder(mode);
        b.selected = selected;
        b.flagged = flagged;
        b.current = current;
        b.recordCount = recordCount;
        b.logName = logName;
        b.logIdentity = logIdentity;
        b.generation = generation;
        b.cycleNodeIds = cycleNodeIds;
        b.graphName = graphName;
        b.nodeId = nodeId;
        b.nodePosition = nodePosition;
        b.nodeLogged = nodeLogged;
        b.chartName = chartName;
        b.seriesExpr = seriesExpr;
        b.pointTime = pointTime;
        b.pointRecord = pointRecord;
        b.noteNumber = noteNumber;
        b.rangeFrom = rangeFrom;
        b.rangeTo = rangeTo;
        b.rangeFirst = rangeFirst;
        b.rangeLast = rangeLast;
        b.rangeCount = rangeCount;
        b.diffKeys = diffKeys;
        b.inProcessActions = inProcessActions;
        return b;
    }

    /** Assembled by the frame when a menu opens; every setter takes a pointer, never a value. */
    public static final class Builder {
        private Mode mode;
        private List<Integer> selected = List.of();
        private Set<Integer> flagged = Set.of();
        private Integer current;
        private int recordCount;
        private String logName;
        private String logIdentity;
        private long generation;
        private List<String> cycleNodeIds = List.of();
        private String graphName;
        private String nodeId;
        private int nodePosition;
        private Boolean nodeLogged;
        private String chartName;
        private String seriesExpr;
        private Long pointTime;
        private Integer pointRecord;
        private Integer noteNumber;
        private Long rangeFrom;
        private Long rangeTo;
        private Integer rangeFirst;
        private Integer rangeLast;
        private Integer rangeCount;
        private List<String> diffKeys = List.of();
        private boolean inProcessActions = true;

        private Builder(Mode mode) {
            this.mode = mode;
        }

        public Builder mode(Mode m) { this.mode = m; return this; }
        public Builder selected(List<Integer> s) { this.selected = s; return this; }
        public Builder flagged(Set<Integer> f) { this.flagged = f; return this; }
        public Builder current(Integer c) { this.current = c; return this; }
        public Builder log(String name, String identity, long generation, int recordCount) {
            this.logName = name;
            this.logIdentity = identity;
            this.generation = generation;
            this.recordCount = recordCount;
            return this;
        }
        public Builder cycleNodeIds(List<String> ids) { this.cycleNodeIds = ids; return this; }
        public Builder graphName(String g) { this.graphName = g; return this; }
        public Builder node(String id, int position, Boolean logged) {
            this.nodeId = id;
            this.nodePosition = position;
            this.nodeLogged = logged;
            return this;
        }
        public Builder chartPoint(String chart, String expr, Long time, Integer record, Integer note) {
            this.chartName = chart;
            this.seriesExpr = expr;
            this.pointTime = time;
            this.pointRecord = record;
            this.noteNumber = note;
            return this;
        }
        public Builder range(Long from, Long to, Integer first, Integer last, Integer count) {
            this.rangeFrom = from;
            this.rangeTo = to;
            this.rangeFirst = first;
            this.rangeLast = last;
            this.rangeCount = count;
            return this;
        }
        public Builder diffKeys(List<String> keys) { this.diffKeys = keys; return this; }
        public Builder inProcessActions(boolean on) { this.inProcessActions = on; return this; }

        public AskContext build() {
            return new AskContext(mode, selected, flagged, current, recordCount, logName, logIdentity, generation,
                    cycleNodeIds, graphName, nodeId, nodePosition, nodeLogged, chartName, seriesExpr, pointTime,
                    pointRecord, noteNumber, rangeFrom, rangeTo, rangeFirst, rangeLast, rangeCount, diffKeys,
                    inProcessActions);
        }
    }
}
