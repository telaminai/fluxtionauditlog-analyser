package com.acme.app;

import com.acme.app.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** An EDGE rule fires on the transition, not while the condition holds. */
class EdgeTest {

    private List<String> decisionsFor(Object... events) {
        Decisions.reset();          // static holder: clear anything a previous test left
        AppProcessor flow = new AppProcessor();
        flow.init();
        List<String> out = new ArrayList<>();
        int n = 0;
        for (Object e : events) {
            n++;
            flow.onEvent(e);
            for (String d : Decisions.drain()) out.add(n + "," + d);
        }
        flow.tearDown();
        return out;
    }

    @Test
    void firesOnTheTransitionAndAgainAfterItGoesFalse() {
        List<String> d = decisionsFor(
                new Limit("temp", 100.0),
                new Reading("S1", 99.0),     // under
                new Reading("S1", 120.0),    // ← rises: fires
                new Reading("S1", 130.0),    // still over: silent
                new Reading("S1", 90.0),     // falls
                new Reading("S1", 140.0));   // ← rises again: fires
        assertEquals(List.of("3,ALERT,S1", "6,ALERT,S1"), d);
    }

    /**
     * Each key has its own edge. This example's SensorState holds only the LAST reading, so the two
     * sensors interleave through one slot — which is itself the lesson: an EdgeDetector keyed by
     * subject is only as good as the state feeding it. A real engine keeps per-key state in the node.
     */
    @Test
    void eachKeyHasItsOwnEdge() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.roseFor("S1", true), "S1 first goes true");
        assertTrue(d.roseFor("S2", true), "S2 is tracked separately, not masked by S1");
        assertFalse(d.roseFor("S1", true), "S1 still true — no second edge");
        assertFalse(d.roseFor("S1", false), "S1 goes false — not an edge either");
        assertTrue(d.roseFor("S1", true), "S1 rises again — fires");
    }
}
