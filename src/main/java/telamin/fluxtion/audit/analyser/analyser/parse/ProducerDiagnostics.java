package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * What the log says about the PRODUCER that wrote it — the mistakes made on the way in, named on the
 * way out.
 *
 * <p>Every one of these was hit while following *Producing an audit log* end to end (2026-08-25), and
 * every one of them <b>failed silently</b>: the file opened, looked like a log, and was wrong. That is
 * precisely the failure this application exists to make visible, so leaving it undetected at our own
 * front door was the sharpest inconsistency in the product.
 *
 * <p>These are <b>statements about the file</b>, not repairs. Nothing here re-frames, re-parses or
 * fixes anything — the same rule {@link TimeOrderReport} follows, and for the same reason: a
 * mis-written log is a finding about the emitter, and silently coping with it hides the bug from the
 * person who can fix it.
 *
 * <p>Pure and headless by construction: the inputs are the index and a function from row to text, so
 * every case below is unit-tested without a file, a store or a window.
 */
public record ProducerDiagnostics(List<Finding> findings) {

    /** How much of a record's text to scan for a second record header. */
    private static final int SCAN_LIMIT = 1 << 20;

    /** The marker that opens every record in the canonical format (spec §1). */
    private static final String RECORD_KEY = "eventLogRecord:";

    /** The control event the framework dispatches when the audit level is set. */
    private static final String CONTROL_EVENT = "EventLogControlEvent";

    public enum Kind {
        /**
         * Several records ran together because the writer never emitted the {@code ---} separator.
         * The worst of the three: the file opens, and the record COUNT is wrong with no other symptom.
         */
        UNSEPARATED,
        /** Records arrived, but no node logged anything — the audit auditor was never installed. */
        NO_NODE_LOGS,
        /** The only thing in the log is the framework's own control event. */
        ONLY_CONTROL_EVENTS
    }

    /**
     * @param kind    which mistake
     * @param message what is wrong AND what to do — a diagnostic that names a cause without a fix just
     *                moves the confusion
     */
    public record Finding(Kind kind, String message) {
    }

    public static ProducerDiagnostics clean() {
        return new ProducerDiagnostics(List.of());
    }

    public boolean isClean() {
        return findings.isEmpty();
    }

    /** The findings as plain lines, for the status bar and the {@code context} echo. */
    public List<String> messages() {
        return findings.stream().map(Finding::message).toList();
    }

    /**
     * Inspect a loaded log.
     *
     * @param idx     the index — record count, per-record event name and node-log count
     * @param rawText row → the record's own text, as {@link LogStore#rawText}. May return null.
     */
    public static ProducerDiagnostics of(LogIndex idx, IntFunction<String> rawText) {
        if (idx == null || idx.size() == 0) return clean();
        List<Finding> out = new ArrayList<>();

        unseparated(idx, rawText).ifPresent(out::add);
        // Only worth saying when the log is not ALREADY explained by one of the others: a file that ran
        // together also has no node logs on rows 1..n-1, and saying both would be two names for one bug.
        if (out.isEmpty()) {
            onlyControlEvents(idx).ifPresent(out::add);
        }
        if (out.isEmpty()) {
            noNodeLogs(idx).ifPresent(out::add);
        }
        return new ProducerDiagnostics(List.copyOf(out));
    }

    /**
     * A record whose own text contains a SECOND {@code eventLogRecord:} can only mean the separator is
     * missing: the framer splits on {@code ---} lines, so without them every record in the file is
     * delivered as one.
     *
     * <p>Detected on the text rather than by counting, because the count alone proves nothing — a
     * one-record log is perfectly legal, and that is exactly what an unseparated ten-record log looks
     * like from the outside.
     */
    private static java.util.Optional<Finding> unseparated(LogIndex idx, IntFunction<String> rawText) {
        if (rawText == null) return java.util.Optional.empty();
        for (int row = 0; row < idx.size(); row++) {
            String text = rawText.apply(row);
            if (text == null || text.isEmpty()) continue;
            int scanned = Math.min(text.length(), SCAN_LIMIT);
            int first = text.indexOf(RECORD_KEY);
            if (first < 0) continue;
            int second = text.indexOf(RECORD_KEY, first + RECORD_KEY.length());
            if (second >= 0 && second < scanned) {
                int buried = count(text, scanned);
                return java.util.Optional.of(new Finding(Kind.UNSEPARATED,
                        "This log is missing its record separators: record " + (row + 1) + " alone "
                                + "contains " + buried + " records run together, so the count above is "
                                + "wrong and every record after the first is invisible. A text audit "
                                + "log is a sequence of documents separated by lines of '---' (Format "
                                + "specification §1). record.toString() does NOT write it — the "
                                + "sink must: append(\"---\\n\") before each record."));
            }
        }
        return java.util.Optional.empty();
    }

    /** Records exist and not one of them carries a node log. */
    private static java.util.Optional<Finding> noNodeLogs(LogIndex idx) {
        for (int row = 0; row < idx.size(); row++) {
            if (idx.nodeLogsCount(row) > 0) return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Finding(Kind.NO_NODE_LOGS,
                "No node logged anything in any of the " + idx.size() + " record(s), so there is "
                        + "nothing to read, filter or plot. Usually the graph was built without "
                        + "addEventAudit() — without it the EventLogManager auditor is never installed "
                        + "and nodeLogs is empty for every cycle. A node also needs an audit logger "
                        + "(typically by extending EventLogNode) and must call auditLog.info(key, "
                        + "value) for its values to appear."));
    }

    /** Every record is the framework announcing its own logging configuration. */
    private static java.util.Optional<Finding> onlyControlEvents(LogIndex idx) {
        for (int row = 0; row < idx.size(); row++) {
            String event = idx.event(row);
            if (event == null || !event.contains(CONTROL_EVENT)) return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Finding(Kind.ONLY_CONTROL_EVENTS,
                "Every record here is the framework's own " + CONTROL_EVENT + " — the log contains "
                        + "the audit configuration and nothing the system did. Setting the audit level "
                        + "dispatches that event THROUGH the graph, so a sink attached before the level "
                        + "is set captures it. Set the level first (setAuditLogLevel then "
                        + "setAuditLogProcessor), or drop the control record in the sink."));
    }

    private static int count(String text, int limit) {
        int n = 0;
        for (int i = text.indexOf(RECORD_KEY); i >= 0 && i < limit;
             i = text.indexOf(RECORD_KEY, i + RECORD_KEY.length())) {
            n++;
        }
        return n;
    }
}
