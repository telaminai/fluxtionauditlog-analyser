package com.example.myapp;

import com.example.myapp.event.PriceUpdate;
import com.example.myapp.event.Trade;
import com.example.myapp.generated.MyProcessor;
import com.example.myapp.node.MarkToMarketNode;
import com.example.myapp.node.PositionNode;
import com.telamin.fluxtion.runtime.DataFlow;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Runs trades and prices for three instruments and checks positions and PnL against an independent host ledger. */
public final class TradeScenario {
    private static final Map<String, Double> position = new HashMap<>();
    private static final Map<String, Double> cash = new HashMap<>();
    private static final Map<String, Double> mark = new HashMap<>();
    private static final java.util.Set<String> priced = new java.util.HashSet<>();

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (PrintWriter audit = new PrintWriter(Files.newBufferedWriter(output.resolve("audit.yaml")));
             PrintWriter states = new PrintWriter(Files.newBufferedWriter(output.resolve("positions.csv")))) {
            DataFlow flow = new MyProcessor();
            flow.setAuditLogProcessor(record -> { audit.println("---"); audit.println(record.toString()); audit.flush(); });
            flow.init();
            flow.start();
            PositionNode positions = flow.getNodeById("positionNode");
            MarkToMarketNode pnl = flow.getNodeById("markToMarketNode");
            states.println("time,step,symbol,position,symbolPnl,totalPnl");

            trade(flow, states, positions, pnl, "AAPL", 100, 190.0);
            trade(flow, states, positions, pnl, "MSFT", 200, 410.0);
            price(flow, states, positions, pnl, "AAPL", 191.5);
            trade(flow, states, positions, pnl, "NVDA", -30, 900.0);
            trade(flow, states, positions, pnl, "AAPL", 50, 192.0);
            price(flow, states, positions, pnl, "MSFT", 413.0);
            trade(flow, states, positions, pnl, "MSFT", -50, 415.0);
            price(flow, states, positions, pnl, "NVDA", 905.0);
            trade(flow, states, positions, pnl, "NVDA", -20, 910.0);
            trade(flow, states, positions, pnl, "AAPL", -80, 195.0);
            price(flow, states, positions, pnl, "AAPL", 194.0);
            trade(flow, states, positions, pnl, "AAPL", -120, 193.0);
            price(flow, states, positions, pnl, "NVDA", 885.0);
            trade(flow, states, positions, pnl, "NVDA", 10, 880.0);
            trade(flow, states, positions, pnl, "MSFT", -50, 412.0);
            price(flow, states, positions, pnl, "AAPL", 190.5);
            trade(flow, states, positions, pnl, "AAPL", 50, 191.0);
            price(flow, states, positions, pnl, "MSFT", 416.0);
            price(flow, states, positions, pnl, "NVDA", 870.0);

            expect("final AAPL position", 0, positions.getPosition("AAPL"));
            expect("final MSFT position", 100, positions.getPosition("MSFT"));
            expect("final NVDA position", -40, positions.getPosition("NVDA"));
            flow.stop();
            flow.tearDown();
            System.out.println("PASS: positions and PnL matched the host ledger at all 19 steps; totalPnl=" + pnl.getTotalPnl());
        }
    }

    private static void trade(DataFlow flow, PrintWriter states, PositionNode positions, MarkToMarketNode pnl,
                              String symbol, double quantity, double price) throws InterruptedException {
        position.merge(symbol, quantity, Double::sum);
        cash.merge(symbol, -quantity * price, Double::sum);
        if (!priced.contains(symbol)) mark.put(symbol, price); // same fallback as the node: last trade price until a PriceUpdate
        flow.onEvent(new Trade(symbol, quantity, price));
        measure(states, "trade", symbol, positions, pnl);
    }

    private static void price(DataFlow flow, PrintWriter states, PositionNode positions, MarkToMarketNode pnl,
                              String symbol, double price) throws InterruptedException {
        priced.add(symbol);
        mark.put(symbol, price);
        flow.onEvent(new PriceUpdate(symbol, price, 7));
        measure(states, "price", symbol, positions, pnl);
    }

    private static void measure(PrintWriter states, String step, String symbol, PositionNode positions,
                                MarkToMarketNode pnl) throws InterruptedException {
        double expectedTotal = 0;
        for (String s : position.keySet()) {
            expectedTotal += cash.get(s) + position.get(s) * mark.get(s);
        }
        String row = System.currentTimeMillis() + "," + step + "," + symbol + "," + positions.getPosition(symbol)
                + "," + pnl.getPnl(symbol) + "," + pnl.getTotalPnl();
        states.println(row);
        states.flush();
        System.out.println(row);
        expect(step + " " + symbol + " position", position.get(symbol), positions.getPosition(symbol));
        expect(step + " " + symbol + " totalPnl", expectedTotal, pnl.getTotalPnl());
        Thread.sleep(25);
    }

    private static void expect(String what, double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-6) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
