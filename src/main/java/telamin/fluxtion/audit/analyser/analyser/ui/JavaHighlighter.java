package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based Java syntax colouring for the source viewer (one file at a time). Theme-aware: chooses
 * a light or dark palette from {@link ThemeManager#isDark()} so code stays readable on any theme.
 * Distinct hues for keywords, types, methods, constants, numbers, annotations, strings and comments.
 */
public final class JavaHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "sealed", "permits", "yield", "true", "false", "null");

    private static final Pattern WORD = Pattern.compile("\\b[A-Za-z_$][\\w$]*\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d[\\w.]*\\b");
    private static final Pattern ANNOTATION = Pattern.compile("@[A-Za-z_$][\\w$]*");
    private static final Pattern STRING = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern ALL_CAPS = Pattern.compile("[A-Z][A-Z0-9_]*[A-Z0-9]");

    private SimpleAttributeSet base, keyword, type, method, constant, number, annotation, string, comment;

    public void render(StyledDocument doc, String text) {
        boolean dark = ThemeManager.isDark();
        base = attr(dark ? 0xC9D1D9 : 0x1F2328, false);
        keyword = attr(dark ? 0xFF7B72 : 0xCF222E, false);
        type = attr(dark ? 0x7EE787 : 0x0E7490, false);
        method = attr(dark ? 0xD2A8FF : 0x7C3AED, false);
        constant = attr(dark ? 0xFFA657 : 0xB45309, false);
        number = attr(dark ? 0x79C0FF : 0x2563EB, false);
        annotation = attr(dark ? 0xFFA657 : 0x9A3412, false);
        string = attr(dark ? 0xA5D6FF : 0x15803D, false);
        comment = attr(dark ? 0x8B949E : 0x6B7280, true);

        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, base);
        } catch (BadLocationException e) {
            return;
        }
        Matcher wm = WORD.matcher(text);
        while (wm.find()) {
            SimpleAttributeSet a = classifyWord(wm.group(), text, wm.end());
            if (a != null) doc.setCharacterAttributes(wm.start(), wm.group().length(), a, true);
        }
        apply(doc, text, NUMBER, number);
        apply(doc, text, ANNOTATION, annotation);
        apply(doc, text, STRING, string);
        apply(doc, text, LINE_COMMENT, comment);
        apply(doc, text, BLOCK_COMMENT, comment);
        UiTheme.applyReadingRhythm(doc);
    }

    private SimpleAttributeSet classifyWord(String w, String text, int end) {
        if (KEYWORDS.contains(w)) return keyword;
        if (w.length() > 1 && ALL_CAPS.matcher(w).matches()) return constant;
        if (Character.isUpperCase(w.charAt(0))) return type;
        if (followedByParen(text, end)) return method;
        return null;
    }

    private static boolean followedByParen(String text, int end) {
        int i = end;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        return i < text.length() && text.charAt(i) == '(';
    }

    private static void apply(StyledDocument doc, String text, Pattern p, SimpleAttributeSet a) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), a, true);
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
