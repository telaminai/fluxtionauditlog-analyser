import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/** Rung-4 oracle: the greenfield VWAP spec. Checks only what the spec fixes: the Trade event, the `clear` signal and
 *  the `vwap` sink. VwapUpdate's shape (record or class) and field names are the author's choice; each output must
 *  carry one String (the symbol) and one number (the vwap). Expected: (A,10) (A,15) (B,50) (A,22.5) (A,30) (A,60) (B,70). */
public class R4Oracle {
    record Out(String symbol, double vwap) {}
    static final List<Out> outputs = new ArrayList<>();

    /** The values an output carries: a record's components, or else its public no-argument accessors. The spec asks
     *  for "a VwapUpdate carrying the symbol and its VWAP", not for a record (lad-r4-h55-1 wrote a final class). */
    static List<Object> values(Object o) throws Exception {
        List<Object> out = new ArrayList<>();
        if (o.getClass().isRecord()) {
            for (RecordComponent c : o.getClass().getRecordComponents()) out.add(c.getAccessor().invoke(o));
            return out;
        }
        for (java.lang.reflect.Method m : o.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getDeclaringClass() != Object.class && !m.getName().equals("hashCode")
                    && !m.getName().equals("toString") && m.getReturnType() != void.class)
                out.add(m.invoke(o));
        }
        return out;
    }

    static Out read(Object o) throws Exception {
        String sym = null; Double v = null;
        for (Object x : values(o)) {
            if (x instanceof String s) { if (sym != null) throw new AssertionError("two String fields"); sym = s; }
            else if (x instanceof Number n) { if (v != null) throw new AssertionError("two numeric fields"); v = n.doubleValue(); }
        }
        if (sym == null || v == null) throw new AssertionError("needs one String and one number: " + o);
        return new Out(sym, v);
    }

    static void step(int n, int size) {
        if (outputs.size() != size) throw new AssertionError("step " + n + ": expected " + size + " outputs, got " + outputs.size() + " " + outputs);
    }

    public static void main(String[] a) throws Exception {
        var p = new com.example.myapp.generated.TradeProcessor();
        p.setAuditLogProcessor(r -> {});
        p.init();
        p.addSink("vwap", (Object o) -> { try { outputs.add(read(o)); } catch (Exception e) { throw new RuntimeException(e); } });
        Object[][] trades = {{"A", 10.0, 100L}, {"A", 20.0, 100L}, {"B", 50.0, 10L}, {"A", 30.0, 200L}, {"A", 40.0, 100L}, {"A", 99.0, 0L}};
        int[] sizes = {1, 2, 3, 4, 5, 5};
        for (int i = 0; i < trades.length; i++) {
            p.onEvent(new com.example.myapp.event.Trade((String) trades[i][0], (Double) trades[i][1], (Long) trades[i][2]));
            step(i + 1, sizes[i]);
        }
        p.publishSignal("clear"); step(7, 5);
        p.onEvent(new com.example.myapp.event.Trade("A", 60.0, 100L)); step(8, 6);
        p.onEvent(new com.example.myapp.event.Trade("B", 70.0, 30L)); step(9, 7);
        List<Out> want = List.of(new Out("A", 10), new Out("A", 15), new Out("B", 50), new Out("A", 22.5), new Out("A", 30),
                new Out("A", 60), new Out("B", 70));
        for (int i = 0; i < want.size(); i++)
            if (!want.get(i).symbol().equals(outputs.get(i).symbol()) || Math.abs(want.get(i).vwap() - outputs.get(i).vwap()) > 1e-9)
                throw new AssertionError("output " + i + ": " + outputs.get(i) + " expected " + want.get(i));
        System.out.println("PASS outputs=" + outputs);
    }
}
