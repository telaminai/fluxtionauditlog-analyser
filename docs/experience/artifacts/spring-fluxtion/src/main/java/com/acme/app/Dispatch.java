package com.acme.app;
public record Dispatch(String orderId, String carrierId, double weightKg, long timestampMs) {}
