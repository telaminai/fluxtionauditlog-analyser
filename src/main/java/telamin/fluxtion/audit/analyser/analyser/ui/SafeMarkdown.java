package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M74 P2 (spec §5.9): a small, LINEAR parser for the CommonMark subset the transcript renders — ATX headings (1–3),
 * emphasis and strong, bullet and numbered lists (three levels), block quotes, inline code, fenced code blocks, tables
 * as monospace text, and bare {@code http(s)://} autolinks. <b>Everything else is literal text</b>, and three things are
 * refused by construction: HTML (a tag is its characters — there is no HTML renderer to reach), images
 * ({@code ![alt](url)} is the text {@code [image: alt] url}, never fetched) and scripts (nothing is evaluated).
 *
 * <p><b>Linear</b> (r8 F21): it runs again on every streamed revision, so pathological input (64 KB of unclosed
 * emphasis) must not go quadratic. Each delimiter's NEXT occurrence is precomputed in one backward pass, so finding a
 * closer is a lookup, and an unclosed delimiter is a literal character.
 *
 * <p>The model's text is untrusted data. This produces runs of text with a {@link Kind}; what is SHOWN for any control
 * or format character is {@link VisibleMarks}' job, applied by the view.
 */
public final class SafeMarkdown {

    private SafeMarkdown() {
    }

    /** A block of the answer. */
    public enum BlockKind { PARAGRAPH, HEADING, BULLET, NUMBERED, QUOTE, CODE, TABLE, BLANK }

    /** An inline run. */
    public enum Kind { TEXT, EMPHASIS, STRONG, CODE, LINK, IMAGE }

    /** A run of inline text; {@code url} for a link (and an image, whose URL is shown, never fetched). */
    public record Run(Kind kind, String text, String url) {
    }

    /**
     * A block: its kind, level (heading level 1–3, list depth 1–3), the list item's number, a code block's language and
     * text (exact), and its inline runs (empty for code).
     */
    public record Block(BlockKind kind, int level, int number, String language, String code, List<Run> runs) {
        public Block {
            runs = List.copyOf(runs == null ? List.of() : runs);
        }
    }

    /** Parse {@code source}; never throws, never loops more than linearly. */
    public static List<Block> parse(String source) {
        List<Block> out = new ArrayList<>();
        if (source == null || source.isEmpty()) return out;
        String[] lines = source.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String fence = trimmed.substring(0, 3);
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                boolean first = true;
                while (i < lines.length && !lines[i].stripLeading().startsWith(fence)) {
                    if (!first) code.append('\n');
                    code.append(lines[i]);
                    first = false;
                    i++;
                }
                if (i < lines.length) i++;                  // the closing fence; an unclosed block runs to the end
                out.add(new Block(BlockKind.CODE, 0, 0, lang.toLowerCase(Locale.ROOT), code.toString(), List.of()));
                continue;
            }
            if (trimmed.isEmpty()) {
                out.add(new Block(BlockKind.BLANK, 0, 0, null, null, List.of()));
                i++;
                continue;
            }
            int hashes = 0;
            while (hashes < trimmed.length() && hashes < 4 && trimmed.charAt(hashes) == '#') hashes++;
            if (hashes >= 1 && hashes <= 3 && trimmed.length() > hashes && trimmed.charAt(hashes) == ' ') {
                out.add(new Block(BlockKind.HEADING, hashes, 0, null, null, inline(trimmed.substring(hashes + 1).strip())));
                i++;
                continue;
            }
            if (trimmed.startsWith("|")) {
                StringBuilder table = new StringBuilder();
                while (i < lines.length && lines[i].stripLeading().startsWith("|")) {
                    if (!table.isEmpty()) table.append('\n');
                    table.append(lines[i]);
                    i++;
                }
                out.add(new Block(BlockKind.TABLE, 0, 0, null, table.toString(), List.of()));
                continue;
            }
            if (trimmed.startsWith("> ") || trimmed.equals(">")) {
                out.add(new Block(BlockKind.QUOTE, 1, 0, null, null, inline(trimmed.length() > 2 ? trimmed.substring(2) : "")));
                i++;
                continue;
            }
            int indent = line.length() - trimmed.length();
            int level = Math.min(3, 1 + indent / 2);
            if ((trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) && trimmed.length() > 2) {
                out.add(new Block(BlockKind.BULLET, level, 0, null, null, inline(trimmed.substring(2))));
                i++;
                continue;
            }
            int digits = 0;
            while (digits < trimmed.length() && digits < 9 && Character.isDigit(trimmed.charAt(digits))) digits++;
            if (digits > 0 && digits + 1 < trimmed.length() && (trimmed.charAt(digits) == '.' || trimmed.charAt(digits) == ')')
                    && trimmed.charAt(digits + 1) == ' ') {
                int n = Integer.parseInt(trimmed.substring(0, digits));
                out.add(new Block(BlockKind.NUMBERED, level, n, null, null, inline(trimmed.substring(digits + 2))));
                i++;
                continue;
            }
            out.add(new Block(BlockKind.PARAGRAPH, 0, 0, null, null, inline(line)));
            i++;
        }
        return out;
    }

    /**
     * Inline runs of one line, linearly: each delimiter's next occurrence is a lookup into an array filled by one
     * backward pass, so a delimiter with no closer costs one step and becomes a literal character.
     */
    static List<Run> inline(String s) {
        List<Run> runs = new ArrayList<>();
        int n = s.length();
        int[] nextStar = next(s, '*');
        int[] nextUnder = next(s, '_');
        int[] nextTick = next(s, '`');
        int[] nextClose = next(s, ']');
        int[] nextParen = next(s, ')');
        int[] nextDouble = nextDouble(s, '*');
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '`') {
                int close = i + 1 < n ? nextTick[i + 1] : -1;
                if (close > i + 1) {
                    flush(runs, text);
                    runs.add(new Run(Kind.CODE, s.substring(i + 1, close), null));
                    i = close + 1;
                    continue;
                }
            } else if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                int close = i + 2 < n ? nextDouble[i + 2] : -1;
                if (close > i + 2) {
                    flush(runs, text);
                    runs.add(new Run(Kind.STRONG, s.substring(i + 2, close), null));
                    i = close + 2;
                    continue;
                }
            } else if ((c == '*' || c == '_') && i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))) {
                int[] table = c == '*' ? nextStar : nextUnder;
                int close = table[i + 1];
                boolean wordUnderscore = c == '_' && i > 0 && Character.isLetterOrDigit(s.charAt(i - 1));
                if (close > i + 1 && !wordUnderscore && !Character.isWhitespace(s.charAt(close - 1))) {
                    flush(runs, text);
                    runs.add(new Run(Kind.EMPHASIS, s.substring(i + 1, close), null));
                    i = close + 1;
                    continue;
                }
            } else if ((c == '[' || (c == '!' && i + 1 < n && s.charAt(i + 1) == '['))) {
                int open = c == '!' ? i + 1 : i;
                int close = open + 1 < n ? nextClose[open + 1] : -1;
                if (close > open && close + 1 < n && s.charAt(close + 1) == '(') {
                    int end = close + 2 < n ? nextParen[close + 2] : -1;
                    if (end > close + 1) {
                        flush(runs, text);
                        String label = s.substring(open + 1, close);
                        String url = s.substring(close + 2, end).trim();
                        runs.add(c == '!' ? new Run(Kind.IMAGE, "[image: " + label + "] " + url, url)
                                : new Run(Kind.LINK, label, url));
                        i = end + 1;
                        continue;
                    }
                }
            } else if ((c == 'h' || c == 'H') && (s.startsWith("http://", i) || s.startsWith("https://", i))
                    && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) {
                int end = i;
                while (end < n && !Character.isWhitespace(s.charAt(end)) && s.charAt(end) != '<' && s.charAt(end) != '>'
                        && s.charAt(end) != '"') {
                    end++;
                }
                while (end > i && ".,;:!?)".indexOf(s.charAt(end - 1)) >= 0) end--;   // trailing punctuation is prose
                flush(runs, text);
                String url = s.substring(i, end);
                runs.add(new Run(Kind.LINK, url, url));
                i = end;
                continue;
            }
            text.append(c);
            i++;
        }
        flush(runs, text);
        return runs;
    }

    private static void flush(List<Run> runs, StringBuilder text) {
        if (text.isEmpty()) return;
        runs.add(new Run(Kind.TEXT, text.toString(), null));
        text.setLength(0);
    }

    /** {@code out[i]} = the index of the first {@code ch} at or after {@code i}, or -1. */
    private static int[] next(String s, char ch) {
        int[] out = new int[s.length() + 1];
        out[s.length()] = -1;
        for (int i = s.length() - 1; i >= 0; i--) out[i] = s.charAt(i) == ch ? i : out[i + 1];
        return out;
    }

    /** {@code out[i]} = the index of the first {@code chch} at or after {@code i}, or -1. */
    private static int[] nextDouble(String s, char ch) {
        int[] out = new int[s.length() + 1];
        out[s.length()] = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            out[i] = i + 1 < s.length() && s.charAt(i) == ch && s.charAt(i + 1) == ch ? i : out[i + 1];
        }
        return out;
    }

    /** Whether {@code url} is one the transcript may open after its confirmation: {@code http} or {@code https} only. */
    public static boolean openable(String url) {
        if (url == null) return false;
        String lower = url.strip().toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://")) && lower.length() > 8;
    }
}
