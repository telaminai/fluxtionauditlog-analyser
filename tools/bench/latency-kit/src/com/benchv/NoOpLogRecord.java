package com.benchv;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

/**
 * A {@link LogRecord} that does <b>nothing</b> — the hoop the audit path jumps through with the record
 * removed.
 *
 * <p>The auditor still runs, every node still calls {@code auditLog.info(k, v)}, every call still
 * reaches an {@code addRecord} override. Nothing is written. So the difference between this and the
 * no-auditor baseline is <b>the audit dispatch and the EventLogger call chain</b>, and the difference
 * between this and a real record is <b>the cost of building the record</b>.
 *
 * <p>Without this arm those two are inseparable, and the question "is native's audit penalty the shape
 * of the generated code or the implementation of the encoder?" cannot be answered — only guessed at.
 *
 * <p>{@code terminateRecord} returns {@code true} so the sink is still invoked exactly once per event,
 * keeping the publish path identical to the real records.
 */
public final class NoOpLogRecord extends LogRecord {

    private int entries;

    public NoOpLogRecord(Clock clock) {
        super(clock);
    }

    @Override public void addRecord(String sourceId, String propertyKey, double value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, long value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, int value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, char value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, boolean value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, CharSequence value) { entries++; }
    @Override public void addRecord(String sourceId, String propertyKey, Object value) { entries++; }
    @Override public void addTrace(String sourceId) { }
    @Override public void triggerEvent(Event event) { entries = 0; }
    @Override public void triggerObject(Object event) { entries = 0; }
    @Override public boolean terminateRecord() { return true; }
    @Override public void clear() { entries = 0; }

    /** Proves the call chain really ran — a zero here would mean the arm measured nothing. */
    public int entries() { return entries; }

    @Override
    public CharSequence asCharSequence() {
        throw new UnsupportedOperationException("no-op record - nothing was written");
    }
}
