package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #46 — replacing a report without {@code sections} must not destroy it.
 *
 * <p>The danger was never "replace is wrong": rebuilding a report under its own name is the right way
 * to update it. The danger was that <b>silence</b> meant empty. Retitling, adding notes, or attaching
 * a {@code path} to render a PDF are all natural follow-up calls on an existing report, and each one
 * wiped every section unless the whole array was resent — with no prompt, and no way back, because a
 * replace never entered the recently-deleted list that {@code delete} uses.
 */
class AbsentSectionsMeanUnchangedTest {

    private static final LogFingerprint AUTHORED =
            new LogFingerprint("authored-against.yaml", 100, null, null);
    private static final LogFingerprint TODAY =
            new LogFingerprint("a-different-log.yaml", 7, null, null);

    private static ReportSpec existing() {
        return new ReportSpec("r", "The original title", "2026-01-01T00:00:00Z", "the original notes",
                AUTHORED, FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.narrative("one"),
                        ReportSpec.SectionSpec.chart("a chart")));
    }

    private static ReportVerb.Parsed parse(Map<String, Object> params) {
        return ReportVerb.parse(params, TODAY, FilterSnapshot.all());
    }

    @Test
    @DisplayName("Retitling keeps the sections")
    void retitleKeepsSections() {
        ReportSpec merged = parse(Map.of("name", "r", "title", "A new title")).onto(existing());

        assertEquals("A new title", merged.title(), "the change that WAS asked for still happens");
        assertEquals(2, merged.sections().size(), "and the two that were not asked about survive");
        assertEquals("the original notes", merged.notes(), "as do the notes");
    }

    @Test
    @DisplayName("An explicit empty array still empties it — this only changes what SILENCE means")
    void explicitEmptyStillEmpties() {
        ReportSpec merged = parse(Map.of("name", "r", "sections", List.of())).onto(existing());

        assertEquals(0, merged.sections().size(),
                "'sections: []' is a supplied value and an explicit one; it must keep working");
    }

    @Test
    @DisplayName("Supplied sections replace, as before")
    void suppliedSectionsReplace() {
        ReportSpec merged = parse(Map.of("name", "r",
                "sections", List.of(Map.of("kind", "narrative", "text", "only this")))).onto(existing());

        assertEquals(1, merged.sections().size());
        assertEquals(ReportSpec.Kind.NARRATIVE, merged.sections().getFirst().kind());
    }

    /**
     * The subtle half. A report stores REFERENCES, and a reference only means something against the log
     * it was written for. Carrying sections forward while stamping them with today's fingerprint would
     * relabel evidence nobody re-checked — the report would then claim, in its own header and in every
     * PDF, to have been written against a log it never saw.
     */
    @Test
    @DisplayName("Carried-over sections keep the log and view they were written against")
    void theAuthoringContextTravelsWithTheSections() {
        ReportSpec merged = parse(Map.of("name", "r", "title", "Retitled")).onto(existing());

        assertEquals("authored-against.yaml", merged.fingerprint().logName(),
                "NOT today's log — these references were not re-checked against it");
        assertEquals("2026-01-01T00:00:00Z", merged.createdAt(), "createdAt says when it was MADE");
    }

    @Test
    @DisplayName("…but supplying sections re-stamps it, because those references ARE new")
    void suppliedSectionsTakeTodaysContext() {
        ReportSpec merged = parse(Map.of("name", "r",
                "sections", List.of(Map.of("kind", "narrative", "text", "fresh")))).onto(existing());

        assertEquals("a-different-log.yaml", merged.fingerprint().logName());
    }

    @Test
    @DisplayName("A brand-new report is unaffected by any of this")
    void nothingToMergeOnto() {
        ReportSpec spec = parse(Map.of("name", "new",
                "sections", List.of(Map.of("kind", "narrative", "text", "x")))).onto(null);

        assertEquals("new", spec.name());
        assertEquals(1, spec.sections().size());
    }

    @Test
    @DisplayName("The parser records which keys the call carried, not what they resolved to")
    void suppliedTracksTheCallNotTheResult() {
        // a sections array whose only entry is invalid parses to ZERO sections — but it WAS supplied,
        // and must not be mistaken for silence, or a typo would silently preserve the old report
        var parsed = parse(Map.of("name", "r",
                "sections", List.of(Map.of("kind", "not-a-kind"))));

        assertTrue(parsed.supplied().contains("sections"));
        assertEquals(0, parsed.spec().sections().size());
        assertFalse(parsed.warnings().isEmpty(), "and it says why: " + parsed.warnings());
        assertEquals(0, parsed.onto(existing()).sections().size(),
                "an explicit-but-invalid array empties the report, and the warning is how you know");
    }
}
