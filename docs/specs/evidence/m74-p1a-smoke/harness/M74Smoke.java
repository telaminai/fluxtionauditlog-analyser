package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentLock;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * M74 P1a live smoke: drives an ISOLATED analyser's own Assistant-tab path (the real buttons, the real performer, the
 * real adapter and bridge) against the person's installed, logged-in claude. Run with -Duser.home and -Djava.io.tmpdir
 * under the smoke folder, with only the smoke jar on the classpath.
 */
public final class M74Smoke {

    static PrintWriter log;
    static MainFrame frame;
    static long t0 = System.currentTimeMillis();

    static void ev(String s) {
        String line = String.format("[%7.1fs] %s", (System.currentTimeMillis() - t0) / 1000.0, s);
        System.out.println(line);
        log.println(line);
        log.flush();
    }

    static Object field(String name) throws Exception {
        Field f = MainFrame.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(frame);
    }

    static LocalAgentState st() throws Exception {
        return ((SessionDriver) field("session")).snapshot().localAgent();
    }

    static LocalAgentTranscript store() throws Exception {
        return (LocalAgentTranscript) field("localAgentStore");
    }

    static LocalAgentPanel panel() throws Exception {
        return (LocalAgentPanel) field("localAgentPanel");
    }

    static AssistantPanel assistant() throws Exception {
        return (AssistantPanel) field("assistantPanel");
    }

    static boolean waitFor(String what, long ms, Predicate<LocalAgentState> p) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (p.test(st())) return true;
            Thread.sleep(50);
        }
        ev("TIMEOUT waiting for " + what + " — phase=" + st().phase() + " verdict=" + st().verdict());
        return false;
    }

    static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void click(String which) throws Exception {
        edt(() -> {
            try {
                switch (which) {
                    case "claude" -> assistant().claudeModeButton().doClick();
                    case "inapp" -> {
                        var f = AssistantPanel.class.getDeclaredField("inAppMode");
                        f.setAccessible(true);
                        ((javax.swing.JRadioButton) f.get(assistant())).doClick();
                    }
                    case "start" -> panel().startButton().doClick();
                    case "stop" -> panel().stopButton().doClick();
                    default -> throw new IllegalArgumentException(which);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ev("click " + which);
    }

    static void send(String text) throws Exception {
        edt(() -> {
            try {
                panel().composerArea().setText(text);
                var b = LocalAgentPanel.class.getDeclaredMethod("sendButton");
                b.setAccessible(true);
                // the Send button is disabled while busy; press it as the person would, or post as Enter does
                var send = (javax.swing.JButton) b.invoke(panel());
                if (send.isEnabled()) send.doClick();
                else {
                    var m = LocalAgentPanel.class.getDeclaredMethod("onSend");
                    m.setAccessible(true);
                    m.invoke(panel());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ev("send " + quote(text));
    }

    static String quote(String s) {
        return "\"" + s.replace(" ", "<NBSP>").replace("\n", "\\n") + "\"";
    }

    static void dumpEntries(String label) throws Exception {
        var s = st();
        ev("ENTRIES " + label + " (phase=" + s.phase() + " verdict=" + s.verdict() + " gen=" + s.generation()
                + " admitted=" + s.admittedGeneration() + " spent=" + s.spentMicros() + " busy=" + s.busy() + ")");
        for (var e : s.entries()) {
            String text = e.id() == 0 ? e.detail() : store().text(e.id());
            ev("   " + e.kind() + "#" + e.ticket() + " [" + e.status() + "] " + quote(text.length() > 600
                    ? text.substring(0, 600) + "…" : text));
        }
        ev("   answer=" + s.answer());
    }

    static List<ProcessHandle> agentTree() {
        return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).toList();
    }

    static void dumpTree(String label) {
        var tree = agentTree();
        ev("TREE " + label + ": " + tree.size() + " live descendant(s)");
        for (var h : tree) {
            String cmd = h.info().command().orElse("?");
            String args = String.join(" ", h.info().arguments().orElse(new String[0]));
            ev("   pid " + h.pid() + " " + cmd.substring(Math.max(0, cmd.lastIndexOf('/') + 1)) + " "
                    + (args.length() > 900 ? args.substring(0, 900) + "…" : args));
        }
    }

    static String sessionArg() {
        for (var h : agentTree()) {
            String[] a = h.info().arguments().orElse(new String[0]);
            for (int i = 0; i + 1 < a.length; i++) {
                if (a[i].equals("--session-id") || a[i].equals("--resume")) return a[i] + " " + a[i + 1];
            }
        }
        return "(no claude process)";
    }

    static boolean idle(LocalAgentState s) {
        return !s.busy() && "READY".equals(s.phase());
    }

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/smoke-events.txt")));
        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.assistantActionsRest = true;
        c.claudeCodeModel = "haiku";
        c.claudeCodeBudgetUsd = 1.20;           // node-enforced session total for this run (hard cap is US$2 overall)
        cs.save(c);
        ev("user.home=" + System.getProperty("user.home") + " tmpdir=" + System.getProperty("java.io.tmpdir"));

        edt(() -> {
            frame = new MainFrame();
            frame.setVisible(true);
            frame.openFile(smoke.resolve("sample.yml"));
        });
        long deadline = System.currentTimeMillis() + 20_000;
        while (!((SessionDriver) field("session")).snapshot().logOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        ev("log open=" + ((SessionDriver) field("session")).snapshot().logOpen());

        // ---- (7) M2 and (1) the handshake: Start, kill before any Send, Start again --------------------------------
        click("claude");
        long s1 = System.currentTimeMillis();
        click("start");
        boolean r1 = waitFor("READY #1", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev("START#1 " + (r1 ? "phase=" + st().phase() + " verdict=" + st().verdict() : "") + " cold start ms="
                + (System.currentTimeMillis() - s1) + " " + sessionArg());
        dumpTree("after start #1");
        String first = sessionArg();
        click("inapp");                                        // kills it: a stop before the first Send
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        dumpTree("after the stop before the first Send");
        click("claude");
        long s2 = System.currentTimeMillis();
        click("start");
        waitFor("READY #2", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        String second = sessionArg();
        ev("START#2 phase=" + st().phase() + " verdict=" + st().verdict() + " cold start ms="
                + (System.currentTimeMillis() - s2) + " first=" + first + " second=" + second
                + " M2 " + (second.startsWith("--session-id") && !second.equals(first) ? "PASS" : "FAIL"));
        // a stop WHILE starting, then start again
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        click("claude");
        click("start");
        Thread.sleep(300);
        ev("phase while starting=" + st().phase());
        click("stop");
        waitFor("OFF after stop-while-starting", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        click("start");
        waitFor("READY #3", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        String third = sessionArg();
        ev("START#3 phase=" + st().phase() + " " + third + " M2(stop-while-starting) "
                + (third.startsWith("--session-id") ? "PASS" : "FAIL"));
        var h = store().header(st().headerRef());
        ev("HEADER " + h);
        dumpEntries("after handshakes");

        // ---- (2) a turn using the verbs ------------------------------------------------------------------------------
        send("Call the analyser_context tool, then call analyser_read for the first 3 records of the open log. Then "
                + "answer in two short sentences: how many records does the log have, and what is the first record's "
                + "event type?");
        waitFor("turn 1", 120_000, M74Smoke::idle);
        dumpEntries("after turn 1 (verbs)");

        // ---- (3) a destructive verb ----------------------------------------------------------------------------------
        send("Use the analyser_open tool to open the log file /tmp/other-log.yml. If you cannot, reply with one sentence "
                + "saying exactly why.");
        waitFor("turn 2", 120_000, M74Smoke::idle);
        dumpEntries("after turn 2 (destructive)");

        // ---- (4) blocked slash commands, locally ---------------------------------------------------------------------
        int users = (int) st().entries().stream().filter(e -> LocalAgentState.USER.equals(e.kind())).count();
        for (String typed : List.of("/config", "/clear", " /config", "  /config", "!echo hi", "# note", "@x.txt hi")) {
            send(typed);
            Thread.sleep(200);
            ev("   → verdict=" + st().verdict() + " answer=" + st().answer().reason());
        }
        int usersAfter = (int) st().entries().stream().filter(e -> LocalAgentState.USER.equals(e.kind())).count();
        ev("SLASH user turns before=" + users + " after=" + usersAfter + " " + (users == usersAfter ? "PASS" : "FAIL"));

        // ---- (5) busy, (6) Stop mid-turn then Send in the same session -------------------------------------------------
        String beforeStop = sessionArg();
        send("Count from 1 to 400, one number per line. Do not stop early.");
        waitFor("busy", 20_000, LocalAgentState::busy);
        Thread.sleep(400);
        send("hello while busy");
        Thread.sleep(200);
        ev("BUSY verdict=" + st().verdict() + " answer=" + st().answer().reason() + " "
                + ("BUSY".equals(st().verdict()) ? "PASS" : "FAIL"));
        Thread.sleep(2500);
        long stopAt = System.currentTimeMillis();
        click("stop");
        waitFor("turn stopped", 30_000, x -> !x.busy());
        ev("STOP ended in ms=" + (System.currentTimeMillis() - stopAt) + " phase=" + st().phase());
        dumpEntries("after stop");
        send("Reply with exactly: resumed-ok");
        waitFor("turn after stop", 120_000, M74Smoke::idle);
        ev("SAME PROCESS after stop: before=" + beforeStop + " now=" + sessionArg());
        dumpEntries("after the send following stop");

        // ---- kill and resume by id ----------------------------------------------------------------------------------
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        click("claude");
        click("start");
        waitFor("READY resume", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        String resumed = sessionArg();
        ev("RESUME " + resumed + " (was " + beforeStop + ") " + (resumed.startsWith("--resume")
                && resumed.substring(9).equals(beforeStop.substring(beforeStop.indexOf(' ') + 1)) ? "PASS" : "FAIL"));
        send("Reply with exactly: after-resume");
        waitFor("turn after resume", 120_000, M74Smoke::idle);
        dumpEntries("after resume");

        // ---- (9) a second analyser instance cannot take this session's lock ----------------------------------------
        Path lockDir = LocalAgentLock.directory(Path.of(System.getProperty("user.home")));
        try (var locks = Files.list(lockDir)) {
            for (Path p : locks.filter(x -> x.toString().endsWith(".lock")).toList()) {
                String id = p.getFileName().toString().replace(".lock", "");
                Process other = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("java.class.path"), M74Smoke.class.getName() + "$LockProbe",
                        lockDir.toString(), id).redirectErrorStream(true).start();
                String out = new String(other.getInputStream().readAllBytes()).trim();
                other.waitFor();
                ev("LOCK second instance on " + (resumed.contains(id) ? "the live session" : "an old session") + ": "
                        + out);
            }
        }

        // ---- (8) after stop, nothing remains -----------------------------------------------------------------------
        dumpTree("before final stop");
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(4000);
        dumpTree("after final stop");
        ev("TREE " + (agentTree().isEmpty() ? "PASS" : "FAIL"));
        ev("SPENT micros=" + st().spentMicros() + " under-reported=" + st().costUnderReported());
        edt(() -> frame.dispose());
        log.close();
        System.exit(0);
    }

    /** The second analyser instance: try to take a session's lock. */
    public static final class LockProbe {
        public static void main(String[] a) throws Exception {
            var lock = LocalAgentLock.tryAcquire(Path.of(a[0]), a[1]);
            System.out.println(lock.isPresent() ? "ACQUIRED (not held)" : "LOCKED — refused");
            lock.ifPresent(LocalAgentLock::close);
        }
    }
}
