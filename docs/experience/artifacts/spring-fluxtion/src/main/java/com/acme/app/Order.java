package com.acme.app;
public record Order(String orderId, String customerId, String sku, int quantity, long timestampMs) {}
