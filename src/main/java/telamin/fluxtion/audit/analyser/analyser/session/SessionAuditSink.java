package telamin.fluxtion.audit.analyser.analyser.session;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Where the session processor's own audit records go — M44 D-S8.3.
 *
 * <p><b>The default this replaces is not "nothing".</b> Fluxtion's no-arg {@code EventLogManager()}
 * defaults its sink to {@code System.out::println}, and the no-arg constructor is exactly what the
 * generated processor declares — verified in our own {@code SessionProcessor}. So an analyser that
 * forgot to attach a sink would not lose its audit log; it would <em>print every record to stdout</em>,
 * in a desktop application, forever. A green build, a running app, and a silent defect. That failure
 * mode is the reason {@link SessionDriver} attaches this before the first event rather than lazily.
 *
 * <h2>Bounded, and ours</h2>
 * A ring of at most {@code capacity} records. It is deliberately <b>not</b> the user's business audit
 * log: mixing the analyser's own reasoning into the evidence someone is investigating would make the
 * tool a participant in the thing it is examining.
 *
 * <h2>Snapshot, not live</h2>
 * {@link #export(Path)} writes a fixed file. That is the boundary: capture a session, snapshot it, then
 * open the snapshot. Opening a live sink would mean the act of inspecting the log changes the log —
 * self-observation altering the evidence, which is the one failure this product may not have.
 *
 * <h2>Retained by kind (M44.4b, spec §13 D-S13.5)</h2>
 * The processor is generated with tracing on, so every event publishes one record — that is what makes "absent from
 * the record" mean "did not run", and it is kept. Follow reports each poll's appends as a {@code LogAppended} fact, a
 * re-scope that at one poll a second would evict a 2,000-record ring's transitions in about half an hour. So
 * re-scopes are held in a ring of their own ({@link #RESCOPE_CAPACITY}) and can never push a transition out. Both
 * rings count what they dropped, and {@link #records()} and the export interleave them in arrival order, so the
 * record says what it omitted rather than silently omitting it.
 *
 * <p>Not thread-safe by design; the driver is synchronous and single-threaded.
 */
public final class SessionAuditSink implements LogRecordListener {

    /** Enough to hold a long investigation's worth of transitions, small enough to never matter. */
    public static final int DEFAULT_CAPACITY = 2_000;

    /** Re-scopes kept: enough to see recent Follow activity, never enough to matter. */
    public static final int RESCOPE_CAPACITY = 200;

    /** The event line a re-scope record carries — read from a real record, not assumed. */
    static final String RESCOPE_EVENT = "event: LogAppended";

    private final int capacity;
    private final Deque<Held> records = new ArrayDeque<>();
    private final Deque<Held> rescopes = new ArrayDeque<>();
    private long sequence;
    private long droppedRescopes;

    private record Held(long seq, String text) { }

    private long total;
    private long dropped;
    private long sinkFailures;
    private String firstSinkFailure;

    public SessionAuditSink() {
        this(DEFAULT_CAPACITY);
    }

    public SessionAuditSink(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * A sink that throws must never break a session transition — the user asked to open a project, not
     * to write a diagnostic. The failure is counted and surfaced once rather than swallowed entirely,
     * because a sink that is silently failing looks exactly like a session that is silently quiet.
     */
    @Override
    public void processLogRecord(LogRecord logRecord) {
        try {
            String text = logRecord.asCharSequence().toString();
            total++;
            Held held = new Held(sequence++, text);
            if (text.contains(RESCOPE_EVENT)) {
                rescopes.addLast(held);
                while (rescopes.size() > RESCOPE_CAPACITY) {
                    rescopes.removeFirst();
                    droppedRescopes++;
                }
            } else {
                records.addLast(held);
                while (records.size() > capacity) {
                    records.removeFirst();
                    dropped++;
                }
            }
        } catch (RuntimeException e) {
            sinkFailures++;
            if (firstSinkFailure == null) {
                firstSinkFailure = e.toString();
            }
        }
    }

    /** The records currently held, both rings interleaved, oldest first. */
    public List<String> records() {
        List<Held> all = new ArrayList<>(records.size() + rescopes.size());
        all.addAll(records);
        all.addAll(rescopes);
        all.sort(java.util.Comparator.comparingLong(Held::seq));
        List<String> out = new ArrayList<>(all.size());
        for (Held h : all) out.add(h.text());
        return out;
    }

    /** The transition records only — everything except re-scopes. */
    public List<String> transitions() {
        List<String> out = new ArrayList<>(records.size());
        for (Held h : records) out.add(h.text());
        return out;
    }

    /** Every record ever offered, including any the ring has since dropped. */
    public long total() {
        return total;
    }

    /** How many the rings discarded, both kinds — nonzero means {@link #records()} is not the whole session. */
    public long dropped() {
        return dropped + droppedRescopes;
    }

    /** How many TRANSITION records were discarded. Re-scopes cannot cause this. */
    public long droppedTransitions() {
        return dropped;
    }

    public long droppedRescopes() {
        return droppedRescopes;
    }

    public long sinkFailures() {
        return sinkFailures;
    }

    public String firstSinkFailure() {
        return firstSinkFailure;
    }

    /** True when the record held is complete: nothing dropped and nothing failed to be written. */
    public boolean isComplete() {
        return dropped() == 0 && sinkFailures == 0;
    }

    public void clear() {
        records.clear();
        rescopes.clear();
        total = 0;
        dropped = 0;
        droppedRescopes = 0;
        sinkFailures = 0;
        firstSinkFailure = null;
    }

    /**
     * Write a snapshot the analyser can then open as an ordinary audit log.
     *
     * @return the path written
     */
    public Path export(Path target) throws IOException {
        // Framed as the analyser's reader frames: a `---` line before every record (finish-first review
        // F4 — the unframed export reopened as ONE merged record, so per-dispatch identity and opIds were
        // lost on the round trip). No header line is invented: the runtime record carries its own
        // logTime, and a level the sink never retained would be a fabrication.
        StringBuilder out = new StringBuilder();
        for (String record : records()) {
            out.append("---\n");
            out.append(record);
            if (record.isEmpty() || record.charAt(record.length() - 1) != '\n') {
                out.append('\n');
            }
        }
        out.append("---\n");
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        return target;
    }

    /** Every record containing the given text — the cheap way to ask "what happened to operation 7?". */
    public List<String> matching(String text) {
        List<String> hits = new ArrayList<>();
        for (String record : records()) {
            if (record.contains(text)) {
                hits.add(record);
            }
        }
        return hits;
    }
}
