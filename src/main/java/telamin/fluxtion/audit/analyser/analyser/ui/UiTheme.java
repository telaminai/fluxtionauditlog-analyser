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

    private static Color or(Color c, Color fallback) {
        return c != null ? c : fallback;
    }
}
