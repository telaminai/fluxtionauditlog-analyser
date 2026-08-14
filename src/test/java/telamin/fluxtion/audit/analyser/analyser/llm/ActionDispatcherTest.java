package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ActionDispatcher} validation + routing (spec-assistant-actions §8): it never throws — every bad
 * input becomes a structured {@code ok:false} the model can act on (#3).
 */
class ActionDispatcherTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());
    private final Supplier<LogIndex.Snapshot> snap = () -> store.index().snapshot();

    private ActionDispatcher inProcess() {
        return new ActionDispatcher(false, null, snap, store::rawText);
    }

    private ActionDispatcher rest(String token) {
        return new ActionDispatcher(true, token, snap, store::rawText);
    }

    @Test
    void aggregateActionSucceeds() {
        ActionResult r = inProcess().dispatch("{\"action\":\"aggregate\",\"params\":{\"groupBy\":\"dimension\"}}");
        assertTrue(r.ok());
        assertEquals("aggregate", r.action());
        assertEquals(21L, ((Map<?, ?>) r.toMap().get("result")).get("total"));
    }

    @Test
    void unknownVerbIsRejected() {
        ActionResult r = inProcess().dispatch("{\"action\":\"frobnicate\"}");
        assertFalse(r.ok());
        assertTrue(r.error().contains("unknown verb"));
    }

    @Test
    void renderVerbsReportNotEnabledYet() {
        ActionResult r = inProcess().dispatch("{\"action\":\"graph\",\"params\":{}}");
        assertFalse(r.ok());
        assertTrue(r.error().contains("not enabled"));
    }

    @Test
    void versionMismatchIsRejected() {
        ActionResult r = inProcess().dispatch("{\"v\":2,\"action\":\"aggregate\"}");
        assertFalse(r.ok());
        assertTrue(r.error().contains("version"));
    }

    @Test
    void missingActionIsRejected() {
        ActionResult r = inProcess().dispatch("{\"params\":{}}");
        assertFalse(r.ok());
        assertTrue(r.error().contains("action"));
    }

    @Test
    void malformedJsonIsRejectedNotThrown() {
        ActionResult r = inProcess().dispatch("{not json");
        assertFalse(r.ok());
        assertTrue(r.error().toLowerCase().contains("json"));
    }

    @Test
    void aggregateValidationErrorsBecomeStructuredFailures() {
        ActionResult r = inProcess().dispatch("{\"action\":\"aggregate\",\"params\":{\"metric\":\"median\"}}");
        assertFalse(r.ok());
        assertTrue(r.error().contains("unknown metric"), "typo'd metric → actionable ok:false");
    }

    @Test
    void tokenGuardedDispatcherRejectsANullToken() {
        assertThrows(IllegalArgumentException.class, () -> new ActionDispatcher(true, null, snap, store::rawText),
                "misconfiguration fails fast at construction, never a per-request NPE");
    }

    @Test
    void restRequiresACorrectToken() {
        ActionDispatcher d = rest("s3cr3t");
        assertFalse(d.dispatch("{\"action\":\"aggregate\"}").ok(), "missing token rejected");
        assertFalse(d.dispatch("{\"token\":\"wrong\",\"action\":\"aggregate\"}").ok(), "wrong token rejected");
        assertTrue(d.dispatch("{\"token\":\"s3cr3t\",\"action\":\"aggregate\"}").ok(), "correct token accepted");
    }
}
