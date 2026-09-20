package com.example.riskwatch.event;

/** Risk desk changing the maximum gross exposure allowed for a symbol. */
public record RiskLimitUpdate(String symbol, double maxExposure) {
}
