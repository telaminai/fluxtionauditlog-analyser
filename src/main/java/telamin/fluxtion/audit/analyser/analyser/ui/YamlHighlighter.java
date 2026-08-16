package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight colouring of a Fluxtion audit record (one record at a time). Theme-aware: picks a
 * light or dark palette from {@link ThemeManager#isDark()} at render time so text stays readable
 * whatever FlatLaf theme is active.
 */
public final class YamlHighlighter {

    private static final Pattern KEY_PAT = Pattern.compile("([A-Za-z_][\\w.$]*)(\\s*:)(\\s|$)");
    private static final Pattern NUM_PAT = Pattern.compile("(?<![\\w.])[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])");
    private static final Pattern KEYWORD_PAT = Pattern.compile("\\b(true|false|null|NaN|Infinity)\\b");

    public void render(StyledDocument doc, String text) {
        boolean dark = ThemeManager.isDark();
        SimpleAttributeSet base = attr(dark ? 0xC9D1D9 : 0x1F2328, false);
        SimpleAttributeSet comment = attr(dark ? 0x8B949E : 0x6A737D, true);
        SimpleAttributeSet key = attr(dark ? 0x79C0FF : 0x0550AE, false);
        SimpleAttributeSet number = attr(dark ? 0xD2A8FF : 0x8250DF, false);
        SimpleAttributeSet keyword = attr(dark ? 0xFF7B72 : 0xB31D28, false);
        SimpleAttributeSet punct = attr(dark ? 0x8B949E : 0x57606A, false);

        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, base);
        } catch (BadLocationException e) {
            return;
        }
        int pos = 0;
        for (String line : text.split("\n", -1)) {
            styleLine(doc, line, pos, comment, key, number, keyword, punct);
            pos += line.length() + 1;
        }
        UiTheme.applyReadingRhythm(doc);
    }

    private void styleLine(StyledDocument doc, String line, int offset, SimpleAttributeSet comment,
                           SimpleAttributeSet key, SimpleAttributeSet number, SimpleAttributeSet keyword,
                           SimpleAttributeSet punct) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#")) {
            doc.setCharacterAttributes(offset, line.length(), comment, true);
            return;
        }
        Matcher km = KEY_PAT.matcher(line);
        while (km.find()) {
            doc.setCharacterAttributes(offset + km.start(1), km.group(1).length(), key, true);
            doc.setCharacterAttributes(offset + km.start(2), km.group(2).length(), punct, true);
        }
        Matcher nm = NUM_PAT.matcher(line);
        while (nm.find()) {
            doc.setCharacterAttributes(offset + nm.start(), nm.end() - nm.start(), number, true);
        }
        Matcher wm = KEYWORD_PAT.matcher(line);
        while (wm.find()) {
            doc.setCharacterAttributes(offset + wm.start(), wm.end() - wm.start(), keyword, true);
        }
    }

    private static SimpleAttributeSet attr(int rgb, boolean italic) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, new Color(rgb));
        StyleConstants.setFontFamily(a, UiTheme.monoFamily());
        StyleConstants.setItalic(a, italic);
        return a;
    }
}
