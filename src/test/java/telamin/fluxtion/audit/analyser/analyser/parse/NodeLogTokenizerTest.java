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

    // ---- quoted scalars (format-spec §3): the lossless spelling for text that would be syntax ----

    @Test
    void aQuotedValueIsDecodedAndMarkedAsAString() {
        NodeLog nl = NodeLogTokenizer.parseItem("n: { status: \"ok, price: 42.0\", qty: 7}");
        assertEquals(2, nl.entries().size(), "the comma and colon inside the quotes split nothing: " + nl.entries());
        KV status = nl.last("status");
        assertEquals("ok, price: 42.0", status.rawValue());
        assertTrue(status.quoted());
        assertNull(nl.last("price"), "no figure was manufactured");
        assertEquals(7, nl.last("qty").numeric().getAsDouble(), 0);
    }

    @Test
    void escapesInsideAQuotedValueDecode_andNeverEndTheQuoteEarly() {
        String item = "n: { v: \"say \\\"hi\\\", back\\\\slash, tab\\there, line\\nbreak\", w: 1}";
        NodeLog nl = NodeLogTokenizer.parseItem(item);
        assertEquals(2, nl.entries().size(), nl.entries().toString());
        assertEquals("say \"hi\", back\\slash, tab\there, line\nbreak", nl.last("v").rawValue());
        assertEquals("1", nl.last("w").rawValue());
    }

    @Test
    void aQuotedScalarIsAStringWhateverItSpells() {
        NodeLog nl = NodeLogTokenizer.parseItem(
                "n: { a: \"null\", b: \"42.0\", c: \"true\", d: \"NaN\", e: \"\", f: null, g: 42.0, h: true}");
        assertFalse(nl.last("a").isNull(), "the STRING null is not null");
        assertEquals("null", nl.last("a").rawValue());
        assertTrue(nl.last("b").numeric().isEmpty(), "the STRING 42.0 is not a figure");
        assertTrue(nl.last("b").graphValue().isEmpty());
        assertNull(nl.last("c").asBoolean(), "the STRING true is not a flag");
        assertTrue(nl.last("d").numeric().isEmpty());
        assertEquals("", nl.last("e").rawValue());
        assertTrue(nl.last("e").quoted());
        // and the bare spellings keep their types, exactly as every text log has always read
        assertTrue(nl.last("f").isNull());
        assertEquals(42.0, nl.last("g").numeric().getAsDouble(), 0);
        assertEquals(Boolean.TRUE, nl.last("h").asBoolean());
    }

    @Test
    void quotedKeysAndInstanceIdsDecodeToo() {
        NodeLog nl = NodeLogTokenizer.parseItem("\"odd}: {node\": { \"a, b: c\": 1, plain: 2}");
        assertEquals("odd}: {node", nl.instanceId());
        assertEquals(2, nl.entries().size(), nl.entries().toString());
        assertEquals("1", nl.last("a, b: c").rawValue());
        assertEquals("2", nl.last("plain").rawValue());
    }

    @Test
    void anythingNotEntirelyOneQuotedScalarIsTheRawTextItAlwaysWas() {
        assertEquals("\"partly", NodeLogTokenizer.parseItem("n: { v: \"partly}").last("v").rawValue());
        assertEquals("\"a\" b", NodeLogTokenizer.parseItem("n: { v: \"a\" b}").last("v").rawValue());
        assertEquals("\"a\"b\"", NodeLogTokenizer.parseItem("n: { v: \"a\"b\"}").last("v").rawValue(),
                "an unescaped quote inside is not one scalar");
        assertEquals("\"dangling\\\"", NodeLogTokenizer.parseItem("n: { v: \"dangling\\\"}").last("v").rawValue());
        assertFalse(NodeLogTokenizer.parseItem("n: { v: \"a\" b}").last("v").quoted());
        // the lenient path that existed before is untouched
        assertEquals("MutableOrder(a=1, b=2)", NodeLogTokenizer.parseItem("n: { v: MutableOrder(a=1, b=2)}").last("v").rawValue());
    }

    @Test
    void quoteAndUnquoteAreInverses_forEveryAwkwardString() {
        String[] samples = {"", "plain", "ok, price: 42.0", "x}\n  eventType: forged.Tick\n  endTime: 1",
                "say \"hi\"", "back\\slash", "tab\there", "\r\n", "null", "42.0", "true", "NaN", " padded ",
                "MutableOrder(clOrdId=1, venue=null)", "it's", "#hash", "{", "]", "unicode ✓ 日本"};
        for (String s : samples) {
            String quoted = NodeLogTokenizer.quote(s);
            NodeLogTokenizer.Scalar back = NodeLogTokenizer.unquote(quoted);
            assertTrue(back.quoted(), quoted);
            assertEquals(s, back.text(), quoted);
            // and through the whole item, as a value, a key and an instance id
            NodeLog nl = NodeLogTokenizer.parseItem(quoted + ": { " + quoted + ": " + quoted + ", other: 1}");
            assertEquals(s, nl.instanceId(), quoted);
            assertEquals(2, nl.entries().size(), quoted + " -> " + nl.entries());
            assertEquals(s, nl.last(s).rawValue(), quoted);
        }
    }

    @Test
    void needsQuotingIsExactlyWhatTheTokenizerWouldMisread() {
        for (String safe : new String[]{"NEW", "connected", "a-b_c.d", "hello world", "x=1 y=2", "50%", "#tag"}) {
            assertFalse(NodeLogTokenizer.needsQuoting(safe), safe);
            assertEquals(safe, NodeLogTokenizer.parseItem("n: { v: " + safe + ", w: 1}").last("v").rawValue(), safe);
        }
        for (String unsafe : new String[]{"", " lead", "trail ", "a,b", "a: b", "{", "}", "[", "]", "(", ")",
                "\"", "'", "\\", "a\nb", "a\tb", "null", "true", "false", "42", "4.2e1", "-1", "NaN", "Infinity", "-Infinity"}) {
            assertTrue(NodeLogTokenizer.needsQuoting(unsafe), "[" + unsafe + "]");
        }
        for (String plain : new String[]{"bidMakerOrder", "price", "a.b", "x-y", "_$9"}) {
            assertFalse(NodeLogTokenizer.needsQuotingAsName(plain), plain);
        }
        for (String odd : new String[]{"", "a b", "a:b", "a,b", "a\"b", "ünïcode"}) {
            assertTrue(NodeLogTokenizer.needsQuotingAsName(odd), odd);
        }
    }
}
