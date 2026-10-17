package telamin.fluxtion.audit.analyser.analyser.ui;

/**
 * M74 P2 (spec §5.9, r8 F16): what is seen is what is copied. A model's words can hold terminal escape sequences, C0/C1
 * control characters, bidirectional overrides and isolates and zero-width characters, any of which can make a command
 * that is SHOWN differ from what Copy puts on the clipboard. None is ever interpreted: each is shown as a visible mark —
 * ESC as {@code ␛} (so {@code ESC[31m} reads {@code ␛[31m}), everything else as {@code ⟨U+202E⟩} — in body text and code
 * blocks alike. Newline and tab are the only control characters kept as themselves.
 */
public final class VisibleMarks {

    private VisibleMarks() {
    }

    /** The mark shown for ESC. */
    public static final String ESC_MARK = "␛";

    /** Whether {@code cp} is one the transcript shows as a mark rather than as itself. */
    public static boolean hidden(int cp) {
        if (cp == '\n' || cp == '\t') return false;
        if (cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp <= 0x9F)) return true;            // C0, DEL, C1
        if ((cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069)) return true;  // bidi overrides, isolates
        if (cp == 0x200E || cp == 0x200F || cp == 0x061C) return true;                       // directional marks
        int type = Character.getType(cp);
        return type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    /** {@code s} as shown: each hidden character replaced by its visible mark. */
    public static String show(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int width = Character.charCount(cp);
            if (hidden(cp)) {
                if (sb == null) sb = new StringBuilder(s.length() + 16).append(s, 0, i);
                sb.append(mark(cp));
            } else if (sb != null) {
                sb.appendCodePoint(cp);
            }
            i += width;
        }
        return sb == null ? s : sb.toString();
    }

    /** The mark for one hidden character. */
    public static String mark(int cp) {
        return cp == 0x1B ? ESC_MARK : String.format("⟨U+%04X⟩", cp);
    }

    /** How many characters of {@code s} are shown as marks — what Copy says when it copies them as themselves. */
    public static int count(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (hidden(cp)) n++;
            i += Character.charCount(cp);
        }
        return n;
    }

    /** The words a Copy says (§5.9): plain, or that the copied text holds hidden characters shown as marks. */
    public static String copiedWords(String source) {
        int n = count(source);
        return n == 0 ? "copied" : "copied; it contains " + n + " hidden character" + (n == 1 ? "" : "s")
                + ", shown as marks";
    }
}
