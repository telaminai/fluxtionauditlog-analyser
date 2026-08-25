package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M35.1 — the headless half. What actually clears is Swing and is verified by running the jar
 * (rule 4); what is pinned here is the CONTRACT: the verb surface, the published schema, the
 * refusal, and — the one that matters most — that profile state degrades LOUDLY once the log is
 * gone rather than silently vanishing or silently resolving.
 */
class CloseVerbTest {

    @Test
    void theSchemaPublishesClose_soAnAgentDiscoversItRatherThanGuessing() {
        String rendered = VerbSchemas.all().get("open").toString();
        assertTrue(rendered.contains("close"), "the manifest is the single source of truth (AV.3)");
        assertTrue(rendered.contains("PROFILE state and survive"),
                "and it states what is NOT destroyed, so an agent can close without fear");
    }

    @Test
    void closeIsNotItsOwnVerb_theSurfaceDoesNotGrow() {
        assertFalse(VerbSchemas.all().containsKey("close"),
                "M35.1 extends 'open' rather than adding a 15th verb — closing is the same "
                        + "lifecycle concept, and the verb surface is a compatibility surface");
        assertEquals(14, VerbSchemas.all().size());
    }

    // ---- the rule: profile state SURVIVES and degrades loudly --------------------------------------

    @Test
    void aSavedReportSurvivesTheLogAndSaysWhyItCannotResolve() {
        // The whole reason closeLog() does not go near config: a report is profile state. With the
        // log gone it must still be there, and must announce rather than quietly resolving.
        var spec = new ReportSpec("inv", "Breach", "", "",
                new LogFingerprint("risk.yaml", 726, 100L, 900L), FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.record(42, null),
                        ReportSpec.SectionSpec.narrative("what I concluded")));

        var r = ReportResolver.resolve(spec, null, null, Map.of(), Set.of(), Set.of(),
                new FilterState());

        assertNotNull(r.fingerprintMismatch(), "the absence of a log is announced, not ignored");
        assertTrue(r.fingerprintMismatch().contains("no log is loaded"), r.fingerprintMismatch());
        assertFalse(r.sections().get(0).resolved(), "the record anchor cannot resolve against nothing");
        assertTrue(r.sections().get(1).resolved(), "narrative is not an anchor and still renders");
        assertEquals("1 of 1 anchor(s) did not resolve against this log", r.summary());
        assertFalse(r.clean());
    }

    @Test
    void closingTheLogNeverSilentlyResolvesAnAnchorItCannotCheck() {
        var spec = new ReportSpec("inv", "t", "", "", null, FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.finding(7)));
        var r = ReportResolver.resolve(spec, null, null, Map.of(), Set.of(), Set.of(), new FilterState());
        assertFalse(r.sections().get(0).resolved());
        assertNotNull(r.sections().get(0).reason(), "and it says WHY: " + r.sections().get(0).reason());
    }

    @Test
    void theSchemaSaysCloseIgnoresTheOtherParams_soTheEchoHasSomethingToBeConsistentWith() {
        String rendered = VerbSchemas.all().get("open").toString();
        assertTrue(rendered.contains("Ignored when combined with log/graphml/processor"),
                "the manifest states the precedence; review R2 made the ECHO say it too, and the "
                        + "two must not drift: " + rendered.substring(0, Math.min(400, rendered.length())));
    }
}
