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
            switch (args[0]) {
                case "first" -> first(Path.of(args[1]));
                case "returning" -> returning(false);
                case "personal" -> personal();
                case "personal-returning" -> returning(true);
                case "substituted" -> substituted(Path.of(args[1]));
                case "failed" -> failed(Path.of(args[1]));
                default -> fail("unknown isolated probe");
            }
            edt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
            assertTrue(closed.await(10, TimeUnit.SECONDS), "the actual close sequence finishes");
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

    private static void first(Path output) throws Exception {
        await(() -> snapshot().walkPlayback().step() == 0 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                20, "the first step is actually shown; " + state());
        edt(() -> {
            var w = snapshot().walkPlayback();
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
            capture(output.resolve("first-step.png"));
        });
        await(() -> snapshot().walkPlayback().step() == 2 && "SHOWN".equals(snapshot().walkPlayback().phase()),
                25, "automatic pacing reaches step three; " + state());
        edt(() -> {
            var table = ((LogTablePanel) field(frame, "tablePanel")).table();
            assertEquals(4, table.getSelectedRow(), "the repeated input, not the preceding input, is selected");
            assertEquals("DID_NOT_RUN", execution("d"), "the actual canvas classifies d as not run");
            assertEquals("LOGGED", execution("a"), "the actual canvas lights a");
            capture(output.resolve("repeated-input.png"));
        });
        await(() -> "DOORS".equals(snapshot().firstMinute().phase()), 25, "all four steps finish at the doors; " + state());
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
                    0, telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition.walk(), 2, "FIRST_MINUTE"));
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
                    + " / startup=" + driver().auditSink().matching("event: FirstMinuteStartupObserved"));
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
        fail(why + " — settled at " + state());
    }
}
