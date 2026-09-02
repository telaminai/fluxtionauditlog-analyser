package com.vendor.contract;
import java.util.function.BiConsumer;
/** The trace sink every component writes to. The application supplies the destination. */
public class Audit {
    public static BiConsumer<String, Double> SINK = (s, v) -> {};
    public static BiConsumer<String, String> NOTE = (s, v) -> {};
    public static void log(String stage, double value) { SINK.accept(stage, value); }
    public static void note(String k, String v) { NOTE.accept(k, v); }
}
