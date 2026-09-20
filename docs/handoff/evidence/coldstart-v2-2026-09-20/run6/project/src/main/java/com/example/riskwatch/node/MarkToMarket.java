package com.example.riskwatch.node;

import com.fluxtion.runtime.annotations.OnTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks the book against the latest prices.
 *
 * <p>Fluxtion works out that this node sits downstream of both books and calls {@link #revalue()}
 * once per event, after whichever parent changed has finished updating.
 */
public class MarkToMarket {

    /** One symbol's valuation at the current prices. */
    public record SymbolRisk(String symbol, int quantity, double averageCost, double price,
                             double exposure, double unrealisedPnl) {
    }

    private final PriceBook priceBook;
    private final PositionBook positionBook;
    private final List<SymbolRisk> risk = new ArrayList<>();
    private double totalPnl;
    private int revaluations;

    public MarkToMarket(PriceBook priceBook, PositionBook positionBook) {
        this.priceBook = priceBook;
        this.positionBook = positionBook;
    }

    @OnTrigger
    public boolean revalue() {
        risk.clear();
        totalPnl = 0;
        for (String symbol : positionBook.symbols()) {
            if (!priceBook.hasPrice(symbol)) {
                continue; // cannot mark a position we have no price for
            }
            int qty = positionBook.quantityOf(symbol);
            double cost = positionBook.averageCostOf(symbol);
            double price = priceBook.priceOf(symbol);
            double pnl = (price - cost) * qty;
            totalPnl += pnl;
            risk.add(new SymbolRisk(symbol, qty, cost, price, Math.abs(qty * price), pnl));
        }
        revaluations++;
        return !risk.isEmpty();
    }

    public List<SymbolRisk> getRisk() {
        return risk;
    }

    public double getTotalPnl() {
        return totalPnl;
    }

    public int getRevaluations() {
        return revaluations;
    }
}
