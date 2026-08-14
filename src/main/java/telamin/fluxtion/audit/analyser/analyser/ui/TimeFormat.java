package telamin.fluxtion.audit.analyser.analyser.ui;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** UTC date/time rendering for epoch-millis fields (decision: all times shown in UTC). */
public final class TimeFormat {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private TimeFormat() {
    }

    /** Formats epoch millis as {@code yyyy-MM-dd HH:mm:ss.SSS} in UTC; null → empty string. */
    public static String utc(Long epochMillis) {
        if (epochMillis == null) return "";
        return FMT.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Human-readable duration for a millisecond span, e.g. {@code 0s}, {@code 12.3s}, {@code 4m 05s}, {@code 1h 02m}. */
    public static String duration(long ms) {
        if (ms <= 0) return "0s";
        long totalSec = ms / 1000;
        if (totalSec < 60) return String.format("%.1fs", ms / 1000.0);
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        return String.format("%dm %02ds", m, s);
    }
}