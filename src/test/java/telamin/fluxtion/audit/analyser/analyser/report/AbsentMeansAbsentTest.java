package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #51 review, two findings on #46's merge.
 *
 * <p><b>An explicit null is ABSENT.</b> The merge asked {@code params.containsKey}, so a client
 * filling an optional argument it has no value for — {@code "sections": null} — wiped the report.
 * That is the defect #46 exists to remove, reached by a different route: silent, unprompted, and
 * not in the recently-deleted list. JSON clients send nulls for unset optionals routinely.
 *
 * <p><b>{@code createdAt} follows the sections.</b> The reviewer disagreed with keeping the old
 * date on a full replace, and was right: the rule #46 established is that the authoring context
 * travels with the sections, and keeping the date made the PDF print CREATED beside WRITTEN AGAINST
 * describing different reports.
 */
class AbsentMeansAbsentTest {

    private static final LogFingerprint AUTHORED = new LogFingerprint("authored.yaml", 100, null, null);
    private static final LogFingerprint TODAY = new LogFingerprint("today.yaml", 7, null, null);

    private static ReportSpec existing() {
        return new ReportSpec("r", "The original", "2026-01-01T00:00:00Z", "the original notes",
                AUTHORED, FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.narrative("one"), ReportSpec.SectionSpec.chart("a chart")));
    }

    /** {@code Map.of} refuses nulls, so an explicit null needs a HashMap — as a real client's JSON gives. */
    private static Map<String, Object> withNull(String key) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "r");
        params.put(key, null);
        return params;
    }

    private static ReportSpec merge(Map<String, Object> params) {
        return ReportVerb.parse(params, TODAY, FilterSnapshot.all()).onto(existing());
    }

    @Test
    @DisplayName("\"sections\": null keeps the sections — it does not wipe them")
    void nullSectionsIsAbsent() {
        ReportSpec merged = merge(withNull("sections"));

        assertEquals(2, merged.sections().size(),
                "a client that sends null for an optional it has no value for must not destroy the report");
        assertEquals("authored.yaml", merged.fingerprint().logName(),
                "and the authoring context stays with the sections it belongs to");
    }

    @Test
    @DisplayName("\"title\": null and \"notes\": null keep theirs too")
    void nullTitleAndNotesAreAbsent() {
        assertEquals("The original", merge(withNull("title")).title());
        assertEquals("the original notes", merge(withNull("notes")).notes());
    }

    @Test
    @DisplayName("An explicit empty array still empties — null and [] are different answers")
    void emptyArrayStillEmpties() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "r");
        params.put("sections", List.of());

        assertEquals(0, merge(params).sections().size(),
                "'sections: []' is how a report is emptied deliberately, and must keep working");
    }

    @Test
    @DisplayName("A parse records null as NOT supplied")
    void theParseSaysSo() {
        assertFalse(ReportVerb.parse(withNull("sections"), TODAY, FilterSnapshot.all())
                .supplied().contains("sections"));
    }

    // ---- createdAt follows the sections ---------------------------------------------------------

    @Test
    @DisplayName("Kept sections keep their created date")
    void keptSectionsKeepTheirDate() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "r");
        params.put("title", "Retitled");

        assertEquals("2026-01-01T00:00:00Z", merge(params).createdAt(),
                "nothing was re-authored, so the report was still made then");
    }

    @Test
    @DisplayName("Replaced sections take today's date, so CREATED and WRITTEN AGAINST agree")
    void replacedSectionsTakeTodaysDate() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "r");
        params.put("sections", List.of(Map.of("kind", "narrative", "text", "all new")));

        ReportSpec merged = merge(params);

        assertNotEquals("2026-01-01T00:00:00Z", merged.createdAt(),
                "every section is new and the fingerprint is today's; a CREATED row from January beside a "
                        + "WRITTEN AGAINST row from today describes two different reports");
        assertEquals("today.yaml", merged.fingerprint().logName(), "the two move together");
    }
}
