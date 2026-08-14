package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lenient parser for the {@code #time [thread] LEVEL logger} header comment line. */
public final class HeaderParser {

    // #10:57:37.431 [marketMaker-DEMO] INFO  MAKER_USDMXN_DEMO
    private static final Pattern HEADER = Pattern.compile(
            "^#\\s*(\\d{1,2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)?\\s*" +   // 1: time (optional)
            "(?:\\[([^\\]]*)\\])?\\s*" +                              // 2: thread (optional)
            "(\\S+)?\\s*" +                                          // 3: level (optional)
            "(.*\\S)?\\s*$");                                        // 4: logger (rest, optional)

    private HeaderParser() {
    }

    /** Parses a header line (with or without the leading {@code #}); never throws. */
    public static RecordHeader parse(String line) {
        if (line == null) return RecordHeader.EMPTY;
        String s = line.strip();
        if (!s.startsWith("#")) return RecordHeader.EMPTY;
        Matcher m = HEADER.matcher(s);
        if (!m.matches()) return RecordHeader.EMPTY;
        return new RecordHeader(nz(m.group(1)), nz(m.group(2)), nz(m.group(3)), nz(m.group(4)));
    }

    private static String nz(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
