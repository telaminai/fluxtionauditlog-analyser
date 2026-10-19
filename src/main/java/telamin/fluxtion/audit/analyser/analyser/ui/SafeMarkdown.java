package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M74 P2 (spec §5.9): a small, LINEAR parser for the CommonMark subset the transcript renders — ATX headings (1–6),
 * emphasis, strong and strikethrough, bullet and numbered lists (three levels) including task-list checkboxes, block
 * quotes (nested up to three levels), inline code, fenced code blocks, thematic breaks ({@code ---}/{@code ***}/
 * {@code ___} alone on a line), GFM tables, and bare {@code http(s)://} autolinks. <b>Everything else is literal
 * text</b>, and three things are refused by construction: HTML (a tag is its characters — there is no HTML renderer
 * to reach), images ({@code ![alt](url)} is the text {@code [image: alt] url}, never fetched) and scripts (nothing is
 * evaluated).
 *
 * <p><b>Tables</b>: a run of lines starting with {@code |} is a table only when its second line is a valid GFM
 * separator row (per-column {@code :---}/{@code ---:}/{@code :---:}/{@code ---} cells, same column count as the
 * header); its cells become inline runs (so {@code **bold**} etc. work inside a cell), {@code \|} is a literal pipe,
 * and missing trailing pipes are tolerated. A pipe run with no valid separator row stays the literal, raw monospace
 * text it always was — the view draws that as-is, never interpreted. Tables whose rectangular display would exceed
 * 4,096 cells, or whose repeated stacked labels exceed four times the source size (with a 1,024-character floor),
 * also stay raw. This bounds display expansion as well as parsing; missing cells are never allocated.
 *
 * <p><b>Linear</b> (r8 F21): it runs again on every streamed revision, so pathological input (64 KB of unclosed
 * emphasis, or a huge pipe-delimited line) must not go quadratic. Each delimiter's NEXT occurrence is precomputed in
 * one backward pass, so finding a closer is a lookup, and an unclosed delimiter is a literal character; a table row is
 * split into cells in one forward pass.
 *
 * <p>The model's text is untrusted data. This produces runs of text with a {@link Kind}; what is SHOWN for any control
 * or format character is {@link VisibleMarks}' job, applied by the view.
 */
public final class SafeMarkdown {

    static final int MAX_TABLE_CELLS = 4_096;

    private SafeMarkdown() {
    }

    /** A block of the answer. */
    public enum BlockKind { PARAGRAPH, HEADING, BULLET, NUMBERED, QUOTE, CODE, TABLE, BLANK, RULE, TASK_OPEN, TASK_DONE }

    /** An inline run. */
    public enum Kind { TEXT, EMPHASIS, STRONG, CODE, LINK, IMAGE, STRIKE }

    /** A GFM column's declared alignment (the separator row's {@code :} placement); {@code LEFT} is the default. */
    public enum Align { LEFT, CENTER, RIGHT }

    /** A run of inline text; {@code url} for a link (and an image, whose URL is shown, never fetched). */
    public record Run(Kind kind, String text, String url) {
    }

    /** One table cell's inline runs (reuses {@link #inline}, so Markdown inside a cell renders). */
    public record Cell(List<Run> runs) {
        public Cell {
            runs = List.copyOf(runs == null ? List.of() : runs);
        }
    }

    /** A parsed GFM table: the header row, each column's alignment, and sparse body rows (no missing-cell padding). */
    public record Table(List<Cell> header, List<Align> aligns, List<List<Cell>> rows) {
        public Table {
            header = List.copyOf(header == null ? List.of() : header);
            aligns = List.copyOf(aligns == null ? List.of() : aligns);
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /**
     * A block: its kind, level (heading level 1–6, list/quote depth 1–3), the list item's number, a code block's
     * language and text (exact), its inline runs (empty for code and for a structured table), and a parsed
     * {@link Table} (non-null only for a {@code TABLE} block whose separator row validated; otherwise a {@code TABLE}
     * block's raw text is in {@code code}, exactly as typed, for the monospace fallback).
     */
    public record Block(BlockKind kind, int level, int number, String language, String code, List<Run> runs, Table table) {
        public Block {
            runs = List.copyOf(runs == null ? List.of() : runs);
        }

        /** A block with no table (every kind except a structured {@code TABLE}). */
        public Block(BlockKind kind, int level, int number, String language, String code, List<Run> runs) {
            this(kind, level, number, language, code, runs, null);
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
            while (hashes < trimmed.length() && hashes < 7 && trimmed.charAt(hashes) == '#') hashes++;
            if (hashes >= 1 && hashes <= 6 && trimmed.length() > hashes && trimmed.charAt(hashes) == ' ') {
                out.add(new Block(BlockKind.HEADING, hashes, 0, null, null, inline(trimmed.substring(hashes + 1).strip())));
                i++;
                continue;
            }
            if (trimmed.startsWith("|")) {
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && lines[i].stripLeading().startsWith("|")) {
                    tableLines.add(lines[i]);
                    i++;
                }
                Table table = parseTable(tableLines);
                if (table != null) {
                    out.add(new Block(BlockKind.TABLE, 0, 0, null, null, List.of(), table));
                } else {
                    out.add(new Block(BlockKind.TABLE, 0, 0, null, String.join("\n", tableLines), List.of()));
                }
                continue;
            }
            if (trimmed.matches("-{3,}") || trimmed.matches("\\*{3,}") || trimmed.matches("_{3,}")) {
                out.add(new Block(BlockKind.RULE, 0, 0, null, null, List.of()));
                i++;
                continue;
            }
            if (trimmed.startsWith(">")) {
                int level = 0;
                int idx = 0;
                while (idx < trimmed.length() && trimmed.charAt(idx) == '>') {
                    if (level < 3) level++;
                    idx++;
                    if (idx < trimmed.length() && trimmed.charAt(idx) == ' ') idx++;
                }
                out.add(new Block(BlockKind.QUOTE, level, 0, null, null, inline(trimmed.substring(idx))));
                i++;
                continue;
            }
            int indent = line.length() - trimmed.length();
            int level = Math.min(3, 1 + indent / 2);
            if ((trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) && trimmed.length() > 2) {
                String rest = trimmed.substring(2);
                if (rest.length() >= 4 && rest.charAt(0) == '[' && rest.charAt(2) == ']' && rest.charAt(3) == ' '
                        && (rest.charAt(1) == ' ' || rest.charAt(1) == 'x' || rest.charAt(1) == 'X')) {
                    boolean done = rest.charAt(1) != ' ';
                    out.add(new Block(done ? BlockKind.TASK_DONE : BlockKind.TASK_OPEN, level, 0, null, null,
                            inline(rest.substring(4))));
                    i++;
                    continue;
                }
                out.add(new Block(BlockKind.BULLET, level, 0, null, null, inline(rest)));
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
     * {@code lines} is a run of consecutive {@code |}-prefixed lines. Valid only when line 2 is a GFM separator row
     * (per-column {@code :?-+:?}) with the same column count as the header; returns {@code null} otherwise (the
     * caller keeps the raw text). Linear in the total length of {@code lines}.
     */
    private static Table parseTable(List<String> lines) {
        if (lines.size() < 2) return null;
        List<String> headerCells = splitRow(lines.get(0));
        List<String> sepCells = splitRow(lines.get(1));
        if (headerCells.isEmpty() || sepCells.size() != headerCells.size()) return null;
        // The view may stack every row and repeat its labels. Refuse expansion BEFORE allocating cell runs:
        // a wide header followed by thousands of one-cell rows is small input, not a licence for a huge matrix.
        long bodyRows = lines.size() - 2L;
        long sourceChars = lines.size() - 1L;
        for (String line : lines) sourceChars += line.length();
        long labelChars = 0;
        for (String cell : headerCells) labelChars += cell.length();
        if ((bodyRows + 1) * headerCells.size() > MAX_TABLE_CELLS
                || bodyRows * labelChars > Math.max(1_024L, 4L * sourceChars)) return null;
        List<Align> aligns = new ArrayList<>();
        for (String raw : sepCells) {
            String c = raw.strip();
            if (!c.matches(":?-+:?")) return null;
            boolean left = c.startsWith(":");
            boolean right = c.endsWith(":");
            aligns.add(left && right ? Align.CENTER : right ? Align.RIGHT : Align.LEFT);
        }
        List<Cell> header = new ArrayList<>();
        for (String c : headerCells) header.add(new Cell(inline(c.strip())));
        List<List<Cell>> rows = new ArrayList<>();
        for (int r = 2; r < lines.size(); r++) {
            List<String> raw = splitRow(lines.get(r));
            List<Cell> row = new ArrayList<>();
            for (int c = 0; c < headerCells.size() && c < raw.size(); c++) {
                row.add(new Cell(inline(raw.get(c).strip())));
            }
            rows.add(row);
        }
        return new Table(header, aligns, rows);
    }

    /**
     * Split one table row into its cell texts: a leading {@code |} is dropped, {@code \|} is a literal pipe, a
     * trailing (unescaped) {@code |} ends the row without an extra empty cell. One forward pass — linear.
     */
    private static List<String> splitRow(String line) {
        String t = line.strip();
        int n = t.length();
        int i = 0;
        if (i < n && t.charAt(i) == '|') i++;
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean lastWasDelimiter = false;
        for (; i < n; i++) {
            char c = t.charAt(i);
            if (c == '\\' && i + 1 < n && t.charAt(i + 1) == '|') {
                cur.append('|');
                i++;
                lastWasDelimiter = false;
                continue;
            }
            if (c == '|') {
                cells.add(cur.toString());
                cur.setLength(0);
                lastWasDelimiter = true;
                continue;
            }
            cur.append(c);
            lastWasDelimiter = false;
        }
        if (!lastWasDelimiter) cells.add(cur.toString());
        return cells;
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
        int[] nextDoubleTilde = nextDouble(s, '~');
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
            } else if (c == '~' && i + 1 < n && s.charAt(i + 1) == '~') {
                int close = i + 2 < n ? nextDoubleTilde[i + 2] : -1;
                if (close > i + 2) {
                    flush(runs, text);
                    runs.add(new Run(Kind.STRIKE, s.substring(i + 2, close), null));
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
