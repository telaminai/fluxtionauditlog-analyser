package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeLogTokenizerTest {

    @Test
    void splitTopLevelProtectsParensBracketsAndBraces() {
        List<String> parts = NodeLogTokenizer.splitTopLevel("a: 1, b: Foo(x=1, y=2), c: [p, q, r], d: {m: 1, n: 2}", ',');
        assertEquals(4, parts.size(), "commas inside () [] {} must not split");
    }

    @Test
    void indexOfSepFindsFirstTopLevelColonSpace() {
        assertEquals(1, NodeLogTokenizer.indexOfSep("a: b"));
        // the ':' inside Foo(...) is protected; the real separator is the first top-level ": "
        int i = NodeLogTokenizer.indexOfSep("key: Foo(a: 1)");
        assertEquals("key", "key: Foo(a: 1)".substring(0, i));
    }

    @Test
    void parsesMutableOrderValueWithoutSplittingItsCommas() {
        String item = "bidMakerOrder: { orderStatus: NEW, price: 19.977, "
                + "orderUpdate: MutableOrder(clOrdId=1, venue=null, cancelledQuantity=0.0)}";
        NodeLog nl = NodeLogTokenizer.parseItem(item);
        assertEquals("bidMakerOrder", nl.instanceId());
        assertEquals(3, nl.entries().size());
        assertEquals("NEW", nl.last("orderStatus").rawValue());
        assertEquals(19.977, nl.last("price").numeric().getAsDouble(), 1e-9);
        KV order = nl.last("orderUpdate");
        assertNotNull(order);
        assertTrue(order.rawValue().startsWith("MutableOrder("));
        assertTrue(order.rawValue().endsWith(")"));
        assertTrue(order.rawValue().contains("cancelledQuantity=0.0"), "inner commas preserved");
    }

    @Test
    void keepsSpaceSeparatedToStringAsOneValue() {
        NodeLog nl = NodeLogTokenizer.parseItem(
                "venueMonitor_3: { venueStatus: connected=true requiredOrderVenues=[demoRfqOrders] missingOrderVenues=[]}");
        assertEquals(1, nl.entries().size());
        assertEquals("connected=true requiredOrderVenues=[demoRfqOrders] missingOrderVenues=[]",
                nl.last("venueStatus").rawValue());
    }

    @Test
    void nanIsNumericButNotFinite() {
        NodeLog nl = NodeLogTokenizer.parseItem("hedgeConnectionMonitor: { hedgeQuantity: NaN}");
        KV q = nl.last("hedgeQuantity");
        assertTrue(q.numeric().isPresent());
        assertTrue(Double.isNaN(q.numeric().getAsDouble()));
        assertFalse(q.isFiniteNumber());
    }

    @Test
    void listValueAndBareTokenTolerated() {
        NodeLog nl = NodeLogTokenizer.parseItem("n: { connectedVenues: [demoRfqOrders], connected: true, flagOnly}");
        assertEquals("[demoRfqOrders]", nl.last("connectedVenues").rawValue());
        assertEquals(Boolean.TRUE, nl.last("connected").asBoolean());
        assertNull(nl.last("flagOnly").rawValue(), "a bare token becomes key with null value");
    }

    @Test
    void unbalancedBracesNeverThrow() {
        assertDoesNotThrow(() -> NodeLogTokenizer.parseItem("weird: { a: b, c: Foo(bar, d: [1,2"));
        assertDoesNotThrow(() -> NodeLogTokenizer.parseBlock("    - x: }}}{{{\n    - y: no-colon-here\n"));
    }

    @Test
    void parseBlockSplitsItemsAndPreservesDuplicates() {
        String block = "    - a: { x: 1}\n    - a: { y: 2}\n    - b: { z: 3}\n";
        List<NodeLog> logs = NodeLogTokenizer.parseBlock(block);
        assertEquals(3, logs.size());
        assertEquals("a", logs.get(0).instanceId());
        assertEquals("a", logs.get(1).instanceId(), "duplicate instanceId preserved as separate entries");
        assertEquals("b", logs.get(2).instanceId());
    }
}
