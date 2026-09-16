package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.DefaultStyledDocument;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * A generated processor can embed a description or metadata as ONE string literal of tens of thousands of
 * characters. The string-literal regex was {@code "(\\.|[^"\\])*"} — an alternation inside a repetition, which
 * Java's regex engine matches by recursing once per character — so rendering such a file overflowed the EDT's
 * stack (reported on 1.13.0 from a JBang deployment, 2026-09-16).
 */
class JavaHighlighterLongLiteralTest {

    @Test
    void aFiftyThousandCharacterStringLiteralRendersWithoutOverflowingTheStack() {
        StringBuilder sb = new StringBuilder("package p;\nclass A {\n  String s = \"");
        for (int i = 0; i < 50_000; i++) sb.append((char) ('a' + (i % 26)));
        sb.append("\";\n  char c = 'x';\n}\n");
        String source = sb.toString();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        assertDoesNotThrow(() -> new JavaHighlighter().render(doc, source));
    }

    @Test
    void escapedQuotesInsideALongLiteralStillEndWhereTheLiteralEnds() {
        StringBuilder sb = new StringBuilder("class A { String s = \"");
        for (int i = 0; i < 20_000; i++) sb.append(i % 7 == 0 ? "\\\"" : "x");
        sb.append("\"; int n = 1; }");
        DefaultStyledDocument doc = new DefaultStyledDocument();
        assertDoesNotThrow(() -> new JavaHighlighter().render(doc, sb.toString()));
    }

    /** The actual trigger: one apostrophe in a javadoc and kilobytes of file after it with no closing quote. */
    @Test
    void anUnpairedApostropheInACommentDoesNotScanTheRestOfTheFile() {
        StringBuilder sb = new StringBuilder("// the node's handler\nclass A {\n");
        for (int i = 0; i < 3_000; i++) sb.append("  int f").append(i).append(" = ").append(i).append(";\n");
        sb.append("}\n");
        String source = sb.toString();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        assertDoesNotThrow(() -> new JavaHighlighter().render(doc, source));
        javax.swing.text.AttributeSet body = doc.getCharacterElement(source.indexOf("int f1000")).getAttributes();
        javax.swing.text.AttributeSet comment = doc.getCharacterElement(source.indexOf("node's")).getAttributes();
        org.junit.jupiter.api.Assertions.assertNotEquals(
                javax.swing.text.StyleConstants.getForeground(comment), javax.swing.text.StyleConstants.getForeground(body),
                "the body keeps its own colour: it was not swallowed into one literal starting at the apostrophe");
    }
}
