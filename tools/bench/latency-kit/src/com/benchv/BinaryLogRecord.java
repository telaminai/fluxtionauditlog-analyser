package com.benchv;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
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

    /** Resolved once at construction. The String switch this replaces cost a hash and an equals on
     *  every header and every terminator — twice per record — and was an artifact of the class
     *  carrying three experiment modes. A real encoder has one mode. */
    private final boolean useProcessTime = "process".equals(clockMode);
    private final boolean noClock = "none".equals(clockMode);

    /** One unaligned store and one bounds check, instead of eight of each. Round 63 §19.4. */
    private static final VarHandle LONG_VIEW =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_VIEW =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT_VIEW =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /** {@code -Dstore=bytewise} restores the original loop, so the two can be compared in one binary. */
    private static final boolean BYTEWISE = "bytewise".equals(System.getProperty("store", "varhandle"));

    /** Echoed on the RESULT line so a comparison records which store path it measured. */
    public static String storeMode() { return BYTEWISE ? "bytewise" : "varhandle"; }

    /**
     * {@code -Dintern=map|none}. {@code none} writes a constant id and never looks one up — it is not a
     * usable encoder, it is the CEILING: what the record would cost if name resolution were free.
     * Round 63 §19.5.
     */
    private static final String INTERN = System.getProperty("intern", "table");
    private static final boolean NO_INTERN = "none".equals(INTERN);
    private static final boolean MAP_INTERN = "map".equals(INTERN);

    public static String internMode() { return INTERN; }

    /**
     * Open-addressed identity table — the fix for interning, Round 63 §19.5.
     *
     * <p>The map version cost 30.7 ns/event on JIT and 27.5 on native, because a one-slot-per-role
     * cache misses whenever two nodes alternate and every miss falls through to
     * {@code IdentityHashMap.get}. Generated code passes interned String constants, so identity is the
     * right comparison and a power-of-two table with linear probing resolves a name in one array read
     * and one reference compare on the hit path — no hashing of characters, no Map call.
     *
     * <p>Sized generously and never resized: the name set is fixed after warm-up because it comes from
     * constants in generated source. A full table falls back to the map rather than looping.
     */
    private static final int TBL = 256, MASK = TBL - 1;
    private final String[] tblKey = new String[TBL];
    private final short[] tblVal = new short[TBL];

    private short tableId(String name) {
        int i = System.identityHashCode(name) & MASK;
        for (int probe = 0; probe < 8; probe++) {
            String k = tblKey[i];
            if (k == name) { cacheHits++; return tblVal[i]; }
            if (k == null) {
                cacheMisses++;
                short id = intern(name);
                tblKey[i] = name;
                tblVal[i] = id;
                return id;
            }
            i = (i + 1) & MASK;
        }
        cacheMisses++;
        return intern(name);
    }

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
        if (NO_INTERN) { return 1; }
        if (!MAP_INTERN) { return tableId(name); }
        if (name == lastNode) {          // reference equality — generated code passes constants
            cacheHits++;
            return lastNodeId;
        }
        cacheMisses++;
        lastNode = name;
        return lastNodeId = intern(name);
    }

    private short keyId(String name) {
        if (NO_INTERN) { return 2; }
        if (!MAP_INTERN) { return tableId(name); }
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
        if (pos + 2 <= buf.length) {
            if (BYTEWISE) { buf[pos++] = (byte) (v >>> 8); buf[pos++] = (byte) v; }
            else { SHORT_VIEW.set(buf, pos, (short) v); pos += 2; }
        } else { overflow = true; }
    }

    private void i64(long v) {
        if (pos + 8 <= buf.length) {
            if (BYTEWISE) {
                buf[pos++] = (byte) (v >>> 56); buf[pos++] = (byte) (v >>> 48);
                buf[pos++] = (byte) (v >>> 40); buf[pos++] = (byte) (v >>> 32);
                buf[pos++] = (byte) (v >>> 24); buf[pos++] = (byte) (v >>> 16);
                buf[pos++] = (byte) (v >>> 8);  buf[pos++] = (byte) v;
            } else { LONG_VIEW.set(buf, pos, v); pos += 8; }
        } else { overflow = true; }
    }

    private void i32(int v) {
        if (pos + 4 <= buf.length) {
            if (BYTEWISE) {
                buf[pos++] = (byte) (v >>> 24); buf[pos++] = (byte) (v >>> 16);
                buf[pos++] = (byte) (v >>> 8);  buf[pos++] = (byte) v;
            } else { INT_VIEW.set(buf, pos, v); pos += 4; }
        } else { overflow = true; }
    }

    private long now() {
        if (useProcessTime) { return clock.getProcessTime(); }
        if (noClock) { return 0L; }
        return clock.getWallClockTime();
    }

    // ---- the id path: EventLogger resolved these once per node, so nothing is looked up here ----

    /** {@code -Dintern=string} forces the old String path, so the two can be compared in one binary. */
    private static final boolean FORCE_STRING = "string".equals(INTERN);

    @Override
    public int internName(String name) {
        return FORCE_STRING ? NO_ID : intern(name);
    }

    private void headById(int sourceRef, int keyRef) {
        u16(sourceRef);
        u16(keyRef);
    }

    @Override
    public void addRecord(int sourceRef, int keyRef, double value) {
        headById(sourceRef, keyRef); u8(TAG_DOUBLE); i64(Double.doubleToRawLongBits(value));
        firstProp = false;
    }

    @Override
    public void addRecord(int sourceRef, int keyRef, long value) {
        headById(sourceRef, keyRef); u8(TAG_LONG); i64(value); firstProp = false;
    }

    @Override
    public void addRecord(int sourceRef, int keyRef, int value) {
        headById(sourceRef, keyRef); u8(TAG_INT); i32(value); firstProp = false;
    }

    @Override
    public void addRecord(int sourceRef, int keyRef, boolean value) {
        headById(sourceRef, keyRef); u8(TAG_BOOL); u8(value ? 1 : 0); firstProp = false;
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
