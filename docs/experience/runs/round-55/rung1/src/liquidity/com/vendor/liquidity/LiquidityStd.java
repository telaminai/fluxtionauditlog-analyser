package com.vendor.liquidity;
import com.vendor.contract.*;
/** Entry point: book depth and a liquidity score. */
public class LiquidityStd {
    public final LqTick tick; public final Book book; public final Score score;
    public LiquidityStd(DepthApi depth, AdjustedApi adjusted) {
        this.tick = new LqTick();
        this.book = new Book(tick, depth);
        this.score = new Score(adjusted, book); }
    public LiquidityStd(LqTick tick, Book book, Score score) {
        this.tick = tick; this.book = book; this.score = score; }
}
