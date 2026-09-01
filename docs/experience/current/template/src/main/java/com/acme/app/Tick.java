package com.acme.app;

/** An event is a plain Java type. Records work well. */
public record Tick(String symbol, double price) {}
