package telamin.fluxtion.audit.analyser.analyser.summary;

/**
 * One summary line: a group (event dimension) with its record count and log-time span within the
 * active filter.
 */
public record SummaryRow(String dimension, long count, Long firstLog, Long lastLog) {

    /** Span in millis (last-first), or 0 if not computable. */
    public long spanMillis() {
        if (firstLog == null || lastLog == null) return 0L;
        return Math.max(0L, lastLog - firstLog);
    }

    /** Records per minute across the span, or 0 when the span is zero. */
    public double ratePerMinute() {
        long span = spanMillis();
        if (span <= 0) return 0.0;
        return count / (span / 60_000.0);
    }
}
