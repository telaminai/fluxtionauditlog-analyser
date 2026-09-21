package com.acmerisk.api;

/** Anything that can price a symbol. A customer's own market data event may implement this. */
public interface Quote {
    String symbol();
    double price();
}
