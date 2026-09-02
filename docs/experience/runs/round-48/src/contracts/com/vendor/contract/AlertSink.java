package com.vendor.contract;
/** Where published alerts go. Register an implementation as a service; the engine routes it. */
public interface AlertSink { void publish(String alert); }
