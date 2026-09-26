package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A report must be removable and renameable (#23).
 *
 * <p>Until this, a report could be created and replaced by name and never removed. A profile only
 * accumulated: a two-section throwaway built to diagnose whether topology sections render in a PDF
 * became a permanent fixture of a shipped profile, indistinguishable in the Reports tab from real
 * findings, and the only way out was to close the project and hand-edit the properties file.
 *
 * <p>These cover the pure parts — the spec transform and the naming rule. The wiring into
 * {@code MainFrame} is exercised by the frame suite.
 */
class ReportCanBeRemovedTest {

    private static ReportSpec report(String name, String title) {
        return new ReportSpec(name, title, "2026-09-26T00:00:00Z", "notes",
                null, null, List.of());
    }

    @Test
    @DisplayName("Renaming keeps everything the report held")
    void renameKeepsContent() {
        ReportSpec original = new ReportSpec("scratch", "An explicit title", "2026-09-26T00:00:00Z",
                "the notes", null, null, List.of());

        ReportSpec renamed = original.withName("Contra mapping — before and after");

        assertEquals("Contra mapping — before and after", renamed.name());
        assertEquals("the notes", renamed.notes(), "notes survive");
        assertEquals("2026-09-26T00:00:00Z", renamed.createdAt(), "createdAt is when it was MADE, not renamed");
        assertEquals(original.sections(), renamed.sections());
    }

    @Test
    @DisplayName("An EXPLICIT title is left alone by a rename")
    void explicitTitleSurvives() {
        ReportSpec original = report("scratch", "An explicit title");

        assertEquals("An explicit title", original.withName("new-name").title(),
                "a title someone chose is not collateral damage of a rename");
    }

    @Test
    @DisplayName("A DEFAULTED title follows the name")
    void defaultedTitleFollows() {
        // the compact constructor defaults a blank title to the name
        ReportSpec original = new ReportSpec("scratch", null, "", "", null, null, List.of());
        assertEquals("scratch", original.title(), "precondition: the title was defaulted to the name");

        ReportSpec renamed = original.withName("probe-topology-section");

        assertEquals("probe-topology-section", renamed.title(),
                "otherwise a report renamed away from 'scratch' still displays 'scratch' as its title");
    }

    @Test
    @DisplayName("The name is trimmed and normalised the same way on rename as on create")
    void renameGoesThroughTheSameNormalisation() {
        ReportSpec renamed = report("a", "t").withName("  spaced  ");

        assertEquals("spaced", renamed.name(), "the compact constructor trims — rename must not bypass it");
    }

    @Test
    @DisplayName("Renaming to blank falls back rather than producing an unnameable report")
    void blankRenameIsNormalised() {
        ReportSpec renamed = report("a", "t").withName("   ");

        assertEquals("report", renamed.name(),
                "the compact constructor's fallback applies; MainFrame.renameReport refuses blank BEFORE "
                        + "reaching here, so this is the belt to that braces");
    }

    // ---- the confirmation names the report and what is lost (spec D-4) --------------------------

    @Test
    @DisplayName("The delete confirmation names the report, the log it cites, and what survives")
    void deleteConfirmationSaysWhatIsLost() {
        LogFingerprint fp = new LogFingerprint("maker-fxoc-audit.yaml", 1_412, null, null);
        ReportSpec spec = new ReportSpec("contra-gate", "Contra gate", "2026-09-26T00:00:00Z", "",
                fp, null, List.of(ReportSpec.SectionSpec.narrative("a"),
                ReportSpec.SectionSpec.chart("positions")));

        String warning = telamin.fluxtion.audit.analyser.analyser.ui.ReportsPanel.deleteWarning(spec);

        assertTrue(warning.contains("\"contra-gate\""), "names the report: " + warning);
        assertTrue(warning.contains("maker-fxoc-audit.yaml"),
                "names the LOG it cites — a report authored against a log that is no longer open is "
                        + "the one most likely deleted by mistake: " + warning);
        assertTrue(warning.contains("2 sections"), "says how much is lost: " + warning);
        assertTrue(warning.contains("cannot be undone"), warning);
        assertTrue(warning.contains("NOT touched"),
                "and what SURVIVES — deleting the assembly is not deleting the evidence: " + warning);
    }

    @Test
    @DisplayName("A report with no fingerprint says so rather than naming nothing")
    void deleteConfirmationWithoutAFingerprint() {
        ReportSpec spec = new ReportSpec("scratch", null, "", "", null, null,
                List.of(ReportSpec.SectionSpec.narrative("a")));

        String warning = telamin.fluxtion.audit.analyser.analyser.ui.ReportsPanel.deleteWarning(spec);

        assertTrue(warning.contains("no particular log"), warning);
        assertTrue(warning.contains("1 section") && !warning.contains("1 sections"),
                "singular reads as singular: " + warning);
    }
}
