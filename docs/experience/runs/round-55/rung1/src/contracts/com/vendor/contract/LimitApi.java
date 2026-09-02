package com.vendor.contract;
/**
 * A limit supervisor. Whether it PROPAGATES is the signal - components below it run only when it
 * does. The value it publishes is the exposure that breached.
 */
public interface LimitApi { double breachedExposure(); double limit(); }
