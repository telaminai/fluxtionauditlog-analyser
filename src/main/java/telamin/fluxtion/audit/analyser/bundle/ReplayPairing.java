package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does a replay file belong to the open log (spec-evidence-bundle-replay §4.1)? What the frame OBSERVES and reports
 * with a capture request; whether the capture may carry it is the {@code evidenceCapture} node's decision.
 *
 * <p>A replay holds the run's INPUTS only (R-D10: the graph's own events are never recorded), each stamped with the
 * instant its cycle ran at. So its records must appear, in order, WITHIN the log: each matching a log record with the
 * same event name and the same {@code eventTime} and, where the log prints the event, the same content. The log
 * records between them are the ones the graph raised itself and exported-service calls. A replay whose inputs differ
 * from the log's, or re-stamped by a writer that read the clock again, fails at its first record that has no match.
 * This is consistency evidence, not run identity (PR #70 review 5): a replay that agrees with the log in every checked
 * field pairs, whichever run wrote it; an input the log does not print is matched by type and instant only, and
 * counted as that.
 *
 * <p>Nothing is loaded from the file: each record's event class NAME, its time and its component text are read, and
 * any document that is not a replay record refuses the file. That, and the pairing, is what stands between an
 * arbitrary file named as a replay and the bundle it would otherwise be packed into.
 */
public final class ReplayPairing {

    private ReplayPairing() {
    }

    /** The event type the log names for an exported-service call: a replay does not carry one (spec §3.4). */
    public static final String SERVICE_CALL = "ExportFunctionAuditEvent";

    /**
     * What was observed. {@code problem} is null when the replay pairs with the log; otherwise it names the first thing
     * that does not, in words for the refusal. {@code serviceCalls} counts the log's exported-service calls.
     * {@code sha256} is the digest of exactly the bytes that were paired, taken in the same pass: the writer copies the
     * file later, off the event thread, and refuses a copy whose digest differs.
     */
    public record Observed(int records, int serviceCalls, String problem, String sha256, int uncarried, int unproven) {
        public Observed(int records, int serviceCalls, String problem) {
            this(records, serviceCalls, problem, null, 0, 0);
        }

        public Observed(int records, int serviceCalls, String problem, String sha256, int uncarried) {
            this(records, serviceCalls, problem, sha256, uncarried, 0);
        }

        public boolean pairs() {
            return problem == null;
        }
    }

    private static final String HEADER = "!!com.telamin.fluxtion.runtime.event.ReplayRecord";
    private static final Pattern EVENT = Pattern.compile("^event: !!([\\w.$]+) \\{(.*)}$");
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$");

    public static Observed observe(Path replay, LogIndex index, int records) {
        int serviceCalls = 0;
        for (int i = 0; i < records; i++) if (SERVICE_CALL.equals(index.event(i))) serviceCalls++;
        if (replay == null || !Files.isRegularFile(replay)) {
            return new Observed(0, serviceCalls, "cannot read " + replay + ": not a file");
        }
        int k = 0;          // replay records read
        int j = 0;          // the next log record a replay record may match
        int unproven = 0;   // matched by type and instant only: the log does not print the event, so content is unknown
        java.util.Set<String> names = new java.util.HashSet<>();   // the event types the replay carries
        boolean[] matched = new boolean[records];
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new IllegalStateException(x);
        }
        try (var digesting = new java.security.DigestInputStream(Files.newInputStream(replay), md);
             BufferedReader in = new BufferedReader(new java.io.InputStreamReader(digesting, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (lineNo == 1) line = telamin.fluxtion.audit.analyser.analyser.parse.AuditText.withoutLeadingBom(line);
                if (line.isBlank()) continue;
                if (!line.equals("---")) return notRecord(k, serviceCalls, lineNo);
                String header = in.readLine();
                String event = in.readLine();
                String time = in.readLine();
                lineNo += 3;
                if (header == null || event == null || time == null) {
                    return new Observed(k, serviceCalls, "its last record, " + k + ", is cut off: was the file still "
                            + "being written when it was copied?", null, 0);
                }
                Matcher e = event == null ? null : EVENT.matcher(event);
                Matcher t = time == null ? null : TIME.matcher(time);
                if (!HEADER.equals(header) || e == null || !e.matches() || t == null || !t.matches()) {
                    return notRecord(k, serviceCalls, lineNo);
                }
                String name = simpleName(e.group(1));
                long at = Long.parseLong(t.group(1));
                String content = recordText(name, e.group(2));       // the event as its record's toString prints it
                String contentMiss = null;
                boolean proven = false;
                for (; j < records; j++) {
                    if (!name.equals(index.event(j)) || !Long.valueOf(at).equals(index.eventTime(j))) continue;
                    String logged = index.eventToString(j);
                    boolean provable = logged != null && content != null && logged.startsWith(name + "[");
                    if (!provable) break;                                 // type and instant only: counted below
                    if (logged.equals(content)) {
                        proven = true;
                        break;
                    }
                    // PR #70 review, finding 1: the same type at the same instant, with other content, is NOT this
                    // input; look on (a graph-raised event may share an input's type and instant), and remember why
                    if (contentMiss == null) {
                        contentMiss = "its record " + k + " (" + name + " at " + at + ") matches log record " + j
                                + " by type and instant, but not by content: the log has '" + logged
                                + "', the replay '" + content + "'";
                    }
                }
                if (j == records) {
                    if (contentMiss != null) return new Observed(k, serviceCalls, contentMiss);
                    String rest;
                    while ((rest = in.readLine()) != null && rest.isBlank()) { }
                    return new Observed(k, serviceCalls, "its record " + k + " (" + name + " at " + at
                            + ") matches no log record after the previous one: " + (rest == null
                            ? "it is the last, so it may be cut off, or it is not this log's input"
                            : "it is not this log's input"));
                }
                if (!proven) unproven++;
                matched[j] = true;
                names.add(name);
                j++;
                k++;
            }
        } catch (IOException | NumberFormatException x) {
            return new Observed(k, serviceCalls, "cannot read it: " + x.getMessage());
        }
        if (k == 0) return new Observed(0, serviceCalls, "it holds no replay records");
        // review S1: a replay cut short still pairs (each record it has IS one of the log's). Count what it does not
        // carry of its own event types, so the capture can say so rather than read as the whole run
        int uncarried = 0;
        for (int i = 0; i < records; i++) if (!matched[i] && names.contains(index.event(i))) uncarried++;
        return new Observed(k, serviceCalls, null, java.util.HexFormat.of().formatHex(md.digest()), uncarried, unproven);
    }

    /**
     * The event as a Java record's {@code toString} prints it, rebuilt from the replay record's components:
     * {@code MarketDataEvent[symbol=DEMO-A, bid=100.1, ask=100.3]}. Strings and chars unquoted and unescaped (the writer
     * quotes them); every other value as written (the writer writes {@code String.valueOf}, as {@code toString} does).
     * Null when the body cannot be read, so nothing is claimed about its content.
     */
    static String recordText(String name, String body) {
        StringBuilder out = new StringBuilder(name).append('[');
        List<String> parts = split(body);
        for (int i = 0; i < parts.size(); i++) {
            String kv = parts.get(i);
            int colon = kv.indexOf(':');
            if (colon < 0) return null;
            String raw = kv.substring(colon + 1).strip();
            String value;
            if (raw.startsWith("\"")) {
                value = unquote(raw);
                if (value == null) return null;
            } else {
                value = raw;
            }
            if (i > 0) out.append(", ");
            out.append(kv.substring(0, colon).strip()).append('=').append(value);
        }
        return out.append(']').toString();
    }

    /** Split {@code a: 1, b: "x, y"} at top-level commas, respecting quoted strings and their escapes. */
    static List<String> split(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quoted && ch == '\\' && i + 1 < body.length()) {
                cur.append(ch).append(body.charAt(++i));
                continue;
            }
            if (ch == '"') quoted = !quoted;
            if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** One quoted token unescaped, as the replay writer escapes it; null when it is not one. */
    static String unquote(String raw) {
        if (raw.length() < 2 || !raw.endsWith("\"")) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < raw.length() - 1; i++) {
            char ch = raw.charAt(i);
            if (ch != '\\') {
                out.append(ch);
                continue;
            }
            if (++i >= raw.length() - 1) return null;
            switch (raw.charAt(i)) {
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= raw.length() - 1) return null;
                    try {
                        out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException x) {
                        return null;
                    }
                    i += 4;
                }
                default -> {
                    return null;
                }
            }
        }
        return out.toString();
    }

    private static Observed notRecord(int k, int serviceCalls, int lineNo) {
        return new Observed(k, serviceCalls, "line " + lineNo + " is not part of a replay record: it is not a replay file");
    }

    /** {@code com.acme.demo.event.Events$MarketDataEvent} → {@code MarketDataEvent}, as the log names it. */
    static String simpleName(String fqcn) {
        String s = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return s.substring(s.lastIndexOf('$') + 1);
    }
}
