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
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "demo.yaml", true, "", "", "");
        assertTrue(line.contains("complete"),
                "a file that says it is whole must say so to a person, not only to `context`: " + line);
    }

    @Test
    void aLogThatClaimsNothingSaysNothing() {
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "demo.yaml", false, "", "", "");
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

    /**
     * Re-review B2. The set's count and one member's declaration were siblings in the same map with no
     * file named, so {@code declaredRecords: 6} sat beside {@code recordsRead: 25} under a
     * missing-records state.
     */
    @Test
    void aSetsMemberNumbersAreNestedUnderTheFileThatOwnsThem() {
        var end = new StreamEnd(StreamEnd.State.MISSING_RECORDS, 6, 3, null,
                new StreamEnd.Member("g.log", 3, 0), java.util.List.of());
        var facts = MainFrame.streamEndFacts(end, 25);

        assertEquals(25L, facts.get("recordsRead"), "the top level is always the whole log");
        assertNull(facts.get("declaredRecords"),
                "a member's declaration beside the set's count reads as 'declared 6, read 25'");

        @SuppressWarnings("unchecked")
        var member = (java.util.Map<String, Object>) facts.get("member");
        assertNotNull(member, "the set must name the file its verdict came from");
        assertEquals("g.log", member.get("file"));
        assertEquals(3L, member.get("recordsRead"), "the member states its OWN count");
        assertEquals(6L, member.get("declaredRecords"));
    }

    /** A member's run positions are inside that member, so they travel inside it too. */
    @Test
    void aSetsMemberRunPositionsStayInsideTheMember() {
        var end = new StreamEnd(StreamEnd.State.MISSING_RECORDS, 6, 3,
                new StreamEnd.Segment(2, 2, 4, 5), new StreamEnd.Member("g.log", 5, 20), java.util.List.of());
        @SuppressWarnings("unchecked")
        var member = (java.util.Map<String, Object>) MainFrame.streamEndFacts(end, 25).get("member");
        assertEquals("g.log", member.get("file"));
        assertEquals(5L, member.get("recordsRead"), "the member's own count, not the run's 3");
        // Round four: the run's numbers used to be FLATTENED into the member, so `recordsRead: 3` read
        // as g.log's count while g.log held 5, and the member's own count was absent entirely.
        @SuppressWarnings("unchecked")
        var run = (java.util.Map<String, Object>) member.get("run");
        assertNotNull(run, "a run inside a member is its own scope");
        assertEquals(2, run.get("ordinal"));
        // A-6: the pair a verb accepts is the SET's numbering. g.log starts at set row 20, so the run
        // that is rows 2-4 of that file is rows 22-24 of the log `read` and `goto` index.
        assertEquals(22L, run.get("firstRecord"),
                "this reported 2, and `read {recordIndex: 2}` landed in a different file's run");
        assertEquals(24L, run.get("lastRecord"));
        assertEquals(2L, run.get("firstRecordInFile"), "the member-local pair is kept, and labelled");
        assertEquals(4L, run.get("lastRecordInFile"));
        assertEquals(3L, run.get("recordsRead"), "the run's count, labelled as the run's");
    }

    /** Re-review finding 3: an empty run has no positions, and printed "records 25 to 24". */
    @Test
    void anEmptyRunGivesNoPositionsRatherThanABackwardsRange() {
        var end = new StreamEnd(StreamEnd.State.MISSING_RECORDS, 3, 0,
                new StreamEnd.Segment(2, 25, 24, 25));
        @SuppressWarnings("unchecked")
        var run = (java.util.Map<String, Object>) MainFrame.streamEndFacts(end, 25).get("run");
        assertNull(run.get("firstRecord"), "an empty run has no first record to point at");
        assertNull(run.get("lastRecord"), "and certainly not one before its first");
        assertEquals(0L, run.get("recordsRead"));
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

    // ---- what makes a follow tick refresh the human surfaces --------------------------------------

    /**
     * Round six S-2. The predicate compared only the STATE, so a live read that learned an earlier run
     * had lost records — state UNKNOWN before and after, runs 0 then 1 — refreshed nothing. The store
     * reported the loss and `context` listed it while the tooltip stayed empty: the exact split between
     * an agent's surface and a person's that this contract exists to prevent.
     */
    @Test
    void aVerdictThatGainsAFailingRunRefreshesEvenWithNoNewRecords() {
        var before = StreamEnd.unknown(3);
        var after = StreamEnd.unknown(3).withRuns(java.util.List.of(
                new StreamEnd.Run(1, 0, 2, StreamEnd.State.MISSING_RECORDS, 5, 3)));

        assertEquals(before.state(), after.state(), "the state is identical — that was the trap");
        assertTrue(MainFrame.followNeedsDiagnosticRefresh(before, after, 0),
                "a proven loss arrived and nobody was told");
    }

    @Test
    void anUnchangedVerdictWithNoNewRecordsRefreshesNothing() {
        var same = StreamEnd.unknown(3);
        assertFalse(MainFrame.followNeedsDiagnosticRefresh(same, same, 0),
                "a quiet tick must not rebuild diagnostics on every poll");
    }

    @Test
    void newRecordsAlwaysRefresh() {
        assertTrue(MainFrame.followNeedsDiagnosticRefresh(
                StreamEnd.unknown(3), StreamEnd.unknown(4), 1));
    }

    @Test
    void theArrivalOfAMarkerRefreshes() {
        assertTrue(MainFrame.followNeedsDiagnosticRefresh(
                StreamEnd.unknown(3), StreamEnd.declared(3, 3), 0),
                "the last thing a writer does is emit its marker, and it adds no records");
    }

    @Test
    void theNoteNeverDisplacesWhatWasAlreadyThere() {
        String line = MainFrame.statusText(25, "10:00 → 10:05 UTC", "DEMO  (demo.yaml)", true,
                "  ·  ⚠ time-order violations (3) — ask 'context' or see the load report",
                "  ·  ⚠ clock skew — ask 'context', or hover", "");
        assertTrue(line.startsWith("25 records · 10:00 → 10:05 UTC · DEMO  (demo.yaml)"), line);
        assertTrue(line.contains("complete"), line);
        assertTrue(line.contains("time-order violations (3)"), line);
        assertTrue(line.contains("clock skew"), line);
        assertTrue(line.indexOf("complete") < line.indexOf("time-order"),
                "the claim sits with the file's identity, before the warnings: " + line);
    }
}
