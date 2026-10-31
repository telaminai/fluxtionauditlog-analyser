package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Same input fixture for the 1.0.79/1.0.83 captures; see the accompanying handoff evidence. */
class SessionProcessorCompatibilityTest {
    enum Entry { OBJECT, TYPED, BATCH_END, HOST_CYCLE }

    @Test
    void captureCompatibilityFixture(@TempDir Path temporary) throws Exception {
        Path out = Path.of(System.getProperty("session.compatibility.capture", temporary.toString()));
        FakeSessionAdapter adapter = new FakeSessionAdapter()
                .withProfile("/projects/DEMO-alpha.properties").withProfile("/projects/DEMO-beta.properties");
        SessionDriver driver = new SessionDriver(adapter);
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),
                "/projects/DEMO-alpha.properties", TransitionKind.EXPLICIT_SWITCH, "DEMO fixture"));
        SessionFixtures.openLog(driver, adapter, "/logs/DEMO.yaml");
        driver.submit(SessionFixtures.graph("/graphs/DEMO.graphml"));
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),
                "/projects/DEMO-beta.properties", TransitionKind.EXPLICIT_SWITCH, "DEMO fixture"));
        assertTrue(driver.auditSink().isComplete());
        driver.auditSink().export(out.resolve("healthy.yaml"));

        SessionDriver fault = new SessionDriver(new FakeSessionAdapter());
        fault.auditSink().clear();
        fault.processor().onEvent((Object) new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        RuntimeException expected = failNextClose(fault);
        assertSame(expected, assertThrows(RuntimeException.class, () -> invoke(fault.processor(), Entry.OBJECT)));
        assertEquals(AssistantProfile.FULL, fault.processor().aiSetup.profile(), "node state is not rolled back");
        fault.processor().onEvent((Object) new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        assertTrue(fault.auditSink().isComplete());
        fault.auditSink().export(out.resolve("failed-close.yaml"));
        Files.writeString(out.resolve("outcome.txt"), "profile after next event: "
                + fault.processor().aiSetup.profile() + "\noriginal exception preserved: true\n");
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void nextEventRunsAfterAuditCloseFails(Entry entry) {
        SessionDriver driver = new SessionDriver(new FakeSessionAdapter());
        RuntimeException expected = failNextClose(driver);
        assertSame(expected, assertThrows(RuntimeException.class, () -> invoke(driver.processor(), entry)));
        driver.processor().onEvent((Object) new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        assertEquals(AssistantProfile.SAFE, driver.processor().aiSetup.profile(),
                "the next event must be dispatched, not silently queued behind a failed cycle: " + entry);
    }

    private static RuntimeException failNextClose(SessionDriver driver) {
        AtomicBoolean armed = new AtomicBoolean(false);
        RuntimeException failure = new IllegalStateException("DEMO audit sink failure");
        driver.processor().setAuditLogProcessor(record -> {
            if (armed.getAndSet(false)) throw failure;
            driver.auditSink().processLogRecord(record);
        });
        armed.set(true); // Installing the listener itself dispatches an audit-control event.
        return failure;
    }

    private static void invoke(SessionProcessor processor, Entry entry) {
        var event = new SessionEvents.AssistantProfileObserved(AssistantProfile.FULL);
        switch (entry) {
            case OBJECT -> processor.onEvent((Object) event);
            case TYPED -> processor.onEvent(event);
            case BATCH_END -> processor.batchEnd();
            case HOST_CYCLE -> processor.runInEventCycle("DEMO host cycle", () -> { });
        }
    }
}
