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
                // ONE dispatch method, taking Object. There is no onReading(Reading).
                case "READING" -> flow.onEvent(new Reading(f[1], Double.parseDouble(f[2])));
                case "LIMIT"   -> flow.onEvent(new Limit(f[1], Double.parseDouble(f[2])));
                default -> throw new IllegalArgumentException("unknown event: " + f[0]);
            }
            // a decision is whatever your engine decided in THIS cycle; read it once, then clear it
            if (ThresholdAlert.lastAlert != null) {
                out.add(eventNumber + ",ALERT," + ThresholdAlert.lastAlert);
                ThresholdAlert.lastAlert = null;
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
