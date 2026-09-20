package com.example.myapp.event;

/** A fill. Quantity is signed: positive buys, negative sells. */
public record Trade(String symbol, double quantity, double price) {}
