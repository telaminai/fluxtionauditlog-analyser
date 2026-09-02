package com.vendor.contract;
/** How a fee is calculated. Implementations are published by the component that owns fees. */
public interface FeeStrategy { double fee(double exposure); String name(); }
