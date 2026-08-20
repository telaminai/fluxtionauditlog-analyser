package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec.SectionSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M33.1 (spec-investigation-reports): the model and its resolution, headless — D-I1 (a report
 * includes findings, never authors them), D-I3 (references re-resolve and degrade loudly) and D-I3a
 * (the authoring context is captured, compared, announced — because the dangerous failure arrives
 * RESOLVED, not dangling).
 */
class ReportModelTest {

    private static String records(long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: e\n---\n");
        }
        return sb.toString();
    }

    private static final HeapLogStore STORE = new HeapLogStore(records(100, 200, 300, 400));

    // ---- D-I1: flag is the one write site -----------------------------------------------------

    @Test
    void aFindingSectionHasNowhereToPutText_dI1IsStructural() {
        SectionSpec s = new SectionSpec(ReportSpec.Kind.FINDING, 2, null, null, null,
                "a SECOND account of record 2", null, null, null);
        assertNull(s.text(), "text supplied on a finding section is DROPPED, not stored — the model "
                + "has no field for a second account of a record (D-I1)");
    }

    @Test
    void aResolvedFindingHandsOverTheFlagObjectItself_byteIdentical() {
        Finding written = new Finding(2, "liveOrders exceeded the limit", "check riskMonitor");
        var r = ReportResolver.resolve(
                spec(SectionSpec.finding(2)), STORE.index(), Map.of(2, written), Set.of(), Set.of(),
                new FilterState());
        assertSame(written, r.sections().get(0).finding(),
                "the resolver hands over the Finding, never a restatement (D-I1)");
    }

    @Test
    void anUnflaggedRecordCannotCarryAFindingSection() {
        var r = ReportResolver.resolve(
                spec(SectionSpec.finding(1)), STORE.index(), Map.of(), Set.of(), Set.of(),
                new FilterState());
        var s = r.sections().get(0);
        assertFalse(s.resolved());
        assertTrue(s.reason().contains("has no flag"), s.reason());
        assertTrue(s.reason().contains("what 'flag' wrote"), "the refusal teaches the rule: " + s.reason());
    }

    // ---- D-I3: references degrade loudly, never silently ---------------------------------------

    @Test
    void unresolvedAnchorsAreCountedAndNamed_narrativeIsNotAnAnchor() {
        var r = ReportResolver.resolve(spec(
                        SectionSpec.finding(99),                    // out of range
                        SectionSpec.record(1, null),                // fine
                        SectionSpec.chart("gone graph"),            // undefined
                        SectionSpec.narrative("prose resolves by definition")),
                STORE.index(), Map.of(), Set.of("real graph"), Set.of(), new FilterState());
        assertEquals("2 of 3 anchor(s) did not resolve against this log", r.summary());
        assertTrue(r.sections().get(2).reason().contains("'gone graph'"),
                "the reason names the missing reference");
        assertTrue(r.sections().get(3).resolved());
        assertFalse(r.clean());
    }

    @Test
    void chartTopologySeriesAndTableReferencesResolveAgainstTheirOwnNamespaces() {
        var r = ReportResolver.resolve(spec(
                        SectionSpec.chart("spread"),
                        SectionSpec.topology("risk path"),
                        SectionSpec.series(Map.of("expr", "a.x - b.y")),
                        SectionSpec.table(Map.of("verb", "read"), List.of(), null, null)),
                STORE.index(), Map.of(), Set.of("spread"), Set.of("risk path"), new FilterState());
        assertNull(r.summary(), "everything resolves");
        assertTrue(r.clean());
    }

    @Test
    void aMalformedSeriesExprIsUnresolvedWithTheParseMessage() {
        var r = ReportResolver.resolve(
                spec(SectionSpec.series(Map.of("expr", "1 < 2 < 3"))),
                STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        assertFalse(r.sections().get(0).resolved());
        assertTrue(r.sections().get(0).reason().contains("does not parse"));
    }

    @Test
    void aTableMustDeriveItsRowsFromANamedVerb_dI7() {
        var r = ReportResolver.resolve(
                spec(SectionSpec.table(Map.of(), List.of(), null, null)),
                STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        assertFalse(r.sections().get(0).resolved());
        assertTrue(r.sections().get(0).reason().contains("DERIVED"), r.sections().get(0).reason());
    }

    @Test
    void aMalformedRowWhenIsAWarning_theTableStillRenders() {   // acceptance 7
        var r = ReportResolver.resolve(
                spec(SectionSpec.table(Map.of("verb", "read"), List.of(), "1 < 2 < 3", "bad rows")),
                STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        var s = r.sections().get(0);
        assertTrue(s.resolved(), "a bad highlight rule must not take the table down");
        assertTrue(s.warning().contains("renders without highlighting"), s.warning());
        assertTrue(s.warning().contains("1 < 2 < 3"), "the warning names the rule: " + s.warning());
    }

    // ---- D-I3a: the authoring context — compare, announce, offer -------------------------------

    @Test
    void fingerprintCapturesIdentityNotEvidence() {
        LogFingerprint fp = LogFingerprint.of(STORE.index(), "demo.yaml");
        assertEquals(4, fp.records());
        assertEquals(100L, fp.firstTime());
        assertEquals(400L, fp.lastTime());
        assertTrue(fp.describe().startsWith("demo.yaml · 4 record(s)"), fp.describe());
    }

    @Test
    void aDifferentLogIsAnnouncedBeforeAnySectionRenders_neverRefused() {   // acceptance 9
        LogFingerprint authored = new LogFingerprint("demo.yaml", 582, 100L, 900L);
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", authored, FilterSnapshot.all(),
                        List.of(SectionSpec.record(1, null))),
                STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        assertNotNull(r.fingerprintMismatch(), "the mismatch is DATA, positioned before the sections");
        assertTrue(r.fingerprintMismatch().contains("582 record(s)"), r.fingerprintMismatch());
        assertTrue(r.fingerprintMismatch().contains("the loaded log differs"), r.fingerprintMismatch());
        assertTrue(r.sections().get(0).resolved(),
                "announce, never forbid: the section still resolves and renders");
    }

    @Test
    void theAnnounceLineNamesTheLogYOUAREON_notTheOneTheReportRemembers() {
        // the announce line's whole job is "you are looking at a different log" — naming the report's
        // own file as the loaded one makes it point at the log you are NOT looking at
        LogFingerprint authored = new LogFingerprint("demo.yaml", 582, 100L, 900L);
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", authored, FilterSnapshot.all(),
                        List.of(SectionSpec.record(1, null))),
                STORE.index(), "other.yaml", Map.of(), Set.of(), Set.of(), new FilterState());
        assertTrue(r.fingerprintMismatch().contains("written against demo.yaml"),
                r.fingerprintMismatch());
        assertTrue(r.fingerprintMismatch().contains("differs (other.yaml"),
                "the file actually open is named: " + r.fingerprintMismatch());
    }

    @Test
    void aRenamedCopyGetsTheSofterAnnounce_notTheDifferentLogBanner() {
        // Q1, decided by the owner after review: same content + different name announces SOFTLY —
        // the reader learns it is a different file without the strong banner a content change earns
        LogFingerprint authored = LogFingerprint.of(STORE.index(), "demo.yaml");
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", authored, FilterSnapshot.all(), List.of()),
                STORE.index(), "demo-copy.yaml", Map.of(), Set.of(), Set.of(), new FilterState());
        assertNotNull(r.fingerprintMismatch());
        assertTrue(r.fingerprintMismatch().contains("matches on content but is a different file"),
                r.fingerprintMismatch());
        assertTrue(r.fingerprintMismatch().contains("'demo-copy.yaml'"),
                "the loaded file is named: " + r.fingerprintMismatch());
        assertFalse(r.fingerprintMismatch().contains("the loaded log differs"),
                "the strong banner stays reserved for a CONTENT difference");
        assertEquals("SAME CONTENT — A DIFFERENT FILE",
                ReportResolver.fingerprintHeading(r.fingerprintMismatch()),
                "a soft message under the strong heading contradicts itself");
    }

    @Test
    void theSameLogAnnouncesNothing() {
        LogFingerprint authored = LogFingerprint.of(STORE.index(), "demo.yaml");
        var r = ReportResolver.resolve(
                new ReportSpec("inv", "t", "", "", authored, FilterSnapshot.all(), List.of()),
                STORE.index(), Map.of(), Set.of(), Set.of(), new FilterState());
        assertNull(r.fingerprintMismatch());
        assertTrue(r.clean());
    }

    @Test
    void aDifferentFilterIsAnOffer_namingWhatDiffers() {   // acceptance 10 groundwork
        FilterState current = new FilterState();
        current.setText("breach");
        var snap = FilterSnapshot.all();
        var diff = snap.difference(current);
        assertTrue(diff.isPresent());
        assertTrue(diff.get().contains("text query"), diff.get());
        assertTrue(diff.get().contains("can be applied"), "offer, never act: " + diff.get());
    }

    @Test
    void acceptingTheOfferRestoresTheAuthoredView() {
        FilterState current = new FilterState();
        current.setText("breach");
        current.setDimensions(Set.of("RiskBreachEvent"));
        FilterSnapshot authored = new FilterSnapshot(100L, 300L, null, "",
                FilterState.GroupMode.DIMENSION);
        authored.applyTo(current);
        assertTrue(authored.difference(current).isEmpty(),
                "after applying the stored context, nothing differs");
        assertEquals(100L, current.fromMillis());
        assertNull(current.dimensions(), "null = all, FilterState's own rule, preserved");
    }

    @Test
    void anEmptyLogNamesItself() {
        LogFingerprint authored = new LogFingerprint("demo.yaml", 4, 100L, 400L);
        assertTrue(authored.mismatch(null).orElseThrow().contains("no log is loaded"));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static ReportSpec spec(SectionSpec... sections) {
        return new ReportSpec("inv", "title", "", "", null, FilterSnapshot.all(), List.of(sections));
    }
}
