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
        if (idx == null && pendingFrame == null) {
            // No index SUPPLIED — callers that only want the reader's diagnostics echoed pass null.
            // That is not the same as a log with no records, and MA-0 must not fire on it: f20 parses a
            // whole record and still passes null here. D-MA0b keys on size() == 0, and only that.
            return new ProducerDiagnostics(List.copyOf(out));
        }
        boolean noRecords = idx == null || idx.size() == 0;
        if (idx != null && idx.size() == 0 && pendingFrame == null) {
            // MA-0. Placed HERE, before the early return, because that return is why an empty log has
            // always been silent. Damage findings are already in `out`, so SOURCE_DAMAGE is stated
            // first and this second (MA-0.6). Not said while Follow holds a pending frame: bytes are
            // arriving, and that path is MA-0.5's, still open.
            out.add(new Finding(Kind.EMPTY_LOG,
                    "No records in this file. A file can be empty because nothing was written yet, "
                            + "because the writer is buffering, or because the processor cannot audit "
                            + "at all — this says the file is empty, not that the run produced nothing."));
            return new ProducerDiagnostics(List.copyOf(out));
        }
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
        // MA-6 is independent of the three above: a document with no record key is a producer fault
        // whatever else the file shows, and it is what lets a marker vouch for AFMT-3's output.
        noRecordKey(idx, rawText).ifPresent(out::add);
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
     *
     * <p><b>A leading separator is part of the record boundary, not its content</b> (independent review,
     * F3). The built-in framers consume separators, so their record text never holds one. A reader PLUGIN
     * hands over whatever it renders, and the analyser's own binary reader renders a record as
     * {@code ---\neventLogRecord:\n…}. Stopping at that {@code ---} diagnosed every healthy binary
     * record as having no key. So plain separator lines are skipped before the first content line — PLAIN,
     * meaning {@code ---} with only space, tab or CR around it, the exact set the exporter escapes and the
     * framers split on. The protection this check exists for is untouched: the key must still be the
     * first CONTENT line, so a document that merely mentions it later is still named.
     */
    private static boolean opensWithRecordKey(String text) {
        return firstContentLine(text).equals(RECORD_KEY);
    }

    /** The first line that is not blank, a comment, or a plain leading separator; "" when there is none. */
    private static String firstContentLine(String text) {
        int from = 0;
        while (from <= text.length()) {
            int nl = text.indexOf('\n', from);
            int end = nl < 0 ? text.length() : nl;
            String raw = text.substring(from, end);
            // AuditText.strip, not trim(): trim() keeps U+FEFF, so a healthy UTF-8 file with a BOM
            // read as having no record key on its first record. One strip, shared.
            String line = AuditText.strip(raw);
            boolean plainSeparator = AuditText.asciiStrip(raw).equals("---");
            if (!line.isEmpty() && !line.startsWith("#") && !plainSeparator) return line;
            if (nl < 0) break;
            from = nl + 1;
        }
        return "";
    }

    /**
     * MA-6 — a document that carries no {@code eventLogRecord:} key.
     *
     * <p>The reader counts it as a record, so a marker written over it would count it too. Naming it is
     * what stops a completeness claim silently covering a document the format cannot read.
     *
     * <p><b>Observation, then conditions, then a possible cause — never the one case as every case</b>
     * (independent review, F6). The first wording said the log "reads as complete while the document's
     * header, keys and newlines are gone". That is what AFMT-3 produced once, under a marker. Said of an
     * unmarked, readable, merely headerless document it was false twice over: the state was UNKNOWN, and
     * the keys and newlines were plainly there. This finding does not know the container's state, so it
     * does not state one; it says what a marker WOULD and would not establish.
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
        String first = firstContentLine(rawText.apply(firstRow));
        return java.util.Optional.of(new Finding(Kind.NO_RECORD_KEY,
                (affected == 1
                        ? "Record " + (firstRow + 1) + " does not open with the 'eventLogRecord:' key"
                        : affected + " records do not open with the 'eventLogRecord:' key, the first at "
                        + (firstRow + 1))
                        + " — its first line is " + quoted(first) + ". The format requires each "
                        + "document to begin with that key, but this one is still counted as a record. "
                        + "If a stream-end marker covers it, the marker counts it too: a matching marker "
                        + "shows how many documents were written, not that each is well formed. One "
                        + "known producer-side cause is a per-node audit level of NONE, which can "
                        + "corrupt the record that follows it; this file does not say which cause "
                        + "applies here."));
    }

    /** A line shown back to the reader: bounded, and with control characters made visible. */
    private static String quoted(String line) {
        StringBuilder sb = new StringBuilder("'");
        int max = 60;
        for (int i = 0; i < line.length() && i < max; i++) {
            char c = line.charAt(i);
            sb.append(Character.isISOControl(c) ? '?' : c);
        }
        if (line.length() > max) sb.append('…');
        return sb.append("'").toString();
    }

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
            if (!isControlEvent(event)) return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Finding(Kind.ONLY_CONTROL_EVENTS,
                "Every record here is the framework's own " + CONTROL_EVENT + " — the log contains "
                        + "the audit configuration and nothing the system did. Setting the audit level "
                        + "dispatches that event THROUGH the graph, so a sink attached before the level "
                        + "is set captures it. Set the level first (setAuditLogLevel then "
                        + "setAuditLogProcessor), or drop the control record in the sink."));
    }

    /**
     * The framework's level-change event, matched on its simple class name EXACTLY: a fully-qualified
     * name is accepted, a lookalike such as {@code FakeEventLogControlEventX} is not. The ONE predicate —
     * {@code PerNodeLevelChanges} (MA-8) delegates here, so ONLY_CONTROL_EVENTS and coverage cannot
     * disagree about which records are control records (phase 1 round 4, F4).
     */
    public static boolean isControlEvent(String event) {
        if (event == null) return false;
        int dot = event.lastIndexOf('.');
        return (dot < 0 ? event : event.substring(dot + 1)).equals(CONTROL_EVENT);
    }
}
