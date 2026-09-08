package com.benchv;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;

/**
 * Round 63 — sinks for measuring what the audit trail costs to BUILD, separate from writing it.
 *
 * <p>{@link NoOp} is a genuinely empty listener, as asked for. {@link Counting} does one field
 * increment. <b>Both are measured, because a truly empty method risks the compiler eliminating the
 * record construction that precedes it</b> — in which case the number would be a deleted loop, not a
 * cheap one. If NoOp and Counting agree, nothing was eliminated and NoOp's figure is trustworthy.
 */
public class NullSink {

    public static final class NoOp implements LogRecordListener {
        @Override
        public void processLogRecord(LogRecord logRecord) {
        }
    }

    public static final class Counting implements LogRecordListener {
        public long count;
        @Override
        public void processLogRecord(LogRecord logRecord) {
            count++;
        }
    }
}
