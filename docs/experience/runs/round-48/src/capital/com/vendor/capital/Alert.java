package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
/** SIDE EFFECT: publishes to AlertSink. Below the supervisor. */
public class Alert extends EventLogNode implements AlertApi {
    private final LimitApi detector;
    @NoTriggerReference private final ChargeApi charge;
    public Alert(@AssignToField("detector") LimitApi detector, @AssignToField("charge") ChargeApi charge) { this.detector = detector; this.charge = charge; }
    public double charge() { return charge.charge(); }
    @OnTrigger public boolean calc() {
        AlertSink.PUBLISH.accept(String.format("BREACH charge=%.2f", charge.charge()));
        auditLog.info("stage", "capital.alert").info("value", charge.charge()); return true; }
}
