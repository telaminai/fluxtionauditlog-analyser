package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.text.*;
import java.awt.Color;
import java.util.regex.Pattern;

/** XML text colouring only; parsing and location policy belong to the design model. */
public final class XmlHighlighter {
    public void render(StyledDocument doc, String text) {
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, colour(ThemeManager.isDark() ? 0xC9D1D9 : 0x1F2328));
            paint(doc, text, "</?[\\w:.-]+|/?>", ThemeManager.isDark() ? 0xFF7B72 : 0xCF222E);
            paint(doc, text, "\\b[\\w:.-]+(?=\\s*=)", ThemeManager.isDark() ? 0x79C0FF : 0x0550AE);
            paint(doc, text, "\"[^\"]*\"|'[^']*'", ThemeManager.isDark() ? 0xA5D6FF : 0x116329);
            paint(doc, text, "(?s)<!--.*?-->", ThemeManager.isDark() ? 0x8B949E : 0x6B7280);
            UiTheme.applyReadingRhythm(doc);
        } catch (BadLocationException e) { throw new IllegalStateException(e); }
    }
    private static SimpleAttributeSet colour(int rgb) { var a = new SimpleAttributeSet(); StyleConstants.setForeground(a, new Color(rgb)); return a; }
    private static void paint(StyledDocument doc, String text, String pattern, int rgb) {
        var m = Pattern.compile(pattern).matcher(text);
        while (m.find()) doc.setCharacterAttributes(m.start(), m.end() - m.start(), colour(rgb), true);
    }
}
