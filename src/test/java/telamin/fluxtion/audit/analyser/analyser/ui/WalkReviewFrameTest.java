package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * Review PR57 (implementation findings) — the cross-component checks the node tests could not make, on a REAL frame:
 * the real session, the real presenter, the real overlay and the real selection. Each test is a reviewer probe
 * scenario, turned into a regression.
 */
class WalkReviewFrameTest {

    static Map<String, Object> target(String name, String caption) {
        return Map.of("target", name, "caption", caption);
    }

    static void call(AsyncOpenInterleavingFrameTest.Frame f, String verb, Map<String, Object> params) throws Exception {
        onEdt(() -> render(f.ex, verb, params));
    }

    @Test
    @DisplayName("#72: a walk step can point at Java source, and waits while it is read")
    void aWalkStepCanPointAtJavaSource(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path root = tmp.resolve("src");
        Path file = root.resolve("com/acme/Priced.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "package com.acme;\npublic class Priced {\n"
                + "    double spread() { return 0.004; }\n" + "    // context\n".repeat(40) + "}\n");

        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            onEdt(() -> {
                ((telamin.fluxtion.audit.analyser.analyser.source.SourceService) field(f.frame, "sourceService"))
                        .configure(List.of(root.toString()), null);
                ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config"))
                        .sourceRoots.add(root.toString());
            });

            var step = Map.of("view", Map.of("tab", "source"), "targets", List.of(
                    target("source:java:com.acme.Priced:line:3", "the line that makes the spread")));
            call(f, "walk", Map.of("name", "DEMO_java", "steps", List.of(step)));
            call(f, "walk", Map.of("name", "DEMO_java", "play", true));

            // the source is read OFF the event thread, so the step is not settled synchronously --
            // it waits, and then says what it lit
            await("the java step settles", () -> walk(f).showing() && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("SHOWN", walk(f).phase(),
                        "aJavaTargetIsSHOWN — the walk refused source outright before #72: " + walk(f).reason());
                assertEquals(1, overlay.lit().size(), "and it is actually lit: " + overlay.lit());
                assertEquals("source:java:com.acme.Priced:line:3", overlay.lit().get(0).target());
            });
        }
    }

    @Test
    @DisplayName("R3: an unavailable target 1 and an available target 2 — the overlay draws 2, not 1")
    void theOverlayKeepsTheSessionsNumbers(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var partial = Map.of("view", Map.of("tab", "topology"), "targets", List.of(
                    target("topology:node:DEMO_missing", "unavailable first"), target("topology:node:priceListener", "second")));
            call(f, "walk", Map.of("name", "DEMO_partial", "steps", List.of(partial)));
            call(f, "walk", Map.of("name", "DEMO_partial", "play", true));
            await("partly shown and lit", () -> "PARTLY_SHOWN".equals(walk(f).phase()) && overlay.isLit());
            onEdt(() -> {
                assertEquals(List.of(1, 2), walk(f).targets().stream().map(t -> t.n()).toList(), "control: the session numbers 1 and 2");
                assertFalse(walk(f).targets().get(0).available(), "control: target 1 is not available");
                List<SpotlightOverlay.Lit> lit = overlay.lit();
                assertEquals(1, lit.size(), "only the available target is lit");
                assertEquals("topology:node:priceListener", lit.get(0).target());
                assertEquals(2, lit.get(0).n(), "the overlay must draw the number the strip and context state: " + lit);
            });
        }
    }

    @Test
    @DisplayName("R5: a record the step's filter hides is not SHOWN, and a detail target that depends on it is not available")
    void aHiddenRecordIsNotClaimedAsShown(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var hidden = Map.of("view", Map.of("tab", "summary", "record", 1, "filter", Map.of("dimensions", List.of())),
                    "targets", List.of(target("detail", "record one claim")));
            call(f, "walk", Map.of("name", "DEMO_hidden", "steps", List.of(hidden)));
            call(f, "walk", Map.of("name", "DEMO_hidden", "play", true));
            await("the step decided", () -> walk(f).showing() && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                int[] selected = ((LogTablePanel) field(f.frame, "tablePanel")).selectedModelRows();
                assertEquals(0, selected.length, "control: the real selection is empty — record 1 is hidden");
                assertNotEquals("SHOWN", walk(f).phase(), "a hidden record must not be reported as shown");
                assertFalse(walk(f).targets().get(0).available(),
                        "the detail target depends on the requested record, which is not shown: " + walk(f).targets());
                assertTrue(walk(f).targets().get(0).reason().contains("record 1"), walk(f).targets().get(0).reason());
                assertFalse(overlay.isLit(), "and nothing is lit that would point at the wrong record");
            });
        }
    }

    @Test
    @DisplayName("R5 / §3.4: a step whose record is not in the log is refused whole — the previous step stays on screen")
    void aRefusedViewLeavesThePreviousStep(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var first = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "first")));
            var beyond = Map.of("view", Map.of("tab", "summary", "record", 999_999, "filter", Map.of("text", "DEMO_changed")),
                    "targets", List.of(target("status", "beyond")));
            call(f, "walk", Map.of("name", "DEMO_refused", "steps", List.of(first, beyond)));
            call(f, "walk", Map.of("name", "DEMO_refused", "play", true));
            await("step 1 lit", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
            var filter = (telamin.fluxtion.audit.analyser.analyser.filter.FilterState) field(f.frame, "filter");
            String textBefore = filter.text();

            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            await("step 2 decided", () -> walk(f).step() == 1 && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("NOT_SHOWN", walk(f).phase(), "the step is refused");
                assertTrue(walk(f).reason().contains("999999") || walk(f).reason().contains("999,999"), walk(f).reason());
                assertEquals(textBefore, filter.text(), "nothing of the refused view was applied");
                assertTrue(overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:priceListener")),
                        "the previous step stays on screen: " + overlay.lit());
            });
        }
    }

    @Test
    @DisplayName("R6: replacing the SHOWING walk is decided by the session — it ends, and nothing of either version is left lit")
    void replacingTheShowingWalkIsTheSessionsDecision(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var structural = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "original")));
            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(structural, structural)));
            call(f, "walk", Map.of("name", "DEMO_edit", "play", true));
            await("original shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(Map.of("targets", List.of(target("status", "replacement"))))));

            await("the session decided", () -> !walk(f).showing() || walk(f).count() == 1);
            onEdt(() -> {
                assertFalse(walk(f).showing(), "a replaced definition ends its showing: published count=" + walk(f).count());
                assertTrue(walk(f).reason().contains("changed"), walk(f).reason());
                assertFalse(overlay.lit().stream().anyMatch(l -> "original".equals(l.caption())),
                        "the old version's caption is not left lit: " + overlay.lit());
            });
        }
    }

    @Test
    @DisplayName("R6: a rename keeps the frozen version showing under its new name; a delete ends it")
    void renameKeepsItAndDeleteEndsIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var one = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "one")));
            var two = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:quotePublisher", "two")));
            call(f, "walk", Map.of("name", "DEMO_a", "steps", List.of(one, two)));
            call(f, "walk", Map.of("name", "DEMO_a", "play", true));
            await("shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            call(f, "walk", Map.of("name", "DEMO_a", "rename", "DEMO_b"));
            onEdt(() -> {
                assertTrue(walk(f).showing(), "a rename does not change what is shown");
                assertEquals("DEMO_b", walk(f).walk(), "the showing walk carries its new name");
            });
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            await("step 2 of the frozen version", () -> walk(f).step() == 1 && "SHOWN".equals(walk(f).phase()));

            call(f, "walk", Map.of("name", "DEMO_b", "delete", true));
            onEdt(() -> assertFalse(walk(f).showing(), "a deleted walk is not left showing"));
        }
    }

    @Test
    @DisplayName("R7: while a walk is showing, a refused play of it (step 99) is reported as refused, not as success")
    void aRefusedPlayIsReportedAsRefused(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var one = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "one")));
            call(f, "walk", Map.of("name", "DEMO_edit", "steps", List.of(one)));
            call(f, "walk", Map.of("name", "DEMO_edit", "play", true));
            await("shown", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());

            var result = new java.util.concurrent.atomic.AtomicReference<telamin.fluxtion.audit.analyser.analyser.llm.ActionResult>();
            onEdt(() -> result.set(f.ex.render("walk", new java.util.LinkedHashMap<>(Map.of("name", "DEMO_edit", "play", true, "step", 99)))));
            assertFalse(result.get().ok(), "the node refused step 99; the reply must say so: " + result.get().toMap());
            assertTrue(String.valueOf(result.get().error()).contains("no step 99"), String.valueOf(result.get().error()));
            onEdt(() -> assertTrue(walk(f).showing(), "and the walk that was showing keeps showing"));

            var again = new java.util.concurrent.atomic.AtomicReference<telamin.fluxtion.audit.analyser.analyser.llm.ActionResult>();
            onEdt(() -> again.set(f.ex.render("walk", new java.util.LinkedHashMap<>(Map.of("name", "DEMO_edit", "play", true, "step", 1)))));
            assertTrue(again.get().ok(), "a valid replay of the showing walk is accepted: " + again.get().toMap());
        }
    }

    @Test
    @DisplayName("R1: when the session's identity becomes UNVERIFIED, a record target is re-resolved UNRESOLVED, and not lit")
    void aDegradedIdentityConstrainsTheTargets(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session");
            var record = Map.of("view", Map.of("tab", "summary", "record", 0), "targets", List.of(target("records:row:0", "record claim")));
            call(f, "walk", Map.of("name", "DEMO_record", "steps", List.of(record)));
            call(f, "walk", Map.of("name", "DEMO_record", "play", true));
            await("record lit", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
            long ticket = walk(f).ticket();

            onEdt(() -> session.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved(
                    session.snapshot().logGeneration(), "UNVERIFIED", "DEMO identity probe")));
            await("re-resolved", () -> walk(f).ticket() > ticket && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("UNVERIFIED", session.snapshot().logIdentity(), "control: the session's verdict moved");
                var t = walk(f).targets().get(0);
                assertEquals("UNRESOLVED", t.state(), "a record read from a file that changed cannot be certified: " + t);
                assertFalse(t.available(), "so it is not available: " + t);
                assertFalse(overlay.lit().stream().anyMatch(l -> l.target().equals("records:row:0")),
                        "and not lit: " + overlay.lit());
            });
        }
    }

    // ---- review PR57 R6, second round: the BULK paths report too --------------------------------------------

    /** A walk of one status step whose caption says which version it is. */
    static telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec version(String name, String caption) {
        return new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec(name, "", "person", "", "", null, List.of(),
                List.of(new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Step(caption,
                        telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.View.NONE,
                        List.of(new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec.Target("status", "", null)))),
                Map.of());
    }

    @Test
    @DisplayName("R6: a Settings IMPORT that replaces the showing walk is the session's decision, like a save")
    void animportOfTheShowingWalkIsReported(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            call(f, "walk", Map.of("name", "DEMO_imported", "steps",
                    List.of(Map.of("caption", "as saved", "targets", List.of(target("status", "here"))))));
            call(f, "walk", Map.of("name", "DEMO_imported", "play", true));
            await("showing", () -> walk(f).showing());

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            var incoming = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
            incoming.walks.add(version("DEMO_imported", "a different version, imported"));
            var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
            var categories = java.util.Set.of(
                    telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category.REPORTS);

            onEdt(() -> {
                share.apply(share.preview(share.export(incoming, categories), config), categories, config);
                try {
                    ChartLifecycleReviewFrameTest.invoke(f.frame, "applyImportedConfig");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            await("the session ended it", () -> !walk(f).showing());
            onEdt(() -> {
                assertTrue(walk(f).reason().contains("changed while it was showing"),
                        "the node's own policy for a changed definition, applied to an import: " + walk(f).reason());
                assertEquals("a different version, imported", config.walks.get(0).steps().get(0).caption(),
                        "control: the import really did replace the definition");
            });
        }
    }

    @Test
    @DisplayName("R6: an import that re-states the SAME walk changes nothing — it is not a change")
    void anImportOfTheSameWalkLeavesItShowing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            call(f, "walk", Map.of("name", "DEMO_same", "steps",
                    List.of(Map.of("caption", "as saved", "targets", List.of(target("status", "here"))))));
            call(f, "walk", Map.of("name", "DEMO_same", "play", true));
            await("showing", () -> walk(f).showing());

            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            var incoming = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
            incoming.walks.addAll(config.walks);                    // the very same definitions
            var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
            var categories = java.util.Set.of(
                    telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category.REPORTS);

            onEdt(() -> {
                share.apply(share.preview(share.export(incoming, categories), config), categories, config);
                try {
                    ChartLifecycleReviewFrameTest.invoke(f.frame, "applyImportedConfig");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            onEdt(() -> assertTrue(walk(f).showing(),
                    "re-stating what was already there must not end a walk, exactly as an unchanged save does not: "
                            + walk(f).reason()));
        }
    }

    // ---- W-A4: playback persists NOTHING, on the real frame, in bytes -----------------------------------------

    /**
     * "Once pending saves settle" (W-A4), deterministically: the project profile's write is coalesced behind
     * {@code ProjectSession.requestSave()}, so a sleep proves nothing. Flushing it and the machine config forces
     * anything playback changed IN MEMORY out to disk, which is what makes the byte comparison meaningful rather
     * than merely quiet.
     */
    private static void settle(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> {
            try {
                var projectField = MainFrame.class.getDeclaredField("project");
                projectField.setAccessible(true);
                Object project = projectField.get(f.frame);
                if (project != null) project.getClass().getMethod("flush").invoke(project);
                var save = MainFrame.class.getDeclaredMethod("saveConfigQuietly");
                save.setAccessible(true);
                save.invoke(f.frame);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** The settings text as comparable lines, minus the timestamp comment a properties store always writes. */
    private static List<String> lines(byte[] bytes) {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines()
                .filter(l -> !l.startsWith("#")).sorted().toList();
    }

    /** Every settings file this frame may write: the machine config and, when a project is open, its profile. */
    private static Map<Path, byte[]> settingsBytes(Path home, Path profile) throws Exception {
        Map<Path, byte[]> out = new java.util.LinkedHashMap<>();
        for (Path f : List.of(home.resolve(".fluxtion-analyser").resolve("config"), profile)) {
            out.put(f, java.nio.file.Files.exists(f) ? java.nio.file.Files.readAllBytes(f) : new byte[0]);
        }
        return out;
    }

    /**
     * W-A4 — the half the response left unattempted: not "no save funnel was called" (the presenter tests assert
     * that) but "both settings files are the same BYTES afterwards".
     *
     * <p>A dirty start first, because the leak this guards against is the walk carrying the person's state into a
     * step and then writing it back: the opposite grouping mode, a dimension and text filter, another tab and
     * another record, all in force before the walk starts.
     */
    @Test
    @DisplayName("W-A4: a dirty start does not leak into step 1, and playback leaves both settings files byte-identical")
    void playbackPersistsNothing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            Path home = Path.of(System.getProperty("user.home"));
            Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(
                    java.nio.file.Files.createDirectories(tmp.resolve("wa4Project")));
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile,
                    new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            call(f, "open", Map.of("project", profile.toString()));
            // opening a project closes the log, which is the realistic order anyway: project, then log
            call(f, "open", Map.of("log", Path.of("src/main/resources/demo/demo-quote-series.yaml")
                    .toAbsolutePath().toString()));
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            Thread.sleep(300);

            call(f, "walk", Map.of("name", "DEMO_wa4", "steps", List.of(
                    Map.of("caption", "one", "view", Map.of("tab", "topology"),
                            "targets", List.of(target("topology:node:priceListener", "here"))),
                    Map.of("caption", "two", "view", Map.of("tab", "summary"),
                            "targets", List.of(target("status", "and here"))),
                    // a CHART step, because the persisting path W-A4 names is the chart one: selecting a chart
                    // must not reach the save funnel that opening or editing one goes through
                    Map.of("caption", "three", "view", Map.of("tab", "graph", "graph", "Graph 1"),
                            "targets", List.of(target("status", "the chart"))))));

            // the dirty start: the opposite grouping mode, a dimension and text filter, another tab, another record
            call(f, "filter", Map.of("group", "RAW_EVENT", "text", "dirty"));
            call(f, "goto", Map.of("recordIndex", 1));
            call(f, "spotlight", Map.of("target", "tab:reports", "caption", "somewhere else"));
            settle(f);                               // every save the setup asked for lands BEFORE the baseline

            var before = settingsBytes(home, profile);
            var walksBefore = List.copyOf(
                    ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config")).walks);

            call(f, "walk", Map.of("name", "DEMO_wa4", "play", true));
            await("step 1 settled", () -> !"PREPARING".equals(walk(f).phase()));
            call(f, "walk", Map.of("name", "DEMO_wa4", "play", true, "step", 2));
            await("step 2 settled", () -> !"PREPARING".equals(walk(f).phase()));
            call(f, "walk", Map.of("name", "DEMO_wa4", "play", true, "step", 3));
            await("step 3 settled", () -> !"PREPARING".equals(walk(f).phase()));
            call(f, "walk", Map.of("end", true));
            settle(f);                               // and anything playback changed is forced out to disk

            var after = settingsBytes(home, profile);
            for (Path file : before.keySet()) {
                assertEquals(lines(before.get(file)), lines(after.get(file)),
                        "W-A4: playing a walk wrote to " + file.getFileName() + ". Playback restores a view; it "
                                + "must never persist one, or replaying someone's explanation would quietly "
                                + "rewrite the reader's own setup");
            }
            onEdt(() -> assertEquals(walksBefore,
                    ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config")).walks,
                    "and the saved definitions are what they were"));
        }
    }

    /** Import {@code w} through the real Settings path: SettingsShare.apply, then the frame's applyImportedConfig. */
    static void importing(AsyncOpenInterleavingFrameTest.Frame f, telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec w)
            throws Exception {
        var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
        var incoming = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        incoming.walks.add(w);
        var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
        var categories = java.util.Set.of(telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category.REPORTS);
        onEdt(() -> {
            share.apply(share.preview(share.export(incoming, categories), config), categories, config);
            try {
                ChartLifecycleReviewFrameTest.invoke(f.frame, "applyImportedConfig");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    @DisplayName("R6 (fix review, finding 1): a verb save BETWEEN two imports cannot leave the bulk diff stale")
    void aVerbSaveBetweenTwoImportsIsStillReported(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            var d1 = version("DEMO_w", "D1");
            importing(f, d1);                                        // the bulk path has now seen DEMO_w as D1
            call(f, "walk", Map.of("name", "DEMO_w", "steps",
                    List.of(Map.of("caption", "D2", "targets", List.of(target("status", "here"))))));
            call(f, "walk", Map.of("name", "DEMO_w", "play", true));
            await("showing D2", () -> walk(f).showing());

            importing(f, d1);                                        // config goes back to D1

            await("the session ended it", () -> !walk(f).showing());
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(f.frame, "config");
            onEdt(() -> {
                assertEquals("D1", config.walks.get(0).steps().get(0).caption(), "control: the import restored D1");
                assertTrue(walk(f).reason().contains("changed while it was showing"),
                        "D2 was showing and config now holds D1: the node must be told, whatever the bulk path saw last: "
                                + walk(f).reason());
            });
        }
    }
}
