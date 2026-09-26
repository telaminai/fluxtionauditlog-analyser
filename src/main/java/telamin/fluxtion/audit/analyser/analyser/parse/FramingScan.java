package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * M68.3 (spec-evidence-integrity D-E9, acceptance 10): where, in one framed item's text, ANOTHER record appears to
 * start — the evidence for suspected collapsed framing, and nothing more than that.
 *
 * <p><b>The verdict this replaces was false.</b> The old check counted raw occurrences of {@code eventLogRecord:}
 * anywhere in the text, so a legal one-record file whose quoted value contained the key as literal text was reported
 * as "record 1 alone contains 2 records run together" — on {@code context.producer} and the status bar.
 *
 * <p>A candidate is now a PHYSICAL LINE that begins, at column 0, with {@code eventLogRecord:} and nothing after it
 * but spaces — the shape every record header has — and that does not lie inside a quoted scalar. Quotes are tracked
 * across lines, because a quoted scalar can span them; a quote only OPENS where a YAML value can begin (the first
 * character after {@code key: }, after {@code - }, or at the start of a line), so the apostrophe in {@code it's} opens
 * nothing and cannot hide a real collapse behind it.
 *
 * <p><b>Still a suspicion, always.</b> A writer that prints a payload unquoted, with a raw newline in it, can put a
 * line that looks exactly like a header inside one record. The text cannot tell those apart, so every finding built
 * from this says "suspected" and names the lines, and the reader checks them.
 *
 * <p>A single U+FEFF at the very start of the text is the file's byte-order mark, which the reader accepts; the first
 * line's header test looks past it (independent review R6). Nowhere else is a BOM skipped.
 *
 * @param candidates     1-based line numbers, within the item, of lines that look like the start of a further record
 * @param inspectedChars how much of the item was read
 * @param inspectedLines how many of its lines that covered
 * @param truncated      the item is longer than the limit, so the rest was NOT assessed
 */
public record FramingScan(List<Integer> candidates, int inspectedChars, int inspectedLines, boolean truncated) {

    static final String RECORD_KEY = "eventLogRecord:";

    public boolean suspected() {
        return !candidates.isEmpty();
    }

    public static FramingScan of(String text, int limit) {
        if (text == null || text.isEmpty()) return new FramingScan(List.of(), 0, 0, false);
        int end = Math.min(text.length(), limit);
        List<Integer> candidates = new ArrayList<>();
        char open = 0;                 // the quote currently open, or 0
        int line = 0;
        boolean seenHeader = false;    // the item's OWN header is not a further record
        int i = 0;
        while (i < end) {
            line++;
            int lineEnd = text.indexOf('\n', i);
            if (lineEnd < 0 || lineEnd > end) lineEnd = end;
            // Independent review R6: the reader accepts ONE byte-order mark at the start of the file, so the item's first
            // line may begin with one — and that line is still the item's own header. Unrecognised, a BOM'd collapsed file
            // had its SECOND header taken as its own and was reported clean. Only position 0, only once: a BOM anywhere
            // else is payload text, and never makes a line a header.
            // The BOM rule lives in AuditText (phase 1: every BOM site goes through it, guarded by ByteOrderMarkSitesTest).
            int headerAt = i == 0 && AuditText.isBom(text.charAt(0)) ? 1 : i;
            if (open == 0 && isHeader(text, headerAt, lineEnd)) {
                if (seenHeader) candidates.add(line);
                seenHeader = true;
            }
            open = quoteStateAfter(text, i, lineEnd, open);
            i = lineEnd + 1;
        }
        return new FramingScan(List.copyOf(candidates), end, line, text.length() > limit);
    }

    /** Column 0, the key, then only spaces or a CR. */
    private static boolean isHeader(String s, int start, int end) {
        if (!s.startsWith(RECORD_KEY, start)) return false;
        for (int k = start + RECORD_KEY.length(); k < end; k++) {
            char c = s.charAt(k);
            if (c != ' ' && c != '\t' && c != '\r') return false;
        }
        return true;
    }

    /**
     * Walk one line and return the quote still open at its end. Double quotes honour backslash escapes; single quotes
     * close on a lone {@code '} ({@code ''} is an escaped quote). Outside a quote, a quote opens only at a value
     * position — so ordinary prose with an apostrophe never opens one.
     */
    private static char quoteStateAfter(String s, int start, int end, char open) {
        int k = start;
        boolean valuePosition = open == 0;          // the start of a line is a value position (indentation skipped)
        while (k < end) {
            char c = s.charAt(k);
            if (open == '"') {
                if (c == '\\') { k += 2; continue; }
                if (c == '"') open = 0;
            } else if (open == '\'') {
                if (c == '\'') {
                    if (k + 1 < end && s.charAt(k + 1) == '\'') { k += 2; continue; }
                    open = 0;
                }
            } else {
                if (c == ' ' || c == '\t') { k++; continue; }            // indentation keeps a value position
                if (valuePosition && (c == '"' || c == '\'')) { open = c; valuePosition = false; k++; continue; }
                if (c == '#' && valuePosition) return 0;                  // a comment: the rest of the line is not YAML
                if (c == ':' && k + 1 < end && s.charAt(k + 1) == ' ') { valuePosition = true; k += 2; continue; }
                if (c == '-' && valuePosition && k + 1 < end && s.charAt(k + 1) == ' ') { k += 2; continue; }
                valuePosition = false;                                    // inside a plain scalar: quotes are text
            }
            k++;
        }
        return open;
    }
}
