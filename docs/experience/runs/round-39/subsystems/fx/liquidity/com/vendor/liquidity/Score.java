package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.pricing.Adjusted;
public class Score extends EventLogNode {
    private final Adjusted adjusted; private final Book book;
    public transient double value;
    public Score(Adjusted adjusted, Book book) { this.adjusted = adjusted; this.book = book; }
    @OnTrigger public boolean calc() {
        value = adjusted.value / 10 + book.value / 1000;
        auditLog.info("stage", "liquidity.score").info("value", value); return true; }
}
