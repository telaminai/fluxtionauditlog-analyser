package com.vendor.liquidity;
import com.vendor.pricing.Adjusted;
public class Score {
    private final Adjusted adjusted; private final Book book;
    public double value;
    public Score(Adjusted adjusted, Book book) { this.adjusted = adjusted; this.book = book; }
    public boolean calc() {
        value = adjusted.value / 10 + book.value / 1000;
        com.vendor.Audit.log("liquidity.score", value); return true; }
}
