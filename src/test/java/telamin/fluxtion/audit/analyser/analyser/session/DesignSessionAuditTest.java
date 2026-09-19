package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.design.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Validate authored ordering from the emitted audit records and outcomes, not dispatcher source text. */
class DesignSessionAuditTest {
    @Test void auditRecordsAcceptedReadThenRejectionOfLateReadAndProjectClear() throws Exception {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile("/projects/demo.properties");
        SessionDriver driver = new SessionDriver(adapter);
        var state = driver.processor().designSession;
        var first = DesignDocument.parse("demo.xml", "<beans><bean id='first'/></beans>");
        var second = DesignDocument.parse("demo.xml", "<beans><bean id='second'/></beans>");
        driver.submit(new DesignEvents.ReadCompleted("demo.xml", first, "", true, state.generation()));
        long oldGeneration = state.generation();
        driver.submit(new DesignEvents.ReadCompleted("demo.xml", second, "", false, oldGeneration));
        driver.submit(new DesignEvents.ReadCompleted("demo.xml", first, "", false, oldGeneration));
        assertEquals(List.of("second"), state.document().beanIds());
        var readRecords = driver.auditSink().matching("ReadCompleted");
        assertEquals(3, readRecords.size());
        assertTrue(readRecords.get(0).contains("loaded"));
        assertTrue(readRecords.get(1).contains("loaded"));
        assertTrue(readRecords.get(2).contains("stale result ignored"));
        assertFalse(String.join("", readRecords).contains("<beans>"), "audit records contain revisions, not entire source documents");

        var result = new ProducerResult("result.json", "build", "", "", List.of(), Map.of(), Map.of());
        driver.submit(new DesignEvents.ResultReadCompleted(result, "", state.generation()));
        driver.submit(new DesignEvents.ResultReadCompleted(null, "unsupported schema", state.generation()));
        assertNull(state.result());
        var resultRecords = driver.auditSink().matching("ResultReadCompleted");
        assertEquals(2, resultRecords.size());
        assertTrue(resultRecords.get(0).contains("build"));
        assertTrue(resultRecords.get(1).contains("cleared"));

        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), "/projects/demo.properties", TransitionKind.EXPLICIT_SWITCH, "test"));
        assertNull(state.document());
        assertTrue(driver.auditSink().matching("ProfileApplied").stream().anyMatch(r -> r.contains("designSession") && r.contains("cleared")));
    }
}
