package com.acme.app;

/** Reference data — configuration that arrives as an event but is NOT activity. */
public record Limit(String metric, double threshold) {}
