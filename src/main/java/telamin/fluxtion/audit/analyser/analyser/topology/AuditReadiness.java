package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.List;

/**
 * Whether a processor can produce an audit log <b>at all</b> — read from its graph, before any log
 * exists.
 *
 * <p><b>Why this belongs to the analyser rather than upstream.</b> Everything else that catches a
 * producer mistake catches it too late: {@code ProducerDiagnostics} needs a log to examine, and a log
 * that was never written cannot be examined. The graph, though, is emitted by the same build — and it
 * records the answer. If {@code addEventAudit()} was called, the compiler installs the
 * {@code EventLogManager} auditor and it appears as a node. If it was not, the node is absent and the
 * processor will never write a record, however carefully its nodes narrate themselves.
 *
 * <p><b>Verified, not inferred</b> (2026-08-27, rule 6). The same program — one {@code EventLogNode}
 * calling {@code auditLog.info(…)} three times, an audit level set, a sink attached — was run twice
 * with a single difference. With {@code addEventAudit()}: two records, 613 bytes. Without it:
 * <b>an empty file</b>. Not degraded, not partial: nothing. That is why the verdict below is phrased
 * flatly instead of hedged.
 *
 * <p><b>Limit, stated (review N1).</b> The evidence is the presence of Fluxtion's own
 * {@code EventLogManager}. A processor using a CUSTOM auditor that writes the same record format
 * would be reported NOT_ENABLED although it logs perfectly well. That is the safe direction for a
 * warning to be wrong in, but it is wrong, so a reader who has one should know why.
 *
 * <p>This is the earliest point at which the mistake is catchable: open the GraphML your build emitted
 * and the analyser can tell you that the run you are about to do will record nothing — before the run,
 * before the export, before someone opens an empty log and concludes the system was quiet.
 */
public record AuditReadiness(Verdict verdict, String message, int nodeCount) {

    /** The class the Fluxtion compiler installs when {@code addEventAudit()} is on the graph. */
    private static final String AUDITOR = "EventLogManager";

    /** The control event that auditor handles — present with the auditor, corroborating evidence. */
    private static final String CONTROL_EVENT = "EventLogControlEvent";

    public enum Verdict {
        /** The auditor is on the graph: this processor can write an audit log. */
        ENABLED,
        /** No auditor: this processor writes NOTHING, whatever its nodes do. */
        NOT_ENABLED,
        /** No graph loaded, so nothing can be said — never guessed from the log's contents. */
        UNKNOWN
    }

    public boolean isEnabled() {
        return verdict == Verdict.ENABLED;
    }

    /** True only when we positively know audit is off — the state worth interrupting someone for. */
    public boolean isProblem() {
        return verdict == Verdict.NOT_ENABLED;
    }

    public static AuditReadiness unknown() {
        return new AuditReadiness(Verdict.UNKNOWN, null, 0);
    }

    public static AuditReadiness of(ProcessorTopology topology) {
        if (topology == null || topology.nodeCount() == 0) return unknown();

        boolean auditor = false;
        boolean controlEvent = false;
        for (ProcessorTopology.Node n : topology.nodes()) {
            String type = n.simpleName() == null ? "" : n.simpleName();
            if (AUDITOR.equals(type)) auditor = true;
            if (CONTROL_EVENT.equals(type)) controlEvent = true;
        }

        int total = topology.nodeCount();
        if (auditor) {
            return new AuditReadiness(Verdict.ENABLED,
                    "Audit logging is installed on this processor (" + AUDITOR + " is on the graph), "
                            + "so a run can produce a log. Whether a given NODE appears in it is a "
                            + "separate question — a node logs only if it has an audit logger and "
                            + "calls it, which is why 'did not log' is never by itself 'did not run'.",
                    total);
        }
        // The corroborating half: the control event without its handler would be strange, and saying so
        // is cheaper than being quietly wrong about an unfamiliar producer.
        String caveat = controlEvent
                ? " (the graph does carry " + CONTROL_EVENT + ", which is unusual without the auditor —"
                        + " worth a second look at how this graph was built)"
                : "";
        return new AuditReadiness(Verdict.NOT_ENABLED,
                "This processor was built WITHOUT audit logging: " + AUDITOR + " is not on the graph, "
                        + "so it will write no audit log at all — not a sparse one, none" + caveat
                        + ". Nodes extending EventLogNode and calling auditLog.info(…) change nothing "
                        + "on their own; the auditor that collects them is installed by addEventAudit() "
                        + "on the graph builder. Add it and rebuild.",
                total);
    }

    /** The one-line form for a status bar or a panel row. */
    public String summary() {
        return switch (verdict) {
            case ENABLED -> "audit logging: installed";
            case NOT_ENABLED -> "⚠ audit logging NOT installed — this processor writes no log";
            case UNKNOWN -> "audit logging: unknown (no graph loaded)";
        };
    }

    /** Keys for the {@code context} echo — an agent should not have to parse the sentence. */
    public java.util.Map<String, Object> echo() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("auditLogging", verdict.name().toLowerCase(java.util.Locale.ROOT));
        if (message != null) out.put("auditLoggingNote", message);
        return out;
    }

    /** For tests and callers that want the evidence rather than the prose. */
    public static List<String> evidenceTypes() {
        return List.of(AUDITOR, CONTROL_EVENT);
    }
}
