package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.Optional;

/**
 * The stream-end marker as it appears in a text container — {@code spec-audit-stream-end.md} D-E1.
 *
 * <p>Two flat scalars on an ordinary {@code eventLogRecord}:
 *
 * <pre>
 * ---
 * eventLogRecord:
 *   logTime: 1789993421904
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
 * <p><b>Recognised here, not in {@link LogRecord}.</b> The marker is a container fact, and it must never
 * reach the index, a count or the table (D-E4). Detecting it at the store boundary keeps it out of the
 * hot record type entirely: no field, no builder change, nothing to remember to filter downstream.
 */
public record StreamEndMarker(String reason, long records) {

    /** Cheap reject before any scanning: the overwhelming majority of records are not markers. */
    private static final String KEY = "streamEnd:";

    /**
     * The marker carried by one record's text, or empty when it is an ordinary record.
     *
     * <p>A record that carries {@code streamEnd} but no readable count yields {@code records = -1},
     * which {@link StreamEnd#declared} then reports as a mismatch rather than silently accepting. A
     * marker that cannot say how much it wrote is not evidence of completeness.
     */
    public static Optional<StreamEndMarker> of(String recordText) {
        if (recordText == null || recordText.indexOf(KEY) < 0) return Optional.empty();
        String reason = null;
        long records = -1;
        for (String line : recordText.split("\n")) {
            String t = line.trim();
            if (t.startsWith(KEY)) {
                reason = value(t.substring(KEY.length()));
            } else if (t.startsWith("streamEndRecords:")) {
                String v = value(t.substring("streamEndRecords:".length()));
                try {
                    records = Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                    records = -1;                 // unreadable count: treated as no count at all
                }
            }
        }
        // `streamEnd` inside nodeLogs or a quoted value is not a marker: the key must be top level,
        // which for this format means it parsed to a non-empty reason.
        return reason == null || reason.isEmpty() ? Optional.empty()
                : Optional.of(new StreamEndMarker(reason, records));
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
        String v = rest.trim();
        if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'')) {
            int close = v.indexOf(v.charAt(0), 1);
            if (close > 0) return v.substring(1, close);
        }
        int hash = v.indexOf('#');
        if (hash >= 0) v = v.substring(0, hash).trim();
        return v;
    }
}
