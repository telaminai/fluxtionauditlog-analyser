import com.acme.demo.event.Events;
import com.acme.demo.generated.DemoQuoteRecordedProcessor;
import com.acme.demo.replay.ReplayCapture;
import com.acme.demo.replay.ReplayReader;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.StringWriter;

/**
 * A producer's own run of the generated DEMO processor, driven directly, as a live system would drive it (PR #70
 * review 3 and 6): each input named to the replay writer at its consumption point, then dispatched, on a clock set to
 * the input's instant. Optionally one more input: an EXTERNAL RiskBreachEvent, the same type the graph raises itself.
 * Returns {what the replay writer recorded, the audit log}. Compiled by the test against a recipient's DEMO build.
 */
public final class LiveRecordingDriver {
    public static String[] run(String replay, boolean externalBreach) throws Exception {
        var p = new DemoQuoteRecordedProcessor();
        p.init();
        long[] now = {0};
        p.onEvent(ClockStrategy.registerClockEvent(() -> now[0]));
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        StringBuilder audit = new StringBuilder();
        p.setAuditLogProcessor(r -> audit.append("---\n").append(r).append('\n'));
        audit.setLength(0);                              // the set-up above is this driver's, not the run's
        var writer = (ReplayCapture) p.getAuditorById(ReplayCapture.NAME);
        StringWriter recorded = new StringWriter();
        writer.setTarget(recorded);
        long last = 0;
        for (var e : ReplayReader.read(replay, writer.getHandled())) {
            now[0] = last = e.time();
            writer.expect(e.event());
            p.onEvent(e.event());
        }
        if (externalBreach) {
            Object external = new Events.RiskBreachEvent("DEMO-external", 8);
            now[0] = last + 10;
            writer.expect(external);
            p.onEvent(external);
        }
        return new String[]{recorded.toString(), audit.toString()};
    }
}
