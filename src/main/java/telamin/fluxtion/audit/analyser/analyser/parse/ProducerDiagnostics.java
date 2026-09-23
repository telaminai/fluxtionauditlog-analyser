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
        /**
         * The file holds NO records at all (MA-0).
         *
         * <p>Every empty shape used to return from {@link #of} before any check, so an empty log raised
         * nothing — marked or unmarked. A marker only changed the label from {@code unknown} to
         * {@code complete}; what made an empty file look healthy was the absent warning, not the verdict.
         *
         * <p>It is a FINDING beside an unchanged state, never a seventh stream-end state: the six are a
         * published contract that three store paths and {@code context} agree on.
         *
         * <p>Its wording is about the FILE, never the run. A buffered writer was measured holding zero
         * bytes on disk while ~24 records sat in memory, so "this run produced nothing" would be a claim
         * the file cannot support (V4).
         */
        EMPTY_LOG,
        /**
         * A document carried no {@code eventLogRecord:} key (MA-6).
         *
         * <p>Any producer can emit one; the known live source is AFMT-3, where a per-node level of
         * {@code NONE} corrupts the next record into a run-together line with no header and no keys.
         * Measured: such a document is COUNTED as a record and nothing flags it, so a marker over it
         * reads {@code complete} and vouches for the corruption.
         */
        NO_RECORD_KEY,
        /** Records arrived, but no node logged anything — the audit auditor was never installed. */
        NO_NODE_LOGS,
        /** The only thing in the log is the framework's own control event. */
        ONLY_CONTROL_EVENTS,
        /**
         * The READER could not read part of the source - a cut tail, names the file never defined.
         * Stated by the reader ({@code AuditLogReader.read} with a diagnostic consumer), carried by the
         * store, shown here beside the records it did read. Not a repair, not a record.
         */
        SOURCE_DAMAGE,
        /**
         * The container's own claim about completeness did not check out — records missing, more than
         * declared, or an end asserted with no readable count ({@code spec-audit-stream-end.md} D-E3).
         *
         * <p>Round five: these used to arrive as {@link #SOURCE_DAMAGE}, because every store diagnostic
         * did. A rolled set of individually whole files was therefore labelled <i>source damage</i> on
         * the status bar, which is both wrong and alarming — nothing about it is damaged, and what is
         * unknown is the SET, not any file in it.
         */
        COMPLETENESS_GAP,
        /**
         * A completeness statement that is not a fault: what the container established, and what it did
         * not. A set whose every member says it is whole lands here. It belongs in the tooltip and in
         * {@code context}, and it must NOT raise a warning on the status bar.
         */
        COMPLETENESS_NOTE
    }

    /** True for a finding a person should see flagged, as opposed to one that merely states a limit. */
    public boolean isWarning() {
        return !findings.isEmpty() && findings.get(0).kind() != Kind.COMPLETENESS_NOTE;
    }

    /** The first finding worth a warning glyph, or empty when the only findings are plain statements. */
    public java.util.Optional<Finding> firstWarning() {
        return findings.stream().filter(f -> f.kind() != Kind.COMPLETENESS_NOTE).findFirst();
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
        return of(idx, rawText, List.of());
    }

    /**
     * @param sourceDiagnostics what the reader said it could not read ({@link LogStore#sourceDiagnostics()});
     *                          each becomes a {@link Kind#SOURCE_DAMAGE} finding, listed FIRST, because
     *                          a log that is not all there is the first thing to know about it
     */
    public static ProducerDiagnostics of(LogIndex idx, IntFunction<String> rawText, List<String> sourceDiagnostics) {
        return of(idx, rawText, sourceDiagnostics, List.of(), false);
    }

    /**
     * @param completeness what the container said about its own wholeness; {@code note} when those are
     *                     statements of a limit rather than reports of a fault (D-E3)
     */
    public static ProducerDiagnostics of(LogIndex idx, IntFunction<String> rawText,
                                         List<String> sourceDiagnostics, List<String> completeness,
                                         boolean note) {
        List<Finding> out = new ArrayList<>();
        for (String d : sourceDiagnostics) {
            out.add(new Finding(Kind.SOURCE_DAMAGE, d));
        }
        for (String d : completeness) {
            out.add(new Finding(note ? Kind.COMPLETENESS_NOTE : Kind.COMPLETENESS_GAP, d));
        }
        if (idx == null) {
            // No index SUPPLIED — callers that only want the reader's diagnostics echoed pass null.
            // That is not the same as a log with no records, and MA-0 must not fire on it: f20 parses a
            // whole record and still passes null here. D-MA0b keys on size() == 0, and only that.
            return new ProducerDiagnostics(List.copyOf(out));
        }
        if (idx.size() == 0) {
            // MA-0. Placed HERE, before the early return, because that return is why an empty log has
            // always been silent. Damage findings are already in `out`, so SOURCE_DAMAGE is stated
            // first and this second (MA-0.6).
            out.add(new Finding(Kind.EMPTY_LOG,
                    "No records in this file. A file can be empty because nothing was written yet, "
                            + "because the writer is buffering, or because the processor cannot audit "
                            + "at all — this says the file is empty, not that the run produced nothing."));
            return new ProducerDiagnostics(List.copyOf(out));
        }

        int damage = out.size();
        unseparated(idx, rawText).ifPresent(out::add);
        // Only worth saying when the log is not ALREADY explained by one of the others: a file that ran
        // together also has no node logs on rows 1..n-1, and saying both would be two names for one bug.
        if (out.size() == damage) {
            onlyControlEvents(idx).ifPresent(out::add);
        }
        if (out.size() == damage) {
            noNodeLogs(idx).ifPresent(out::add);
        }
        // MA-6 is independent of the three above: a document with no record key is a producer fault
        // whatever else the file shows, and it is what lets a marker vouch for AFMT-3's output.
        noRecordKey(idx, rawText).ifPresent(out::add);
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
    /**
     * Does this document OPEN with the record key, as §1 requires?
     *
     * <p><b>Framing, not a substring search.</b> An earlier version asked whether the text contained
     * {@code eventLogRecord:} anywhere, which a payload defeats: a headerless document whose content
     * merely mentions the key read as a well-formed record, so a marker over it declared a count
     * including it and the file read {@code complete} with no finding. That is V1 — a payload changing
     * the verdict — and it is the same class as the injection MA-7 fixes.
     *
     * <p>The test is therefore positional: the FIRST non-blank, non-comment line must trim to the key.
     * Comments are skipped because §1 allows them before a record, and the analyser's own fixtures use
     * them.
     */
    private static boolean opensWithRecordKey(String text) {
        int from = 0;
        while (from <= text.length()) {
            int nl = text.indexOf('\n', from);
            int end = nl < 0 ? text.length() : nl;
            // StreamEndMarker.strip, not trim(): trim() keeps U+FEFF, so a healthy UTF-8 file with a
            // BOM read as having no record key on its first record. One strip, shared.
            String line = StreamEndMarker.strip(text.substring(from, end));
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line.equals(RECORD_KEY);
            }
            if (nl < 0) break;
            from = nl + 1;
        }
        return false;
    }

    /**
     * MA-6 — a document that carries no {@code eventLogRecord:} key.
     *
     * <p>The reader counts it as a record, so a marker written over it declares a count that includes
     * it and the file reads {@code complete}: the marker vouches for a document whose header, keys and
     * newlines are gone. Naming it is what stops a completeness claim covering corruption.
     *
     * <p>Reports the FIRST such row and how many there are, rather than one finding per row, so a badly
     * affected file says one clear thing.
     */
    private static java.util.Optional<Finding> noRecordKey(LogIndex idx, IntFunction<String> rawText) {
        if (rawText == null) return java.util.Optional.empty();
        int firstRow = -1;
        int affected = 0;
        for (int row = 0; row < idx.size(); row++) {
            String text = rawText.apply(row);
            if (text == null || text.isBlank()) continue;
            if (!opensWithRecordKey(text)) {
                if (firstRow < 0) firstRow = row;
                affected++;
            }
        }
        if (firstRow < 0) return java.util.Optional.empty();
        return java.util.Optional.of(new Finding(Kind.NO_RECORD_KEY,
                (affected == 1
                        ? "Record " + (firstRow + 1) + " carries no 'eventLogRecord:' key"
                        : affected + " records carry no 'eventLogRecord:' key, the first at "
                        + (firstRow + 1))
                        + ". A document without it is not a record this format can read, yet it is "
                        + "counted as one — so a stream-end marker written over it declares a count "
                        + "that includes it, and the log reads as complete while the document's "
                        + "header, keys and newlines are gone. The known producer-side cause is a "
                        + "per-node audit level of NONE, which corrupts the record that follows it."));
    }

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
