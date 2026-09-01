package com.acme.app;

/** An event is a plain Java type — this template is deliberately domain-neutral. Records work well. */
public record Reading(String sensorId, double value) {}
