package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReportRevisionTest {
    @Test void titleNotesAndSectionsArePartOfTheRevisionEvenWhenTheTimestampIsKept() {
        var before = new ReportSpec("DEMO", "Title", "same-time", "Note", null, null,
                List.of(ReportSpec.SectionSpec.narrative("One")));
        String revision = ConfigStore.reportRevision(before);
        assertEquals(revision, ConfigStore.reportRevision(before));
        assertNotEquals(revision, ConfigStore.reportRevision(new ReportSpec("DEMO", "Changed", "same-time", "Note",
                null, null, before.sections())), "a title edit changes the revision");
        assertNotEquals(revision, ConfigStore.reportRevision(new ReportSpec("DEMO", "Title", "same-time", "Changed",
                null, null, before.sections())), "a notes edit changes the revision");
        assertNotEquals(revision, ConfigStore.reportRevision(new ReportSpec("DEMO", "Title", "same-time", "Note",
                null, null, List.of(ReportSpec.SectionSpec.narrative("Two")))), "a section edit changes the revision");
        assertNull(ConfigStore.reportRevision(null));
    }
}
