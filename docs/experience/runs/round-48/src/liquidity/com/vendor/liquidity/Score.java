package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
public class Score extends EventLogNode implements ScoreApi {
    private final AdjustedApi adjusted; private final BookApi book;
    public transient double value;
    public Score(@AssignToField("adjusted") AdjustedApi adjusted, @AssignToField("book") BookApi book) { this.adjusted = adjusted; this.book = book; }
    public double score() { return value; }
    @OnTrigger public boolean calc() {
        value = adjusted.adjusted() / 10 + book.book() / 1000;
        auditLog.info("stage", "liquidity.score").info("value", value); return true; }
}
