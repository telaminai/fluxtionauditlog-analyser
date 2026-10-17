package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Window;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static telamin.fluxtion.audit.analyser.analyser.ui.M74P1bSmoke.*;

/**
 * M74 P1b review re-smoke: the items the 14 fixes touch, and the warm-start item the first smoke could not reach —
 * the hosted bridge authenticating with its credential alone (finding 6), Settings applied while a session runs
 * (finding 4: withdrawn, changed while idle, changed mid-turn), a hook planted in the scratch folder (finding 3), and
 * warm start with the full switch on (§8.1 item 8), with its default-posture control. Same isolation and wrapper as
 * {@link M74P1bSmoke}; no project is opened, so Claude Code works in the analyser's scratch folder.
 */
public final class M74P1bFixSmoke {

    static void applySettings(String what) throws Exception {
        edt(() -> {
            try {
                Method m = MainFrame.class.getDeclaredMethod("onConfigChanged");
                m.setAccessible(true);
                m.invoke(frame);                    // what Settings ▸ OK calls in the application
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ev("SETTINGS applied: " + what);
    }

    static void dismissStrayDialogs() throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && d.isShowing()) {
                ev("dismissing a dialog the run did not ask for: \"" + d.getTitle() + "\"");
                edt(d::dispose);
            }
        }
    }

    static void startReady(String what) throws Exception {
        click("start");
        waitFor(what, 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev(what + ": phase=" + st().phase() + " verdict=" + st().verdict() + " " + sessionArg());
    }

    static void stopAll() throws Exception {
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        click("claude");
    }

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/fix-smoke-events.txt")));
        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.claudeCodeFull = true;
        c.claudeCodeFullMode = "default";
        c.assistantActionsRest = true;
        c.claudeCodeModel = "haiku";
        c.claudeCodeBudgetUsd = 2.0;              // a runaway guard
        cs.save(c);
        edt(() -> {
            frame = new MainFrame();
            frame.setVisible(true);
            frame.openFile(smoke.resolve("sample.yml"));
        });
        long deadline = System.currentTimeMillis() + 20_000;
        while (!((SessionDriver) field("session")).snapshot().logOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Thread.sleep(1500);
        dismissStrayDialogs();
        ev("log open, no project: the scratch folder");

        // ---- finding 6 live: the hosted bridge on the hosted credential alone ----------------------------------------
        click("claude");
        String dialog = startAnswering(null);
        waitFor("READY", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev("EMPTY SCRATCH: no dialog=" + (dialog == null) + " phase=" + st().phase() + " " + sessionArg());
        Path lockDir = Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "local-agent");
        try (var files = Files.list(lockDir)) {
            for (Path p : files.filter(x -> x.getFileName().toString().startsWith("endpoint-")).toList()) {
                String text = Files.readString(p);
                ev("HOSTED FILE " + p.getFileName() + " keys=" + text.replaceAll("\"[0-9a-f]{16,}\"", "\"<hex>\"")
                        .replaceAll("\"http://127.0.0.1:[0-9]+\"", "\"<url>\"").replaceAll("\"startedAt\":\"[^\"]*\"",
                                "\"startedAt\":\"<t>\"") + " has token=" + text.contains("\"token\""));
            }
        }
        send("Call the analyser_context tool, then analyser_read for the first 2 records. Then answer in one sentence: how "
                + "many records does the open log have?");
        waitFor("verbs turn", 150_000, M74P1bSmoke::idle);
        dumpEntries("verbs over the hosted credential alone");

        // ---- finding 4 live: a changed posture while idle ends the session at once ------------------------------------
        config().claudeCodeFullMode = "plan";
        applySettings("full mode default → plan, idle");
        Thread.sleep(1500);
        ev("CHANGED WHILE IDLE: phase=" + st().phase() + " verdict=" + st().verdict() + " reason=\"" + st().reason()
                + "\" tree=" + tree().size() + " " + ("OFF".equals(st().phase()) && "POSTURE_CHANGED".equals(st().verdict())
                ? "PASS" : "FAIL"));
        Thread.sleep(2500);
        click("newchat");
        startReady("START under plan");

        // ---- finding 4 live: changed mid-turn ends it after the turn -------------------------------------------------
        send("Count from 1 to 120, one number per line.");
        waitFor("busy", 30_000, LocalAgentState::busy);
        config().claudeCodeFullMode = "default";
        applySettings("full mode plan → default, mid-turn");
        Thread.sleep(300);
        ev("MID-TURN right after: phase=" + st().phase() + " busy=" + st().busy() + " (the turn goes on)");
        waitFor("turn end", 150_000, x -> !x.busy());
        Thread.sleep(1500);
        ev("MID-TURN after the turn: phase=" + st().phase() + " verdict=" + st().verdict() + " " + ("OFF".equals(st().phase())
                ? "PASS" : "FAIL"));
        dumpEntries("after the mid-turn change");
        Thread.sleep(2500);

        // ---- finding 4 live: withdrawn ends it at once, even mid-turn ------------------------------------------------
        click("newchat");
        startReady("START for withdrawal");
        send("Count from 1 to 200, one number per line.");
        waitFor("busy", 30_000, LocalAgentState::busy);
        config().claudeCodeAllowed = false;
        applySettings("Allow the analyser to start Claude Code → off, mid-turn");
        Thread.sleep(800);
        ev("WITHDRAWN: phase=" + st().phase() + " verdict=" + st().verdict() + " " + ("OFF".equals(st().phase())
                && "SWITCHED_OFF".equals(st().verdict()) ? "PASS" : "FAIL"));
        Thread.sleep(3000);
        dumpTree("after withdrawal");
        config().claudeCodeAllowed = true;
        applySettings("Allow back on (nothing runs)");

        // ---- finding 3 live: a hook planted in the scratch folder is asked about ---------------------------------------
        Path scratch = (Path) field("localAgentScratch");
        Files.createDirectories(scratch.resolve(".claude"));
        Files.writeString(scratch.resolve(".claude/settings.json"), "{\"hooks\": {\"SessionStart\": [{\"hooks\": "
                + "[{\"type\": \"command\", \"command\": \"echo planted >> " + smoke.resolve("raw/scratch-hook.txt") + "\"}]}]}}");
        click("newchat");
        String planted = startAnswering("Don't trust");
        Thread.sleep(1500);
        ev("SCRATCH HOOK: asked=" + (planted != null) + " names it=" + (planted != null
                && planted.contains("SessionStart (1)")) + " after No: phase=" + st().phase() + " hook ran="
                + Files.exists(smoke.resolve("raw/scratch-hook.txt")) + " " + (planted != null && "OFF".equals(st().phase())
                && !Files.exists(smoke.resolve("raw/scratch-hook.txt")) ? "PASS" : "FAIL"));
        Files.delete(scratch.resolve(".claude/settings.json"));
        Files.delete(scratch.resolve(".claude"));

        // ---- §8.1 item 8: warm start with the full switch on starts nothing -------------------------------------------
        dismissStrayDialogs();
        config().claudeCodeWarmStart = true;
        boolean full = warmClick();
        ev("WARM full posture: started=" + full + " " + (!full ? "PASS" : "FAIL"));
        config().claudeCodeFull = false;            // control: the default posture in the confirmed scratch folder
        applySettings("full switch off (control)");
        click("newchat");
        dismissStrayDialogs();
        boolean def = warmClick();
        ev("WARM control, default posture: started=" + def + " — the click " + (def ? "is" : "was NOT") + " the person's "
                + "direct selection");
        waitFor("control ready", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        stopAll();
        Thread.sleep(3000);
        dumpTree("after the final stop");
        ev("TREE " + (tree().isEmpty() ? "PASS" : "FAIL"));
        ev("SPENT micros=" + st().spentMicros() + " under-reported=" + st().costUnderReported() + " billing=" + st().billing());
        edt(() -> frame.dispose());
        log.close();
        System.exit(0);
    }

    static boolean warmClick() throws Exception {
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) field("sideTabs");
        Component dock = (Component) field("assistantDock");
        edt(() -> {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tabs.getComponentAt(i) != dock) {
                    tabs.setSelectedIndex(i);
                    break;
                }
            }
        });
        Thread.sleep(400);
        java.awt.Rectangle[] r = new java.awt.Rectangle[1];
        edt(() -> r[0] = tabs.getBoundsAt(tabs.indexOfComponent(dock)));
        if (r[0] == null) {
            ev("WARM click: the Assistant tab has no bounds (not shown) — not clicked");
            return false;
        }
        boolean[] before = new boolean[1];
        edt(() -> before[0] = tabs.getSelectedComponent() == dock);
        ev("WARM click: before the click the assistant tab is selected=" + before[0]);
        long gen = st().generation();
        var q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();
        int x = r[0].x + r[0].width / 2;
        int y = r[0].y + r[0].height / 2;
        q.postEvent(new java.awt.event.MouseEvent(tabs, java.awt.event.MouseEvent.MOUSE_PRESSED, now,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        q.postEvent(new java.awt.event.MouseEvent(tabs, java.awt.event.MouseEvent.MOUSE_RELEASED, now + 5, 0, x, y, 1,
                false, java.awt.event.MouseEvent.BUTTON1));
        Thread.sleep(4000);
        ev("WARM click: assistant tab selected=" + (tabs.getSelectedComponent() == dock) + " generation " + gen + " → "
                + st().generation() + " phase=" + st().phase());
        return st().generation() != gen;
    }

    @SuppressWarnings("unused")
    private static final Class<?> KEEP = JOptionPane.class;
}
