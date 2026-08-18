package telamin.fluxtion.audit.analyser.analyser.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The external-series CSV loader (spec-external-series M29.1). The analyser never learns a foreign
 * format — an agent (or a human) adapts FIX/GC/venue data into {@code (timestamp, value)} CSV and this
 * loader turns it into plottable points. It returns <b>points, not a chart</b> (spec §D): M29's graph
 * surface and M32's marker source are two consumers of the same result.
 *
 * <p>The contract is deliberately explicit (D-F1: <b>declared, never inferred</b>): the caller names
 * the time and value columns, the time format and — unless the format carries an offset — the zone.
 * Nothing is sniffed, because a wrong guess about a clock silently converts "the venue messaged us,
 * then our book moved" into its reverse, and a wrong <i>declaration</i> is at least visible.
 *
 * <p>Honesty rules, each pinned by test:
 * <ul>
 *   <li>rows whose timestamp fails to parse are counted and reported with their <b>original line
 *       number</b> and a short <b>sanitised</b> excerpt — never the cell verbatim (D-F4: the error
 *       message is the one place file content reaches an agent);</li>
 *   <li>a blank/non-numeric value cell is a {@code NaN} point → a gap (the existing invariant);</li>
 *   <li>out-of-order rows are <b>sorted on load</b> and the reorder count reported (G1);</li>
 *   <li>duplicate timestamps are kept — the audit side permits several records per millisecond (G2);</li>
 *   <li>past {@link #MAX_ROWS} the file is <b>refused</b>, loudly, during the streaming pass — never
 *       a silent subset, and never buffered first to find out (G3).</li>
 * </ul>
 */
public final class ExternalCsvLoader {

    /** Refusal bound (G3): beyond this a "series" is a dataset, and silence would be a silent subset. */
    public static final int MAX_ROWS = 5_000_000;

    /** Diagnostics are bounded — the report names how many more rows failed beyond these. */
    public static final int MAX_DIAGNOSTICS = 20;

    /** Longest excerpt of a failing cell that a diagnostic may carry (escaped) — D-F4. */
    public static final int MAX_EXCERPT = 24;

    /** The declared contract (D-F1). {@code zone} may be null only when the format carries an offset. */
    public record Spec(String label, String timeColumn, String timeFormat, String zone,
                       String valueColumn, long offsetMillis) {
    }

    /**
     * Points plus the honesty report. {@code series} carries {@code (epochMillis, value)} in time
     * order with the declared offset applied; NaN values are gaps, exactly like every other series.
     */
    public record Result(Series series, int rowsLoaded, int rowsSkipped, int rowsReordered,
                         Long fromMillis, Long toMillis, List<String> diagnostics) {
    }

    private ExternalCsvLoader() {
    }

    public static Result load(Path file, Spec spec) throws IOException {
        return load(file, spec, MAX_ROWS);
    }

    /** {@code maxRows} is parameterised for tests; production callers use {@link #MAX_ROWS}. */
    public static Result load(Path file, Spec spec, int maxRows) throws IOException {
        TimeParser timeParser = timeParser(spec);

        List<long[]> lineAndTime = new ArrayList<>();   // {originalLine, epochMillis}
        List<Double> values = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        int skipped = 0;
        int reordered = 0;
        long maxTimeSeen = Long.MIN_VALUE;   // a row is out of order if ANY earlier row was later

        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = in.readLine();
            if (headerLine == null) throw new IllegalArgumentException("'" + file.getFileName() + "' is empty");
            Map<String, Integer> header = headerIndex(headerLine);
            int timeIdx = column(header, spec.timeColumn(), "time", file);
            int valueIdx = column(header, spec.valueColumn(), "value", file);

            String line;
            int lineNo = 1;                              // the header was line 1
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                if (lineAndTime.size() >= maxRows) {
                    throw new IllegalArgumentException("'" + file.getFileName() + "' exceeds the "
                            + maxRows + "-row bound at line " + lineNo + " — the loader refuses rather "
                            + "than silently truncating; narrow the export");
                }
                List<String> cells = splitCsv(line);
                String timeCell = cell(cells, timeIdx);
                Long t = timeParser.parse(timeCell);
                if (t == null) {
                    skipped++;
                    if (diagnostics.size() < MAX_DIAGNOSTICS) {
                        diagnostics.add("line " + lineNo + ": '" + spec.timeColumn() + "' did not parse as "
                                + spec.timeFormat() + " (" + excerpt(timeCell) + ")");
                    }
                    continue;
                }
                long time = t + spec.offsetMillis();
                if (time < maxTimeSeen) reordered++;     // observed during the pass, before the sort
                else maxTimeSeen = time;
                lineAndTime.add(new long[]{lineNo, time});
                values.add(parseValue(cell(cells, valueIdx)));   // blank/non-numeric → NaN → gap
            }
        }
        if (skipped > MAX_DIAGNOSTICS) {
            diagnostics.add("… and " + (skipped - MAX_DIAGNOSTICS) + " more rows with unparseable times");
        }

        // sort by time, stably (duplicates keep file order); values follow their rows
        Integer[] order = new Integer[lineAndTime.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(lineAndTime.get(a)[1], lineAndTime.get(b)[1]));

        Series series = new Series(spec.label());
        Long from = null, to = null;
        for (int i : order) {
            long time = lineAndTime.get(i)[1];
            series.add(time, values.get(i));
            if (from == null) from = time;
            to = time;
        }
        return new Result(series, series.size(), skipped, reordered, from, to, List.copyOf(diagnostics));
    }

    // ---- time formats (D-F1: declared, never inferred) --------------------------------------------

    private interface TimeParser {
        Long parse(String cell);
    }

    private static TimeParser timeParser(Spec spec) {
        String fmt = spec.timeFormat() == null ? "" : spec.timeFormat().trim();
        if (fmt.isEmpty()) {
            throw new IllegalArgumentException("'timeFormat' is required (epochMillis | epochSeconds | "
                    + "iso8601 | a DateTimeFormatter pattern) — the clock domain is declared, never inferred");
        }
        switch (fmt) {
            case "epochMillis":
                return cell -> parseLong(cell);
            case "epochSeconds":
                return cell -> {
                    Long v = parseLong(cell);
                    return v == null ? null : v * 1000L;
                };
            case "iso8601": {
                ZoneId zone = optionalZone(spec);
                return cell -> {
                    if (cell == null || cell.isBlank()) return null;
                    String s = cell.trim();
                    try {
                        return OffsetDateTime.parse(s).toInstant().toEpochMilli();   // offset in the data
                    } catch (RuntimeException ignored) {
                        // no offset in the text — the declared zone is then REQUIRED (D-F1)
                    }
                    if (zone == null) return null;
                    try {
                        return LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli();
                    } catch (RuntimeException e) {
                        return null;
                    }
                };
            }
            default: {
                DateTimeFormatter pattern;
                try {
                    pattern = DateTimeFormatter.ofPattern(fmt);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("'" + fmt + "' is not a known timeFormat or a valid "
                            + "DateTimeFormatter pattern: " + e.getMessage());
                }
                ZoneId zone = optionalZone(spec);
                return cell -> {
                    if (cell == null || cell.isBlank()) return null;
                    String s = cell.trim();
                    try {
                        return OffsetDateTime.parse(s, pattern).toInstant().toEpochMilli();
                    } catch (RuntimeException ignored) {
                        // pattern may not carry an offset — fall through to the declared zone
                    }
                    if (zone == null) return null;
                    try {
                        return LocalDateTime.parse(s, pattern).atZone(zone).toInstant().toEpochMilli();
                    } catch (RuntimeException e) {
                        return null;
                    }
                };
            }
        }
    }

    /**
     * The declared zone, parsed eagerly so a typo fails the LOAD, not every row. Null is legal here;
     * whether it was REQUIRED is decided at parse time — a text with no offset and no zone yields
     * an unparseable-time diagnostic naming the format, which is D-F1 refusing to guess. For the
     * epoch formats the zone is meaningless and ignored.
     */
    private static ZoneId optionalZone(Spec spec) {
        if (spec.zone() == null || spec.zone().isBlank()) return null;
        try {
            return ZoneId.of(spec.zone().trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + spec.zone() + "' is not an IANA zone (e.g. UTC, "
                    + "Europe/London)");
        }
    }

    // ---- cells -------------------------------------------------------------------------------------

    private static Map<String, Integer> headerIndex(String headerLine) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        List<String> names = splitCsv(headerLine);
        for (int i = 0; i < names.size(); i++) idx.put(names.get(i).trim(), i);
        return idx;
    }

    private static int column(Map<String, Integer> header, String name, String role, Path file) {
        Integer i = name == null ? null : header.get(name.trim());
        if (i == null) {
            throw new IllegalArgumentException("no '" + name + "' column (" + role + ") in '"
                    + file.getFileName() + "' — header has " + header.keySet());
        }
        return i;
    }

    private static String cell(List<String> cells, int idx) {
        return idx < cells.size() ? cells.get(idx) : "";
    }

    /** RFC-4180-lite: quoted fields with doubled-quote escapes; no embedded newlines. */
    public static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static Long parseLong(String cell) {
        if (cell == null || cell.isBlank()) return null;
        try {
            return Long.parseLong(cell.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseValue(String cell) {
        if (cell == null || cell.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(cell.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;   // non-numeric value → gap, the existing invariant
        }
    }

    /**
     * A short, escaped excerpt of a failing cell (D-F4): enough to recognise the shape of the problem,
     * never the content — {@code line 4: expected number, got "ssh-rsa AAAAB3…"} into an agent's
     * context is the leak this bound exists to prevent.
     */
    static String excerpt(String cell) {
        if (cell == null || cell.isBlank()) return "blank";
        String cleaned = cell.trim().replaceAll("[\\p{Cntrl}]", "?");
        String cut = cleaned.length() <= MAX_EXCERPT ? cleaned : cleaned.substring(0, MAX_EXCERPT) + "…";
        return "starts \"" + cut + "\"";
    }
}
