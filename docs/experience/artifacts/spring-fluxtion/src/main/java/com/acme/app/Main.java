package com.acme.app;

import com.acme.app.generated.AppProcessor;
import java.nio.file.*;
import java.util.*;

/**
 * Reads a scenario file, feeds it through the graph, and writes two things: the decisions your engine
 * made, and the framework's audit log.
 *
 * <p>Keep this shape. Driving the engine from a file rather than hardcoded events is what lets someone
 * else run it against inputs you have not seen — and lets {@code ./trace.sh} show you what ran.
 *
 * <p>Note the ORDER: the audit processor is attached BEFORE {@code init()}. The pom compiles in two
 * passes so this class may import the generated processor; see README.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Path scenario  = Paths.get(args.length > 0 ? args[0] : "scenario.csv");
        Path decisions = Paths.get(args.length > 1 ? args[1] : "logs/decisions.txt");
        Path auditPath = Paths.get(args.length > 2 ? args[2] : "logs/audit.yaml");

        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(rec -> audit.add("---\n" + rec));   // BEFORE init()
        flow.init();

        List<String> out = new ArrayList<>();
        int eventNumber = 0;
        for (String line : Files.readAllLines(scenario)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;     // not events, not counted
            String[] f = line.split(",");
            eventNumber++;
            switch (f[0]) {
                case "PRODUCT" -> flow.onEvent(new Product(f[1], Double.parseDouble(f[2]), Boolean.parseBoolean(f[3])));
                case "CUSTOMER" -> flow.onEvent(new Customer(f[1], f[2], Double.parseDouble(f[3])));
                case "CARRIER" -> flow.onEvent(new Carrier(f[1], Double.parseDouble(f[2]), Boolean.parseBoolean(f[3])));
                case "RECEIPT" -> flow.onEvent(new Receipt(f[1], Integer.parseInt(f[2]), Long.parseLong(f[3])));
                case "ADJUST" -> flow.onEvent(new Adjust(f[1], Integer.parseInt(f[2]), Long.parseLong(f[3])));
                case "COUNT" -> flow.onEvent(new Count(f[1], Integer.parseInt(f[2]), Long.parseLong(f[3])));
                case "ORDER" -> flow.onEvent(new Order(f[1], f[2], f[3], Integer.parseInt(f[4]), Long.parseLong(f[5])));
                case "AMEND" -> flow.onEvent(new Amend(f[1], Integer.parseInt(f[2]), Long.parseLong(f[3])));
                case "CANCEL" -> flow.onEvent(new Cancel(f[1], Long.parseLong(f[2])));
                case "PAID" -> flow.onEvent(new Paid(f[1], Double.parseDouble(f[2]), Long.parseLong(f[3])));
                case "PAYFAIL" -> flow.onEvent(new Payfail(f[1], Long.parseLong(f[2])));
                case "PICKSTART" -> flow.onEvent(new PickStart(f[1], Long.parseLong(f[2])));
                case "PICKDONE" -> flow.onEvent(new PickDone(f[1], Long.parseLong(f[2])));
                case "DISPATCH" -> flow.onEvent(new Dispatch(f[1], f[2], Double.parseDouble(f[3]), Long.parseLong(f[4])));
                case "DELIVERED" -> flow.onEvent(new Delivered(f[1], Long.parseLong(f[2])));
                default -> throw new IllegalArgumentException("unknown event: " + f[0]);
            }

            // Collect decisions from this cycle
            for (DecisionCollector.Decision d : DecisionCollector.getAndClear()) {
                out.add(eventNumber + "," + d.decisionType() + "," + d.key());
            }
        }
        flow.tearDown();     // lifecycle is init/start/stop/tearDown — there is no shutdown()

        for (Path p : List.of(decisions, auditPath))
            if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.write(decisions, out);
        Files.write(auditPath, audit);
        System.out.println("wrote " + out.size() + " decisions and " + audit.size() + " audit records");
    }
}
