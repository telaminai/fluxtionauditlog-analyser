package com.acme.app;
public record Paid(String orderId, double amount, long timestampMs) {}
