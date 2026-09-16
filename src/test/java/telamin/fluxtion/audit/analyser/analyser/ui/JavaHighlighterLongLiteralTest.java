package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.DefaultStyledDocument;

import static org.junit.jupiter.api.Assertions.*;

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

    private static java.awt.Color fg(DefaultStyledDocument doc, int offset) {
        return javax.swing.text.StyleConstants.getForeground(doc.getCharacterElement(offset).getAttributes());
    }

    /** Review R2-F4: where a literal ends, by colour span — an escape cannot carry a literal across a line end. */
    @Test
    void anEscapeBeforeALineEndDoesNotJoinTwoLinesIntoOneLiteral() {
        String src = "String z = \"ok\";\nString a = \"abc\\\nnext\"; int n = 1;\nString b = \"x\ry\";";
        DefaultStyledDocument doc = new DefaultStyledDocument();
        new JavaHighlighter().render(doc, src);
        java.awt.Color literal = fg(doc, src.indexOf("ok"));                 // a terminated literal
        java.awt.Color body = fg(doc, src.indexOf(" n = 1") + 1);   // a plain identifier: `int` is keyword-coloured
        org.junit.jupiter.api.Assertions.assertNotEquals(literal, body, "control: a literal and plain code differ");
        assertEquals(body, fg(doc, src.indexOf("next")), "LF after a backslash ends the (unterminated) literal");
        assertEquals(body, fg(doc, src.indexOf("abc")), "and the unterminated literal itself colours nothing");
        assertEquals(body, fg(doc, src.indexOf("y\"") ), "CR is a line end too");
    }

    /** Review R2-F4: a text block spans lines and is coloured whole. */
    @Test
    void aTextBlockIsColouredAsOneLiteral() {
        String src = "String t = \"\"\"\n    alpha\n    beta\n    \"\"\";\nint n = 1;";
        DefaultStyledDocument doc = new DefaultStyledDocument();
        new JavaHighlighter().render(doc, src);
        java.awt.Color open = fg(doc, src.indexOf("\"\"\""));
        assertEquals(open, fg(doc, src.indexOf("alpha")), "the body is part of the literal");
        assertEquals(open, fg(doc, src.indexOf("beta")));
        org.junit.jupiter.api.Assertions.assertNotEquals(open, fg(doc, src.indexOf(" n = 1") + 1), "and the code after it is not");
    }
}
