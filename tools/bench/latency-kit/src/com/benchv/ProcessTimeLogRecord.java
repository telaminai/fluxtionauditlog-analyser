package com.benchv;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

/**
 * Round 63 §7.4 Z4 control — the stock <b>text</b> record, changed in exactly one way: {@code logTime}
 * and {@code endTime} come from {@code Clock.getProcessTime()} (already read once per event by
 * {@code Clock.eventReceived}) instead of two live {@code getWallClockTime()} calls.
 *
 * <p>If the clock is what §7.4 claims, the text arm must improve by the same absolute nanoseconds as
 * the binary arm did.
 */
public final class ProcessTimeLogRecord extends LogRecord {

    public ProcessTimeLogRecord(Clock clock) {
        super(clock);
    }

    @Override
    public void triggerEvent(Event event) {
        header(event.getClass(), event, event.filterString());
    }

    @Override
    public void triggerObject(Object event) {
        if (event instanceof Event) {
            triggerEvent((Event) event);
        } else {
            header(event.getClass(), event, null);
        }
    }

    private void header(Class<?> type, Object event, String filter) {
        if (loggingEnabled()) {
            sb.append("eventLogRecord: ");
            sb.append("\n    eventTime: ");
            timeFormatter.accept(sb, clock.getEventTime());
            sb.append("\n    logTime: ");
            timeFormatter.accept(sb, clock.getProcessTime());     // <- the only change
            sb.append("\n    groupingId: ").append(groupingId);
            sb.append("\n    event: ").append(type.getSimpleName());
            if (printEventToString) {
                sb.append("\n    eventToString: ").append(event.toString());
            }
            if (printThreadName) {
                sb.append("\n    thread: ").append(Thread.currentThread().getName());
            }
            if (filter != null && !filter.isEmpty()) {
                sb.append("\n    eventFilter: ").append(filter);
            }
            sb.append("\n    nodeLogs: ");
        }
    }

    @Override
    public boolean terminateRecord() {
        boolean logged = !firstProp;
        if (loggingEnabled()) {
            if (sourceId != null) {
                sb.append("}");
            }
            sb.append("\n    endTime: ");
            timeFormatter.accept(sb, clock.getProcessTime());     // <- and here
        }
        firstProp = true;
        sourceId = null;
        return logged;
    }
}
