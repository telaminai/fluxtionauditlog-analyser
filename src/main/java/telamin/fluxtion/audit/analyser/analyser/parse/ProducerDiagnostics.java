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
        COMPLETENESS_NOTE,
        /**
         * M68.3 (D-E9): an item longer than the framing check reads, in which nothing suspicious was found in the part
         * it did read. The rest was NOT assessed — which is a limit to state, never a clean bill (acceptance 10).
         */
        FRAMING_NOT_ASSESSED
    }

    /** Findings that state a limit rather than report a fault: they reach the tooltip and context, never a glyph. */
    private static boolean isNote(Kind k) {
        return k == Kind.COMPLETENESS_NOTE || k == Kind.FRAMING_NOT_ASSESSED;
    }

    /** True for a finding a person should see flagged, as opposed to one that merely states a limit. */
    public boolean isWarning() {
        return findings.stream().anyMatch(f -> !isNote(f.kind()));
    }

    /** The first finding worth a warning glyph, or empty when the only findings are plain statements. */
    public java.util.Optional<Finding> firstWarning() {
        return findings.stream().filter(f -> !isNote(f.kind())).findFirst();
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
        return of(idx, rawText, sourceDiagnostics, completeness, note, null);
    }

    /**
     * @param pendingFrame M68.3: the text still being written under Follow — after the last {@code ---}, not yet a
     *                     record — or null. It is SCANNED, so a live collapsed log is suspected before its first
     *                     separator ever arrives, and it is never accepted as a record by being looked at.
     */
    public static ProducerDiagnostics of(LogIndex idx, IntFunction<String> rawText,
                                         List<String> sourceDiagnostics, List<String> completeness,
                                         boolean note, String pendingFrame) {
        List<Finding> out = new ArrayList<>();
        for (String d : sourceDiagnostics) {
            out.add(new Finding(Kind.SOURCE_DAMAGE, d));
        }
        for (String d : completeness) {
            out.add(new Finding(note ? Kind.COMPLETENESS_NOTE : Kind.COMPLETENESS_GAP, d));
        }
        boolean noRecords = idx == null || idx.size() == 0;
        if (noRecords && pendingFrame == null) return new ProducerDiagnostics(List.copyOf(out));

        int damage = out.size();
        if (!noRecords) unseparated(idx, rawText, out);
        // Independent review R7: the pending frame is scanned unless a collapse is ALREADY suspected. It was skipped
        // whenever any finding had been added — including a NOT_ASSESSED one, which says nothing about the pending frame.
        if (out.stream().skip(damage).noneMatch(f -> f.kind() == Kind.UNSEPARATED)) {
            pendingUnseparated(pendingFrame).ifPresent(out::add);
        }
        if (noRecords) return new ProducerDiagnostics(List.copyOf(out));
        boolean framingExplained = out.stream().anyMatch(f -> f.kind() == Kind.UNSEPARATED);
        if (framingExplained) {
            out.removeIf(f -> f.kind() == Kind.FRAMING_NOT_ASSESSED);   // one root cause; the limit is moot beside it
        }
        // Only worth saying when the log is not ALREADY explained by one of the others: a file that ran
        // together also has no node logs on rows 1..n-1, and saying both would be two names for one bug.
        boolean explained = out.stream().skip(damage).anyMatch(f -> !isNote(f.kind()));
        if (!explained) {
            onlyControlEvents(idx).ifPresent(out::add);
        }
        explained = out.stream().skip(damage).anyMatch(f -> !isNote(f.kind()));
        if (!explained) {
            noNodeLogs(idx).ifPresent(out::add);
        }
        return new ProducerDiagnostics(List.copyOf(out));
    }

    /**
     * M68.3 (D-E9): SUSPECTED collapsed framing — a record whose text contains lines that start like further records.
     * {@link FramingScan} decides what counts: a column-0 header line outside any quoted value. The old test counted the
     * key anywhere, including inside a quoted value, and called a legal one-record file two records run together.
     *
     * <p>Detected on the text rather than by counting, because the count alone proves nothing — a one-record log is
     * perfectly legal, and that is exactly what an unseparated ten-record log looks like from the outside. An item
     * too long to scan whole, with nothing found in the part scanned, is reported as NOT ASSESSED rather than clean.
     */
    private static void unseparated(LogIndex idx, IntFunction<String> rawText, List<Finding> out) {
        if (rawText == null) return;
        Finding notAssessed = null;
        for (int row = 0; row < idx.size(); row++) {
            String text = rawText.apply(row);
            if (text == null || text.isEmpty()) continue;
            FramingScan scan = FramingScan.of(text, SCAN_LIMIT);
            if (scan.suspected()) {
                out.add(new Finding(Kind.UNSEPARATED, suspectedMessage("record " + (row + 1), scan)));
                return;
            }
            if (scan.truncated() && notAssessed == null) {
                notAssessed = new Finding(Kind.FRAMING_NOT_ASSESSED, "Record " + (row + 1) + " is longer than the "
                        + scan.inspectedChars() + " characters the framing check reads, and nothing in that part "
                        + "looked like a further record. Whether more records run into the rest was NOT assessed.");
            }
        }
        if (notAssessed != null) out.add(notAssessed);
    }

    private static java.util.Optional<Finding> pendingUnseparated(String pendingFrame) {
        if (pendingFrame == null || pendingFrame.isBlank()) return java.util.Optional.empty();
        FramingScan scan = FramingScan.of(pendingFrame, SCAN_LIMIT);
        if (scan.suspected()) {
            return java.util.Optional.of(new Finding(Kind.UNSEPARATED, suspectedMessage(
                    "the record still being written (not yet ended by '---', and not counted)", scan)));
        }
        // Independent review R7: the indexed path said "NOT assessed" for a record too long to scan; the pending path
        // said nothing, so a long live frame read as checked. It is said here too — as pending, never as a record.
        if (scan.truncated()) {
            return java.util.Optional.of(new Finding(Kind.FRAMING_NOT_ASSESSED, "The record still being written (not "
                    + "yet ended by '---', and not counted) is longer than the " + scan.inspectedChars() + " characters the "
                    + "framing check reads, and nothing in that part looked like a further record. Whether more records "
                    + "run into the rest was NOT assessed."));
        }
        return java.util.Optional.empty();
    }

    private static String suspectedMessage(String where, FramingScan scan) {
        int runTogether = scan.candidates().size() + 1;
        String lines = scan.candidates().size() <= 8 ? scan.candidates().toString()
                : scan.candidates().subList(0, 8) + " and " + (scan.candidates().size() - 8) + " more";
        return "Suspected missing record separators: " + where + " appears to hold " + runTogether
                + " records run together — its line(s) " + lines + " start like new records ('" + RECORD_KEY
                + "' at the start of a line, outside any quoted value) with no '---' before them. Inspected lines 1–"
                + scan.inspectedLines() + " (" + scan.inspectedChars() + " characters)"
                + (scan.truncated() ? "; the rest was not assessed" : "") + ". If they are records, the count above "
                + "is wrong and every record after the first is hidden. A text audit log is a sequence of documents "
                + "separated by lines of '---' (Format specification §1). record.toString() does NOT write it — the "
                + "sink must: append(\"---\\n\") before each record. If instead a value was written unquoted with a "
                + "line break in it, quote it.";
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

}
