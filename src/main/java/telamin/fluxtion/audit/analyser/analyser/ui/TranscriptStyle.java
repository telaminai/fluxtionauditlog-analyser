package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Color;

/**
 * M74 P2 (spec §5.9): the transcript's look, by role and kind, for each theme. Colour is NEVER the only signal: every role
 * has its text marker ({@code You}, {@code Claude Code}) and every mark its words ({@code ✓}, {@code refused},
 * {@code denied by you}) and screen-reader words that follow them ({@link #spoken}). Body text meets 4.5:1 against its background and marks 3:1, in
 * both themes — computed by {@code TranscriptStyleTest}, not asserted by eye.
 *
 * <p>The palette takes {@code dark} explicitly so it can be tested without switching the look and feel; the view passes
 * {@code ThemeManager.isDark()} and re-styles on a theme change.
 */
public final class TranscriptStyle {

    private TranscriptStyle() {
    }

    /** Who or what an entry is. */
    public enum Role {
        /** the person's own turn: an accent band */
        YOU,
        /** Claude's words: body text, safe Markdown */
        CLAUDE,
        /** a tool line: monospace, compact, with its mark */
        TOOL,
        /** a status or note line: muted, smaller */
        NOTE,
        /** an interrupted or ended turn: the answer kept, muted */
        STOPPED,
        /** M74 UX: a demonstration's scripted or recorded lines — body text, plain, never Markdown */
        PLAIN
    }

    /** A tool line's outcome mark. */
    public enum Mark {
        OK("✓", "succeeded"),
        /** Review M74 usability #5: what is SPOKEN follows the line's own words — see {@link TranscriptStyle#spoken}. */
        DENIED("denied", "denied"),
        REFUSED("refused", "refused by the analyser"),
        APPROVED("approved by you", "approved by you"),
        PENDING("…", "still running");

        /** The visible words (never colour alone). */
        public final String words;
        /** What a screen reader says. */
        public final String spoken;

        Mark(String words, String spoken) {
            this.words = words;
            this.spoken = spoken;
        }

        /** The mark of an adapter outcome word ({@code ✓}, {@code refused}, {@code denied …}). */
        public static Mark of(String outcome) {
            if (outcome == null || outcome.isBlank()) return PENDING;
            String o = outcome.strip();
            if (o.startsWith("✓")) return OK;
            if (o.startsWith("refused")) return REFUSED;
            if (o.startsWith("approved")) return APPROVED;
            if (o.startsWith("allowed")) return APPROVED;        // #10b: "allowed: it changes nothing"
            if (o.startsWith("denied")) return DENIED;
            return PENDING;
        }
    }

    /**
     * Review M74 usability #5 (WCAG 1.3.1, 4.1.2): what a screen reader says for a tool line's outcome — the SAME words the
     * line shows. "denied by you", "denied: not answered within 120 s" and "denied: not allowed in Safe analysis" are three
     * different decision makers; the mark's colour is one for all three, so the words must carry the difference.
     */
    public static String spoken(Mark mark, String outcome) {
        if (mark == Mark.PENDING || mark == Mark.OK || outcome == null || outcome.isBlank()) return mark.spoken;
        String o = outcome.strip();
        return mark == Mark.REFUSED && o.equals("refused") ? Mark.REFUSED.spoken : o;
    }

    /** One theme's colours. */
    public record Palette(Color background, Color body, Color muted, Color youBand, Color youText, Color link,
                          Color codeBackground, Color codeText, Color ok, Color denied, Color red, Color keyword,
                          Color string, Color comment, Color number, Color chipBackground, Color chipText,
                          Color fullStandard, Color fullStrong) {
    }

    private static final Palette LIGHT = new Palette(
            new Color(0xFCFDFF),   // background — UiTheme.surface()
            new Color(0x1F2328),   // body
            new Color(0x57606A),   // muted
            new Color(0xE8F0FD),   // the person's band
            new Color(0x1F2328),
            new Color(0x0B4FB8),   // link — UiTheme.accentText()
            new Color(0xF2F4F7),   // code background
            new Color(0x24292F),
            new Color(0x1A7F37),   // ✓
            new Color(0x4B5563),   // denied — a neutral ink, its own token (UiTheme.deniedForeground), never full's orange
            new Color(0xCF222E),   // refused
            new Color(0x8250DF),   // keyword
            new Color(0x0A3069),   // string
            new Color(0x5C6670),   // comment (review §4.1: #6E7781 was 4.47:1, 4.13:1 on the code block)
            new Color(0x0550AE),   // number
            new Color(0xDDEBFF),   // chip
            new Color(0x0B4FB8),
            new Color(0x9A4D00),   // full Claude Code: standard (default, plan) — UiTheme.fullCue
            new Color(0xC2185B));  // full Claude Code: strong (acceptEdits, auto)

    private static final Palette DARK = new Palette(
            new Color(0x1B1F24),
            new Color(0xE6EDF3),
            new Color(0x9DA7B3),
            new Color(0x1F2E44),
            new Color(0xE6EDF3),
            new Color(0x8FC8FF),
            new Color(0x11151A),
            new Color(0xE6EDF3),
            new Color(0x56D364),
            new Color(0xC9D1D9),   // denied — neutral
            new Color(0xFF7B72),
            new Color(0xD2A8FF),
            new Color(0xA5D6FF),
            new Color(0x8B949E),
            new Color(0x79C0FF),
            new Color(0x1C3352),
            new Color(0x9CCBFF),
            new Color(0xF0A040),
            new Color(0xFF8FBF));  // review §4.1: #FF6FA8 was 4.09:1 on the dark panel

    public static Palette palette(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    /** The words a role is marked with (colour is never the only signal). */
    public static String marker(Role role) {
        return switch (role) {
            case YOU -> "You";
            case CLAUDE -> "Claude Code";
            case TOOL -> "▸";
            case NOTE -> "";
            case STOPPED -> "— stopped";
            case PLAIN -> "";
        };
    }

    /** The muted line once per answer (§5.9). */
    public static final String NOT_VERIFIED = "the model's words, not a verified result";

    /** A role's text colour on its own background. */
    public static Color foreground(Role role, boolean dark) {
        Palette p = palette(dark);
        return switch (role) {
            case YOU -> p.youText();
            case CLAUDE, TOOL, PLAIN -> p.body();
            case NOTE, STOPPED -> p.muted();
        };
    }

    /** A role's background: the person's turn is a band, everything else sits on the surface. */
    public static Color background(Role role, boolean dark) {
        Palette p = palette(dark);
        return role == Role.YOU ? p.youBand() : p.background();
    }

    /** A mark's colour. */
    public static Color mark(Mark m, boolean dark) {
        Palette p = palette(dark);
        return switch (m) {
            case OK, APPROVED -> p.ok();
            case DENIED -> p.denied();
            case REFUSED -> p.red();
            case PENDING -> p.muted();
        };
    }

    /**
     * Full Claude Code's cue colour (owner request, 2026-10-17): {@code strong} for the modes that ask least
     * ({@code acceptEdits}, {@code auto}), standard for {@code default} and {@code plan}. Distinct per theme.
     */
    public static Color fullPosture(boolean strong, boolean dark) {
        Palette p = palette(dark);
        return strong ? p.fullStrong() : p.fullStandard();
    }

    /** The WCAG contrast ratio of two colours. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
