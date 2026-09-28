import com.telamin.fluxtion.builder.replay.YamlReplayRecordWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import com.telamin.fluxtion.runtime.time.ClockStrategy;
import java.io.StringWriter;
// Which instant does YamlReplayRecordWriter record, installed with the processor's own Clock? (finding 7)
public class RecorderClockProbe {
    public static void main(String[] a) {
        long[] t = {1000};
        Clock c = new Clock();
        c.setClockStrategy(ClockStrategy.registerClockEvent(() -> t[0]++));   // ticks on every read, as a wall clock does
        StringWriter out = new StringWriter();
        YamlReplayRecordWriter w = new YamlReplayRecordWriter(c);   // the processor's own clock, as installed
        w.setTargetWriter(out); w.init();
        c.eventReceived("evt");            // the clock auditor is FirstAfterEvent: it fixes processTime first
        w.eventReceived("evt");            // then the recorder
        System.out.println("processTime (what the cycle and the audit log use) = " + c.getProcessTime());
        System.out.println("eventTime = " + c.getEventTime());
        System.out.println("recorded:\n" + out);
    }
}
