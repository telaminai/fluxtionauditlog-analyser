import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Rung-2 oracle: drives the subject's compiled processor through the frozen scenario and checks only what the spec
 * fixes — event types, the `pause` signal, the exported Commands.reset(), and the `checked` sink. Checked's fields are
 * the author's choice, so each output is read reflectively: its numeric components must be exactly {price, count}.
 *
 * Expected (sealed 2026-09-23): outputs Checked(10,1) then Checked(40,1); nothing for the filter-9 price, nothing while
 * paused, nothing from reset itself; after reset the count restarts.
 *
 * Run: java -cp <subject target/classes>:<runtime jars> R2Oracle.java
 */
public class R2Oracle {
    static List<double[]> outputs = new ArrayList<>();

    static double[] values(Object checked) throws Exception {
        if (!checked.getClass().isRecord()) throw new AssertionError("Checked is not a record: " + checked.getClass());
        List<Double> v = new ArrayList<>();
        for (RecordComponent c : checked.getClass().getRecordComponents()) {
            Object x = c.getAccessor().invoke(checked);
            if (x instanceof Number n) v.add(n.doubleValue());
        }
        return v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    static void expect(int step, int size) {
        if (outputs.size() != size) throw new AssertionError("step " + step + ": expected " + size + " outputs, got " + outputs.size());
        System.out.println("STEP " + step + " outputs=" + outputs.size());
    }

    public static void main(String[] a) throws Exception {
        var p = new com.example.myapp.generated.MyProcessor();
        p.setAuditLogProcessor(r -> {});
        p.init();
        p.addSink("checked", (Object c) -> {
            try { outputs.add(values(c)); } catch (Exception e) { throw new RuntimeException(e); }
        });
        expect(0, 0);
        p.onEvent(new com.example.myapp.event.PriceUpdate("DEMO", 10, 7)); expect(1, 1);
        p.onEvent(new com.example.myapp.event.PriceUpdate("DEMO", 20, 9)); expect(2, 1);
        p.publishSignal("pause"); expect(3, 1);
        p.onEvent(new com.example.myapp.event.PriceUpdate("DEMO", 30, 7)); expect(4, 1);
        p.getExportedService(com.example.myapp.service.Commands.class).reset(); expect(5, 1);
        p.onEvent(new com.example.myapp.event.PriceUpdate("DEMO", 40, 7)); expect(6, 2);
        double[][] want = {{1, 10}, {1, 40}};
        for (int i = 0; i < 2; i++)
            if (!java.util.Arrays.equals(outputs.get(i), want[i]))
                throw new AssertionError("output " + i + ": " + java.util.Arrays.toString(outputs.get(i)));
        System.out.println("PASS outputs=[{10,1},{40,1}]");
    }
}
