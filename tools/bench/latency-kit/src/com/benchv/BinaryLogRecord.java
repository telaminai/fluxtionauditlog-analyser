package com.benchv;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

import java.util.IdentityHashMap;

/**
 * A {@link LogRecord} that writes <b>bits, not characters</b>.
 *
 * <p>Installed through the seam that already exists — {@code new EventLogControlEvent(record)} — so no
 * core change is needed to measure it. Every one of the seven {@code addRecord} overloads is
 * overridden; none of them touches the inherited {@code StringBuilder}.
 *
 * <h2>Wire shape</h2>
 * Names are not written. A node name or property key is interned once to a {@code short} id, and only
 * the id goes on the wire; the id table is written out separately (it is fixed after warm-up, being
 * generated code passing String constants). A value is a 1-byte type tag plus its raw bits.
 *
 * <pre>
 *   record  := header, entry*, terminator
 *   header  := 0x01, eventTime:long, logTime:long, eventTypeId:short
 *   entry   := nodeId:short, keyId:short, tag:byte, bits
 *   term    := 0x00, endTime:long
 * </pre>
 *
 * <p>The dictionary is what buys the compression: a 17-character double becomes 8 bytes, and
 * {@code "        - notional: { value: "} becomes 4.
 */
public final class BinaryLogRecord extends LogRecord {

    private static final byte TAG_DOUBLE = 1, TAG_LONG = 2, TAG_INT = 3, TAG_CHAR = 4,
            TAG_CHARSEQ = 5, TAG_OBJECT = 6, TAG_BOOL = 7;

    /** {@code live} = stock behaviour, {@code process} = reuse the clock read Clock already did,
     *  {@code none} = no wall-clock read at all. Z-arm switch for round 63 §7.4. */
    public static String clockMode = System.getProperty("clock", "live");

    private final byte[] buf;
    private int pos;
    private boolean overflow;

    /** Interning: the fallback map, plus a one-entry identity cache that generated code should always hit. */
    private final IdentityHashMap<String, Short> ids = new IdentityHashMap<>();
    private short nextId = 1;
    /** Two slots, not one: {@code head()} alternates node then key, so a single slot never hits. */
    private String lastNode, lastKey;
    private short lastNodeId, lastKeyId;
    private long cacheHits, cacheMisses;

    public BinaryLogRecord(Clock clock, int capacity) {
        super(clock);
        this.buf = new byte[capacity];
    }

    private short nodeId(String name) {
        if (name == lastNode) {          // reference equality — generated code passes constants
            cacheHits++;
            return lastNodeId;
        }
        cacheMisses++;
        lastNode = name;
        return lastNodeId = intern(name);
    }

    private short keyId(String name) {
        if (name == lastKey) {
            cacheHits++;
            return lastKeyId;
        }
        cacheMisses++;
        lastKey = name;
        return lastKeyId = intern(name);
    }

    private short intern(String name) {
        Short existing = ids.get(name);
        if (existing != null) {
            return existing;
        }
        short id = nextId++;
        ids.put(name, id);
        return id;
    }

    private void u8(int v) {
        if (pos < buf.length) { buf[pos++] = (byte) v; } else { overflow = true; }
    }

    private void u16(int v) {
        if (pos + 2 <= buf.length) { buf[pos++] = (byte) (v >>> 8); buf[pos++] = (byte) v; } else { overflow = true; }
    }

    private void i64(long v) {
        if (pos + 8 <= buf.length) {
            buf[pos++] = (byte) (v >>> 56); buf[pos++] = (byte) (v >>> 48);
            buf[pos++] = (byte) (v >>> 40); buf[pos++] = (byte) (v >>> 32);
            buf[pos++] = (byte) (v >>> 24); buf[pos++] = (byte) (v >>> 16);
            buf[pos++] = (byte) (v >>> 8);  buf[pos++] = (byte) v;
        } else { overflow = true; }
    }

    private void i32(int v) {
        if (pos + 4 <= buf.length) {
            buf[pos++] = (byte) (v >>> 24); buf[pos++] = (byte) (v >>> 16);
            buf[pos++] = (byte) (v >>> 8);  buf[pos++] = (byte) v;
        } else { overflow = true; }
    }

    private long now() {
        switch (clockMode) {
            case "process": return clock.getProcessTime();
            case "none":    return 0L;
            default:        return clock.getWallClockTime();
        }
    }

    private void head(String sourceId, String propertyKey) {
        u16(nodeId(sourceId));
        u16(propertyKey == null ? 0 : keyId(propertyKey));
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, double value) {
        head(sourceId, propertyKey);
        u8(TAG_DOUBLE);
        i64(Double.doubleToRawLongBits(value));
        firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, long value) {
        head(sourceId, propertyKey); u8(TAG_LONG); i64(value); firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, int value) {
        head(sourceId, propertyKey); u8(TAG_INT); i32(value); firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, char value) {
        head(sourceId, propertyKey); u8(TAG_CHAR); u16(value); firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, boolean value) {
        head(sourceId, propertyKey); u8(TAG_BOOL); u8(value ? 1 : 0); firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, CharSequence value) {
        head(sourceId, propertyKey);
        u8(TAG_CHARSEQ);
        int n = value == null ? 0 : value.length();
        u16(n);
        for (int i = 0; i < n; i++) { u8(value.charAt(i)); }
        firstProp = false;
    }

    @Override
    public void addRecord(String sourceId, String propertyKey, Object value) {
        // The only overload that cannot avoid text. A deployment aiming at the latency profile should
        // not be logging Objects; it is here so the record is complete, not because it is fast.
        head(sourceId, propertyKey);
        u8(TAG_OBJECT);
        String s = value == null ? "NULL" : value.toString();
        u16(s.length());
        for (int i = 0; i < s.length(); i++) { u8(s.charAt(i)); }
        firstProp = false;
    }

    @Override
    public void addTrace(String sourceId) {
        head(sourceId, null);
        u8(0);
    }

    @Override
    public void triggerEvent(Event event) { header(event.getClass()); }

    @Override
    public void triggerObject(Object event) { header(event.getClass()); }

    private void header(Class<?> type) {
        pos = 0;
        overflow = false;
        u8(1);
        i64(clock.getEventTime());
        i64(now());
        u16(intern(type.getName()));
    }

    @Override
    public boolean terminateRecord() {
        boolean logged = !firstProp;
        u8(0);
        i64(now());
        firstProp = true;
        sourceId = null;
        return logged;
    }

    @Override
    public void clear() {
        firstProp = true;
        sourceId = null;
        pos = 0;
    }

    /** The encoded record. The sink writes {@code buf[0..length)} and nothing else. */
    public byte[] buffer() { return buf; }

    public int length() { return pos; }

    public boolean overflowed() { return overflow; }

    public long cacheHits() { return cacheHits; }

    public long cacheMisses() { return cacheMisses; }

    public int dictionarySize() { return ids.size(); }

    /** The id table, so a reader can resolve ids back to names. Index 0 is unused. */
    public String[] dictionary() {
        String[] out = new String[nextId];
        ids.forEach((name, id) -> out[id] = name);
        return out;
    }

    @Override
    public CharSequence asCharSequence() {
        throw new UnsupportedOperationException("binary record - use buffer()/length()");
    }
}
