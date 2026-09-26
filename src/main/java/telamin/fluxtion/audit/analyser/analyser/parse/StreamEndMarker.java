package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.Optional;

/**
 * The stream-end marker as it appears in a text container — {@code spec-audit-stream-end.md} D-E1.
 *
 * <p>Two flat scalars on an otherwise empty {@code eventLogRecord}:
 *
 * <pre>
 * ---
 * eventLogRecord:
 *   streamEnd: normal
 *   streamEndRecords: 25
 * ---
 * </pre>
 *
 * <p><b>Flat, not nested.</b> The spec first drew this as a nested mapping. Implementing it showed the
 * cost: {@link RecordParser} switches on top-level SCALAR keys, and a nested mapping would need new
 * parser machinery for a two-field payload. Both shapes are equally invisible to an older reader —
 * {@code c02-unknown-fields} pins that scalar and mapping unknowns are alike tolerated — so the flat
 * pair buys the same compatibility for materially less code.
 *
 * <p><b>Recognised here, not in {@link telamin.fluxtion.audit.analyser.analyser.model.LogRecord}.</b> The
 * marker is a container fact, and it must never reach the index, a count or the table (D-E4). Detecting
 * it at the store boundary keeps it out of the hot record type entirely: no field, no builder change,
 * nothing to remember to filter downstream.
 *
 * <p><b>Why recognition is an allow-list, corrected in review.</b> The first version asked only whether
 * any line of the record trimmed to something starting with {@code streamEnd:}. {@link RecordParser} is
 * indentation-insensitive, so nothing stopped that line from being the CONTENT of a multiline
 * {@code eventToString} — and a record whose event's {@code toString} happened to contain
 * {@code "streamEnd: normal"} was then dropped from the index on all three paths, and its absence
 * reported as missing records. That is silent, content-controlled data loss: a D-T8 violation, and
 * reachable by an ordinary producer without malice. The rule below is therefore the other way round:
 * a record is a marker only when EVERY line in it is one this marker is allowed to carry. A record that
 * holds anything else is a record, and is indexed, however much it also mentions {@code streamEnd}.
 */
public record StreamEndMarker(String reason, long records) {

    /** Cheap reject before any scanning: the overwhelming majority of records are not markers. */
    private static final String KEY = "streamEnd:";
    private static final String COUNT_KEY = "streamEndRecords:";

    /**
     * The marker carried by one record's text, or empty when it is an ordinary record.
     *
     * <p>A marker that carries no readable count yields {@code records = -1}, which
     * {@link StreamEnd#declared} reports as {@link StreamEnd.State#UNVERIFIED}: a marker that cannot say
     * how much it wrote is not evidence of completeness.
     */
    public static Optional<StreamEndMarker> of(String recordText) {
        if (recordText == null || recordText.indexOf(KEY) < 0) return Optional.empty();
        String reason = null;
        long records = -1;
        boolean sawCount = false;
        for (String raw : recordText.split("\n")) {
            String t = strip(raw);
            // Blank lines, the record header comment and the `eventLogRecord:` opener carry no content.
            if (t.isEmpty() || t.charAt(0) == '#' || t.equals("eventLogRecord:")) continue;
            if (t.startsWith(KEY)) {
                if (reason != null) return Optional.empty();       // two of them: not a marker
                reason = value(t.substring(KEY.length()));
            } else if (t.startsWith(COUNT_KEY)) {
                if (sawCount) return Optional.empty();
                sawCount = true;
                try {
                    String v = value(t.substring(COUNT_KEY.length()));
                    // §1a: ASCII digits with an optional sign, and nothing else. Long.parseLong accepts
                    // ANY Unicode decimal digit, so a fullwidth or Arabic-Indic "3" was read as 3 and a
                    // file reported COMPLETE where a reader following the prose said unverified — the
                    // unsafe direction, found by implementing §1a from its own text.
                    records = isAsciiInteger(v) ? Long.parseLong(v) : -1;
                } catch (NumberFormatException ignored) {
                    records = -1;                 // unreadable or overflowing count: no count at all
                }
                // A negative count normalises to the single "no count" value. Re-review's M8 showed this
                // line is an EQUIVALENT mutant against StreamEnd.declared, which already treats any
                // negative as unverified — so it is kept for the invariant on this record's own accessor,
                // not for the verdict, and a test now pins that accessor rather than the verdict.
                if (records < 0) records = -1;
            } else if (!isAllowedCompanion(t)) {
                // Anything else at all — an event, a nodeLogs block, a line of someone's toString —
                // makes this a record. Evidence is never dropped to recognise a container fact.
                return Optional.empty();
            }
        }
        return reason == null || reason.isEmpty() ? Optional.empty()
                : Optional.of(new StreamEndMarker(reason, records));
    }

    /**
     * {@link String#strip()} plus a leading byte-order mark.
     *
     * <p>{@code strip()} treats U+FEFF as a character, not whitespace, so a UTF-8 BOM at the head of a
     * file makes the first record's opener read as {@code "﻿eventLogRecord:"}. Re-review found a
     * BOM'd file whose FIRST record was a marker being indexed as an ordinary record, and the file then
     * reported one record more than it declared. Rare — a marker is seldom first — and a one-line fix, so
     * there is no reason to leave it.
     *
     * <p>Package-private so {@code ProducerDiagnostics} uses THIS one rather than its own. MA-6 first
     * shipped with {@link String#trim()}, which keeps the BOM, and flagged every healthy BOM'd file as
     * having no record key — a duplicated framing rule drifting from the original, which is the failure
     * this project has now made often enough to name.
     */
    static String strip(String line) {
        return AuditText.strip(line);
    }

    /**
     * §1a whitespace: space, tab, CR and LF. Nothing else.
     *
     * <p><b>The one helper, used for lines, values and counts alike.</b> Round six measured what three
     * different notions of whitespace cost: lines used {@link String#strip()}, which removes every
     * Unicode space, so a form feed or an em space before {@code streamEnd} made a marker the published
     * text says is not one — and the file read COMPLETE where a conforming reader said unknown. Values
     * used {@link String#trim()}, which removes every character below U+0021, so a count of {@code 3\f}
     * was accepted. Five of the ten disagreements went in the unsafe direction: claiming completeness a
     * conforming reader would not claim. There is now one definition and one function.
     */
    private static String asciiStrip(String s) {
        return AuditText.asciiStrip(s);
    }

    /**
     * The only other line a marker may carry: its own {@code logTime}.
     *
     * <p>§1a says a writer SHOULD omit it and MUST NOT set it later than the last record, because that is
     * the one way the marker can change an older reader's behaviour — measured against released 1.16.0,
     * a timed marker widened that reader's time range. Tolerated here because a file already written
     * that way must still be read correctly.
     */
    /** §1a: {@code [+-]?[0-9]+}, ASCII only. Nothing else is a count. */
    private static boolean isAsciiInteger(String v) {
        int i = v.isEmpty() ? 0 : (v.charAt(0) == '+' || v.charAt(0) == '-') ? 1 : 0;
        if (i >= v.length()) return false;
        for (; i < v.length(); i++) {
            if (v.charAt(i) < '0' || v.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean isAllowedCompanion(String trimmedLine) {
        return trimmedLine.startsWith("logTime:");
    }

    /**
     * The scalar after a key, with a trailing comment removed and surrounding quotes stripped.
     *
     * <p>Quoted first, THEN unquoted-with-comment. An earlier version stripped quotes before comments,
     * so {@code "stopping"  # shutting down} matched neither rule — the last character was not the
     * opening quote — and the value kept its quotes. A test caught it. Handling the quoted case first
     * also keeps a {@code #} INSIDE quotes, where it is data rather than a comment.
     */
    private static String value(String rest) {
        String v = asciiStrip(rest);
        if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'')) {
            int close = v.indexOf(v.charAt(0), 1);
            // §1a: the value is stripped AFTER it is extracted, quoted or not — so `"3 "` is the count 3
            // and agrees with the published text, which round six found it did not.
            if (close > 0) return asciiStrip(v.substring(1, close));
        }
        int hash = v.indexOf('#');
        if (hash >= 0) v = v.substring(0, hash);
        return asciiStrip(v);
    }
}
