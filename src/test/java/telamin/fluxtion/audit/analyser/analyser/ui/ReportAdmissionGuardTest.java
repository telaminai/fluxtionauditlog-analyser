package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportAdmission;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ★ Owner 2026-10-18: a hosted {@code report} admitted WITHOUT an approval writes, on the event thread, only what the
 * server judged — so a report that appeared (a create) or changed (an update of the session's own) between the judgement
 * and the write is refused, never replaced. Every other caller (no admission: external clients, approved calls) is
 * unchanged. Witness (run 2026-10-18): make {@code MainFrame.unadmittedReportWrite} return null — the appeared and the
 * changed report are written over and this fails.
 */
class ReportAdmissionGuardTest {

    static ReportSpec report(String name, String createdAt) {
        return new ReportSpec(name, null, createdAt, null, null, null, null);
    }

    @Test
    @DisplayName("★ admitted as a create or an own update, a report that appeared or changed meanwhile is refused, not replaced")
    void theWriteRunsOnlyAsJudged() {
        ReportAdmission create = new ReportAdmission("DEMO trace", null);
        assertNull(MainFrame.unadmittedReportWrite(create, "DEMO trace", null), "a create, still free: it runs");
        assertTrue(MainFrame.unadmittedReportWrite(create, "DEMO trace", report("DEMO trace", "p"))
                .contains("exists now"), "a report of that name appeared: refused");

        ReportAdmission own = new ReportAdmission("DEMO trace", "s-1");
        assertNull(MainFrame.unadmittedReportWrite(own, "DEMO trace", report("DEMO trace", "s-1")), "still the session's");
        assertTrue(MainFrame.unadmittedReportWrite(own, "DEMO trace", report("DEMO trace", "p-2")).contains("changed"));
        assertNotNull(MainFrame.unadmittedReportWrite(own, "DEMO trace", null), "deleted meanwhile: not recreated unasked");

        assertNotNull(MainFrame.unadmittedReportWrite(create, "Another", null), "only the report it was judged for");
        assertNotNull(MainFrame.unadmittedReportWrite(new ReportAdmission(null, null), "DEMO trace", null),
                "a file-only admission writes no report");
        assertNull(MainFrame.unadmittedReportWrite(null, "Person's findings", report("Person's findings", "p")),
                "no admission (an external client, an approved call): unchanged");
    }
}
