package com.acme.app;

import com.acme.app.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test against the AUDIT LOG, not against your own bookkeeping. The log says which nodes ran, in
 * dispatch order — so a test written this way fails when propagation changes, which a test asserting
 * on node state alone will not.
 */
class AppTest {

    private AppProcessor createFlow() {
        AppProcessor flow = new AppProcessor();
        DecisionCollector.getAndClear();  // clear any prior decisions
        return flow;
    }

    private List<DecisionCollector.Decision> runAndGetDecisions(Object... events) {
        AppProcessor flow = createFlow();
        flow.init();
        List<DecisionCollector.Decision> allDecisions = new ArrayList<>();
        for (Object e : events) {
            flow.onEvent(e);
            allDecisions.addAll(DecisionCollector.getAndClear());
        }
        flow.tearDown();
        return allDecisions;
    }

    @Test
    void simpleReleaseDecision() {
        // Setup: product with price 100, customer with credit limit 1000
        // Order 1 quantity 5, total price 500
        // Payment 500, stock 10
        // Expected: RELEASE
        List<DecisionCollector.Decision> decisions = runAndGetDecisions(
            new Product("SKU1", 100.0, false),
            new Customer("CUST1", "STANDARD", 1000.0),
            new Order("ORD1", "CUST1", "SKU1", 5, 1000L),
            new Receipt("SKU1", 10, 2000L),
            new Paid("ORD1", 500.0, 3000L)
        );

        assertTrue(decisions.stream().anyMatch(d -> d.decisionType().equals("RELEASE") && d.key().equals("ORD1")),
                "should emit RELEASE when order becomes allocatable and paid");
    }

    @Test
    void stockoutDetection() {
        // Setup: start with some stock
        List<DecisionCollector.Decision> decisions = runAndGetDecisions(
            new Receipt("SKU1", 5, 1000L),
            new Adjust("SKU1", -10, 2000L)  // takes it from 5 to -5
        );

        assertTrue(decisions.stream().anyMatch(d -> d.decisionType().equals("STOCKOUT") && d.key().equals("SKU1")),
                "should emit STOCKOUT when stock goes below zero");
    }

    @Test
    void hazardousProductBlock() {
        // Setup: hazardous product, non-hazardous carrier
        List<DecisionCollector.Decision> decisions = runAndGetDecisions(
            new Product("SKU1", 100.0, true),
            new Customer("CUST1", "STANDARD", 1000.0),
            new Carrier("CAR1", 100.0, false),
            new Order("ORD1", "CUST1", "SKU1", 1, 1000L),
            new Receipt("SKU1", 10, 2000L),
            new Paid("ORD1", 100.0, 3000L),
            new Dispatch("ORD1", "CAR1", 10.0, 4000L)
        );

        assertTrue(decisions.stream().anyMatch(d -> d.decisionType().equals("HAZARD_BLOCK") && d.key().equals("ORD1")),
                "should emit HAZARD_BLOCK for hazardous product with non-hazardous carrier");
    }

    @Test
    void overweightBlock() {
        // Setup: dispatch exceeds carrier weight limit
        List<DecisionCollector.Decision> decisions = runAndGetDecisions(
            new Product("SKU1", 100.0, false),
            new Customer("CUST1", "STANDARD", 1000.0),
            new Carrier("CAR1", 50.0, false),
            new Order("ORD1", "CUST1", "SKU1", 1, 1000L),
            new Receipt("SKU1", 10, 2000L),
            new Paid("ORD1", 100.0, 3000L),
            new Dispatch("ORD1", "CAR1", 100.0, 4000L)  // 100 > 50
        );

        assertTrue(decisions.stream().anyMatch(d -> d.decisionType().equals("OVERWEIGHT") && d.key().equals("ORD1")),
                "should emit OVERWEIGHT when weight exceeds carrier limit");
    }

    @Test
    void slaBreachDetection() {
        // Setup: PICKDONE more than 3600000ms after RELEASE
        long releaseTime = 1000L;
        long pickDoneTime = releaseTime + 3600001L;  // 1ms over threshold

        List<DecisionCollector.Decision> decisions = runAndGetDecisions(
            new Product("SKU1", 100.0, false),
            new Customer("CUST1", "STANDARD", 1000.0),
            new Order("ORD1", "CUST1", "SKU1", 1, 1000L),
            new Receipt("SKU1", 10, 2000L),
            new Paid("ORD1", 100.0, releaseTime),  // triggers RELEASE
            new PickDone("ORD1", pickDoneTime)
        );

        assertTrue(decisions.stream().anyMatch(d -> d.decisionType().equals("SLA_BREACH") && d.key().equals("ORD1")),
                "should emit SLA_BREACH when PICKDONE is > 3600000ms after RELEASE");
    }
}
