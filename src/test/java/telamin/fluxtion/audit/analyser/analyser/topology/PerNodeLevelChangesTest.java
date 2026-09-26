package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-8 — the per-node level a log states about itself.
 *
 * <p>Measured before this existed: a file read {@code complete}, 6 of 6, with no findings and ZERO
 * entries for {@code riskCheck}, which had run three times. Its {@code info} lines were suppressed by a
 * per-node level of {@code WARN}, and the control record naming {@code sourceId=riskCheck} was sitting
 * in the log. Coverage listed it as uncovered with no explanation — "this node never ran" being wrong,
 * with the answer already in the file.
 *
 * <p>These drive the parsing and the wording. The coverage wiring is asserted through
 * {@code CoverageService}'s echo.
 */
class PerNodeLevelChangesTest {

    /** The runtime's actual rendering, as measured, so the parse is pinned to a real format. */
    private static final String CONTROL_TEXT =
            "EventLogConfig{level=WARN, logRecordProcessor=null, sourceId=riskCheck, groupId=null}";

    /** MA-8.5 — fields are read out of the control event's text. */
    @Test
    void readsSourceIdAndLevelFromTheRuntimesActualRendering() {
        assertEquals("WARN", PerNodeLevelChanges.field(CONTROL_TEXT, "level"));
        assertEquals("riskCheck", PerNodeLevelChanges.field(CONTROL_TEXT, "sourceId"));
    }

    /**
     * The runtime renders an unset field as the literal {@code null}, which must read as absent — or a
     * global level change would be annotated as though it named a node called "null".
     */
    @Test
    void theLiteralNullReadsAsAbsent() {
        assertNull(PerNodeLevelChanges.field(CONTROL_TEXT, "groupId"));
        assertNull(PerNodeLevelChanges.field(CONTROL_TEXT, "logRecordProcessor"));
    }

    /** A field that is not there at all is absent, not an exception: the format is not a contract. */
    @Test
    void anAbsentFieldIsNullRatherThanAFailure() {
        assertNull(PerNodeLevelChanges.field("EventLogConfig{level=INFO}", "sourceId"));
        assertNull(PerNodeLevelChanges.field("something else entirely", "level"));
    }

    /**
     * MA-8.5's reason for keying on the event type: if the rendering changes, the annotation is lost
     * rather than the load breaking. This pins that a changed format degrades quietly.
     */
    @Test
    void aChangedRenderingLosesTheAnnotationRatherThanBreaking() {
        assertNull(PerNodeLevelChanges.field("EventLogConfig[level: WARN, sourceId: riskCheck]", "level"),
                "a different rendering yields no annotation, and must not throw");
    }

    /**
     * MA-8.5's pin, made LIVE: the rendering is read from the runtime jar this build actually links,
     * not from a string typed into a test. If a runtime upgrade changes EventLogConfig's toString, this
     * fails and says so, instead of every annotation quietly disappearing.
     */
    @Test
    void theRuntimeOnTheClasspathRendersWhatThisClassReads() {
        var warn = com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.WARN;
        var info = com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO;
        String perNode = new com.telamin.fluxtion.runtime.audit.EventLogControlEvent("riskMonitor", null, warn).toString();
        assertTrue(PerNodeLevelChanges.recognised(perNode), "per-node rendering: " + perNode);
        assertEquals("WARN", PerNodeLevelChanges.field(perNode, "level"));
        assertEquals("riskMonitor", PerNodeLevelChanges.field(perNode, "sourceId"));
        String global = new com.telamin.fluxtion.runtime.audit.EventLogControlEvent(info).toString();
        assertTrue(PerNodeLevelChanges.recognised(global), "global rendering: " + global);
        assertNull(PerNodeLevelChanges.field(global, "sourceId"), "a global change names no node");
        String grouped = new com.telamin.fluxtion.runtime.audit.EventLogControlEvent(null, "alpha", warn).toString();
        assertEquals("alpha", PerNodeLevelChanges.field(grouped, "groupId"));
    }
}
