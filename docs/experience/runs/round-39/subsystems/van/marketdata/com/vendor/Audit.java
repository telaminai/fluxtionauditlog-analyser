package com.vendor;
import java.util.function.BiConsumer;
/** The trace sink every subsystem writes to. The consumer supplies the destination. */
public class Audit {
    public static BiConsumer<String, Double> SINK = (s, v) -> {};
    public static void log(String stage, double value) { SINK.accept(stage, value); }
}
