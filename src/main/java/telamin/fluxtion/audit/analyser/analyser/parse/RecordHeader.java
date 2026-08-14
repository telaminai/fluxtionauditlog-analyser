package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * Parsed form of the {@code #HH:mm:ss.SSS [thread] LEVEL logger} comment line that precedes each
 * {@code eventLogRecord}. Any field may be {@code null} if the header is missing or malformed.
 *
 * @param time   wall-clock time string as printed (e.g. {@code 10:57:37.431})
 * @param thread agent/thread name (e.g. {@code marketMaker-DEMO})
 * @param level  log level (e.g. {@code INFO})
 * @param logger the processor's audit logger name (e.g. {@code MAKER_USDMXN_DEMO})
 */
public record RecordHeader(String time, String thread, String level, String logger) {
    static final RecordHeader EMPTY = new RecordHeader(null, null, null, null);
}
