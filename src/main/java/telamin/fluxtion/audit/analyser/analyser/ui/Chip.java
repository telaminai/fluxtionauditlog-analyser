package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * A header chip (§5.9) that is as wide as its words — and, when the row is narrower than that, as wide as the row, its
 * words wrapping (robot re-walk #6, 2026-10-18: at the default 1200×800 the docked column is about 190 px, and a chip such
 * as "model: opus (confirmed after the first turn)" ran off its right edge). Plain text always: a model name or a folder
 * is not the analyser's words, and a text area never renders markup.
 */
final class Chip extends FitLine {

    Chip(String text, boolean strong, Color edge) {
        super(text);
        setOpaque(true);
        setBackground(UiTheme.accentWash(strong ? 0.22f : 0.10f));
        setForeground(strong ? UiTheme.accentText() : UIManager.getColor("Label.foreground"));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(edge, 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        Font f = UIManager.getFont("Label.font");
        if (f != null) setFont(f.deriveFont(strong ? Font.BOLD : Font.PLAIN, f.getSize2D() - 1f));
        getAccessibleContext().setAccessibleName(text);
    }
}
