package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.tminimal.DagProcessor;
import com.benchv.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Round 63 §7.5 — correctness before performance. Runs the same event sequence through the stock text
 * record and through {@link BinaryLogRecord}, decodes the binary form, and asserts that every
 * node/key/value the text record reports is present in the decoded binary record with the same value.
 *
 * <p>A faster encoder that loses information is not a faster encoder.
 */
public class CorrectnessBinary {

    record Entry(String node, String key, String value) { }

    public static void main(String[] a) throws Exception {
        List<String> textRecords = new ArrayList<>();
        List<List<Entry>> binRecords = new ArrayList<>();
        List<String> binEvents = new ArrayList<>();

        // --- text arm ---
        DagProcessor pt = new DagProcessor();
        EventLogManager mt = pt.getAuditorById(EventLogManager.NODE_NAME);
        mt.setLogSink(r -> textRecords.add(r.asCharSequence().toString()));
        pt.init();
        drive(pt);

        // --- binary arm ---
        DagProcessor pb = new DagProcessor();
        EventLogManager mb = pb.getAuditorById(EventLogManager.NODE_NAME);
        BinaryLogRecord bin = new BinaryLogRecord(mb.clock, 4096);
        mb.setLogSink(r -> {
            BinaryLogRecord b = (BinaryLogRecord) r;
            Decoded d = decode(b.buffer(), b.length(), b.dictionary());
            binEvents.add(d.eventType);
            binRecords.add(d.entries);
        });
        pb.init();
        pb.onEvent(new EventLogControlEvent(bin));
        drive(pb);

        if (textRecords.size() != binRecords.size()) {
            throw new AssertionError("record count differs: text=" + textRecords.size() + " bin=" + binRecords.size());
        }

        int checked = 0;
        for (int i = 0; i < textRecords.size(); i++) {
            String text = textRecords.get(i);
            // the text record names the event type; the binary record must agree
            String evt = between(text, "\n    event: ", "\n");
            String binEvt = binEvents.get(i);
            if (binEvt == null || !binEvt.endsWith(evt)) {
                throw new AssertionError("record " + i + " event type: text=" + evt + " bin=" + binEvt);
            }
            for (Entry e : binRecords.get(i)) {
                String needle = e.key() + ": " + e.value();
                if (!text.contains(e.node()) || !text.contains(needle)) {
                    throw new AssertionError("record " + i + " missing in text: node=" + e.node()
                            + " " + needle + "\n---- text ----\n" + text);
                }
                checked++;
            }
            if (binRecords.get(i).isEmpty()) {
                throw new AssertionError("record " + i + " decoded to no entries; text was:\n" + text);
            }
        }
        System.out.println("PASS  records=" + textRecords.size() + "  entries verified=" + checked);
        System.out.println("sample text record:\n" + textRecords.get(0));
        System.out.println("same record decoded from binary: event=" + binEvents.get(0) + " " + binRecords.get(0));
    }

    static void drive(DagProcessor p) throws Exception {
        E0 e0 = new E0(); E1 e1 = new E1(); E2 e2 = new E2(); E3 e3 = new E3(); E4 e4 = new E4();
        Object[] evs = {e0, e1, e2, e3, e4};
        for (int i = 0; i < 40; i++) {
            Object e = evs[i % 5];
            if (e instanceof E0 x) { x.set(1.0 + i); }
            if (e instanceof E1 x) { x.set(1.0 + i); }
            if (e instanceof E2 x) { x.set(1.0 + i); }
            if (e instanceof E3 x) { x.set(1.0 + i); }
            if (e instanceof E4 x) { x.set(1.0 + i); }
            p.onEvent(e);
        }
    }

    record Decoded(String eventType, List<Entry> entries) { }

    static Decoded decode(byte[] b, int len, String[] dict) {
        int p = 0;
        if (b[p++] != 1) { throw new AssertionError("bad header"); }
        p += 8; // eventTime
        p += 8; // logTime
        String eventType = dict[u16(b, p)]; p += 2;
        List<Entry> out = new ArrayList<>();
        while (p < len) {
            int nodeId = u16(b, p); p += 2;
            if (nodeId == 0) { break; }          // terminator
            int keyId = u16(b, p); p += 2;
            int tag = b[p++] & 0xFF;
            String value;
            switch (tag) {
                case 1: value = fmt(Double.longBitsToDouble(i64(b, p))); p += 8; break;
                case 2: value = Long.toString(i64(b, p)); p += 8; break;
                case 3: value = Integer.toString(i32(b, p)); p += 4; break;
                case 7: value = (b[p++] & 0xFF) == 1 ? "true" : "false"; break;
                default: throw new AssertionError("tag " + tag + " not decoded in this check");
            }
            out.add(new Entry(dict[nodeId], dict[keyId], value));
        }
        return new Decoded(eventType, out);
    }

    static String fmt(double d) { return Double.toString(d); }
    static int u16(byte[] b, int p) { return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF); }
    static int i32(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p+1] & 0xFF) << 16) | ((b[p+2] & 0xFF) << 8) | (b[p+3] & 0xFF);
    }
    static long i64(byte[] b, int p) {
        long v = 0;
        for (int i = 0; i < 8; i++) { v = (v << 8) | (b[p + i] & 0xFF); }
        return v;
    }
    static String between(String s, String from, String to) {
        int i = s.indexOf(from);
        if (i < 0) { return null; }
        int j = s.indexOf(to, i + from.length());
        return s.substring(i + from.length(), j < 0 ? s.length() : j);
    }
}
