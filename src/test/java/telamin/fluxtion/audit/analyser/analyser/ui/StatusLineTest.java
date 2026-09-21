package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The status bar's assembled line — the regression check for a defect that had none.
 *
 * <p>D-E3 says a file that claims completeness says so to a person, and {@code context}'s own comment
 * names the status bar as that surface. The note was computed into a local variable and never
 * concatenated in, so the surface did not exist. Re-review raised it under rule 8: the fix was real and
 * nothing would catch it coming back, because rule 4 keeps Swing out of the headless suite.
 *
 * <p><b>These assertions are on the ASSEMBLED string, deliberately.</b> A test of the decision alone
 * ("should the note appear?") passes with the note dropped on the floor, which is the defect that
 * actually happened. {@link MainFrame#statusText} touches no Swing, so it runs headless like any other
 * pure function.
 */
class StatusLineTest {

    @Test
    void aLogThatClaimsCompletenessSaysSoInTheLine() {
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "demo.yaml", true, "", "");
        assertTrue(line.contains("complete"),
                "a file that says it is whole must say so to a person, not only to `context`: " + line);
    }

    @Test
    void aLogThatClaimsNothingSaysNothing() {
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "demo.yaml", false, "", "");
        assertFalse(line.contains("complete"),
                "silence is the ordinary case and must not be dressed as a claim: " + line);
    }

    // ---- what `context` says, which is the surface an agent calculates with ----------------------

    @Test
    void contextReportsTheFilesRecordCountNotOneRunsEvenWhenTheVerdictIsAboutARun() {
        var end = new StreamEnd(StreamEnd.State.MISSING_RECORDS, 3, 2,
                new StreamEnd.Segment(1, 0, 1, 4));
        var facts = MainFrame.streamEndFacts(end, 4);

        assertEquals("missing_records", facts.get("state"));
        assertEquals(4L, facts.get("recordsRead"),
                "this said 2 about a file holding 4, because the run's count sat in the file's key");
        assertNull(facts.get("declaredRecords"),
                "a run's declaration must not sit at the top level unlabelled");

        @SuppressWarnings("unchecked")
        var run = (java.util.Map<String, Object>) facts.get("run");
        assertNotNull(run, "a verdict about one run of several must say which run");
        assertEquals(1, run.get("ordinal"));
        assertEquals(3L, run.get("declaredRecords"));
        assertEquals(2L, run.get("recordsRead"));
    }

    @Test
    void aSingleRunFileCarriesNoRunKeyBecauseTheFileIsTheRun() {
        var facts = MainFrame.streamEndFacts(StreamEnd.declared(9, 2), 2);
        assertEquals(2L, facts.get("recordsRead"));
        assertEquals(9L, facts.get("declaredRecords"));
        assertNull(facts.get("run"), "a nested duplicate of the file's own numbers is noise");
    }

    @Test
    void everyStateReachesContextIncludingTheSilentOne() {
        for (StreamEnd.State s : StreamEnd.State.values()) {
            var facts = MainFrame.streamEndFacts(new StreamEnd(s, -1, 7), 7);
            assertEquals(s.name().toLowerCase(java.util.Locale.ROOT), facts.get("state"),
                    "an agent that cannot tell complete from unverified reads silence as success");
            assertEquals(7L, facts.get("recordsRead"));
        }
    }

    @Test
    void theNoteNeverDisplacesWhatWasAlreadyThere() {
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "DEMO  (demo.yaml)", true,
                "  ·  ⚠ time-order violations (3) — ask 'context' or see the load report",
                "  ·  ⚠ clock skew — ask 'context', or hover");
        assertTrue(line.startsWith("25 records · 10:00 → 10:05 UTC · DEMO  (demo.yaml)"), line);
        assertTrue(line.contains("complete"), line);
        assertTrue(line.contains("time-order violations (3)"), line);
        assertTrue(line.contains("clock skew"), line);
        assertTrue(line.indexOf("complete") < line.indexOf("time-order"),
                "the claim sits with the file's identity, before the warnings: " + line);
    }
}
