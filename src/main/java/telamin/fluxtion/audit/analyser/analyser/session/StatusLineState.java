package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * INTEG-R10: which line the status bar states about the open log and graph, as the {@code statusLine} node decided it.
 *
 * <p>Before this, two writers set the bar independently: the snapshot-driven log line (once the log's evidence scan
 * landed) and the graph line {@code judgeOpenedGraph} composed when a graph opened. After {@code open {log, graphml}}
 * whichever ran last stayed, so the bar depended on timing (rule 9: one status writer). The node now decides which
 * line is in force from facts; the frame composes the words of that one line from the snapshot and sets it once.
 *
 * @param subject       what the line is about
 * @param logGeneration the log generation the decision is about, so a reopen restates its line
 */
public record StatusLineState(Subject subject, long logGeneration) {

    public enum Subject {
        /** No log is open: the bar says nothing about a log. */
        NO_LOG,
        /** A log is open but its evidence (producer findings, time order) has not landed: nothing may be stated yet. */
        WAITING,
        /** The log's own line: no graph is open beside it with a verdict, or Follow is on (the Following line). */
        LOG,
        /** The graph line: a graph is open with a verdict about this log; it carries the log's warnings. */
        LOG_AND_GRAPH
    }

    public static final StatusLineState NONE = new StatusLineState(Subject.NO_LOG, -1);

    /**
     * The decision, pure. A graph line needs the log's evidence as much as the log line does, because it states the
     * log's warnings; so neither is stated until the scan of THIS generation has landed. Follow keeps the log's
     * Following line, which is what a live tail is about.
     */
    public static StatusLineState decide(boolean logOpen, long generation, boolean evidenceLanded, boolean graphVerdict,
                                         boolean following) {
        if (!logOpen) return NONE;
        if (!evidenceLanded) return new StatusLineState(Subject.WAITING, generation);
        if (graphVerdict && !following) return new StatusLineState(Subject.LOG_AND_GRAPH, generation);
        return new StatusLineState(Subject.LOG, generation);
    }
}
