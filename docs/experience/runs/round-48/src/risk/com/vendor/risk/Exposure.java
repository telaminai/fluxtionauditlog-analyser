package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
public class Exposure extends EventLogNode implements ExposureApi {
    private final NotionalApi notional; private final ScoreApi score;
    public transient double value;
    public Exposure(@AssignToField("notional") NotionalApi notional, @AssignToField("score") ScoreApi score) { this.notional = notional; this.score = score; }
    public double exposure() { return value; }
    @OnTrigger public boolean calc() {
        value = notional.notional() * (1 + score.score() / 1000);
        auditLog.info("stage", "risk.exposure").info("value", value); return true; }
}
