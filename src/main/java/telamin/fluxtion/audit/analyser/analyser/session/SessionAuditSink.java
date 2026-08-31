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
 * <p>Not thread-safe by design; the driver is synchronous and single-threaded.
 */
public final class SessionAuditSink implements LogRecordListener {

    /** Enough to hold a long investigation's worth of transitions, small enough to never matter. */
    public static final int DEFAULT_CAPACITY = 2_000;

    private final int capacity;
    private final Deque<String> records = new ArrayDeque<>();

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
            records.addLast(text);
            while (records.size() > capacity) {
                records.removeFirst();
                dropped++;
            }
        } catch (RuntimeException e) {
            sinkFailures++;
            if (firstSinkFailure == null) {
                firstSinkFailure = e.toString();
            }
        }
    }

    /** The records currently held, oldest first. */
    public List<String> records() {
        return List.copyOf(records);
    }

    /** Every record ever offered, including any the ring has since dropped. */
    public long total() {
        return total;
    }

    /** How many the ring discarded — nonzero means {@link #records()} is not the whole session. */
    public long dropped() {
        return dropped;
    }

    public long sinkFailures() {
        return sinkFailures;
    }

    public String firstSinkFailure() {
        return firstSinkFailure;
    }

    /** True when the record held is complete: nothing dropped and nothing failed to be written. */
    public boolean isComplete() {
        return dropped == 0 && sinkFailures == 0;
    }

    public void clear() {
        records.clear();
        total = 0;
        dropped = 0;
        sinkFailures = 0;
        firstSinkFailure = null;
    }

    /**
     * Write a snapshot the analyser can then open as an ordinary audit log.
     *
     * @return the path written
     */
    public Path export(Path target) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String record : records) {
            out.append(record);
            if (record.isEmpty() || record.charAt(record.length() - 1) != '\n') {
                out.append('\n');
            }
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        return target;
    }

    /** Every record containing the given text — the cheap way to ask "what happened to operation 7?". */
    public List<String> matching(String text) {
        List<String> hits = new ArrayList<>();
        for (String record : records) {
            if (record.contains(text)) {
                hits.add(record);
            }
        }
        return hits;
    }
}
