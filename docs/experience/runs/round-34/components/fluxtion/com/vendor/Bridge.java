package com.vendor;
import java.lang.reflect.Field;
/** VENDOR — reads a value field from whatever stage it was handed. */
public final class Bridge {
    private static double get(Object o) {
        try { Field f = o.getClass().getField("value"); return f.getDouble(o); }
        catch (Exception e) { return 0; }
    }
    public static double midOf(Object o) { return get(o); }
    public static double notionalOf(Object o) { return get(o); }
    public static double adjustedOf(Object o) { return get(o); }
}
