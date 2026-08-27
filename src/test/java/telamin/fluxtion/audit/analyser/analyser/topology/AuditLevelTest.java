package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M40.3 — the audit level, read from the LOG because the graph cannot supply it.
 *
 * <p>The tracker gated this slice on "only if the graph distinguishes INFO from TRACE". It does not:
 * the compiler's GraphML carries id, class and style per node and no level string at all (checked
 * against the demo fixture, emitted by a real build with audit installed). And even a build-time
 * {@code addEventAudit(LogLevel.INFO)} would be the wrong fact, because
 * {@code DataFlow.setAuditLogLevel} resets it at runtime. So the gate is answered NO, and the useful
 * version of the slice reads the artefact in hand — every record header carries its level.
 */
class AuditLevelTest {

    private static AuditLevel of(String... levels) {
        return AuditLevel.of(Arrays.asList(levels));
    }

    @Test
    void anInfoLogNamesWhatItWouldHaveDiscarded() {
        AuditLevel l = of("INFO", "INFO", "INFO");
        assertEquals("INFO", l.mostVerbose());
        assertEquals(List.of("DEBUG", "TRACE"), l.hidden());
        assertTrue(l.note().contains("debug or trace"), l.note());
        assertTrue(l.note().contains("may have run and logged below the captured level"),
                "the point is that a gap may be a LEVEL, not a silence: " + l.note());
    }

    @Test
    void itStatesTwoFactsAndRendersNoVerdict() {
        // the log genuinely cannot distinguish "threshold excluded them" from "nothing called debug()",
        // so the note must not assert either — the same refusal as coverage declining an inferred graph
        String note = of("INFO").note();
        assertTrue(note.contains("cannot tell those apart"), note);
        assertFalse(note.toLowerCase(java.util.Locale.ROOT).contains("misconfigur"), note);
        assertFalse(note.contains("should"), "no instruction dressed as a finding: " + note);
    }

    @Test
    void theFinestLevelWinsWhateverOrderTheRecordsArrivedIn() {
        assertEquals("TRACE", of("INFO", "TRACE", "WARN").mostVerbose());
        assertEquals("DEBUG", of("DEBUG", "INFO").mostVerbose());
        assertEquals(List.of("WARN", "INFO", "TRACE"), of("TRACE", "INFO", "WARN").observed(),
                "observed levels are ordered least-to-most verbose, not by arrival");
    }

    @Test
    void atTheFinestLevelThereIsNothingToWarnAbout() {
        AuditLevel l = of("TRACE");
        assertTrue(l.hidden().isEmpty());
        assertNull(l.note(), "a caveat with nothing behind it trains people to skim the ones that matter");
    }

    @Test
    void noRecordsMeansNoClaim() {
        assertNull(AuditLevel.of(null).mostVerbose());
        assertNull(of().note());
        assertNull(of((String) null, "  ").mostVerbose());
        assertTrue(AuditLevel.none().echo().isEmpty(), "nothing to say puts nothing in the echo");
    }

    @Test
    void anUnrecognisedLevelDoesNotImplyALowThreshold() {
        // a custom or misparsed level sorts last, so it never makes the log look FINER than it is and
        // never suppresses the caveat by accident
        AuditLevel l = of("INFO", "AUDIT");
        assertEquals("AUDIT", l.mostVerbose());
        assertTrue(l.hidden().isEmpty(), "unknown means unknown — do not invent what it would hide");
        assertNull(l.note());
    }

    @Test
    void theEchoCarriesTheLevelAsData() {
        var echo = of("INFO").echo();
        assertEquals(List.of("INFO"), echo.get("auditLevels"));
        assertEquals("INFO", echo.get("auditLevelFinest"));
        assertTrue(String.valueOf(echo.get("auditLevelNote")).contains("debug or trace"));
    }
}
