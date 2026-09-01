package com.acme.app;

import com.acme.app.generated.AppProcessor;
import java.nio.file.*;
import java.util.*;

/**
 * Running a graph, complete. Note the ORDER — the audit processor is attached BEFORE init().
 *
 * <p><b>This class imports the generated processor, and the pom is set up so that is fine.</b>
 * Generation runs at {@code process-classes}, which is AFTER {@code compile} — so ordinarily a class
 * importing generated output cannot compile, and you are told to "write Main last". This build
 * compiles in two passes instead: everything except {@code ${generated.dependents}} first, then
 * generation, then the rest. It also deletes the previous generated source at {@code initialize}, so
 * a processor left over from an older node shape can never break the build. Change a node's
 * constructor and just run {@code mvn clean test}.
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
        flow.onEvent(new Reading("SENSOR-1", 120.0));   // over the limit -> ThresholdAlert fires
        flow.onEvent(new Limit("temp", 150.0));         // reference data: LimitStore runs, alert does NOT
        flow.onEvent(new Limit("temp", 150.0));         // unchanged republish -> stops at LimitStore

        flow.tearDown();     // lifecycle is init/start/stop/tearDown — there is no shutdown()

        Path out = Paths.get(args.length > 0 ? args[0] : "logs/audit.yaml");
        Files.createDirectories(out.getParent());
        Files.write(out, audit);
        System.out.println("wrote " + audit.size() + " records to " + out);
    }
}
