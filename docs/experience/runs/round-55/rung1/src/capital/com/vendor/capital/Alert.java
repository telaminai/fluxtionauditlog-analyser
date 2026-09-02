package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
/** SIDE EFFECT: publishes to a registered AlertSink. Below the supervisor. */
public class Alert extends EventLogNode implements AlertApi {
    private final LimitApi detector;
    @NoTriggerReference private final ChargeApi charge;
    private AlertSink sink = a -> {};
    public Alert(@AssignToField("detector") LimitApi detector,
                 @AssignToField("charge") ChargeApi charge) {
        this.detector = detector; this.charge = charge; }
    public double charge() { return charge.charge(); }
    @ServiceRegistered public void alertSink(AlertSink sink, String name) { this.sink = sink; }
    @OnTrigger public boolean calc() {
        sink.publish(String.format("BREACH charge=%.2f", charge.charge()));
        auditLog.info("stage", "capital.alert").info("value", charge.charge()); return true; }
}
