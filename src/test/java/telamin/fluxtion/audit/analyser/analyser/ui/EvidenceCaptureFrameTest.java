package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;

import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Evidence bundle capture on a REAL frame, through the real verb ({@code report {bundle}} into ActionExecutor): the
 * frame's half of the capture — what it observes for the session (freshness, one plain file, a pending load), the
 * project flush, the file work — and the one provocation the node test cannot make: the log closing while the bundle
 * is being written. A background result reaches the session with {@code invokeLater}, so a capture and a close issued
 * in ONE event-thread task always have the close land first. That is what makes the cleanup path testable at all.
 */
class EvidenceCaptureFrameTest {

    static final Path DEMO_LOG = Path.of("src/main/resources/demo/demo-quote-audit.yaml").toAbsolutePath();

    static Path exchange(AsyncOpenInterleavingFrameTest.Frame f, Path tmp) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("exchange"));
        onEdt(() -> {
            var c = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            c.assistantExports = true;
            c.assistantExportDir = dir.toString();
        });
        return dir;
    }

    /** The verb, unasserted: a refusal is the answer these cases want. */
    static Map<String, Object> ask(AsyncOpenInterleavingFrameTest.Frame f, Map<String, Object> params) {
        return f.ex.render("report", new java.util.LinkedHashMap<>(params)).toMap();
    }

    static Map<String, Object> bundle(String path) {
        return Map.of("bundle", Map.of("path", path));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> captureNow(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project"))).get("context");
            out.set(ctx.get("capture"));
        });
        return (Map<String, Object>) out.get();
    }

    /** Until the capture is decided (not WRITING), with a bound. */
    static Map<String, Object> awaitDecided(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        for (int i = 0; i < 100; i++) {
            Map<String, Object> c = captureNow(f);
            if (c != null && !"WRITING".equals(c.get("phase"))) return c;
            Thread.sleep(50);
        }
        return captureNow(f);
    }

    static AsyncOpenInterleavingFrameTest.Frame shown(Path tmp) throws Exception {
        var f = new AsyncOpenInterleavingFrameTest.Frame(tmp);
        onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
        return f;
    }

    static void openLog(AsyncOpenInterleavingFrameTest.Frame f, Path log) throws Exception {
        onEdt(() -> render(f.ex, "open", Map.of("log", log.toString())));
        awaitLoaded(f.ex);
    }

    static Map<String, Object> refused(Map<String, Object> echo, String naming) {
        assertEquals(Boolean.FALSE, echo.get("ok"), "refused: " + echo);
        assertTrue(String.valueOf(echo.get("error")).contains(naming), "refused BY NAME ('" + naming + "'): " + echo);
        return echo;
    }

    static List<Path> leftBehind(Path dir) throws Exception {
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".fexp") || p.getFileName().toString().startsWith(".capture-"))
                    .toList();
        }
    }

    @Test
    @DisplayName("EP-A1 through the verb: no log open — refused by name, nothing written")
    void noLog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("a.fexp"))));
            refused(echo.get(), "no log is open");
            AtomicReference<Map<String, Object>> mixed = new AtomicReference<>();
            onEdt(() -> mixed.set(ask(f, Map.of("bundle", Map.of("path", "b.fexp"), "name", "DEMO"))));
            refused(mixed.get(), "must be used alone");
            assertEquals(List.of(), leftBehind(ex));
        }
    }

    @Test
    @DisplayName("EP-A1 through the verb: bytes appended to the file — the read-through identity says unverified, and it is refused")
    void appendedBytes(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.copy(DEMO_LOG, Files.createDirectories(tmp.resolve("logs")).resolve("demo-quote-audit.yaml"));
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, log);
            Files.writeString(log, "\n---\neventLogRecord:\n    logTime: 1767258000999\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("a.fexp"))));
            refused(echo.get(), "identity: unverified");
            assertEquals(List.of(), leftBehind(ex));
        }
    }

    @Test
    @DisplayName("EB.F6 through the verb: under Follow the file grew — the bundle holds the records READ, not the file")
    void aGrowingLogBundlesWhatWasRead(@TempDir Path tmp) throws Exception {
        // Outside Follow the read-through identity refuses any change (the cases above). Under Follow a change is growth:
        // the owner's rule (2026-09-28) is to bundle what was read so far, as an excerpt of every record read.
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = Files.copy(DEMO_LOG, Files.createDirectories(tmp.resolve("logs")).resolve("demo-quote-audit.yaml"));
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, log);
            onEdt(() -> render(f.ex, "open", Map.of("follow", true)));
            awaitLoaded(f.ex);
            // what the session has READ: under a live read the unterminated last record is pending, not served
            AtomicReference<Integer> read = new AtomicReference<>();
            onEdt(() -> {
                @SuppressWarnings("unchecked")
                var logCtx = (Map<String, Object>) ((Map<String, Object>) render(f.ex, "context",
                        Map.of("sections", List.of("log"))).get("context")).get("log");
                read.set(((Number) logCtx.get("records")).intValue());
            });
            assertTrue(read.get() > 0, "control: the session has read records");
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {        // one task: the append and the request, with no Follow poll between them
                try {
                    Files.writeString(log, "---\neventLogRecord:\n    logTime: 1767258000999\n", StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                echo.set(ask(f, bundle("growing.fexp")));
            });
            assertEquals(Boolean.TRUE, echo.get().get("ok"), "a growing log is captured, not refused: " + echo.get());
            Map<String, Object> c = awaitDecided(f);
            assertEquals("WRITTEN", c.get("phase"), String.valueOf(c));
            assertTrue(((List<?>) c.get("lines")).stream().anyMatch(l -> String.valueOf(l).startsWith("read so far: ")),
                    "and the author is told it holds what was read: " + c.get("lines"));
            var v = EvidenceBundle.verify(ex.resolve("growing.fexp"));
            assertTrue(v.ok(), v.refusal());
            assertNotNull(v.excerpt(), "the manifest states the cut");
            assertEquals(Boolean.TRUE, v.excerpt().get("readSoFar"), "and that it is what was read so far");
            try (ZipFile z = new ZipFile(ex.resolve("growing.fexp").toFile())) {
                assertNotNull(z.getEntry("log/demo-quote-audit.yaml"), "the log member");
                String member = new String(z.getInputStream(z.getEntry("log/demo-quote-audit.yaml")).readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(member.contains("1767258000999"), "the record written after the last read is not in the bundle");
                assertEquals(read.get().intValue(), member.split("eventLogRecord:", -1).length - 1,
                        "exactly the records the session had read, no more");
                assertEquals(read.get().longValue(), ((Number) v.excerpt().get("sourceRecords")).longValue(),
                        "and the manifest counts what was read");
            }
        }
    }

    @Test
    @DisplayName("EP-A1 through the verb: a load pending — refused by name, nothing written")
    void aLoadPending(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {        // one task: the second open is still in flight when the capture is asked for
                render(f.ex, "open", Map.of("log", Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString()));
                echo.set(ask(f, bundle("a.fexp")));
            });
            refused(echo.get(), "a load is pending");
            awaitLoaded(f.ex);
            assertEquals(List.of(), leftBehind(ex));
        }
    }

    @Test
    @DisplayName("EP-A1 through the verb: not one plain file (a rolled set) — refused by name, nothing written")
    void notOnePlainFile(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path dir = Files.createDirectories(tmp.resolve("rolled"));
        Path a = Files.copy(DEMO_LOG, dir.resolve("demo-quote-audit.1.yaml"));
        Path b = Files.copy(DEMO_LOG, dir.resolve("demo-quote-audit.2.yaml"));
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("logs", List.of(a.toString(), b.toString()))));
            awaitLoaded(f.ex);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("a.fexp"))));
            refused(echo.get(), "not one plain file");
            assertEquals(List.of(), leftBehind(ex));
        }
    }

    @Test
    @DisplayName("a memory-mapped log that did NOT change is captured: a large log is not refused for being large")
    void anUnchangedMappedLogIsCaptured(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config")).memoryThresholdMb = 0);
            openLog(f, DEMO_LOG);
            AtomicReference<Object> kind = new AtomicReference<>();
            onEdt(() -> kind.set(field(f.frame, "store").getClass().getSimpleName()));
            assertEquals("MappedLogStore", kind.get(), "control: the log is memory-mapped");
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("big.fexp"))));
            assertEquals(Boolean.TRUE, echo.get().get("ok"), "an unchanged mapped log is capturable: " + echo.get());
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            assertTrue(Files.exists(ex.resolve("big.fexp")));
        }
    }

    @Test
    @DisplayName("written: the bundle verifies, carries the notes, and the report saved IN THE SAME TASK — the flush, not a wait")
    void aCaptureFlushesAndWrites(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {        // one task: the debounce (800 ms) cannot have fired; only the flush can put it in the file
                render(f.ex, "report", Map.of("name", "demo-just-saved", "sections",
                        List.of(Map.of("kind", "narrative", "text", "DEMO"))));
                echo.set(render(f.ex, "report", Map.of("bundle", Map.of("path", "w.fexp", "notes", "# DEMO notes\n"))));
            });
            assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
            Map<String, Object> c = awaitDecided(f);
            assertEquals("WRITTEN", c.get("phase"), String.valueOf(c));
            Path out = ex.resolve("w.fexp");
            var v = EvidenceBundle.verify(out);
            assertTrue(v.ok(), v.refusal());
            assertEquals(v.identity(), c.get("identity"), "context states the bundle's own identity");
            try (ZipFile z = new ZipFile(out.toFile())) {
                for (String member : List.of("notes/NOTES.md", "profile/project.fluxtion-settings", "log/demo-quote-audit.yaml")) {
                    assertNotNull(z.getEntry(member), "the bundle carries " + member);   // a named failure, not an NPE
                }
                assertEquals("# DEMO notes\n", new String(z.getInputStream(z.getEntry("notes/NOTES.md")).readAllBytes(), StandardCharsets.UTF_8));
                String profileMember = new String(z.getInputStream(z.getEntry("profile/project.fluxtion-settings")).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(profileMember.contains("demo-just-saved"),
                        "a report saved in the same task is in the bundle: the capture flushed the project, it did not wait");
                assertArrayEquals(Files.readAllBytes(DEMO_LOG),
                        z.getInputStream(z.getEntry("log/demo-quote-audit.yaml")).readAllBytes(), "the whole log, byte for byte");
            }
            assertTrue(leftBehind(ex).stream().noneMatch(p -> p.getFileName().toString().startsWith(".capture-")),
                    "the working folder is gone");
        }
    }

    @Test
    @DisplayName("PROVOKED: the log closed while the bundle was written — refused, and the .fexp AND its working folder are gone")
    void aCloseDuringTheWriteDeletesEverything(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {        // one task: the write's result can only reach the session after the close has landed
                echo.set(render(f.ex, "report", bundle("gone.fexp")));
                render(f.ex, "open", Map.of("close", "log"));
            });
            assertEquals(Boolean.TRUE, echo.get().get("ok"), "control: the capture was accepted: " + echo.get());
            Map<String, Object> c = awaitDecided(f);
            assertEquals("REFUSED", c.get("phase"), String.valueOf(c));
            assertTrue(String.valueOf(c.get("reason")).startsWith("the log was closed while the bundle was being written"),
                    String.valueOf(c.get("reason")));
            for (int i = 0; i < 40 && !leftBehind(ex).isEmpty(); i++) Thread.sleep(50);
            assertEquals(List.of(), leftBehind(ex), "neither the bundle nor its working folder is left behind");
        }
    }

    @Test
    @DisplayName("PROVOKED (EB.F9): ANOTHER LOG OPENED while a bundle is written, the open landing off the event thread — refused, deleted")
    void anotherLogOpenedDuringTheWriteDeletesTheBundle(@TempDir Path tmp) throws Exception {
        // The real paths, and no timing: the capture goes through the verb, the node and the background write; the second
        // log through the real open and its off-thread load. The test HOLDS the write at its start (BundleWriter's
        // beforeCopy seam) until the other log has landed, so the order is held rather than raced, on every build.
        assumeFalse(GraphicsEnvironment.isHeadless());
        var release = new java.util.concurrent.CountDownLatch(1);
        var reached = new java.util.concurrent.CountDownLatch(1);
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, DEMO_LOG);
            AtomicReference<Long> before = new AtomicReference<>();
            onEdt(() -> before.set(((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .snapshot().logGeneration()));
            telamin.fluxtion.audit.analyser.bundle.BundleWriterAccess.holdWritesUntil(release, reached);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("held.fexp"))));
            assertEquals(Boolean.TRUE, echo.get().get("ok"), "control: the capture was accepted: " + echo.get());
            assertTrue(reached.await(30, java.util.concurrent.TimeUnit.SECONDS), "control: the write started and is held");
            onEdt(() -> render(f.ex, "open", Map.of("log",
                    Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            AtomicReference<Long> after = new AtomicReference<>();
            onEdt(() -> after.set(((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .snapshot().logGeneration()));
            assertNotEquals(before.get(), after.get(), "control: another log really was opened");
            assertEquals("WRITING", captureNow(f).get("phase"), "control: it landed while the bundle was being written");
            release.countDown();                              // the write carries on, and reports under the OLD generation
            Map<String, Object> c = awaitDecided(f);
            assertEquals("REFUSED", c.get("phase"), String.valueOf(c));
            assertTrue(String.valueOf(c.get("reason")).startsWith("another log was opened while the bundle was being written"),
                    String.valueOf(c.get("reason")));
            for (int i = 0; i < 40 && !leftBehind(ex).isEmpty(); i++) Thread.sleep(50);
            assertEquals(List.of(), leftBehind(ex), "neither the bundle nor its working folder is left behind");
        } finally {
            release.countDown();
            telamin.fluxtion.audit.analyser.bundle.BundleWriterAccess.stopHolding();
        }
    }

    @Test
    @DisplayName("an excerpt: records in the window, stated in the manifest, re-read and matched")
    void anExcerpt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(render(f.ex, "report", Map.of("bundle",
                    Map.of("path", "part.fexp", "from", 1767258000200L, "to", 1767258000330L)))));
            assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
            Map<String, Object> c = awaitDecided(f);
            assertEquals("WRITTEN", c.get("phase"), String.valueOf(c));
            var v = EvidenceBundle.verify(ex.resolve("part.fexp"));
            assertTrue(v.ok(), v.refusal());
            assertNotNull(v.excerpt(), "the manifest says the log is an excerpt");
            assertEquals(4, ((Number) v.excerpt().get("firstRecord")).intValue());
            assertEquals(8, ((Number) v.excerpt().get("lastRecord")).intValue());
            assertTrue(((List<?>) c.get("lines")).stream().anyMatch(l -> String.valueOf(l).startsWith("excerpt: records 4..8 of 10")),
                    String.valueOf(c.get("lines")));
            AtomicReference<Map<String, Object>> empty = new AtomicReference<>();
            onEdt(() -> empty.set(ask(f, Map.of("bundle", Map.of("path", "none.fexp", "from", 1L, "to", 2L)))));
            refused(empty.get(), "nothing to excerpt");        // at once, as a named verb error: the node's refusal
            assertFalse(Files.exists(ex.resolve("none.fexp")));
        }
    }
}
