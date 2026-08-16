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

    /** The theme's muted foreground (secondary text), with a sensible fallback. */
    public static Color mutedForeground() {
        return or(UIManager.getColor("Label.disabledForeground"), new Color(0x8A8F98));
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
        return ThemeManager.isDark() ? new Color(0x1B1F24) : new Color(0xFCFDFF);
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

    /** A hairline for the edge of a {@link #surface()}, so the pane reads as a pane at any zoom. */
    public static Color surfaceEdge() {
        return ThemeManager.isDark() ? new Color(0x3A4149) : new Color(0xD6DBE1);
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
