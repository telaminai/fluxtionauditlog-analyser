package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.Main;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.session.resume.SessionResumeStore;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only child JVM. Its parent clears the environment, supplies a stub CLI, and isolates both homes. */
public final class FirstMinuteAppProbe {
    private static MainFrame frame;
    /** r8: the repeated input ("d did not run") is step 8 of 9, index 7. */
    static final int REPEAT_STEP = 7;
    private static final java.util.List<String> transitions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final CountDownLatch closed = new CountDownLatch(1);
    private FirstMinuteAppProbe() { }

    public static void main(String[] args) {
        try {
            if ("failed".equals(args[0])) {
                java.nio.file.Files.createDirectories(DiamondAssets.root().getParent());
                java.nio.file.Files.writeString(DiamondAssets.root(), "DEMO extraction folder is a file");
            }
            Main.main("substituted".equals(args[0]) ? new String[]{"--rest"} : new String[0]);
            await(() -> {
                for (Window window : Window.getWindows()) if (window instanceof MainFrame f && f.isShowing()) {
                    frame = f; frame.exitProcess = code -> closed.countDown();
                    frame.setSize(1200, 800); frame.validate();
                    return true;
                }
                return false;
            }, 15, "real Main creates its frame");
            edt(() -> driver().onSnapshot(next -> {
                String seen = next.walkPlayback().step() + ":" + next.walkPlayback().phase()
                        + (next.walkPlayback().phase().endsWith("_SHOWN") && !next.walkPlayback().reason().isBlank() ? "(" + next.walkPlayback().reason() + ")" : "");
                if (transitions.isEmpty() || !transitions.getLast().equals(seen)) transitions.add(seen);
            }));
            switch (args[0]) {
                case "first" -> first(Path.of(args[1]));
                case "first-minute-config" -> {
                    await(() -> "SHOWN".equals(snapshot().walkPlayback().phase()),
                            20, "the first-minute source and graph have actually loaded");
                    edt(() -> assertEquals(telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition.PROCESSOR,
                            ((telamin.fluxtion.audit.analyser.analyser.source.SourceService) field(frame, "sourceService")).selectedFqn(),
                            "the processor is selected for this presentation even though it is not persisted"));
                }
                case "returning" -> returning(false);
                case "personal" -> personal();
                case "personal-returning" -> returning(true);
                case "substituted" -> substituted(Path.of(args[1]));
                case "failed" -> failed(Path.of(args[1]));
                case "close-all" -> closeAll(Path.of(args[1]));
                case "live-spotlight" -> liveSpotlight();
                default -> fail("unknown isolated probe");
            }
            edt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
            assertTrue(closed.await(10, TimeUnit.SECONDS), "the actual close sequence finishes");
            if ("first-minute-config".equals(args[0])) {
                var saved = new ConfigStore().load();
                assertTrue(saved.selectedEventProcessor == null || saved.selectedEventProcessor.isBlank(),
                        "the first-minute processor must stay session-scoped");
                assertTrue(saved.eventProcessorFqns.isEmpty(), "inference must not persist the shipped processor either");
                assertTrue(saved.logFile == null || saved.logFile.isBlank(), "the first-minute log must not become a machine input");
                assertTrue(saved.graphmlFile == null || saved.graphmlFile.isBlank(), "the first-minute graph must not become a machine input");
                assertTrue(saved.recentFiles.isEmpty(), "the first-minute log must not enter recents");
                assertTrue(saved.recentGraphml.isEmpty(), "the first-minute graph must not enter recents");
                assertTrue(saved.sourceRoots.isEmpty(), "the shipped roots stay transient");
                assertTrue(saved.firstMinutePlayed, "machine-only played state is still saved");
            }
            if ("substituted".equals(args[0])) {
                var saved = new ConfigStore().load();
                assertFalse(saved.recentFiles.isEmpty(), "ordinary opens still persist log recents");
                assertFalse(saved.recentGraphml.isEmpty(), "ordinary opens still persist graph recents");
            }
            var recovery = new SessionResumeStore(new ConfigStore().path().getParent().resolve("sessions"));
            boolean recoverable = args[0].startsWith("personal") || args[0].equals("substituted");
            assertEquals(recoverable, recovery.load(SessionResumeStore.NO_PROJECT).isPresent(),
                    "only an untouched owned diamond is omitted; personal additions and ordinary edited copies are recoverable: "
                            + driver().auditSink().matching("event: FirstMinuteCaptureObserved"));
            System.out.println("PASS " + args[0]);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static Path failureShots;
    private static void first(Path output) throws Exception {
        failureShots = output;
        await(() -> snapshot().walkPlayback().step() == 0 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                20, "the first step is actually shown; " + state());
        edt(() -> {
            var w = snapshot().walkPlayback();
            assertTrue(((SpotlightOverlay) field(frame, "spotlight")).strip().title().contains("bundled commentary"),
                    "shipped commentary is labelled bundled on the real strip");
            assertEquals(WalkPlaybackState.Stage.TOPOLOGY, w.stage(), "the node publishes the centre stage");
            var topology = (TopologyPanel) field(frame, "topologyPanel");
            var source = (SourcePanel) field(frame, "sourcePanel");
            var table = ((LogTablePanel) field(frame, "tablePanel")).table();
            assertTrue(topology.isShowing() && source.isShowing(), "the actual topology and XML are visible together");
            assertTrue(topology.getWidth() > 550, "the topology fills the centre rather than the old side tab: " + topology.getWidth());
            assertTrue(table.getVisibleRect().height >= table.getRowHeight() * 6,
                    "six real record rows fit at 1200x800: " + table.getVisibleRect());
            assertEquals(-1, table.getSelectedRow(), "the declaration-first step begins without a selected record");
            assertTrue(w.targets().stream().allMatch(t -> "CURRENT".equals(t.state())),
                    "the pinned topology and displayed XML match their saved bases");
            assertEveryTargetIsOnScreen();
            String xml = texts(frame.getContentPane());
            assertTrue(xml.contains(telamin.fluxtion.audit.analyser.analyser.firstminute.FirstMinuteLines.VERIFIED)
                    && !xml.contains("Working copy · matched by name · relationship to this run unverified"), "the bundled XML says it is verified by digest: " + xml);
            capture(output.resolve("first-step.png"));
        });
        for (int step = 1; step < REPEAT_STEP; step++) {
            int at = step;
            await(() -> snapshot().walkPlayback().step() == at && "SHOWN".equals(snapshot().walkPlayback().phase()),
                    25, "automatic pacing shows step " + (at + 1) + " in full; " + state());
            edt(() -> {
                var strip = ((SpotlightOverlay) field(frame, "spotlight")).strip();
                assertTrue(strip.position().contains("of 9 · ") && strip.position().contains(
                        snapshot().walkPlayback().definition().steps().get(at).caption()),
                        "the strip names whose work this step shows: " + strip.position());
                assertTrue(snapshot().walkPlayback().targets().stream().allMatch(t -> "CURRENT".equals(t.state()) && t.available()
                                && !t.caption().contains("not compared")),
                        "every target of the shipped step is current and its code caption is checked: " + snapshot().walkPlayback().targets());
                assertEveryTargetIsOnScreen();
                var overlay = (SpotlightOverlay) field(frame, "spotlight");
                Rectangle stripAt = overlay.stripBounds(overlay.getSize());
                for (Rectangle callout : overlay.lastCallouts()) if (callout != null)
                    assertFalse(callout.intersects(stripAt), "step " + (at + 1) + ": no callout is hidden under the strip: " + callout);
                if (snapshot().walkPlayback().definition().steps().get(at).targets().stream().anyMatch(t -> t.target().startsWith("source:java:"))) {
                    String shown = texts(frame.getContentPane());
                    assertTrue(shown.contains(telamin.fluxtion.audit.analyser.analyser.firstminute.FirstMinuteLines.VERIFIED)
                            && !shown.contains("Source/run: unverified"), "the bundled code says it is verified by digest: " + shown);
                }
                capture(output.resolve("step-" + (at + 1) + ".png"));
            });
        }
        await(() -> snapshot().walkPlayback().step() == REPEAT_STEP && "SHOWN".equals(snapshot().walkPlayback().phase()),
                25, "automatic pacing reaches the repeated input; " + state());
        edt(() -> {
            var table = ((LogTablePanel) field(frame, "tablePanel")).table();
            assertEquals(4, table.getSelectedRow(), "the repeated input, not the preceding input, is selected");
            assertEquals("DID_NOT_RUN", execution("d"), "the actual canvas classifies d as not run");
            assertEquals("LOGGED", execution("a"), "the actual canvas lights a");
            capture(output.resolve("repeated-input.png"));
        });
        await(() -> "DOORS".equals(snapshot().firstMinute().phase()), 25, "all nine steps finish at the doors; " + state());
        await(() -> {
            TopologyCanvas canvas = (TopologyCanvas) field(field(frame, "topologyPanel"), "canvas");
            return java.util.List.of("a", "b", "c", "d").stream().allMatch(id -> {
                Rectangle box = canvas.screenBoundsOf(id);
                return box != null && canvas.getVisibleRect().contains(box);
            });
        }, 5, "the end card's layout keeps every authored node inside the resized canvas");
        edt(() -> {
            assertEquals("Click any record", snapshot().firstMinute().callout());
            assertTrue(new ConfigStore().load().firstMinutePlayed, "shown step one, not Esc, persists played");
            capture(output.resolve("doors.png"));
        });
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            // A fresh child JVM cannot reliably become the Mac's key app. Exercise the actual
            // table mouse/selection listeners, not a selection setter or a fabricated session fact.
            // The Xvfb branch below also witnesses delivery from the OS mouse in zero-skip CI.
            edt(() -> {
                JTable table = ((LogTablePanel) field(frame, "tablePanel")).table();
                Rectangle row = table.getCellRect(2, 0, true);
                long at = System.currentTimeMillis();
                for (int kind : new int[]{java.awt.event.MouseEvent.MOUSE_PRESSED,
                        java.awt.event.MouseEvent.MOUSE_RELEASED, java.awt.event.MouseEvent.MOUSE_CLICKED}) {
                    table.dispatchEvent(new java.awt.event.MouseEvent(table, kind, at,
                            kind == java.awt.event.MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON1_DOWN_MASK : 0,
                            row.x + 8, row.y + row.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
                }
            });
        } else {
            var point = new AtomicReference<Point>();
            edt(() -> { frame.toFront(); frame.requestFocus(); });
            await(frame::isFocused, 5, "the test window owns native focus before its one record click");
            edt(() -> {
                JTable table = ((LogTablePanel) field(frame, "tablePanel")).table();
                Rectangle row = table.getCellRect(2, 0, true);
                Point at = new Point(row.x + 8, row.y + row.height / 2);
                SwingUtilities.convertPointToScreen(at, table); point.set(at);
            });
            Robot robot = new Robot();
            robot.mouseMove(point.get().x, point.get().y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        await(() -> {
            JTable table = ((LogTablePanel) field(frame, "tablePanel")).table();
            return table.getSelectedRow() == 2 && "LOGGED".equals(execution("d"));
        }, 5, "one record click through the table's mouse listeners lights that cycle");
    }

    private static void returning(boolean personal) throws Exception {
        await(() -> !"idle".equals(driver().processor().sessionRecovery.state())
                        && !"checking".equals(driver().processor().sessionRecovery.state()),
                15, "returning recovery lookup finishes");
        edt(() -> {
            assertEquals("NONE", snapshot().firstMinute().phase(), "a returning machine never autoplays");
            assertFalse(snapshot().walkPlayback().showing());
            assertFalse(snapshot().logOpen(), "a demo is not implicitly reopened");
            assertEquals(personal, Boolean.TRUE.equals(driver().processor().sessionRecovery.echo().get("available")),
                    "only a person's additions make the diamond recoverable");
            if (personal) assertTrue(driver().processor().sessionRecovery.candidate().view().toString().contains("DEMO first-minute note"),
                    "the offer actually retains the person's finding, not just a filename");
        });
    }

    private static void personal() throws Exception {
        await(() -> snapshot().walkPlayback().step() == 0 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                20, "the first-minute evidence is actually shown before it is annotated");
        edt(() -> {
            var executor = (ActionExecutor) field(frame, "actionExecutor");
            var result = AsyncOpenInterleavingFrameTest.render(executor, "flag",
                    java.util.Map.of("recordIndexes", java.util.List.of(4), "note", "DEMO first-minute note"));
            assertEquals(true, result.get("ok"), "the real flag entrance accepts the finding");
        });
    }

    /** f9d69abb: after the first minute, Audit log ▸ Close log and topology is the start page, not an empty workspace. */
    private static void closeAll(Path output) throws Exception {
        await(() -> snapshot().walkPlayback().step() == 0 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                20, "the first minute is actually shown; " + state());
        edt(() -> {
            assertNotNull(driver().processor().designSession.document(), "control: the diamond's XML is the session's design");
            ((JMenuItem) field(frame, "resetItem")).doClick();
        });
        await(() -> ((StartPanel) field(frame, "startPanel")).isShowing(), 10,
                "Close log and topology after the first minute shows the start page; " + state());
        edt(() -> {
            assertFalse(snapshot().logOpen(), "the log is closed");
            assertNull(driver().processor().designSession.document(), "the design is closed too");
            capture(output.resolve("closed-to-start.png"));
        });
    }

    /** N2: a live spotlight added beside the shipped strip is the assistant's, never "bundled". */
    private static void liveSpotlight() throws Exception {
        await(() -> snapshot().walkPlayback().step() == 0 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                20, "the first step is actually shown; " + state());
        edt(() -> {
            var executor = (ActionExecutor) field(frame, "actionExecutor");
            var result = AsyncOpenInterleavingFrameTest.render(executor, "spotlight", java.util.Map.of(
                    "target", "records:row:5", "caption", "DEMO live callout", "add", true));
            assertEquals(true, result.get("ok"), "the live spotlight is accepted: " + result);
        });
        await(() -> java.util.Arrays.stream(((SpotlightOverlay) field(frame, "spotlight")).getComponents())
                .anyMatch(c -> c.isVisible() && c.getAccessibleContext() != null
                        && "DEMO live callout".equals(c.getAccessibleContext().getAccessibleName())), 5, "the live callout is drawn");
        edt(() -> {
            var overlay = (SpotlightOverlay) field(frame, "spotlight");
            assertTrue(overlay.strip() != null && overlay.strip().title().contains("bundled commentary"),
                    "control: the shipped strip is still up");
            java.util.Map<String, String> described = new java.util.HashMap<>();
            for (var c : overlay.getComponents()) if (c.isVisible() && c.getAccessibleContext() != null)
                described.put(c.getAccessibleContext().getAccessibleName(), String.valueOf(c.getAccessibleContext().getAccessibleDescription()));
            assertTrue(described.get("DEMO live callout").startsWith("assistant commentary"),
                    "N2: the live callout must not carry the bundled label: " + described);
            assertTrue(described.entrySet().stream().anyMatch(e -> !e.getKey().equals("DEMO live callout")
                    && e.getValue().startsWith("bundled commentary")), "control: the walk's own callouts stay bundled: " + described);
        });
    }

    private static void failed(Path output) throws Exception {
        await(() -> "FAILED".equals(snapshot().firstMinute().phase()), 10, "the real extraction failure settles");
        edt(() -> {
            var start = (StartPanel) field(frame, "startPanel");
            var feedback = (JTextArea) field(start, "operationFeedback");
            assertTrue(start.isShowing() && feedback.isShowing(), "the failure is visible on the start page");
            assertEquals(1, feedback.getText().lines().count(), "the failure has exactly one human line");
            assertTrue(feedback.getText().contains("could not be opened"));
            assertTrue(feedback.getText().contains("not a directory"), "the failure line states its cause, not just a filename");
            assertTrue(feedback.getText().contains(snapshot().firstMinute().reason()), "the line carries this open's reason");
            assertFalse(snapshot().walkPlayback().showing(), "a failed extraction starts no hidden walk");
            assertFalse(new ConfigStore().load().firstMinutePlayed, "a failed extraction never marks played");
            for (Window window : Window.getWindows()) assertFalse(window instanceof JDialog && window.isShowing(),
                    "no load-path dialog may block an empty screen");
            capture(output.resolve("failed-extraction.png"));
        });
    }

    private static void substituted(Path output) throws Exception {
        DiamondAssets.install(); // evidence extraction, never generation or an application run
        String text = java.nio.file.Files.readString(DiamondAssets.log());
        var store = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(text);
        String repeated = store.rawText(4);
        assertTrue(repeated.contains("changed: false"), "control: the pinned repeated input");
        String edited = text.replace(repeated, repeated.replace("changed: false", "changed: true"));
        assertNotEquals(text, edited, "control: a changed record, not just a renamed file");
        Path log = java.nio.file.Files.writeString(output.resolve("DEMO-substituted.yaml"), edited);
        edt(() -> frame.openFile(log, OpenRequest.socket(null)));
        await(() -> snapshot().logOpen() && snapshot().total() == 6, 10, "the ordinary edited copy opens");
        edt(() -> {
            var executor = (ActionExecutor) field(frame, "actionExecutor");
            var result = AsyncOpenInterleavingFrameTest.render(executor, "open", java.util.Map.of("graphml", DiamondAssets.graph().toString()));
            assertEquals(true, result.get("ok"));
            assertFalse(snapshot().firstMinute().bundled(), "an ordinary edited copy has no bundled standing");
            // Deliberately bypass E6's earlier admission refusal to attack E5 independently through
            // the real presenter, reader and overlay. Without E5 the graph captions remain available.
            driver().post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkPlayRequested(
                    0, telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition.walk(), REPEAT_STEP, "FIRST_MINUTE"));
        });
        await(() -> !"PREPARING".equals(snapshot().walkPlayback().phase()), 10, "the substituted record's preparation settles");
        edt(() -> {
            assertEquals("NOT_SHOWN", snapshot().walkPlayback().phase(), "a substituted record withholds the entire first-minute claim");
            assertTrue(snapshot().walkPlayback().targets().stream().allMatch(t -> !t.available() && t.caption().isEmpty()),
                    "even a current graph-basis target must lose its caption beside the wrong record");
            assertTrue(((SpotlightOverlay) field(frame, "spotlight")).lit().isEmpty(), "no graph caption survives on the actual overlay");
            capture(output.resolve("withheld-claim.png"));
        });
    }

    /** Owner, 2026-10-27 ("step 5 failed to show the source"): every target of every step is drawn inside its visible pane. */
    private static void assertEveryTargetIsOnScreen() {
        var walk = snapshot().walkPlayback();
        var overlay = (SpotlightOverlay) field(frame, "spotlight");
        assertEquals(walk.targets().size(), overlay.lit().size(), "every target of step " + (walk.step() + 1) + " is lit: " + overlay.lit());
        Rectangle screen = new Rectangle(0, 0, overlay.getWidth(), overlay.getHeight());
        for (var lit : overlay.lit()) {
            assertTrue(lit.bounds().width > 0 && lit.bounds().height > 0 && screen.contains(lit.bounds()),
                    "step " + (walk.step() + 1) + "'s " + lit.target() + " is on screen: " + lit.bounds());
            if (lit.target().startsWith("topology:node:")) {
                TopologyCanvas canvas = (TopologyCanvas) field(field(frame, "topologyPanel"), "canvas");
                Rectangle box = canvas.screenBoundsOf(lit.target().substring("topology:node:".length()));
                Rectangle at = SwingUtilities.convertRectangle(canvas, box, overlay);
                assertTrue(box != null && lit.bounds().intersects(at),
                        "step " + (walk.step() + 1) + "'s " + lit.target() + " is lit ON its box (" + at + "), not where it was: " + lit.bounds());
            }
            if (lit.target().startsWith("source:")) {
                var source = (SourcePanel) field(frame, "sourcePanel");
                Rectangle pane = SwingUtilities.convertRectangle(source, new Rectangle(0, 0, source.getWidth(), source.getHeight()), overlay);
                assertTrue(source.isShowing() && pane.intersects(lit.bounds()),
                        "step " + (walk.step() + 1) + "'s code target is in the visible source pane: " + lit.target());
            }
        }
    }

    /** Every visible text in a component tree, for provenance wording checks. */
    private static String texts(Container root) {
        StringBuilder out = new StringBuilder();
        for (Component c : root.getComponents()) {
            if (!c.isShowing()) continue;
            if (c instanceof JLabel l) out.append(l.getText()).append('\n');
            if (c instanceof javax.swing.text.JTextComponent t) out.append(t.getText()).append('\n');
            if (c instanceof Container k) out.append(texts(k));
        }
        return out.toString();
    }

    private static String execution(String id) {
        TopologyCanvas canvas = (TopologyCanvas) field(field(frame, "topologyPanel"), "canvas");
        return String.valueOf(canvas.executionOf(id));
    }
    private static SessionDriver driver() { return (SessionDriver) field(frame, "session"); }
    private static SessionSnapshot snapshot() { return driver().snapshot(); }
    private static String state() {
        var result = new AtomicReference<String>();
        try { edt(() -> {
            var walk = snapshot().walkPlayback();
            var table = ((LogTablePanel) field(frame, "tablePanel")).table();
            result.set(snapshot().firstMinute() + " / " + walk.phase() + " step=" + walk.step() + " " + walk.reason()
                    + " / selected=" + table.getSelectedRow() + " d=" + execution("d")
                    + " / firstRun=" + field(frame, "firstRunAtStart")
                    + " / walk transitions=" + transitions
                    + " / advances=" + driver().auditSink().matching("event: WalkAdvanceDue").stream()
                            .map(r -> r.replaceAll("(?s).*?(ticket: \\S+).*?(step: \\S+).*", "$1 $2")).toList()
                    + " / navigations=" + driver().auditSink().matching("event: WalkNavigated").size()
                    + " / schedules=" + driver().auditSink().matching("ScheduleWalkAdvanceEffect").size());
        }); }
        catch (Exception failure) { return failure.toString(); }
        return result.get();
    }
    private static void capture(Path output) {
        var image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { frame.paintAll(graphics); ImageIO.write(image, "png", output.toFile()); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        finally { graphics.dispose(); }
    }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void edt(Runnable run) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) run.run(); else SwingUtilities.invokeAndWait(run);
    }
    private static void await(BooleanSupplier condition, int seconds, String why) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            boolean[] ready = {false}; edt(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            Thread.sleep(30);
        }
        if (failureShots != null) try { edt(() -> capture(failureShots.resolve("failure.png"))); } catch (Exception ignored) { }
        fail(why + " — settled at " + state());
    }
}
