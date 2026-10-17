package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Font;

/**
 * Shared visual tokens so every panel uses the same spacing, section headers and status styling —
 * a lighter, more consistent look than per-panel {@code TitledBorder}s. Colours/fonts derive from
 * the active FlatLaf theme so this stays correct in both light and dark modes.
 */
public final class UiTheme {
    private UiTheme() { }

    /** Even inner padding for content panels. */
    public static final int PAD = 8;
    /** Standard gap between clustered controls. */
    public static final int GAP = 6;

    // ---- the spacing scale (owner UI feedback, 2026-10-17): one rhythm for a page's blocks, headings and controls ----
    /** The smallest step: between a caption and what it captions. */
    public static final int SPACE_XS = 4;
    /** Between a heading and its controls, and between controls in a column. */
    public static final int SPACE_SM = 8;
    /** Between related groups of a section. */
    public static final int SPACE_MD = 12;
    /** The gutter between blocks of a grid. */
    public static final int SPACE_LG = 20;
    /** Above and below a section that must not crowd its neighbours. */
    public static final int SPACE_XL = 28;
    /** The least height of a clickable target (a button, a chip). */
    public static final int TARGET_MIN_HEIGHT = 32;
    /** The corner radius of a control's shape. */
    public static final int CONTROL_RADIUS = 8;
    /**
     * Owner decision (2026-10-17): a spotlight callout wraps at about this many characters a line, and shows at most
     * {@link #CALLOUT_MAX_LINES} lines — the third ending "…" when there is more, the whole caption on hover or focus.
     */
    public static final int CALLOUT_LINE_CHARS = 40;
    public static final int CALLOUT_MAX_LINES = 3;

    /**
     * A light section header — a thin top rule and a small, muted title, with even inner padding.
     * Use where a panel genuinely benefits from a label; otherwise prefer {@link #pad()} (no label).
     */
    public static Border section(String title) {
        Color line = or(UIManager.getColor("Component.borderColor"), new Color(0x9AA0A6));
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line), title);
        Font base = UIManager.getFont("Label.font");
        if (base != null) tb.setTitleFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 1f));
        tb.setTitleColor(mutedForeground());
        return BorderFactory.createCompoundBorder(tb, pad());
    }

    /** Even inner padding, no label — the default frame for panels the surrounding context names. */
    public static Border pad() {
        return BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD);
    }

    /** Style a label as muted, slightly smaller status-bar text. */
    public static void status(JLabel l) {
        Font base = UIManager.getFont("Label.font");
        if (base != null) l.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 1f));
        l.setForeground(mutedForeground());
    }

    /*
     * Review M74 usability §4.1 (2026-10-17): every foreground token below meets WCAG 2.2 AA — 4.5:1 for text, 3:1 for a
     * control's boundary — on the panel, the content surface and the start page's band, in both themes. Each takes the
     * theme explicitly so {@code ThemeContrastTest} computes the ratios headless; the no-argument form reads the theme.
     * Each MEANING has its own token (§2, colour semantics): full Claude Code's cue is {@link #fullCue}, a denied call is
     * {@link #deniedForeground}, a status note is {@link #noteForeground}, a warning is {@link #warnForeground} — and
     * none of them is ever the only cue (each carries its words or its glyph).
     */

    /** A warning foreground — brick on light, salmon on dark. Recomputed per call, so a theme switch picks the right one. */
    public static Color warnForeground() {
        return warnForeground(ThemeManager.isDark());
    }

    public static Color warnForeground(boolean dark) {
        return dark ? new Color(0xF4A082) : new Color(0xB04020);
    }

    /**
     * A "this is working" foreground that reads on either theme (D-AI9). Recomputed per call, like
     * {@link #warnForeground()}, so a theme switch picks the right one on the next render — the M42-era
     * bug where explicit colours survived updateComponentTreeUI is the reason both are methods.
     */
    public static Color okForeground() {
        return okForeground(ThemeManager.isDark());
    }

    public static Color okForeground(boolean dark) {
        return dark ? new Color(0x6F, 0xC2, 0x76) : new Color(0x2E, 0x7D, 0x32);
    }

    /**
     * The "look at this, nothing is broken" foreground — AMBER on both themes (D-AI9: the status light has
     * no red; ATTENTION is another window owning the endpoint, or a transient start). Deliberately not
     * {@link #warnForeground()}, whose brick/salmon reads as red in a one-word status-bar label — the
     * owner's first sight of the light in that state was "red MCP starting" (2026-08-28).
     */
    public static Color attentionForeground() {
        return attentionForeground(ThemeManager.isDark());
    }

    public static Color attentionForeground(boolean dark) {
        return dark ? new Color(0xE6, 0xB4, 0x3C) : new Color(0x8A, 0x5A, 0x00);
    }

    /**
     * A status NOTE (the composer's hint, a placed draft): teal, its own meaning — no longer the orange that full Claude
     * Code's cue and a denied call also wore (review §2: one colour meant three things).
     */
    public static Color noteForeground() {
        return noteForeground(ThemeManager.isDark());
    }

    public static Color noteForeground(boolean dark) {
        return dark ? new Color(0x5FC9C9) : new Color(0x00676B);
    }

    /** A refusal or an error in words (an Apply refused, a pack refused): red, always with its words. */
    public static Color errorForeground() {
        return errorForeground(ThemeManager.isDark());
    }

    public static Color errorForeground(boolean dark) {
        return dark ? new Color(0xFF9A92) : new Color(0xCF222E);
    }

    /**
     * A call that was DENIED (by you, by the deadline, by the profile): a neutral ink, bold, with its words — the
     * person's own decision is not a warning, and it must not wear full Claude Code's colour.
     */
    public static Color deniedForeground(boolean dark) {
        return dark ? new Color(0xC9D1D9) : new Color(0x4B5563);
    }

    /** Full Claude Code's cue: the standard modes (default, plan), or {@code strong} for acceptEdits and auto. */
    public static Color fullCue(boolean strong, boolean dark) {
        if (strong) return dark ? new Color(0xFF8FBF) : new Color(0xC2185B);
        return dark ? new Color(0xF0A040) : new Color(0x9A4D00);
    }

    /**
     * The theme's muted foreground (secondary text). Review M74 usability #2: it was FlatLaf's DISABLED grey (#8C8C8C),
     * 2.5–3.3:1 in light and 3.1–4.1:1 in dark — the longest safety explanations were the hardest to read and looked
     * disabled. Now a secondary ink at 4.8:1 or better on every surface it sits on.
     */
    public static Color mutedForeground() {
        return mutedForeground(ThemeManager.isDark());
    }

    public static Color mutedForeground(boolean dark) {
        return dark ? new Color(0xA9B1BA) : new Color(0x57606A);
    }

    /**
     * Paint {@code text} as a placeholder in an empty text component (FlatLaf draws one for a text field, not for a text
     * area): the placeholder token, at the component's text inset; nothing when it has text.
     */
    public static void paintPlaceholder(javax.swing.text.JTextComponent c, java.awt.Graphics g, String text) {
        if (text == null || c.getDocument().getLength() > 0) return;
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(placeholderForeground(ThemeManager.isDark()));
            g2.setFont(c.getFont());
            java.awt.Insets in = c.getInsets();
            g2.drawString(text, in.left + 2, in.top + g2.getFontMetrics().getAscent());
        } finally {
            g2.dispose();
        }
    }

    /** Placeholder text in an empty field: 4.5:1 on the field's own fill. */
    public static Color placeholderForeground(boolean dark) {
        return dark ? new Color(0xB4BCC4) : new Color(0x6B7280);
    }

    /**
     * A control's boundary at rest — a text field, a checkbox, a button, a card, a chip's outline: 3:1 against the
     * panel, the surface and the start page's band (WCAG 1.4.11). FlatLaf's own greys were 1.6–2.0:1.
     */
    public static Color controlBorder() {
        return controlBorder(ThemeManager.isDark());
    }

    public static Color controlBorder(boolean dark) {
        return dark ? new Color(0x8A949E) : new Color(0x6E7F95);
    }

    /** The focus ring and the hover/focus edge of a card: the accent, 3:1 or better on every surface. */
    public static Color focusRing(boolean dark) {
        return accent(dark);
    }

    /** The panel colour FlatLaf paints for each theme (for the contrast test; the live one is {@code Panel.background}). */
    static Color panelOf(boolean dark) {
        return dark ? new Color(0x3C3F41) : new Color(0xF2F2F2);
    }

    /** The start page's hero band: a 0.10 wash of the accent on the surface. */
    static Color bandOf(boolean dark) {
        return mix(surface(dark), accent(dark), 0.10f);
    }

    // ---- content surfaces -------------------------------------------------------------------------

    /**
     * The background for a panel that <b>holds content</b> — source, the record detail, the node log, the
     * topology canvas — as against app chrome (toolbars, filter bars, the frame itself).
     *
     * <p>It is deliberately not the panel background. Content that shares its background with the chrome
     * around it has no edge, so a reader cannot see where the document starts; the effect is worst in dark
     * mode, where FlatLaf's panel grey is close enough to most text backgrounds to lose the boundary
     * entirely. Both values step clearly away from the theme's panel colour in the direction that theme
     * expects — lighter in light mode, darker in dark.
     */
    public static Color surface() {
        return surface(ThemeManager.isDark());
    }

    public static Color surface(boolean dark) {
        return dark ? new Color(0x1B1F24) : new Color(0xFCFDFF);
    }

    // ---- code type --------------------------------------------------------------------------------

    /**
     * Fonts we would rather set code in, best first. Swing's logical {@code Monospaced} is a per-platform
     * alias: on macOS it resolves to something decent, but elsewhere it can land on Courier, which is why
     * the same panel looks markedly worse on another machine. Naming real families first makes the good
     * case the default and leaves the alias as the floor.
     */
    private static final String[] MONO_PREFERENCE = {
            "JetBrains Mono", "SF Mono", "Menlo", "Cascadia Mono", "Consolas",
            "DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono", "Andale Mono"
    };

    private static String monoFamily;

    /** The best monospaced family installed, resolved once. */
    public static synchronized String monoFamily() {
        if (monoFamily != null) return monoFamily;
        java.util.Set<String> installed = new java.util.HashSet<>(java.util.Arrays.asList(
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : MONO_PREFERENCE) {
            if (installed.contains(candidate)) {
                monoFamily = candidate;
                return monoFamily;
            }
        }
        monoFamily = Font.MONOSPACED;
        return monoFamily;
    }

    /** A code font at {@code size} points. */
    public static Font mono(int size) {
        return new Font(monoFamily(), Font.PLAIN, size);
    }

    /**
     * Open the line spacing of a code/record document slightly.
     *
     * <p>Swing sets consecutive lines at the font's own leading, which for a monospaced face is tight
     * enough that dense key/value output reads as a block rather than as lines. A small amount of extra
     * space is most of the difference between Swing text and text on a web page, and costs one call.
     */
    public static void applyReadingRhythm(javax.swing.text.StyledDocument doc) {
        if (doc == null) return;
        javax.swing.text.SimpleAttributeSet spacing = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setLineSpacing(spacing, 0.18f);
        javax.swing.text.StyleConstants.setSpaceAbove(spacing, 0f);
        doc.setParagraphAttributes(0, Math.max(1, doc.getLength()), spacing, false);
    }

    /**
     * Tint for a cluster of <b>controls</b> — the time-range bar, a filter strip — as against
     * {@link #surface()}, which is for content. It shifts the theme's own panel colour a little rather
     * than naming a colour, so it holds for every FlatLaf theme the app offers (Light, Dark, IntelliJ,
     * Darcula) and for any the user adds later: lighter in a dark theme, darker in a light one, which is
     * the direction each theme already uses to mean "raised".
     */
    public static Color controlSurface() {
        Color panel = or(UIManager.getColor("Panel.background"), new Color(0xF2F2F2));
        return ThemeManager.isDark() ? shift(panel, 14) : shift(panel, -10);
    }

    /** Move a colour towards white ({@code amount > 0}) or black, clamped. */
    private static Color shift(Color c, int amount) {
        return new Color(
                Math.max(0, Math.min(255, c.getRed() + amount)),
                Math.max(0, Math.min(255, c.getGreen() + amount)),
                Math.max(0, Math.min(255, c.getBlue() + amount)));
    }

    /** Paint a control cluster with {@link #controlSurface()} — opaque, or the tint would not show. */
    public static void applyControlSurface(javax.swing.JComponent panel) {
        if (panel == null) return;
        panel.setOpaque(true);
        panel.setBackground(controlSurface());
    }

    // ---- accent -----------------------------------------------------------------------------------

    /**
     * The product accent. This is <b>not a new colour</b>: it is the exact blue the topology already
     * paints a hot edge and a selected node with, promoted to a shared token. A start page that
     * introduced its own brand blue would be the one surface in the app that looked like a different
     * product, which is the opposite of what an accent is for.
     */
    public static Color accent() {
        return accent(ThemeManager.isDark());
    }

    public static Color accent(boolean dark) {
        return dark ? new Color(0x6CB6FF) : new Color(0x1F6FEB);
    }

    /**
     * The accent as <b>text</b> on a {@link #surface()}. Deliberately not {@link #accent()}: the same
     * hue that reads well as a 2px stroke is thin and low-contrast as a run of characters, so each
     * theme moves it the way that theme needs — lighter on dark, deeper on light.
     */
    public static Color accentText() {
        return accentText(ThemeManager.isDark());
    }

    public static Color accentText(boolean dark) {
        return dark ? new Color(0x8FC8FF) : new Color(0x0B4FB8);
    }

    /** Readable ink ON an accent-filled shape. */
    public static Color onAccent() {
        return onAccent(ThemeManager.isDark());
    }

    public static Color onAccent(boolean dark) {
        return dark ? new Color(0x0D1117) : Color.WHITE;
    }

    /**
     * A wash of {@link #accent()} MIXED INTO the current surface, {@code strength} in 0..1.
     *
     * <p>Mixing rather than naming a pastel is what keeps this honest across themes: a fixed
     * {@code 0xEAF2FF} is a pleasant tint on the light theme and a glowing panel on the dark one. The
     * mix lands a proportional distance from whatever the surface actually is.
     */
    public static Color accentWash(float strength) {
        return mix(surface(), accent(), strength);
    }

    /** Linear blend, {@code amount} of {@code b} into {@code a}. */
    public static Color mix(Color a, Color b, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** A hairline for the edge of a {@link #surface()}, so the pane reads as a pane at any zoom. */
    public static Color surfaceEdge() {
        return ThemeManager.isDark() ? new Color(0x3A4149) : new Color(0xD6DBE1);
    }

    /** A wash of the accent mixed into the surface of the given theme (for the contrast test). */
    static Color accentWash(float strength, boolean dark) {
        return mix(surface(dark), accent(dark), strength);
    }

    /**
     * Paint {@code view} and its enclosing scroll pane as one content surface, edged against the app
     * background. Setting the view alone is not enough: the viewport shows through wherever the view does
     * not reach — around the margins and during a resize — which is what makes a short document look like
     * a strip of editor floating in a different-coloured panel.
     */
    public static void applySurface(javax.swing.JScrollPane scroll, javax.swing.JComponent view) {
        Color surface = surface();
        if (view != null) {
            view.setBackground(surface);
            view.setOpaque(true);
        }
        if (scroll != null) {
            scroll.setBackground(surface);
            scroll.getViewport().setBackground(surface);
            scroll.setBorder(BorderFactory.createLineBorder(surfaceEdge()));
        }
    }

    private static Color or(Color c, Color fallback) {
        return c != null ? c : fallback;
    }
}
