package com.vendor;
import java.util.function.BiConsumer;
/** Where components record their figures. The application supplies the destination. */
public class Audit {
    public static BiConsumer<String, Double> SINK = (s, v) -> {};
    public static void log(String name, double value) { SINK.accept(name, value); }
}
