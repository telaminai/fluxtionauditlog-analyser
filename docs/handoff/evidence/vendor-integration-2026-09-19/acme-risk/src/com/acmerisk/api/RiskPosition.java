package com.acmerisk.api;

/** Vendor event: the customer's current position in a symbol, in contracts, with the contract multiplier. */
public record RiskPosition(String symbol, double quantity, double multiplier) {}
