package telamin.fluxtion.audit.analyser.analyser.source;

import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.NodeRef;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.Ref;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceNavigationTest {

    private static final String SRC = """
            public class Processor {
              public boolean orderVenueConnected(OrderVenueConnectedEvent e) {
                hedgeConnectionMonitor.orderVenueConnected(e);
                return true;
              }
            }
            """;

    @Test
    void methodDeclOffsetPrefersTheDeclarationOverACall() {
        int off = SourceNavigation.methodDeclOffset(SRC, "orderVenueConnected");
        assertTrue(off >= 0);
        assertTrue(off < SRC.indexOf("hedgeConnectionMonitor"), "declaration comes before the dispatch call");
        assertTrue(SRC.substring(off).trim().startsWith("public boolean orderVenueConnected"));
        assertEquals(-1, SourceNavigation.methodDeclOffset(SRC, "noSuchMethod"));
    }

    @Test
    void resolveAtFindsReceiverAndMethodCall() {
        String line = "    hedgeConnectionMonitor.orderVenueConnected(e);";
        Ref call = SourceNavigation.resolveAt(line, line.indexOf("orderVenueConnected") + 3);
        assertEquals("orderVenueConnected", call.identifier());
        assertEquals("hedgeConnectionMonitor", call.receiver());
        assertTrue(call.methodCall());

        Ref recv = SourceNavigation.resolveAt(line, line.indexOf("hedgeConnectionMonitor") + 2);
        assertEquals("hedgeConnectionMonitor", recv.identifier());
        assertNull(recv.receiver());
        assertFalse(recv.methodCall());
    }

    @Test
    void parseNodeLogLineExtractsInstanceIdAndDrivingMethod() {
        NodeRef n = SourceNavigation.parseNodeLogLine(
                "    - hedgeConnectionMonitor: { orderVenueConnected: OrderVenueConnectedEvent[name=x], status: CLOSED}");
        assertEquals("hedgeConnectionMonitor", n.instanceId());
        assertEquals("orderVenueConnected", n.methodKey(), "the first key is the method that ran on the node");

        assertNull(SourceNavigation.parseNodeLogLine("  event: LifecycleEvent"), "scalar lines are not node-logs");
        NodeRef noBrace = SourceNavigation.parseNodeLogLine("    - bidMakerOrder: connected");
        assertEquals("bidMakerOrder", noBrace.instanceId());
        assertNull(noBrace.methodKey());
    }

    @Test
    void lineAtReturnsTheContainingLine() {
        String s = "a\nbbb\nc";
        assertEquals("bbb", SourceNavigation.lineAt(s, 3));
        assertEquals("bbb", SourceNavigation.lineAt(s, 4));
    }
}
