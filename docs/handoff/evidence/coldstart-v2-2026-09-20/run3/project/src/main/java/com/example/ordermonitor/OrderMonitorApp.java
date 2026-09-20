package com.example.ordermonitor;

import com.telamin.fluxtion.builder.DataFlowBuilder;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.flowfunction.groupby.GroupBy;
import com.telamin.fluxtion.runtime.flowfunction.helpers.Aggregates;

import java.util.List;
import java.util.Map;

/**
 * A small event-driven order-processing monitor built with Fluxtion.
 * <p>
 * One event type, {@link OrderEvent}, is pushed into a single Fluxtion
 * {@link DataFlow} graph that fans out into four independent views of
 * the same stream:
 * <ol>
 *     <li>a raw audit log of every order as it arrives</li>
 *     <li>a running revenue-by-region total (accepted orders only)</li>
 *     <li>a running count of orders by status</li>
 *     <li>a real-time alert whenever an order fails</li>
 * </ol>
 * Fluxtion works out the dependency graph and the order nodes must fire
 * in; this class only has to describe the shape of the computation and
 * push events in.
 */
public class OrderMonitorApp {

    public record OrderEvent(String orderId, String region, String status, double amount) {
    }

    public static void main(String[] args) {
        System.out.println("=== Fluxtion Order Monitor ===");
        System.out.println("building DataFlow graph...\n");

        DataFlow orderMonitor = buildGraph();
        wireSinks(orderMonitor);

        System.out.println("publishing orders...\n");
        for (OrderEvent order : sampleOrders()) {
            System.out.println("-- received: " + order);
            orderMonitor.onEvent(order);
        }

        System.out.println("\n=== stream complete ===");
    }

    /**
     * Builds the DataFlow graph: subscribe once to OrderEvent, then branch
     * into revenue-by-region, count-by-status and a failed-order alert sink.
     */
    private static DataFlow buildGraph() {
        var orders = DataFlowBuilder.subscribe(OrderEvent.class);

        // running revenue total per region, accepted orders only
        orders
                .filter(o -> o.status().equals("ACCEPTED"))
                .groupBy(OrderEvent::region, OrderEvent::amount, Aggregates.doubleSumFactory())
                .map(GroupBy::toMap)
                .sink("revenueByRegion");

        // running count of orders per status
        orders
                .groupBy(OrderEvent::status, Aggregates.countFactory())
                .map(GroupBy::toMap)
                .sink("countByStatus");

        // fire an alert the instant a failed order is seen
        DataFlow dataFlow = orders
                .filter(o -> o.status().equals("FAILED"))
                .map(o -> "ALERT: order " + o.orderId() + " failed in region " + o.region()
                        + " (amount=" + o.amount() + ")")
                .sink("failedOrderAlert")
                .build();

        return dataFlow;
    }

    private static void wireSinks(DataFlow dataFlow) {
        dataFlow.addSink("revenueByRegion",
                (Map<String, Double> revenue) -> System.out.println("   revenue by region  : " + revenue));
        dataFlow.addSink("countByStatus",
                (Map<String, Integer> counts) -> System.out.println("   count by status     : " + counts));
        dataFlow.addSink("failedOrderAlert",
                (String alert) -> System.out.println("   " + alert));
    }

    private static List<OrderEvent> sampleOrders() {
        return List.of(
                new OrderEvent("ORD-1001", "EU", "ACCEPTED", 120.50),
                new OrderEvent("ORD-1002", "US", "ACCEPTED", 89.99),
                new OrderEvent("ORD-1003", "EU", "FAILED", 45.00),
                new OrderEvent("ORD-1004", "APAC", "ACCEPTED", 210.00),
                new OrderEvent("ORD-1005", "US", "FAILED", 15.75),
                new OrderEvent("ORD-1006", "EU", "ACCEPTED", 60.20),
                new OrderEvent("ORD-1007", "APAC", "ACCEPTED", 99.00),
                new OrderEvent("ORD-1008", "US", "ACCEPTED", 133.33),
                new OrderEvent("ORD-1009", "APAC", "FAILED", 27.10),
                new OrderEvent("ORD-1010", "EU", "ACCEPTED", 175.00)
        );
    }
}
