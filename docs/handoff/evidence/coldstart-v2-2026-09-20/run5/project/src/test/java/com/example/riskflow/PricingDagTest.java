package com.example.riskflow;

import com.example.riskflow.event.*;
import com.example.riskflow.generated.PricingDag;
import com.telamin.fluxtion.runtime.DataFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PricingDagTest {

    @Test
    void buildsAndAcceptsEvents() {
        DataFlow flow = new PricingDag();
        flow.init();
        assertNotNull(flow);
        flow.onEvent(new PriceUpdate());
        // No exception = the graph built and dispatched. Add assertions on your
        // node's state once it does real work.
    }
}
