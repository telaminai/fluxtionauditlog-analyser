package com.acme.fulfil;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class EngineTest {

    private Engine engine;
    private List<Event> events;

    private void setup() {
        engine = new Engine();
        events = new ArrayList<>();
    }

    private void addEvent(Event event) {
        events.add(event);
    }

    private void processAll() {
        engine.processAll(events);
    }

    private List<Decision> getDecisions() {
        return engine.getDecisions();
    }

    @Test
    public void testR1OnHandStock() {
        setup();
        // R1: RECEIPT adds, ADJUST adds delta, COUNT replaces
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ADJUST,sku-a,5,2000"));
        addEvent(parseEvent("COUNT,sku-a,8,3000"));
        processAll();

        // No decisions expected for R1 (it's a CONDITION)
        assertTrue(getDecisions().isEmpty());
    }

    @Test
    public void testR2OrderState() {
        setup();
        // R2: ORDER creates OPEN, AMEND changes quantity, CANCEL makes CANCELLED
        addEvent(parseEvent("PRODUCT,sku-a,10,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,1000"));
        addEvent(parseEvent("RECEIPT,sku-a,100,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,50,2500"));
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-1,2,4000"));
        addEvent(parseEvent("CANCEL,ord-1,4500"));
        // After CANCEL, no more events for this order should have effect
        addEvent(parseEvent("DELIVERED,ord-1,5000"));
        processAll();

        // Should see RELEASE decision when order becomes releasable
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type) && "ord-1".equals(d.key));
        assertTrue(hasRelease);
    }

    @Test
    public void testR3PaidAmount() {
        setup();
        // R3: Sum of PAID amounts, PAYFAIL adds nothing
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,100,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,200,2500"));
        addEvent(parseEvent("PAID,ord-1,300,3000"));
        addEvent(parseEvent("PAYFAIL,ord-1,3500"));
        addEvent(parseEvent("PAID,ord-1,100,4000"));
        processAll();

        // Total paid should be 200+300+100=600, order is releasable (600 >= 5*100)
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertTrue(hasRelease);
    }

    @Test
    public void testR4Allocatable() {
        setup();
        // R4: OPEN order is allocatable when on-hand >= quantity
        addEvent(parseEvent("PRODUCT,sku-a,50,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,3,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,250,2500"));
        // Not allocatable yet (on-hand 3 < quantity 5)
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertFalse(hasRelease);

        // Add more stock
        setup();
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,50,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,5,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,250,2500"));
        processAll();
        decisions = getDecisions();
        hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertTrue(hasRelease);
    }

    @Test
    public void testR5CreditOk() {
        setup();
        // R5: Credit-ok when paid >= price OR (tier=GOLD AND price <= creditLimit)

        // Case 1: Sufficient payment
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,1000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertTrue(hasRelease);

        // Case 2: GOLD customer with credit
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-2,GOLD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-2,cust-2,sku-a,5,2000"));
        // No payment, but GOLD customer with high credit limit
        processAll();
        decisions = getDecisions();
        hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertTrue(hasRelease);

        // Case 3: STANDARD customer without sufficient payment or GOLD credit
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-3,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-3,cust-3,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-3,100,2500"));
        processAll();
        decisions = getDecisions();
        hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertFalse(hasRelease);
    }

    @Test
    public void testR6ReleaseEdgeOnce() {
        setup();
        // R6 RELEASE: EDGE - fires once when becomes releasable, re-fires after becoming non-releasable then releasable
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,300,2500")); // Not yet 500
        // Order not releasable
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertFalse(hasRelease);

        // Now make it releasable
        setup();
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,400,2500"));
        addEvent(parseEvent("PAID,ord-1,100,2600")); // Now 500, releasable
        processAll();
        decisions = getDecisions();
        long releaseCount = decisions.stream().filter(d -> "RELEASE".equals(d.type)).count();
        assertEquals(1, releaseCount); // Should fire once

        // Make it non-releasable then releasable again
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500")); // Releasable
        addEvent(parseEvent("AMEND,ord-1,10,3000")); // Change quantity to 10 -> now need 1000 -> not releasable
        addEvent(parseEvent("PAID,ord-1,500,3500")); // Total 1000 -> releasable again
        processAll();
        decisions = getDecisions();
        releaseCount = decisions.stream().filter(d -> "RELEASE".equals(d.type)).count();
        assertEquals(2, releaseCount); // Should fire twice (initial + after becoming non-releasable then releasable)
    }

    @Test
    public void testR7HazardBlockEdge() {
        setup();
        // R7 HAZARD_BLOCK: EDGE - fires on DISPATCH with hazardous product and non-handling carrier
        addEvent(parseEvent("PRODUCT,sku-hazard,100,true"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("CARRIER,carrier-non,1000,false"));
        addEvent(parseEvent("RECEIPT,sku-hazard,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-hazard,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-non,5,4000"));
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasHazard = decisions.stream().anyMatch(d -> "HAZARD_BLOCK".equals(d.type));
        assertTrue(hasHazard);

        // Fire once - HAZARD_BLOCK again on second DISPATCH should not fire
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-hazard,100,true"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("CARRIER,carrier-non,1000,false"));
        addEvent(parseEvent("RECEIPT,sku-hazard,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-hazard,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-non,5,4000"));
        addEvent(parseEvent("DELIVERED,ord-1,4500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-non,2,5000")); // Second dispatch - should not fire again
        processAll();
        decisions = getDecisions();
        long hazardCount = decisions.stream().filter(d -> "HAZARD_BLOCK".equals(d.type)).count();
        assertEquals(1, hazardCount);
    }

    @Test
    public void testR8OverweightEdge() {
        setup();
        // R8 OVERWEIGHT: EDGE - fires on DISPATCH with weight > maxWeightKg
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("CARRIER,carrier-light,10,true"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-light,15,4000")); // 15 > 10
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasOverweight = decisions.stream().anyMatch(d -> "OVERWEIGHT".equals(d.type));
        assertTrue(hasOverweight);

        // Fire once - should not fire again
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("CARRIER,carrier-light,10,true"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-light,15,4000"));
        addEvent(parseEvent("DELIVERED,ord-1,4500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-light,20,5000")); // Second dispatch - should not fire again
        processAll();
        decisions = getDecisions();
        long overweightCount = decisions.stream().filter(d -> "OVERWEIGHT".equals(d.type)).count();
        assertEquals(1, overweightCount);
    }

    @Test
    public void testR9SLABreach() {
        setup();
        // R9 SLA_BREACH: EDGE - fires on PICKDONE when timestamp > releaseTimestamp + 3600000
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500")); // Timestamp 2500 - RELEASED
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,6100500")); // 6100500 > 2500 + 3600000
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasSlaBreach = decisions.stream().anyMatch(d -> "SLA_BREACH".equals(d.type));
        assertTrue(hasSlaBreach);

        // No SLA_BREACH if order not released
        setup();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,100"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        // Order not released (not enough credit)
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,6100500"));
        processAll();
        decisions = getDecisions();
        hasSlaBreach = decisions.stream().anyMatch(d -> "SLA_BREACH".equals(d.type));
        assertFalse(hasSlaBreach);

        // No SLA_BREACH if within SLA
        setup();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500")); // Released at 2500
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3602499")); // 3602499 <= 2500 + 3600000 (within SLA)
        processAll();
        decisions = getDecisions();
        hasSlaBreach = decisions.stream().anyMatch(d -> "SLA_BREACH".equals(d.type));
        assertFalse(hasSlaBreach);
    }

    @Test
    public void testR10StockoutEdge() {
        setup();
        // R10 STOCKOUT: EDGE - fires on event that takes stock from >=0 to <0
        addEvent(parseEvent("RECEIPT,sku-a,5,1000"));
        addEvent(parseEvent("ADJUST,sku-a,-3,2000")); // 5-3=2, still positive
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasStockout = decisions.stream().anyMatch(d -> "STOCKOUT".equals(d.type));
        assertFalse(hasStockout);

        // Now trigger stockout
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("RECEIPT,sku-a,5,1000"));
        addEvent(parseEvent("ADJUST,sku-a,-6,2000")); // 5-6=-1, goes negative
        processAll();
        decisions = getDecisions();
        hasStockout = decisions.stream().anyMatch(d -> "STOCKOUT".equals(d.type));
        assertTrue(hasStockout);

        // Stockout fires again after recovery
        engine = new Engine();
        events.clear();
        addEvent(parseEvent("RECEIPT,sku-a,5,1000"));
        addEvent(parseEvent("ADJUST,sku-a,-6,2000")); // Goes to -1
        addEvent(parseEvent("COUNT,sku-a,3,3000")); // Back to positive
        addEvent(parseEvent("ADJUST,sku-a,-5,4000")); // Goes to -2 again
        processAll();
        decisions = getDecisions();
        long stockoutCount = decisions.stream().filter(d -> "STOCKOUT".equals(d.type)).count();
        assertEquals(2, stockoutCount);
    }

    @Test
    public void testR11ReferenceDataUnchanged() {
        setup();
        // R11: Reference data unchanged must not cause re-evaluation
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        addEvent(parseEvent("PRODUCT,sku-a,100,false")); // Same data - should not re-trigger
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000")); // Same data - should not re-trigger
        processAll();
        List<Decision> decisions = getDecisions();
        // Should still have one RELEASE
        long releaseCount = decisions.stream().filter(d -> "RELEASE".equals(d.type)).count();
        assertEquals(1, releaseCount);
    }

    @Test
    public void testR12ArrestPropagation() {
        // R12: A node that decided nothing should not cause downstream to run
        // This is more of a performance/logical rule
        // Testing that reference data events that don't change don't trigger processing
        setup();
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("PRODUCT,sku-a,100,false")); // Same data
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));
        processAll();
        List<Decision> decisions = getDecisions();
        // Should have RELEASE, no duplicates from the unchanged PRODUCT
        long releaseCount = decisions.stream().filter(d -> "RELEASE".equals(d.type)).count();
        assertEquals(1, releaseCount);
    }

    @Test
    public void testCancelOrderIgnoresLaterEvents() {
        setup();
        // After CANCEL, later events for that order should be ignored
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("RECEIPT,sku-a,10,1000"));
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("CANCEL,ord-1,3000")); // Cancel before release
        addEvent(parseEvent("PAID,ord-1,500,4000")); // This should be ignored
        processAll();
        List<Decision> decisions = getDecisions();
        boolean hasRelease = decisions.stream().anyMatch(d -> "RELEASE".equals(d.type));
        assertFalse(hasRelease); // Order was cancelled before released
    }

    @Test
    public void testComplexScenario() {
        setup();
        // Complex scenario with multiple orders and various conditions
        addEvent(parseEvent("PRODUCT,sku-a,100,false"));
        addEvent(parseEvent("PRODUCT,sku-b,50,true"));
        addEvent(parseEvent("CUSTOMER,cust-1,STANDARD,10000"));
        addEvent(parseEvent("CUSTOMER,cust-2,GOLD,5000"));
        addEvent(parseEvent("CARRIER,carrier-std,100,true"));
        addEvent(parseEvent("CARRIER,carrier-no-haz,100,false"));

        addEvent(parseEvent("RECEIPT,sku-a,20,1000"));
        addEvent(parseEvent("RECEIPT,sku-b,10,1000"));

        // Order 1: Standard order
        addEvent(parseEvent("ORDER,ord-1,cust-1,sku-a,5,2000"));
        addEvent(parseEvent("PAID,ord-1,500,2500"));

        // Order 2: Hazardous product
        addEvent(parseEvent("ORDER,ord-2,cust-1,sku-b,3,2100"));
        addEvent(parseEvent("PAID,ord-2,150,2600"));

        // Order 3: GOLD customer
        addEvent(parseEvent("ORDER,ord-3,cust-2,sku-a,10,2200"));

        // Dispatch order 1 normally
        addEvent(parseEvent("PICKSTART,ord-1,3000"));
        addEvent(parseEvent("PICKDONE,ord-1,3500"));
        addEvent(parseEvent("DISPATCH,ord-1,carrier-std,10,4000"));

        // Dispatch order 2 with hazard block
        addEvent(parseEvent("PICKSTART,ord-2,3100"));
        addEvent(parseEvent("PICKDONE,ord-2,3600"));
        addEvent(parseEvent("DISPATCH,ord-2,carrier-no-haz,5,4100"));

        processAll();
        List<Decision> decisions = getDecisions();

        // Should have releases for ord-1, ord-2, ord-3
        long releaseCount = decisions.stream().filter(d -> "RELEASE".equals(d.type)).count();
        assertTrue(releaseCount >= 2);

        // Should have HAZARD_BLOCK for ord-2
        boolean hasHazard = decisions.stream().anyMatch(d -> "HAZARD_BLOCK".equals(d.type) && "ord-2".equals(d.key));
        assertTrue(hasHazard);
    }

    // Helper method to parse event from CSV string
    private Event parseEvent(String csv) {
        return Event.parse(csv);
    }
}
