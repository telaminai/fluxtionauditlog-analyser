package com.vendor.capital;
import java.util.function.Consumer;
/** Where published alerts go. The application supplies the destination. */
public class AlertSink {
    public static Consumer<String> PUBLISH = a -> {};
}
