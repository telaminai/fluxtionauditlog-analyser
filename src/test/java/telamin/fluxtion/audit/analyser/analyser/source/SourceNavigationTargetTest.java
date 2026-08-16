package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Landing on the <b>right place</b> in a source file, not merely the right file.
 *
 * <p>Asserted against the real generated processor and the real node holder, because both cases exist
 * only because of how Fluxtion actually emits code: one {@code handleEvent} overload per event type, and
 * example graphs that nest every node class inside one holder.
 */
class SourceNavigationTargetTest {

    private static final Path GENERATED =
            Path.of("examples/fixture-generator/src/main/java/com/acme/demo/generated/DemoQuoteProcessor.java");
    private static final Path NODES =
            Path.of("examples/fixture-generator/src/main/java/com/acme/demo/node/Nodes.java");

    private static String read(Path p) throws IOException {
        assertTrue(Files.isReadable(p), p + " missing — regenerate the fixture module");
        return Files.readString(p);
    }

    private static int lineOf(String source, int offset) {
        return source.substring(0, offset).split("\n", -1).length;
    }

    // ---- event → its dispatch ---------------------------------------------------------------------

    @Test
    void eachEventFindsItsOwnHandleEventOverload() throws IOException {
        String src = read(GENERATED);
        int market = SourceNavigation.eventHandlerOffset(src, "MarketDataEvent");
        int order = SourceNavigation.eventHandlerOffset(src, "OrderUpdateEvent");
        int breach = SourceNavigation.eventHandlerOffset(src, "RiskBreachEvent");

        assertTrue(market > 0 && order > 0 && breach > 0, "all three are handled by this processor");
        assertNotEquals(market, order, "a generated processor has one overload per event type");
        assertNotEquals(order, breach);

        // the offset is the START OF THE LINE (indentation included), which is what scrolling wants
        assertTrue(src.substring(market).stripLeading().startsWith("public void handleEvent(MarketDataEvent"),
                src.substring(market, market + 60));
        assertTrue(src.substring(order).stripLeading().startsWith("public void handleEvent(OrderUpdateEvent"));
    }

    @Test
    void plainMethodLookupWouldHaveLandedOnTheWrongOverload() {
        // this is the reason eventHandlerOffset exists rather than reusing methodDeclOffset
        String src = """
                public void handleEvent(AlphaEvent typedEvent) { }
                public void handleEvent(BetaEvent typedEvent) { }
                """;
        assertEquals(SourceNavigation.methodDeclOffset(src, "handleEvent"),
                SourceNavigation.eventHandlerOffset(src, "AlphaEvent"),
                "the first overload happens to be the right one here");
        assertNotEquals(SourceNavigation.methodDeclOffset(src, "handleEvent"),
                SourceNavigation.eventHandlerOffset(src, "BetaEvent"),
                "…and the wrong one here, which is the whole point");
    }

    @Test
    void anEventTheProcessorDoesNotHandleIsNotFound() throws IOException {
        assertEquals(-1, SourceNavigation.eventHandlerOffset(read(GENERATED), "NoSuchEvent"));
        assertEquals(-1, SourceNavigation.eventHandlerOffset(null, "MarketDataEvent"));
        assertEquals(-1, SourceNavigation.eventHandlerOffset("class X {}", " "));
    }

    @Test
    void aFullyQualifiedParameterTypeStillMatches() {
        String src = "public void handleEvent(com.acme.demo.event.Events.MarketDataEvent typedEvent) { }";
        assertEquals(0, SourceNavigation.eventHandlerOffset(src, "MarketDataEvent"));
    }

    // ---- node → its class inside a shared file -----------------------------------------------------

    @Test
    void eachNestedNodeClassIsFoundAtItsOwnDeclaration() throws IOException {
        String src = read(NODES);
        int publisher = SourceNavigation.typeDeclOffset(src, "QuotePublisher");
        int tracker = SourceNavigation.typeDeclOffset(src, "OrderTracker");
        int monitor = SourceNavigation.typeDeclOffset(src, "RiskMonitor");

        assertTrue(publisher > 0 && tracker > 0 && monitor > 0);
        assertTrue(lineOf(src, tracker) < lineOf(src, monitor), "declaration order, not first match");
        assertTrue(lineOf(src, monitor) < lineOf(src, publisher));
        assertTrue(src.substring(publisher).stripLeading().startsWith("public static class QuotePublisher"));
    }

    @Test
    void theOuterTypeIsFoundToo() throws IOException {
        String src = read(NODES);
        assertTrue(SourceNavigation.typeDeclOffset(src, "Nodes") >= 0);
        assertTrue(lineOf(src, SourceNavigation.typeDeclOffset(src, "Nodes"))
                        < lineOf(src, SourceNavigation.typeDeclOffset(src, "OrderTracker")),
                "the holder is declared before what it holds");
    }

    @Test
    void aSingleTypeFileNeedsNoSpecialCase() {
        // the declaration is near the top anyway, so the same call does the right thing
        String src = "package com.acme;\n\npublic final class Only {\n}\n";
        assertTrue(SourceNavigation.typeDeclOffset(src, "Only") > 0);
    }

    @Test
    void everyTypeKeywordIsRecognised() {
        assertTrue(SourceNavigation.typeDeclOffset("interface Thing {}", "Thing") >= 0);
        assertTrue(SourceNavigation.typeDeclOffset("enum Thing {}", "Thing") >= 0);
        assertTrue(SourceNavigation.typeDeclOffset("record Thing() {}", "Thing") >= 0);
        assertTrue(SourceNavigation.typeDeclOffset("public sealed class Thing {}", "Thing") >= 0);
    }

    @Test
    void aMentionIsNotADeclaration() {
        // "new QuotePublisher(...)" must not be mistaken for where the class is declared
        String src = "class Builder {\n  Object o = new QuotePublisher(a, b);\n}\n";
        assertEquals(-1, SourceNavigation.typeDeclOffset(src, "QuotePublisher"));
    }

    @Test
    void missingInputIsMinusOneRatherThanAThrow() {
        assertEquals(-1, SourceNavigation.typeDeclOffset(null, "Thing"));
        assertEquals(-1, SourceNavigation.typeDeclOffset("class Thing {}", null));
        assertEquals(-1, SourceNavigation.typeDeclOffset("class Thing {}", "  "));
        assertEquals(-1, SourceNavigation.typeDeclOffset("class Other {}", "Thing"));
    }
}
