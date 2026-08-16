package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extracting a type's doc comment for the topology tooltip.
 *
 * <p>The cases that matter are the ones where a naive "find the nearest comment" is <b>wrong</b>: a
 * comment belonging to something else, a non-javadoc block comment, and a nested class inside a file
 * whose outer class is documented.
 */
class JavadocTest {

    @Test
    void takesTheCommentAttachedToTheDeclaration() {
        String src = """
                package com.acme;

                /**
                 * Publishes quotes. Second sentence here.
                 */
                public class QuotePublisher { }
                """;
        assertEquals("Publishes quotes. Second sentence here.", Javadoc.forType(src, "QuotePublisher"));
    }

    @Test
    void skipsBackOverModifiersAndAnnotations() {
        String src = """
                /** Handles market data. */
                @SuppressWarnings("unused")
                @Deprecated
                public static final class PriceListener { }
                """;
        assertEquals("Handles market data.", Javadoc.forType(src, "PriceListener"));
    }

    @Test
    void doesNotStealACommentThatBelongsToSomethingElse() {
        String src = """
                /** This documents the field, not the class. */
                private int count;

                public class Thing { }
                """;
        assertNull(Javadoc.forType(src, "Thing"),
                "there is code between the comment and the declaration");
    }

    @Test
    void ignoresAPlainBlockComment() {
        String src = """
                /* not javadoc, just a note */
                public class Thing { }
                """;
        assertNull(Javadoc.forType(src, "Thing"));
    }

    @Test
    void findsANestedClassRatherThanTheFilesOuterOne() {
        String src = """
                /** The outer holder. */
                public final class Nodes {

                    /** Tracks live orders. */
                    public static class OrderTracker { }
                }
                """;
        assertEquals("Tracks live orders.", Javadoc.forType(src, "OrderTracker"));
        assertEquals("The outer holder.", Javadoc.forType(src, "Nodes"));
    }

    @Test
    void stripsInlineTagsAndMarkupSoATooltipReadsAsProse() {
        String src = """
                /**
                 * Calls {@link Other#thing()} and returns {@code true}.
                 * <p><b>Note:</b> propagation stops here.
                 */
                class Calc { }
                """;
        String doc = Javadoc.forType(src, "Calc");
        assertNotNull(doc);
        assertFalse(doc.contains("{@link"), doc);
        assertFalse(doc.contains("<b>"), doc);
        assertTrue(doc.contains("Other#thing()"));
        assertTrue(doc.contains("propagation stops here"));
    }

    @Test
    void stopsAtTheBlockTags() {
        String src = """
                /**
                 * What it does.
                 *
                 * @param x ignored
                 * @return nothing useful
                 */
                class Calc { }
                """;
        assertEquals("What it does.", Javadoc.forType(src, "Calc"));
    }

    @Test
    void summaryIsTheFirstSentenceOnOneLine() {
        assertEquals("Publishes quotes.",
                Javadoc.summary("Publishes quotes.\nThen a second paragraph that a tooltip does not need."));
        assertEquals("No full stop here", Javadoc.summary("No full stop here"));
        assertNull(Javadoc.summary(null));
    }

    @Test
    void aDecimalPointDoesNotEndTheSummary() {
        assertEquals("Scales by 0.5 of the width.",
                Javadoc.summary("Scales by 0.5 of the width. More detail follows."));
    }

    @Test
    void missingTypeOrSourceIsNullRatherThanAThrow() {
        assertNull(Javadoc.forType(null, "Thing"));
        assertNull(Javadoc.forType("class Other { }", "Thing"));
        assertNull(Javadoc.forType("class Thing { }", null));
        assertNull(Javadoc.forType("class Thing { }", "  "));
    }

    @Test
    void anUndocumentedTypeYieldsNull() {
        assertNull(Javadoc.forType("public class Bare { }", "Bare"));
    }
}
