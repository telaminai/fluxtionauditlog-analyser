package com.acme.app;

import com.acme.app.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STEP 1. Do not delete this test and do not weaken it.
 *
 * <p>It fails if your graph is empty — if the builder registers no nodes, or registers a tree that
 * nothing reaches. That failure is otherwise SILENT: the generator runs, emits a processor with no
 * nodes in it, reports no error, and every later symptom looks like a broken build tool. Two authors
 * have lost fifteen build cycles each to exactly that, both concluding the Maven plugin was at fault.
 *
 * <p>Keep it passing from your first commit onwards. When you replace the example nodes, change the
 * event this feeds and the expected node name — do not remove the assertion.
 */
class GraphExistsTest {

    @Test
    void theGraphIsGeneratedAndAtLeastOneNodeRuns() {
        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(r -> audit.add(r.toString()));
        flow.init();
        flow.onEvent(new Product("SKU1", 100.0, false));      // any event your graph handles
        flow.tearDown();

        List<String> business = audit.stream()
                .filter(r -> !r.contains("LifecycleEvent") && !r.contains("EventLogConfig"))
                .toList();
        assertFalse(business.isEmpty(), "no event cycle was recorded — is audit logging enabled?");

        boolean someNodeRan = business.stream().anyMatch(r -> r.contains("- ") && r.contains("method:"));
        assertTrue(someNodeRan,
                "the graph ran NO nodes. The builder registered nothing, or nothing is reachable from "
              + "what it registered. Check that buildGraph calls cfg.addNode(...) on your root, and "
              + "that configureGeneration sets a class name and package.\naudit was:\n" + business);
    }
}
