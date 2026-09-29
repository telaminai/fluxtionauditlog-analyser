package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle capture's time-window excerpt (owner, 2026-09-28) and the file work around it. An excerpt keeps every
 * record's digest and shifts its index; the analyser re-reads what it wrote and refuses anything that is not the chosen
 * records; walks and reports are re-based, and whatever cannot be re-based honestly is left out and named. Headless:
 * the DEMO log, the real opener, the real profile.
 */
class BundleExcerptTest {

    static final Path DEMO_LOG = Path.of("src/main/resources/demo/demo-quote-audit.yaml");
    // records 4..8, which hold the breach (7); re-derived when M70.R0c refreshed the DEMO (one clock read per cycle)
    static final long FROM = 1767258000140L, TO = 1767258000210L;

    static LogStore demo() throws IOException {
        return LogStores.open(DEMO_LOG, 256);
    }

    @Test
    @DisplayName("the window selects the contiguous records whose log time is in it; an empty window selects none")
    void theWindowSelectsARange() throws Exception {
        try (LogStore s = demo()) {
            var r = BundleExcerpt.range(s, FROM, TO);
            assertEquals(4, r.first());
            assertEquals(8, r.last());
            assertEquals(10, r.sourceRecords());
            assertNull(BundleExcerpt.range(s, 1L, 2L), "a window before the log selects nothing");
            assertEquals(0, BundleExcerpt.range(s, null, TO).first(), "an open start begins at the first record");
        }
    }

    @Test
    @DisplayName("an excerpt re-reads as exactly the chosen records, each with the digest the source record has")
    void anExcerptReReadsAsTheSameRecords(@TempDir Path tmp) throws Exception {
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var checked = BundleExcerpt.write(taken, tmp.resolve("x.yaml"), 256);
            assertEquals(5, checked.index().size());
            try (LogStore back = LogStores.open(tmp.resolve("x.yaml"), 256)) {
                for (int i = 0; i < 5; i++) {
                    assertEquals(WalkIdentity.recordDigest(s.rawText(4 + i)), WalkIdentity.recordDigest(back.rawText(i)),
                            "record " + i + " is source record " + (4 + i) + ", byte for byte");
                }
            }
            assertEquals("records:5", checked.runBasis().get(checked.runBasis().size() - 1), "the excerpt's own run basis");
        }
    }

    @Test
    @DisplayName("an excerpt that does not re-read as the chosen records is refused, naming what differed")
    void aMismatchIsRefused(@TempDir Path tmp) throws Exception {
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var wrong = new BundleExcerpt.Taken(taken.range(), taken.text(),
                    List.of("sha256:0", taken.digests().get(1), taken.digests().get(2), taken.digests().get(3), taken.digests().get(4)));
            var e = assertThrows(IOException.class, () -> BundleExcerpt.write(wrong, tmp.resolve("x.yaml"), 256));
            assertTrue(e.getMessage().contains("record 0 differs from source record 4"), e.getMessage());
            var short1 = new BundleExcerpt.Taken(taken.range(), taken.text().substring(0, taken.text().indexOf("---\n", 10)) + "---\n",
                    taken.digests());
            var e2 = assertThrows(IOException.class, () -> BundleExcerpt.write(short1, tmp.resolve("y.yaml"), 256));
            assertTrue(e2.getMessage().contains("re-reads as 1 records, not the 5 chosen"), e2.getMessage());
        }
    }

    @Test
    @DisplayName("an excerpt never claims to be a whole stream: a stream-end marker in it refuses the capture")
    void anExcerptMakesNoEndClaim(@TempDir Path tmp) throws Exception {
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var claiming = new BundleExcerpt.Taken(taken.range(), taken.text() + "streamEnd: normal\nstreamEndRecords: 5\n---\n",   // terminated: Format 1.1 §1a
                    taken.digests());
            var e = assertThrows(IOException.class, () -> BundleExcerpt.write(claiming, tmp.resolve("x.yaml"), 256));
            assertTrue(e.getMessage().contains("claims a complete stream"), e.getMessage());
        }
    }

    static Path profile(Path tmp) throws IOException {
        return BundleProfileTest.senderProfile(tmp);         // a REAL sender profile: walk on record 7, report on record 7
    }

    static AppConfig loaded(Path p) {
        AppConfig c = new AppConfig();
        assertTrue(ProjectProfile.load(p, c, new SettingsShare()).loaded());
        return c;
    }

    @Test
    @DisplayName("re-base: a walk and a report on record 7 become record 3 of an excerpt starting at 4, with its own identity")
    void walksAndReportsAreReBased(@TempDir Path tmp) throws Exception {
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var checked = BundleExcerpt.write(taken, tmp.resolve("x.yaml"), 256);
            Path out = tmp.resolve("p.fluxtion-settings");
            var x = BundleProfile.export(profile(tmp), out, new BundleProfile.Rebase(4, 8, checked.runBasis(), checked.index()));
            assertTrue(x.leftOut().stream().noneMatch(l -> l.startsWith("walk") || l.startsWith("report")), x.leftOut().toString());
            AppConfig c = loaded(out);
            var walk = c.walks.get(0);
            assertEquals(3, walk.steps().get(1).view().record(), "the view's record is re-based");
            assertEquals("records:row:3", walk.steps().get(1).targets().get(0).target(), "and so is the target");
            assertEquals(walk.steps().get(1).targets().get(0).basis().digest(),
                    WalkIdentity.recordDigest(s.rawText(7)), "its digest is unchanged: the same record");
            assertEquals(checked.runBasis(), walk.runBasis(), "the walk's run is the excerpt's");
            assertEquals(5, walk.fingerprint().records(), "and so is its fingerprint");
            var report = c.reports.get(0);
            assertEquals(3, report.sections().stream().filter(r -> r.recordIndex() >= 0).findFirst().orElseThrow().recordIndex());
        }
    }

    @Test
    @DisplayName("re-base: a walk or a report pointing outside the excerpt is left out and named — never silently shifted")
    void whatCannotBeReBasedIsLeftOutAndNamed(@TempDir Path tmp) throws Exception {
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, null, 1767258000170L));      // records 0..5: no 7
            var checked = BundleExcerpt.write(taken, tmp.resolve("x.yaml"), 256);
            Path out = tmp.resolve("p.fluxtion-settings");
            var x = BundleProfile.export(profile(tmp), out, new BundleProfile.Rebase(0, 5, checked.runBasis(), checked.index()));
            assertTrue(x.leftOut().stream().anyMatch(l -> l.startsWith("walk 'why-the-spread-moved' (step 2 shows record 7")),
                    x.leftOut().toString());
            assertTrue(x.leftOut().stream().anyMatch(l -> l.startsWith("report 'breach-0900' (a section is on record 7")),
                    x.leftOut().toString());
            AppConfig c = loaded(out);
            assertEquals(List.of(), c.walks, "the walk is not in the profile");
            assertEquals(List.of(), c.reports, "nor the report");
        }
    }

    @Test
    @DisplayName("re-base: a report whose table is derived by record index is left out and named")
    void aDerivedTableIsLeftOut(@TempDir Path tmp) throws Exception {
        Path p = profile(tmp);
        // the stored form is ConfigStore's own (.call.N.key / .call.N.val)
        Files.writeString(p, Files.readString(p).replace("report.0.s.0.call.count=0",
                "report.0.s.0.call.count=1\nreport.0.s.0.call.0.key=recordIndex\nreport.0.s.0.call.0.val=2"));
        assertTrue(loaded(p).reports.get(0).sections().get(0).call().containsKey("recordIndex"),
                "control: the fixture really carries a call derived by record index, so the rule is reached");
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var checked = BundleExcerpt.write(taken, tmp.resolve("x.yaml"), 256);
            var x = BundleProfile.export(p, tmp.resolve("o.fluxtion-settings"), new BundleProfile.Rebase(4, 8, checked.runBasis(), checked.index()));
            assertTrue(x.leftOut().stream().anyMatch(l -> l.contains("derived by record index")), x.leftOut().toString());
        }
    }

    @Test
    @DisplayName("a capture that fails leaves nothing: no .fexp, no working folder; delete() removes both")
    void aFailedCaptureLeavesNothing(@TempDir Path tmp) throws Exception {
        Path ex = Files.createDirectories(tmp.resolve("exchange"));
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, FROM, TO));
            var wrong = new BundleExcerpt.Taken(taken.range(), taken.text(), List.of("sha256:0", "", "", "", ""));
            var job = new BundleWriter.Job(ex.resolve("x.fexp"), DEMO_LOG, null, "project.fluxtion-settings",
                    Files.readAllBytes(BundleProfileTest.FIXTURE), null, wrong, Instant.now(), "test", 256, null);
            assertThrows(IOException.class, () -> BundleWriter.write(job));
            try (var list = Files.list(ex)) {
                assertEquals(List.of(), list.toList(), "nothing is left in the exchange directory");
            }
        }
        Path out = Files.writeString(ex.resolve("y.fexp"), "DEMO");
        // a killed capture's folder: its marker names this host and nothing holds its lock (BundleWriterReapTest)
        Files.createDirectories(ex.resolve(".capture-123/bundle/log"));
        Files.writeString(ex.resolve(".capture-123").resolve(BundleWriter.OWNER), BundleWriter.HOST);
        BundleWriter.delete(out);
        try (var list = Files.list(ex)) {
            assertEquals(List.of(), list.toList(), "delete removes the bundle and a working folder left beside it");
        }
    }

    @Test
    @DisplayName("re-base: a walk whose TARGET points outside the excerpt, with no view record, is left out and named")
    void aWalkTargetOutsideIsLeftOut(@TempDir Path tmp) throws Exception {
        // the fixture's step 2 shows record 7 AND points at it; without the view record only the target check can catch it
        Path p = profile(tmp);
        Files.writeString(p, Files.readString(p).replace("walk.0.s.1.view.record=7\n", ""));
        assertNull(loaded(p).walks.get(0).steps().get(1).view().record(), "control: the step has no view record now");
        try (LogStore s = demo()) {
            var taken = BundleExcerpt.take(s, BundleExcerpt.range(s, null, 1767258000170L));
            var checked = BundleExcerpt.write(taken, tmp.resolve("x.yaml"), 256);
            var x = BundleProfile.export(p, tmp.resolve("o.fluxtion-settings"), new BundleProfile.Rebase(0, 5, checked.runBasis(), checked.index()));
            assertTrue(x.leftOut().stream().anyMatch(l -> l.startsWith("walk 'why-the-spread-moved' (step 2 points at record 7")),
                    x.leftOut().toString());
        }
    }

    @Test
    @DisplayName("the writer reports what was left out, so capture can say it: the external-series chart, by name")
    void theWriterReportsWhatWasLeftOut(@TempDir Path tmp) throws Exception {
        Path ex = Files.createDirectories(tmp.resolve("exchange"));
        var job = new BundleWriter.Job(ex.resolve("w.fexp"), DEMO_LOG, null, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, Instant.now(), "test", 256, null);
        var w = BundleWriter.write(job);
        assertTrue(w.lines().stream().anyMatch(l -> l.startsWith("left out: chart 'Venue feed latency (external CSV)'")), w.lines().toString());
        assertTrue(EvidenceBundle.verify(ex.resolve("w.fexp")).ok());
    }
}
