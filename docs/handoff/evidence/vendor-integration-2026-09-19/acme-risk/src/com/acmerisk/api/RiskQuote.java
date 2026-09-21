package com.acmerisk.api;

/** Vendor event: latest price for a symbol. */
public record RiskQuote(String symbol, double price) implements Quote {}
