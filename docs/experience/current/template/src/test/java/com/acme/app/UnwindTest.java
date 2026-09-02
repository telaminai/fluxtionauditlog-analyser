package com.acme.app;

import com.acme.app.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The after-event phase: reverse topological order, and only what ran. */
class UnwindTest {

    private String cycleFor(Object... events) {
        Decisions.reset();
        Cycle.reset();
        AppProcessor flow = new AppProcessor();
        flow.init();
        String last = "";
        for (Object e : events) {
            flow.onEvent(e);
            last = Cycle.drain();
        }
        flow.tearDown();
        return last;
    }

    @Test
    void commitsRunInReverseTopologicalOrder() {
        String c = cycleFor(new Reading("S1", 120.0));
        assertEquals("sensorState|thresholdAlert|commit:thresholdAlert|commit:sensorState", c,
                "downstream commits first — the unwind is the reverse of the event-in phase");
    }

    @Test
    void onlyNodesOnTheExecutionPathCommit() {
        // an unchanged reading stops at sensorState, so thresholdAlert neither runs nor commits
        String c = cycleFor(new Reading("S1", 120.0), new Reading("S1", 120.0));
        assertFalse(c.contains("commit:thresholdAlert"),
                "a node that did not run must not commit");
        assertFalse(c.contains("commit:sensorState"),
                "a node that propagated nothing must not commit either — the unwind carries only the path");
    }
}
