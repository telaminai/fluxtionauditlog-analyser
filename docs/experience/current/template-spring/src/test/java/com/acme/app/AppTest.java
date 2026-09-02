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

    /**
     * Returns one audit record per BUSINESS event, in order.
     *
     * <p>Note the filter. The framework also logs lifecycle cycles — Init, TearDown, and the audit
     * config itself — so "the last record" is NOT the last event you fed in. Asserting against the
     * raw last record silently reads the TearDown cycle, which contains no nodes at all and therefore
     * passes any "did not run" assertion for the wrong reason.
     */
    private List<String> run(Object... events) {
        AppProcessor flow = new AppProcessor();
        List<String> audit = new ArrayList<>();
        flow.setAuditLogProcessor(r -> audit.add(r.toString()));
        flow.init();
        for (Object e : events) flow.onEvent(e);
        flow.tearDown();
        return audit.stream()
                .filter(r -> !r.contains("LifecycleEvent") && !r.contains("EventLogConfig"))
                .toList();
    }

    /** The cycle for the last business event fed in. */
    private String lastCycle(List<String> audit) {
        return audit.get(audit.size() - 1);
    }

    @Test
    void unchangedReadingStopsTheCycle() {
        List<String> audit = run(new Reading("SENSOR-1", 99.0), new Reading("SENSOR-1", 99.0));
        assertFalse(lastCycle(audit).contains("thresholdAlert"),
                "an unchanged reading must not reach the alert node");
    }

    @Test
    void readingAboveThresholdAlerts() {
        List<String> audit = run(new Reading("SENSOR-1", 120.0));
        assertTrue(lastCycle(audit).contains("alert: true"),
                "a reading over the limit must raise an alert in the audit log");
    }

    /**
     * The {@code @NoTriggerReference} test. Reference data must not run the alert node — remove that
     * annotation from ThresholdAlert.limitStore and this test fails, which is the point of writing it.
     */
    @Test
    void aLimitEventDoesNotRunTheAlert() {
        List<String> audit = run(new Reading("SENSOR-1", 120.0), new Limit("temp", 150.0));
        String limitCycle = lastCycle(audit);
        assertTrue(limitCycle.contains("limitStore"), "the limit must reach its own store");
        assertFalse(limitCycle.contains("thresholdAlert"),
                "a limit is data, not a reading — it must not trigger the alert node");
    }
}
