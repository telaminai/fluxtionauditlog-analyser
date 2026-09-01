package com.acme.app;

import com.acme.app.generated.AppProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test against the AUDIT LOG, not against your own bookkeeping. The log says which nodes ran, in
 * dispatch order — so a test written this way fails if propagation changes, which a test asserting
 * on node state alone will not.
 */
class AppTest {

    private List<String> run(Reading... ticks) {
        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(r -> audit.add(r.toString()));
        flow.init();
        for (Reading t : ticks) flow.onEvent(t);
        flow.tearDown();
        return audit;
    }

    @Test
    void unchangedPriceStopsTheCycle() {
        List<String> audit = run(new Reading("SENSOR-1", 99.0), new Reading("SENSOR-1", 99.0));
        String second = audit.get(audit.size() - 1);
        assertFalse(second.contains("thresholdAlert"), "unchanged tick must not reach the thresholdAlert");
    }

    @Test
    void priceAboveThresholdAlerts() {
        List<String> audit = run(new Reading("SENSOR-1", 120.0));
        assertTrue(audit.stream().anyMatch(r -> r.contains("alert: true")),
                "a value over 100 must raise an alert in the audit log");
    }
}
