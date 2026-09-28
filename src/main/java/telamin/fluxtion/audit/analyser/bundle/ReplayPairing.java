package telamin.fluxtion.audit.analyser.bundle;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does a replay file belong to the open log (spec-evidence-bundle-replay §4.1)? What the frame OBSERVES and reports
 * with a capture request; whether the capture may carry it is the {@code evidenceCapture} node's decision.
 *
 * <p>A replay holds the run's INPUTS only (R-D10: the graph's own events are never recorded), each stamped with the
 * instant its cycle ran at. So its records must appear, in order, WITHIN the log: each matching a log record with the
 * same event name and the same {@code eventTime}. The log records between them are the ones the graph raised itself
 * and exported-service calls. A replay from another run, or re-stamped by a writer that read the clock again, fails at
 * its first record that has no match.
 *
 * <p>Nothing is loaded from the file: only each record's event class NAME and its time are read, and any document that
 * is not a replay record refuses the file. That, and the pairing, is what stands between an arbitrary file named as a
 * replay and the bundle it would otherwise be packed into.
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
    public record Observed(int records, int serviceCalls, String problem, String sha256) {
        public Observed(int records, int serviceCalls, String problem) {
            this(records, serviceCalls, problem, null);
        }

        public boolean pairs() {
            return problem == null;
        }
    }

    private static final String HEADER = "!!com.telamin.fluxtion.runtime.event.ReplayRecord";
    private static final Pattern EVENT = Pattern.compile("^event: !!([\\w.$]+) \\{.*}$");
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$");

    public static Observed observe(Path replay, LogIndex index, int records) {
        int serviceCalls = 0;
        for (int i = 0; i < records; i++) if (SERVICE_CALL.equals(index.event(i))) serviceCalls++;
        if (replay == null || !Files.isRegularFile(replay)) {
            return new Observed(0, serviceCalls, "cannot read " + replay + ": not a file");
        }
        int k = 0;          // replay records read
        int j = 0;          // the next log record a replay record may match
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
                if (line.isBlank()) continue;
                if (!line.equals("---")) return notRecord(k, serviceCalls, lineNo);
                String header = in.readLine();
                String event = in.readLine();
                String time = in.readLine();
                lineNo += 3;
                Matcher e = event == null ? null : EVENT.matcher(event);
                Matcher t = time == null ? null : TIME.matcher(time);
                if (!HEADER.equals(header) || e == null || !e.matches() || t == null || !t.matches()) {
                    return notRecord(k, serviceCalls, lineNo);
                }
                String name = simpleName(e.group(1));
                long at = Long.parseLong(t.group(1));
                while (j < records && !(name.equals(index.event(j)) && Long.valueOf(at).equals(index.eventTime(j)))) j++;
                if (j == records) {
                    return new Observed(k, serviceCalls, "its record " + k + " (" + name + " at " + at
                            + ") matches no log record after the previous one: is it from another run?");
                }
                j++;
                k++;
            }
        } catch (IOException | NumberFormatException x) {
            return new Observed(k, serviceCalls, "cannot read it: " + x.getMessage());
        }
        if (k == 0) return new Observed(0, serviceCalls, "it holds no replay records");
        return new Observed(k, serviceCalls, null, java.util.HexFormat.of().formatHex(md.digest()));
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
