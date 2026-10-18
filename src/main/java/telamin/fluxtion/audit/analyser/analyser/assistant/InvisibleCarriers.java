package telamin.fluxtion.audit.analyser.analyser.assistant;

/**
 * Review PR #150 R2: invisible characters that are NOT format characters, so a check by {@link Character#FORMAT} passes
 * them — variation selectors (U+FE00–FE0F, U+180B–180F, U+E0100–E01EF), the combining grapheme joiner U+034F, the Hangul
 * fillers U+115F, U+1160, U+3164 and U+FFA0 and the Khmer inherent vowels U+17B4 and U+17B5. A run of variation
 * selectors encodes one byte each (the "emoji smuggling" technique), so text that LOOKS like one sentence can carry
 * instructions no one sees before it is sent.
 *
 * <p>One policy, shared by the prompt pack (refuses), a draft's names (drops) and the transcript (marks): a carrier is
 * {@linkplain #permitted permitted} only as ONE U+FE0F directly after an emoji, as in U+26A0 U+FE0F (a warning sign
 * shown as an emoji) — the emoji presentation selector, which carries at most one bit and never a run.
 */
public final class InvisibleCarriers {

    private InvisibleCarriers() {
    }

    /** The emoji presentation selector — the one carrier ever permitted, and only after an emoji. */
    public static final int EMOJI_PRESENTATION = 0xFE0F;

    /** Whether {@code cp} is a variation selector of any block. */
    public static boolean selector(int cp) {
        return (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0x180B && cp <= 0x180F) || (cp >= 0xE0100 && cp <= 0xE01EF);
    }

    /** Whether {@code cp} is an invisible filler or invisible combining character (not a variation selector). */
    public static boolean filler(int cp) {
        return cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x17B4
                || cp == 0x17B5;
    }

    /** Whether {@code cp} is an invisible carrier: a variation selector or a filler. */
    public static boolean carrier(int cp) {
        return selector(cp) || filler(cp);
    }

    /**
     * Whether the carrier at UTF-16 index {@code at} of {@code s} is permitted: it is U+FE0F, the code point before it is
     * an emoji that is not itself a carrier, and the code point after it is not a carrier (so never a run). An ASCII
     * emoji base ({@code 0-9 # *}) is permitted only as a keycap — followed by U+20E3 — so ordinary digits cannot carry it.
     */
    public static boolean permitted(String s, int at) {
        if (s.codePointAt(at) != EMOJI_PRESENTATION || at == 0) return false;
        int before = s.codePointBefore(at);
        if (carrier(before) || !Character.isEmoji(before)) return false;
        int next = at + 1 < s.length() ? s.codePointAt(at + 1) : -1;
        if (next >= 0 && carrier(next)) return false;
        return before >= 0x80 || next == 0x20E3;
    }

    /** UTF-16 index of the first carrier in {@code s} that is not {@linkplain #permitted permitted}, or -1. */
    public static int firstRefused(String s) {
        if (s == null) return -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (carrier(cp) && !permitted(s, i)) return i;
            i += Character.charCount(cp);
        }
        return -1;
    }
}
