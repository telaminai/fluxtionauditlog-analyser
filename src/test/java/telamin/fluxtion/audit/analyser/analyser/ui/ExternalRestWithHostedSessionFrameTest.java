package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Holistic review B8 (2026-10-17; adopted from the reviewer's H4 scratch test): external REST ON plus a hosted Claude Code
 * session in the REAL frame, with a FAKE process ({@link FixtureAgent}) — no live {@code claude}. External clients keep
 * working with the same url and token while the session runs; switching REST off mid-session refuses them and keeps the
 * session's own connection; switching it back gives the same url and token; and when the session ends the person's
 * REST server stays and the session's hosted credential is revoked. Since review B5 the switch is a FACT
 * ({@code reportRestSwitch}) and the {@code actionListener} node decides the listener.
 */
class ExternalRestWithHostedSessionFrameTest {

    static final String DIALOG_TITLE = "Claude Code — confirm this folder";

    @Test
    @DisplayName("external REST on + a hosted Claude Code session: external clients keep working, with the same url and token")
    void externalRestWithHostedSession(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        Path log = Files.copy(Path.of("src/main/resources/demo/demo-quote-audit.yaml"), tmp.resolve("proj/demo.yaml"));
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true);
                    show.invoke(f, false);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                made[0] = f;
            });
            MainFrame f = made[0];
            ActionExecutor ex = (ActionExecutor) field(f, "actionExecutor");
            onEdt(() -> { f.setSize(1200, 800); f.setVisible(true); });
            onEdt(() -> {
                AppConfig cfg = (AppConfig) field(f, "config");
                cfg.claudeCodeAllowed = true;
                cfg.assistantActionsRest = true;
                invoke(f, "reportRestSwitch");
            });
            Path wk = home.resolve(".fluxtion-analyser/rest-endpoint");
            assertTrue(Files.exists(wk), "REST on: well-known file");
            var ep0 = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            onEdt(() -> render(ex, "open", Map.of("log", log.toString())));
            Thread.sleep(2500);
            String agg = "{\"action\":\"aggregate\",\"params\":{\"metric\":\"count\"}}";
            var r0 = post(http, ep0.url(), agg, ep0.token(), null);
            assertEquals(200, r0.statusCode());
            Object server0 = onEdtGet(() -> field(f, "actionServer"));

            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));
            awaitState(f, s -> s.mode() == telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT, "CC");
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog dialog = awaitDialog(DIALOG_TITLE);
            onEdt(findButton(dialog, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            LocalAgentState ready = awaitState(f, s -> "READY".equals(s.phase()), "READY after Yes");
            assertEquals("BOUND", ready.verdict());
            assertSame(server0, onEdtGet(() -> field(f, "actionServer")), "the same server serves the hosted bridge");
            var ep1 = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            assertEquals(ep0.url(), ep1.url()); assertEquals(ep0.token(), ep1.token());
            var r1 = post(http, ep1.url(), agg, ep1.token(), null);
            assertEquals(r0.body(), r1.body(), "the same answer while the hosted session runs");
            var rp = post(http, ep1.url(), "{\"action\":\"propose\",\"params\":{}}", ep1.token(), null);
            assertTrue(rp.body().contains("unknown verb"));
            onEdt(() -> {
                ((JTextArea) field(panel, "draft")).setText("Reply with exactly: one");
                ((JButton) field(panel, "send")).doClick();
            });
            awaitState(f, s -> "OK".equals(s.verdict()), "posture checked");
            awaitState(f, s -> s.openTicket() == 0, "turn ended");
            Path hostedFile;
            try (var st = Files.walk(home)) {
                hostedFile = st.filter(p -> p.getFileName().toString().matches("endpoint-.*\\.json")).findFirst().orElseThrow();
            }
            var he = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(hostedFile).read();
            var rh = post(http, he.url(), agg, null, he.hosted());
            assertEquals(200, rh.statusCode());
            var rhp = post(http, he.url(), "{\"action\":\"open\",\"params\":{\"close\":\"log\"}}", null, he.hosted());
            assertNotEquals(200, rhp.statusCode());
            var rx = post(http, ep1.url(), "{\"action\":\"open\",\"params\":{\"close\":\"log\"}}", ep1.token(), he.hosted());

            // REST switched off mid-session: external refused, hosted continues
            onEdt(() -> { ((AppConfig) field(f, "config")).assistantActionsRest = false; invoke(f, "reportRestSwitch"); });
            assertFalse(Files.exists(wk), "file withdrawn");
            var r2 = post(http, ep1.url(), agg, ep1.token(), null);
            assertEquals(403, r2.statusCode());
            assertEquals(200, post(http, he.url(), agg, null, he.hosted()).statusCode());
            // REST back on mid-session: same url/token
            onEdt(() -> { ((AppConfig) field(f, "config")).assistantActionsRest = true; invoke(f, "reportRestSwitch"); });
            var ep3 = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            assertEquals(ep0.token(), ep3.token()); assertEquals(ep0.url(), ep3.url());
            assertEquals(200, post(http, ep3.url(), agg, ep3.token(), null).statusCode());

            // New chat ends the session: the person's REST server stays, same token, hosted credential revoked
            onEdt(() -> ((JButton) field(panel, "newChat")).doClick());
            awaitState(f, s -> "OFF".equals(s.phase()), "OFF");
            Thread.sleep(500);
            assertSame(server0, onEdtGet(() -> field(f, "actionServer")), "server not restarted");
            var ep4 = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            assertNotNull(ep4, "well-known file still there after the session ends");
            assertEquals(ep0.token(), ep4.token());
            var r4 = post(http, ep4.url(), agg, ep4.token(), null);
            assertEquals(r0.body(), r4.body());
            var r5 = post(http, he.url(), agg, null, he.hosted());
            assertEquals(403, r5.statusCode());
        } finally {
            System.setProperty("user.home", before);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    static java.net.http.HttpResponse<String> post(java.net.http.HttpClient http, String url, String body, String token,
                                                   String hosted) throws Exception {
        Thread.sleep(150);
        var b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url + "/action"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("X-Analyser-Token", token);
        if (hosted != null) b.header("X-Analyser-Hosted", hosted);
        return http.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    /**
     * External utility review (owner-approved 2026-10-17): a normal window close removes {@code ~/.fluxtion-analyser/
     * rest-endpoint}; only a crash or a kill leaves it (readers check its pid). The close runs the frame's real exit
     * sequence, with only the final process exit replaced. Witness: drop the {@code deleteIfOwnedByThisProcess} step from
     * {@code finishExit} and the endpoint delete from {@code ActionServer.stop} — the file is still there and the last
     * assertion fails.
     */
    @Test
    @DisplayName("★ closing the window removes the REST endpoint file it published")
    void closingTheWindowRemovesTheEndpointFile(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        java.util.concurrent.CountDownLatch exited = new java.util.concurrent.CountDownLatch(1);
        int[] code = {-1};
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.exitProcess = c -> { code[0] = c; exited.countDown(); };
                made[0] = f;
            });
            MainFrame f = made[0];
            onEdt(() -> { f.setSize(1000, 700); f.setVisible(true); });
            onEdt(() -> {
                ((AppConfig) field(f, "config")).assistantActionsRest = true;
                invoke(f, "reportRestSwitch");
            });
            Path wk = home.resolve(".fluxtion-analyser/rest-endpoint");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(wk) && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(Files.exists(wk), "control: REST on publishes the endpoint file");
            assertEquals(ProcessHandle.current().pid(), new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk)
                    .read().pid(), "this process published it");
            onEdt(() -> f.dispatchEvent(new java.awt.event.WindowEvent(f, java.awt.event.WindowEvent.WINDOW_CLOSING)));
            assertTrue(exited.await(20, TimeUnit.SECONDS), "the close ran its exit sequence to the end");
            assertEquals(0, code[0]);
            assertFalse(Files.exists(wk), "a normal window close removes the endpoint file");
        } finally {
            System.setProperty("user.home", before);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    static void invoke(Object o, String m) {
        try {
            var meth = o.getClass().getDeclaredMethod(m);
            meth.setAccessible(true);
            meth.invoke(o);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    static Object onEdtGet(java.util.concurrent.Callable<Object> c) throws Exception {
        AtomicReference<Object> r = new AtomicReference<>();
        onEdt(() -> { try { r.set(c.call()); } catch (Exception e) { throw new RuntimeException(e); } });
        return r.get();
    }

    static LocalAgentState state(MainFrame f) throws Exception {
        AtomicReference<LocalAgentState> s = new AtomicReference<>();
        onEdt(() -> s.set(((SessionDriver) field(f, "session")).snapshot().localAgent()));
        return s.get();
    }

    static LocalAgentState awaitState(MainFrame f, Predicate<LocalAgentState> done, String what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        LocalAgentState s;
        do {
            s = state(f);
            if (done.test(s)) return s;
            Thread.sleep(40);
        } while (System.nanoTime() < until);
        throw new AssertionError(what + ": phase " + s.phase() + ", verdict " + s.verdict() + ", reason " + s.reason());
    }

    static JDialog awaitDialog(String title) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) return d;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("no dialog titled " + title);
    }

    static AbstractButton findButton(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof Container inner) {
                AbstractButton found = findButton(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
