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
            AtomicReference<Integer> pending = new AtomicReference<>();
            onEdt(() -> {
                @SuppressWarnings("unchecked")
                var logCtx = (Map<String, Object>) ((Map<String, Object>) render(f.ex, "context",
                        Map.of("sections", List.of("log"))).get("context")).get("log");
                read.set(((Number) logCtx.get("records")).intValue());
                pending.set(((Number) logCtx.getOrDefault("trailingRecordsPending", 0)).intValue());
            });
            assertTrue(read.get() > 0, "control: the session has read records");
            // review of EB.F6: "in the store" and "read" differ exactly when a trailing record is PENDING; this is that case
            assertTrue(pending.get() > 0, "control: a trailing record is pending, so read and in-the-file really differ here");
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
    @DisplayName("M70.R2 through the verb: a paired replay is carried as format 2; another run's replay is refused, nothing written")
    void aReplayIsCarriedOnlyWhenItPairs(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path recordedLog = Path.of("src/test/resources/replay/demo-quote-recorded-audit.yaml").toAbsolutePath();
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            // a read the analyser confines (review R4): the replay records sit in the exchange directory, named relative
            Files.copy(Path.of("src/test/resources/replay/demo-quote-recorded.replay.yaml"), ex.resolve("demo-quote-recorded.replay.yaml"));
            String replay = "demo-quote-recorded.replay.yaml";
            // the recorded run's replay against ANOTHER run's log: refused by the node, nothing left
            // a genuinely different run: the series log (the short DEMO log, since M70.R0c, IS the recorded run's inputs and pairs)
            openLog(f, Path.of("src/test/resources/topology/demo-quote-series.yaml").toAbsolutePath());
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, Map.of("bundle", Map.of("path", "wrong.fexp", "replay", replay)))));
            refused(echo.get(), "the replay does not belong to this log");
            assertEquals(List.of(), leftBehind(ex), "a refused capture writes nothing");

            // against its own log: written, format 2, with the replay member and what the node says of it
            openLog(f, recordedLog);
            onEdt(() -> echo.set(ask(f, Map.of("bundle", Map.of("path", "run.fexp", "replay", replay)))));
            assertEquals(Boolean.TRUE, echo.get().get("ok"), "a paired replay is capturable: " + echo.get());
            Map<String, Object> c = awaitDecided(f);
            assertEquals("WRITTEN", c.get("phase"), String.valueOf(c));
            assertTrue(String.valueOf(c.get("lines")).contains("the run's 7 recorded inputs, paired with the log in order"),
                    String.valueOf(c.get("lines")));
            var v = telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.verify(ex.resolve("run.fexp"));
            assertTrue(v.ok(), v.refusal());
            assertEquals("replay/demo-quote-recorded.replay.yaml", v.replay().get("member"));
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
    @DisplayName("written: the bundle verifies, carries the notes, and the report saved IN THE SAME TASK — the live settings, not a wait")
    void aCaptureFlushesAndWrites(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {        // one task: the debounce (800 ms) cannot have fired, so only the live settings can hold it
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
                        "a report saved in the same task is in the bundle: the capture took the live settings, it did not wait");
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

    /** The bundle's profile member, as text. */
    static String profileMember(Path bundle) throws Exception {
        try (ZipFile z = new ZipFile(bundle.toFile())) {
            assertNotNull(z.getEntry("profile/project.fluxtion-settings"), "the bundle carries its profile");
            return new String(z.getInputStream(z.getEntry("profile/project.fluxtion-settings")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("EB.F11: a READ-ONLY project profile — the write fails — and the bundle still holds the edit just made")
    void aReadOnlyProfileDoesNotMakeTheBundleStale(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            openLog(f, DEMO_LOG);
            for (int i = 0; i < 40; i++) Thread.sleep(50);                    // let the open's own writes settle
            assertTrue(profile.toFile().setWritable(false), "control: the profile is read-only now");
            try {
                AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
                onEdt(() -> {        // one task: the edit, then the capture; the profile file cannot take the edit
                    render(f.ex, "report", Map.of("name", "demo-unwritable", "sections",
                            List.of(Map.of("kind", "narrative", "text", "DEMO"))));
                    echo.set(ask(f, bundle("ro.fexp")));
                });
                assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
                assertEquals("WRITTEN", awaitDecided(f).get("phase"));
                assertFalse(Files.readString(profile).contains("demo-unwritable"), "control: the FILE never got the edit");
                assertTrue(profileMember(ex.resolve("ro.fexp")).contains("demo-unwritable"),
                        "the bundle holds the session's settings, not a stale file the write could not update");
            } finally {
                profile.toFile().setWritable(true);
            }
        }
    }

    @Test
    @DisplayName("EB.F11: no project, and your own settings file READ-ONLY — its best-effort save fails — the bundle still holds the edit")
    void aReadOnlyOwnSettingsFileDoesNotMakeTheBundleStale(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            openLog(f, DEMO_LOG);
            Path own = tmp.resolve("home/.fluxtion-analyser/config");
            for (int i = 0; i < 40 && !Files.exists(own); i++) Thread.sleep(50);
            assertTrue(Files.exists(own), "control: the analyser has written its own settings");
            assertTrue(own.toFile().setWritable(false), "control: they are read-only now");
            try {
                AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
                onEdt(() -> {
                    render(f.ex, "report", Map.of("name", "demo-own-unwritable", "sections",
                            List.of(Map.of("kind", "narrative", "text", "DEMO"))));
                    echo.set(ask(f, bundle("own.fexp")));
                });
                assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
                assertEquals("WRITTEN", awaitDecided(f).get("phase"));
                assertFalse(Files.readString(own).contains("demo-own-unwritable"), "control: the FILE never got the edit");
                assertTrue(profileMember(ex.resolve("own.fexp")).contains("demo-own-unwritable"),
                        "the bundle holds the session's settings, not the file a failed save left stale");
            } finally {
                own.toFile().setWritable(true);
            }
        }
    }

    @Test
    @DisplayName("EB.F11: a project switch in the same task as the capture — the bundle holds the NEW project's settings")
    void aProjectSwitchIsNotInFlight(@TempDir Path tmp) throws Exception {
        // a project switch is decided and applied within its own dispatch, so nothing is "in flight" by the time a
        // capture is asked for: the capture sees the project now in force, whole
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path a = Files.createDirectories(tmp.resolve("a/.analyser")).resolve("project.fluxtion-settings");
        Path b = Files.createDirectories(tmp.resolve("b/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(a, "share.version=1\nreport.count=1\nreport.0.name=demo-in-a\nreport.0.s.count=0\n");
        Files.writeString(b, "share.version=1\nreport.count=1\nreport.0.name=demo-in-b\nreport.0.s.count=0\n");
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("project", a.toString())));
            openLog(f, DEMO_LOG);
            onEdt(() -> render(f.ex, "open", Map.of("project", b.toString())));
            openLog(f, DEMO_LOG);                                           // a switch closes the log; reopen under b
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(ask(f, bundle("b.fexp"))));
            assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            String member = profileMember(ex.resolve("b.fexp"));
            assertTrue(member.contains("demo-in-b") && !member.contains("demo-in-a"), member);
        }
    }

    @Test
    @DisplayName("EB.F11: an open chart edited in the same task as the capture is in the bundle (the pre-save sync, inside a dispatch)")
    void anOpenChartEditIsCaptured(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        try (var f = shown(tmp)) {
            Path ex = exchange(f, tmp);
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            openLog(f, DEMO_LOG);
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {
                render(f.ex, "graph", Map.of("name", "DEMO spread just drawn", "series", List.of("quotePublisher.spread")));
                echo.set(ask(f, bundle("chart.fexp")));
            });
            assertEquals(Boolean.TRUE, echo.get().get("ok"), String.valueOf(echo.get()));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"), "no protocol violation from syncing charts inside a dispatch");
            assertTrue(profileMember(ex.resolve("chart.fexp")).contains("DEMO\\ spread\\ just\\ drawn")
                            || profileMember(ex.resolve("chart.fexp")).contains("DEMO spread just drawn"),
                    "the chart drawn a moment before is in the bundle");
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
                    // records 4..8, re-derived when M70.R0c refreshed the DEMO (one clock read per cycle)
                    Map.of("path", "part.fexp", "from", 1767258000140L, "to", 1767258000210L)))));
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
