package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

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
