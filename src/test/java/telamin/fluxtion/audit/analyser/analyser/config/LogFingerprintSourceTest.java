package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.3 review F1 — a report header must tell a MATCHED provenance from a DECLARED one. The fingerprint
 * carries how the provenance was obtained, prints the qualification for a match, and persists it.
 */
class LogFingerprintSourceTest {

    @Test
    void aDeclaredProvenancePrintsBare_aMatchedOnePrintsItsQualification() {
        var declared = new LogFingerprint("a.yaml", 10, 1000L, 2000L, "risk-engine · prod", "declared by the opener");
        assertFalse(declared.provenanceMatched());
        assertTrue(declared.describe().startsWith("risk-engine · prod · 10 record(s)"), declared.describe());

        var byDir = new LogFingerprint("a.yaml", 10, 1000L, 2000L, "risk-engine · prod", "project environment 'prod' — the log is under logs/prod");
        assertTrue(byDir.provenanceMatched());
        assertTrue(byDir.describe().startsWith("risk-engine · prod (matched by directory, not declared) · 10 record(s)"), byDir.describe());

        var byDefault = new LogFingerprint("a.yaml", 10, 1000L, 2000L, "risk-engine · uat", "project default environment 'uat'");
        assertTrue(byDefault.describe().contains("(project default environment, not declared)"), byDefault.describe());

        var none = new LogFingerprint("a.yaml", 10, 1000L, 2000L, null, "project default environment 'uat'");
        assertFalse(none.provenanceMatched(), "no provenance, nothing to qualify");
        assertTrue(none.describe().startsWith("a.yaml · 10 record(s)"));
        assertEquals(new LogFingerprint("a.yaml", 10, 1000L, 2000L, "p"), new LogFingerprint("a.yaml", 10, 1000L, 2000L, "p", null),
                "the five-arg shape is the six-arg one with no source");
    }

    @Test
    void theSourceSurvivesTheProfile() {
        var fp = new LogFingerprint("a.yaml", 10, 1000L, 2000L, "risk-engine · prod", "project environment 'prod' — the log is under logs/prod");
        var spec = new ReportSpec("r", "Report", "2026-08-27T00:00:00Z", "", fp, FilterSnapshot.all(), List.of());
        Properties p = new Properties();
        ConfigStore.writeReports(p, List.of(spec));
        List<ReportSpec> back = new java.util.ArrayList<>();
        ConfigStore.readReports(p, back);
        assertEquals(fp, back.get(0).fingerprint());
        assertTrue(back.get(0).fingerprint().describe().contains("matched by directory"), "the qualification is still printed after a round trip");
    }
}
