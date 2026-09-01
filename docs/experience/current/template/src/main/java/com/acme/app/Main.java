package com.acme.app;

import com.acme.app.generated.AppProcessor;
import java.nio.file.*;
import java.util.*;

/**
 * Running a graph, complete. Note the ORDER — the audit processor is attached BEFORE init().
 *
 * <p><b>Write Main LAST.</b> It imports the generated class, and {@code mvn process-classes}
 * compiles your sources before generating — so if Main exists and the generated class does not,
 * Main breaks the very step that would create it. This template ships the generated class already
 * present, so you will not hit that. If you ever delete it: move Main out of the tree, run
 * {@code mvn process-classes}, put Main back.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        AppProcessor flow = new AppProcessor();

        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(rec -> audit.add("---\n" + rec));   // BEFORE init()
        flow.init();

        // ONE dispatch method, taking Object. There is no onReading(Reading).
        flow.onEvent(new Reading("SENSOR-1", 99.0));
        flow.onEvent(new Reading("SENSOR-1", 99.0));    // unchanged -> SensorState returns false, cycle stops
        flow.onEvent(new Reading("SENSOR-1", 120.0));   // over 100 -> ThresholdAlert fires

        flow.tearDown();     // lifecycle is init/start/stop/tearDown — there is no shutdown()

        Path out = Paths.get(args.length > 0 ? args[0] : "logs/audit.yaml");
        Files.createDirectories(out.getParent());
        Files.write(out, audit);
        System.out.println("wrote " + audit.size() + " records to " + out);
    }
}
