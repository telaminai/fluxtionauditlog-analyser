package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.LimitDetector;
/**
 * SIDE EFFECT, and it sits BELOW the arrest. Running it when the detector did not trip publishes an
 * alert for a breach that never happened. No ordering repairs that; the path must not be walked.
 */
public class Alert extends EventLogNode {
    private final LimitDetector detector;
    @NoTriggerReference private final Charge charge;
    public Alert(LimitDetector detector, Charge charge) {
        this.detector = detector; this.charge = charge; }
    @OnTrigger public boolean calc() {
        AlertSink.PUBLISH.accept(String.format("BREACH charge=%.2f", charge.value));
        auditLog.info("stage", "capital.alert").info("value", charge.value);
        return true; }
}
