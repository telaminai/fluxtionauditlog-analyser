package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44.2 — one precedence order, one set of reasons, replacing three near-identical blocks.
 *
 * <p>What this protects is not elegance. A parameter silently dropped reads to the caller exactly like
 * one that was honoured, and on the agent path there is nobody to notice the log they asked for never
 * opened.
 */
class IgnoredParametersTest {

    private static IgnoredParameters.Decision decide(String... supplied) {
        return new IgnoredParameters().apply(Set.of(supplied));
    }

    @Test
    @DisplayName("a project switch wins, because its side effects would sweep away the rest")
    void projectBeatsEverything() {
        IgnoredParameters.Decision d = decide("project", "log", "graphml");
        assertEquals("project", d.honoured());
        assertEquals(List.of("log", "graphml"), d.ignored());
        assertTrue(d.why().contains("session boundary"), d.why());
        assertTrue(d.why().contains("second call"),
                "and it must say what to do instead, not only what it refused: " + d.why());
    }

    @Test
    @DisplayName("`bind` is not a rival act — it is how an analysis is parameterised")
    void subordinatesAreNotIgnored() {
        IgnoredParameters.Decision d = decide("analysis", "bind");
        assertEquals("analysis", d.honoured());
        assertFalse(d.anythingIgnored(),
                "reporting 'bind' as ignored would tell a caller their parameters were dropped when "
                        + "they were the whole point of the call");
        assertNull(d.why());
    }

    @Test
    @DisplayName("a log's own modifiers travel with it")
    void logModifiersAreSubordinate() {
        IgnoredParameters.Decision d = decide("log", "format", "provenance");
        assertEquals("log", d.honoured());
        assertFalse(d.anythingIgnored(), "format and provenance describe the log; they do not compete");
    }

    @Test
    @DisplayName("but a log's modifiers ARE dropped when a larger act wins")
    void modifiersFallWithTheirAct() {
        IgnoredParameters.Decision d = decide("project", "log", "format");
        assertEquals("project", d.honoured());
        assertTrue(d.ignored().contains("log"));
        assertTrue(d.ignored().contains("format"),
                "the declaration was for a log that is not being opened, so it went too");
    }

    @Test
    @DisplayName("close beats a log, and analysis beats close — the order is stated once")
    void theOrderIsTotal() {
        assertEquals("close", decide("close", "log").honoured());
        assertEquals("analysis", decide("analysis", "close", "log").honoured());
        assertEquals("project", decide("project", "analysis", "close").honoured());
    }

    @Test
    @DisplayName("one act on its own ignores nothing")
    void asingleActIsNotAConflict() {
        IgnoredParameters.Decision d = decide("log");
        assertEquals("log", d.honoured());
        assertFalse(d.anythingIgnored());
    }

    @Test
    @DisplayName("an unknown parameter is still reported rather than quietly swallowed")
    void unknownParametersAreNamedToo() {
        IgnoredParameters.Decision d = decide("project", "somethingNobodyDeclared");
        assertEquals("project", d.honoured());
        assertTrue(d.ignored().contains("somethingNobodyDeclared"),
                "a name this surface does not know is exactly the one a caller most needs told about");
    }

    @Test
    @DisplayName("the decision reaches the audit record when it is dispatched")
    void theDecisionIsAudited() {
        SessionDriver driver = new SessionDriver(new FakeSessionAdapter());
        driver.submit(new SessionEvents.OpenRequestReceived(
                driver.nextOpId(), Set.of("project", "log")));

        assertEquals("project", driver.processor().ignoredParameters.decision().honoured());
        assertFalse(driver.auditSink().matching("ignored").isEmpty());
        assertFalse(driver.auditSink().matching("session boundary").isEmpty(),
                "the reason belongs in the record too — an echo a caller reads once is not evidence");
    }
}
