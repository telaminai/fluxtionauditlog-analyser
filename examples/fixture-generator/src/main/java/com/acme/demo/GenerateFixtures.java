package com.acme.demo;

import com.acme.demo.event.Events;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes the analyser's topology test fixtures: a processor's {@code .graphml} and an audit log from
 * running that same processor.
 *
 * <p><b>Pairing is the point.</b> Both come from one build of one graph, so the instance ids in the log
 * are exactly the node ids in the graph. A hand-edited fixture drifts from the log it claims to describe
 * and still renders perfectly — the failure the analyser's build-mismatch warning exists to catch.
 */
public final class GenerateFixtures {

    private static final String PROCESSOR = "com.acme.demo.generated.DemoQuoteProcessor";

    /** Where the analyser keeps the fixtures, relative to this module. */
    private static final Path FIXTURES = Path.of("../../src/test/resources/topology");

    private static final Path EMITTED_GRAPHML =
            Path.of("target/generated-resources/com/acme/demo/generated/DemoQuoteProcessor.graphml");

    /** Fixed instant (2026-01-01T09:00:00Z) so regeneration is byte-reproducible. */
    private static final long FIXED_START_MILLIS = 1_767_258_000_000L;

    public static void main(String[] args) throws Exception {
        StringBuilder log = new StringBuilder();
        // Loaded reflectively, as the golden path does: this class then has no compile-time dependency
        // on generated source, so the module compiles before the processor has been generated.
        DataFlow processor = (DataFlow) Class.forName(PROCESSOR)
                .getDeclaredConstructor().newInstance();
        processor.init();
        // Pin the clock, or every run rewrites the fixture with new timestamps and a real change is
        // lost in the diff noise.
        long[] tick = {FIXED_START_MILLIS};
        processor.onEvent(ClockStrategy.registerClockEvent(() -> tick[0] += 10));
        processor.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        processor.setAuditLogProcessor(record -> append(log, record.toString()));

        processor.onEvent(new Events.MarketDataEvent("DEMO-A", 100.10, 100.30));
        processor.onEvent(new Events.OrderUpdateEvent("ord-1", "LIVE"));
        processor.onEvent(new Events.MarketDataEvent("DEMO-A", 100.12, 100.28));
        processor.onEvent(new Events.OrderUpdateEvent("ord-1", "DONE"));
        processor.onEvent(new Events.MarketDataEvent("DEMO-B", 55.01, 55.09));

        Files.createDirectories(FIXTURES);
        Files.writeString(FIXTURES.resolve("demo-quote-audit.yaml"), log.toString());
        copyGraphMlIfPresent();
        System.out.println("fixtures written to " + FIXTURES.toAbsolutePath().normalize());
    }

    /**
     * Keep graph activity, drop audit configuration. Setting the listener is itself audited, and that
     * record embeds the listener lambda's identity hash — different on every run, so it would defeat the
     * fixed clock and make the fixture churn.
     */
    private static void append(StringBuilder log, String record) {
        if (record.contains("event: EventLogControlEvent")) return;
        log.append("---\n").append(record).append('\n');
    }

    /** The graphml only exists after a generating build; regenerating just the log is still useful. */
    private static void copyGraphMlIfPresent() throws IOException {
        if (Files.exists(EMITTED_GRAPHML)) {
            Files.copy(EMITTED_GRAPHML, FIXTURES.resolve("demo-quote-processor.graphml"),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.out.println("no freshly emitted .graphml — keeping the committed one "
                               + "(run a generating build to refresh it)");
        }
    }
}
