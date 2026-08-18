package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M30.1 — names discover, content orders, violations are reported. The acceptance centrepiece is the
 * logrotate-ambiguity pair: the SAME content named in both index conventions must load in the same,
 * correct time order with zero configuration (D-R1), because the suffix is never consulted for order.
 */
class RollSetResolverTest {

    @TempDir
    Path dir;

    private static String records(long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: e\n---\n");
        }
        return sb.toString();
    }

    private Path file(String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    // ---- discovery -------------------------------------------------------------------------------

    @Test
    void rootExtraction() {
        assertEquals("maker.log", RollSetResolver.rootOf("maker.log.1"));
        assertEquals("maker.log", RollSetResolver.rootOf("maker.log.12"));
        assertEquals("maker.log", RollSetResolver.rootOf("maker-2026-08-17.log"));
        assertEquals("maker.log", RollSetResolver.rootOf("maker-2026-08-17_09.log"));
        assertEquals("maker.log", RollSetResolver.rootOf("maker.2026-08-17_09-00.log"));
        assertEquals("maker.log", RollSetResolver.rootOf("maker-20260817.log"));
        assertNull(RollSetResolver.rootOf("maker.log"), "the bare file carries no roll suffix");
        assertNull(RollSetResolver.rootOf("other.txt"));
    }

    @Test
    void discoveryFindsSiblingsOfEitherShape_andNeverCrossesRoots() throws IOException {
        file("maker.log", records(300));
        file("maker.log.1", records(100));
        file("maker.log.2", records(200));
        file("other.log", records(1));
        file("other.log.1", records(2));
        List<Path> found = RollSetResolver.discoverSiblings(dir.resolve("maker.log"));
        assertEquals(3, found.size());
        assertTrue(found.stream().noneMatch(p -> p.getFileName().toString().startsWith("other")),
                "a same-directory set with a different root is a different set");
    }

    @Test
    void aLoneFileIsNotASet() throws IOException {
        Path only = file("solo.log", records(1));
        assertEquals(List.of(only), RollSetResolver.discoverSiblings(only));
    }

    // ---- ordering: the logrotate ambiguity, dissolved by content ----------------------------------

    @Test
    void bothIndexConventionsOrderIdenticallyByContent() throws IOException {
        // logrotate convention: .1 is the NEWEST rolled file
        Path lr = Files.createDirectory(dir.resolve("logrotate"));
        Files.writeString(lr.resolve("m.log"), records(300, 310));      // live = newest
        Files.writeString(lr.resolve("m.log.1"), records(200, 210));    // newer rolled
        Files.writeString(lr.resolve("m.log.2"), records(100, 110));    // oldest

        // incrementing-writer convention: .1 is the OLDEST
        Path iw = Files.createDirectory(dir.resolve("incrementing"));
        Files.writeString(iw.resolve("m.log.1"), records(100, 110));
        Files.writeString(iw.resolve("m.log.2"), records(200, 210));
        Files.writeString(iw.resolve("m.log"), records(300, 310));

        for (Path d : List.of(lr, iw)) {
            var set = RollSetResolver.resolve(
                    RollSetResolver.discoverSiblings(d.resolve("m.log")));
            assertTrue(set.report().isClean(), d.getFileName() + ": " + set.report().summarise());
            assertEquals(List.of(100L, 200L, 300L),
                    set.ordered().stream().map(RollSetResolver.Sibling::firstTime).toList(),
                    d.getFileName() + " must order by CONTENT, with zero configuration");
        }
    }

    @Test
    void headProbeSkipsUntimedLeadingRecords() throws IOException {
        String untimedThenTimed = "---\n#x [t] INFO L\neventLogRecord:\n  event: startup\n---\n"
                + records(500);
        Path f = file("m-2026-08-17.log", untimedThenTimed);
        var s = RollSetResolver.probe(f, RollSetResolver.MAX_PROBE);
        assertEquals(500L, s.firstTime(), "the probe scans FORWARD to the first TIMED record (R2)");
    }

    @Test
    void aWhollyUntimedFilePlacesByNameAndIsReportedLoudly() throws IOException {
        file("m.log.1", records(100, 110));
        file("m.log.2", "---\n#x [t] INFO L\neventLogRecord:\n  event: boundary\n---\n");
        file("m.log.3", records(200, 210));
        var set = RollSetResolver.resolve(List.of(
                dir.resolve("m.log.1"), dir.resolve("m.log.2"), dir.resolve("m.log.3")));
        assertFalse(set.report().isClean());
        var v = set.report().violations().get(0);
        assertEquals(TimeOrderReport.Kind.UNTIMED_FILE, v.kind());
        assertTrue(v.message().contains("order unverifiable"), v.message());
        assertEquals(3, set.ordered().size(), "the set still loads — refusing was rejected in review");
    }

    @Test
    void overlappingFilesAreReportedWithTheOverlapMeasured() throws IOException {
        file("m.log.1", records(100, 250));   // last = 250
        file("m.log.2", records(200, 300));   // first = 200 → 50ms overlap
        var set = RollSetResolver.resolve(List.of(dir.resolve("m.log.1"), dir.resolve("m.log.2")));
        var v = set.report().violations().get(0);
        assertEquals(TimeOrderReport.Kind.FILE_OVERLAP, v.kind());
        assertTrue(v.message().contains("50ms"), v.message());
    }

    @Test
    void tailProbeFindsTheLastTimedRecord() throws IOException {
        Path f = file("m.log.1", records(100, 200, 300));
        var s = RollSetResolver.probe(f, RollSetResolver.MAX_PROBE);
        assertEquals(100L, s.firstTime());
        assertEquals(300L, s.lastTime());
    }
}
