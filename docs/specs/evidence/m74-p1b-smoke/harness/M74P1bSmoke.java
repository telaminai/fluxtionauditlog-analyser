package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * M74 P1b live smoke (spec §8.1): drives an ISOLATED analyser's own Settings and Assistant-tab path — the real dialogs,
 * buttons, performer, adapter and bridge — against the person's installed, logged-in claude in the FULL posture. Run with
 * -Duser.home and -Djava.io.tmpdir under the smoke folder, the smoke jar only on the classpath, and PATH starting with
 * the smoke's bin/ folder, whose `claude` wrapper adds --strict-mcp-config (OD-74.18: the harness's addition, never the
 * analyser's), so none of the person's MCP servers or connectors loads into the capture.
 */
public final class M74P1bSmoke {

    static PrintWriter log;
    static MainFrame frame;
    static final long T0 = System.currentTimeMillis();

    static void ev(String s) {
        String line = String.format("[%7.1fs] %s", (System.currentTimeMillis() - T0) / 1000.0, s);
        System.out.println(line);
        log.println(line);
        log.flush();
    }

    static Object field(String name) throws Exception {
        Field f = MainFrame.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(frame);
    }

    static AppConfig config() throws Exception {
        return (AppConfig) field("config");
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

    static void later(Runnable r) {
        SwingUtilities.invokeLater(r);
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
                    case "start" -> later(() -> {
                        try {
                            panel().startButton().doClick();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    case "stop" -> panel().stopButton().doClick();
                    case "newchat" -> {
                        var f = LocalAgentPanel.class.getDeclaredField("newChat");
                        f.setAccessible(true);
                        ((javax.swing.JButton) f.get(panel())).doClick();
                    }
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
                var m = LocalAgentPanel.class.getDeclaredMethod("onSend");
                m.setAccessible(true);
                m.invoke(panel());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ev("send \"" + text + "\"");
    }

    static boolean idle(LocalAgentState s) {
        return !s.busy() && "READY".equals(s.phase());
    }

    static void dumpEntries(String label) throws Exception {
        var s = st();
        ev("ENTRIES " + label + " (phase=" + s.phase() + " verdict=" + s.verdict() + " gen=" + s.generation()
                + " admitted=" + s.admittedGeneration() + " posture=" + s.posture() + " servers=" + s.personServers()
                + " billing=" + s.billing() + ")");
        for (var e : s.entries()) {
            String text = e.id() == 0 ? e.detail() : store().text(e.id());
            ev("   " + e.kind() + "#" + e.ticket() + " [" + e.status() + "] \"" + (text.length() > 700
                    ? text.substring(0, 700) + "…" : text).replace("\n", "\\n") + "\"");
        }
    }

    static List<ProcessHandle> tree() {
        return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).toList();
    }

    static void dumpTree(String label) {
        var t = tree();
        ev("TREE " + label + ": " + t.size() + " live descendant(s)");
        for (var h : t) {
            String cmd = h.info().command().orElse("?");
            String args = String.join(" ", h.info().arguments().orElse(new String[0]));
            ev("   pid " + h.pid() + " " + cmd.substring(Math.max(0, cmd.lastIndexOf('/') + 1)) + " "
                    + (args.length() > 1200 ? args.substring(0, 1200) + "…" : args));
        }
    }

    static String sessionArg() {
        for (var h : tree()) {
            String[] a = h.info().arguments().orElse(new String[0]);
            for (int i = 0; i + 1 < a.length; i++) {
                if (a[i].equals("--session-id") || a[i].equals("--resume")) return a[i] + " " + a[i + 1];
            }
        }
        return "(no claude process)";
    }

    static boolean lineHas(String flag) {
        for (var h : tree()) {
            List<String> a = List.of(h.info().arguments().orElse(new String[0]));
            if (a.contains("-p") && a.contains("--input-format")) return a.contains(flag);
        }
        return false;
    }

    // ---- dialogs ----------------------------------------------------------------------------------------------------

    static JDialog waitDialog(String titlePart, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && d.getTitle() != null && d.getTitle().contains(titlePart)) {
                    return d;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    static List<Component> all(Container c) {
        List<Component> out = new ArrayList<>();
        out.add(c);
        for (Component k : c.getComponents()) {
            if (k instanceof Container kc) out.addAll(all(kc));
            else out.add(k);
        }
        return out;
    }

    static String text(JDialog d) {
        StringBuilder sb = new StringBuilder();
        for (Component c : all(d.getContentPane())) {
            if (c instanceof JTextComponent t) sb.append(t.getText()).append('\n');
        }
        return sb.toString();
    }

    static void press(JDialog d, String label) throws Exception {
        edt(() -> {
            for (Component c : all(d.getContentPane())) {
                if (c instanceof AbstractButton b && label.equals(b.getText())) {
                    b.doClick();
                    return;
                }
            }
            throw new IllegalStateException("no button " + label + " in " + d.getTitle());
        });
        ev("press \"" + label + "\" in \"" + d.getTitle() + "\"");
    }

    static boolean labelsRenderNoHtml(JDialog d) {
        for (Component c : all(d.getContentPane())) {
            if (c instanceof javax.swing.JLabel l && l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null) {
                return false;
            }
        }
        return true;
    }

    /** Start, and answer the full-posture folder dialog with {@code answer} (null: expect no dialog). */
    static String startAnswering(String answer) throws Exception {
        long gen = st().generation();
        long req = st().answer().request();
        click("start");
        JDialog d = waitDialog("Full Claude Code — confirm this folder", answer == null ? 3_000 : 20_000);
        if (d == null) {
            ev("FOLDER DIALOG none");
            // the Start is decided: a spawn (a new generation) or an answer to the request (a refusal)
            waitFor("start decided", 10_000, x -> x.generation() > gen || x.answer().request() > req);
            return null;
        }
        String t = text(d);
        ev("FOLDER DIALOG \"" + t.replace("\n", "\\n") + "\" plain-text=" + labelsRenderNoHtml(d));
        if (answer != null) press(d, answer);
        if ("Trust for full Claude Code".equals(answer)) waitFor("spawn after trust", 15_000, x -> x.generation() > gen);
        return t;
    }

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        Path proj = smoke.resolve("proj");
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/smoke-events.txt")));
        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.assistantActionsRest = true;
        c.claudeCodeModel = "haiku";
        c.claudeCodeBudgetUsd = 2.0;              // a runaway guard for the whole run (OD-74.24: optional; set here)
        cs.save(c);
        ev("user.home=<SMOKE>/home budget limit set=" + c.claudeCodeBudgetUsd);

        // a project in the planted folder, opened as the person opens one
        Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(proj);
        AppConfig sample = new AppConfig();
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, sample,
                new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
        edt(() -> {
            frame = new MainFrame();
            frame.setVisible(true);
        });
        edt(() -> {
            try {
                Method m = MainFrame.class.getDeclaredMethod("requestProject", Path.class,
                        telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.class, String.class);
                m.setAccessible(true);
                m.invoke(frame, profile, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH,
                        "smoke");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(2500);
        edt(() -> frame.openFile(smoke.resolve("sample.yml")));
        long deadline = System.currentTimeMillis() + 20_000;
        while (!((SessionDriver) field("session")).snapshot().logOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Method root = MainFrame.class.getDeclaredMethod("projectRoot");
        root.setAccessible(true);
        ev("log open=" + ((SessionDriver) field("session")).snapshot().logOpen() + " project root is proj: "
                + proj.toRealPath().equals(((Path) root.invoke(frame)).toRealPath()));

        // ---- (1) the full switch's warning: Cancel leaves it off, Turn on records it and shows the mode -------------
        Runnable onSaved = () -> {               // the frame's own save path, as Settings ▸ OK calls it in the application
            try {
                Method m = MainFrame.class.getDeclaredMethod("onConfigChanged");
                m.setAccessible(true);
                m.invoke(frame);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        later(() -> ConfigPanel.show(frame, c(), onSaved));
        JDialog settings = waitDialog("Settings", 10_000);
        ev("SETTINGS dialog=" + (settings == null ? "none" : settings.getTitle()));
        JCheckBox full = null;
        JComboBox<?> modeCombo = null;
        for (Component k : all(settings.getContentPane())) {
            if (k instanceof JCheckBox b && b.getText().startsWith("…with my full Claude Code")) full = b;
            if (k instanceof JComboBox<?> cb && "Full Claude Code mode".equals(cb.getAccessibleContext().getAccessibleName())) {
                modeCombo = cb;
            }
        }
        final JCheckBox fullBox = full;
        final JComboBox<?> combo = modeCombo;
        ev("SETTINGS full switch enabled=" + fullBox.isEnabled() + " selected=" + fullBox.isSelected()
                + " mode shown=" + combo.isVisible());
        later(fullBox::doClick);
        JDialog warning = waitDialog("Full Claude Code — read this first", 10_000);
        String warnText = text(warning);
        ev("WARNING shown=" + (warning != null) + " text-is-the-spec's=" + warnText.contains(
                "the analyser does not confine it to the folder") + " default=Cancel:"
                + "Cancel".equals(((AbstractButton) warning.getRootPane().getDefaultButton()).getText()));
        press(warning, "Cancel");
        Thread.sleep(300);
        ev("AFTER CANCEL full selected=" + fullBox.isSelected() + " mode shown=" + combo.isVisible() + " "
                + (!fullBox.isSelected() ? "PASS" : "FAIL"));
        later(fullBox::doClick);
        warning = waitDialog("Full Claude Code — read this first", 10_000);
        press(warning, "Turn on full Claude Code");
        Thread.sleep(300);
        ev("AFTER TURN ON full selected=" + fullBox.isSelected() + " mode shown=" + combo.isVisible()
                + " mode=" + combo.getSelectedItem());
        final JDialog settingsDialog = settings;
        press(settingsDialog, "OK");
        Thread.sleep(500);
        AppConfig saved = new ConfigStore().load();
        ev("SAVED full=" + saved.claudeCodeFull + " mode=" + saved.claudeCodeFullMode + " limit=" + saved.claudeCodeBudgetUsd
                + " " + (saved.claudeCodeFull && "default".equals(saved.claudeCodeFullMode) ? "PASS" : "FAIL"));

        // ---- (2) the unconfirmed planted folder: the dialog names both; No starts nothing; Yes starts ----------------
        click("claude");
        String dialog = startAnswering("Don't trust");
        Thread.sleep(1500);
        ev("NO: names hook=" + (dialog != null && dialog.contains("SessionStart (1)")) + " names server="
                + (dialog != null && dialog.contains("smoke-stub")) + " phase=" + st().phase() + " tree=" + tree().size()
                + " " + ("OFF".equals(st().phase()) && tree().isEmpty() ? "PASS" : "FAIL"));
        Files.deleteIfExists(smoke.resolve("raw/hook-marker.txt"));
        startAnswering("Trust for full Claude Code");
        waitFor("READY full #1", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev("YES: phase=" + st().phase() + " verdict=" + st().verdict() + " " + sessionArg());
        boolean hookNoteBeforeSend = st().entries().stream().anyMatch(e -> e.status().startsWith("HOOK"));
        ev("HOOK before the first Send: note=" + hookNoteBeforeSend + " marker file="
                + Files.exists(smoke.resolve("raw/hook-marker.txt")) + " " + (hookNoteBeforeSend ? "PASS" : "FAIL"));
        dumpTree("full #1");
        ev("LINE carries --restricted=" + lineHas("--restricted") + " --tools=" + lineHas("--tools") + " --add-dir="
                + lineHas("--add-dir") + " --max-budget-usd=" + lineHas("--max-budget-usd")
                + " --strict-mcp-config(harness wrapper)=" + lineHas("--strict-mcp-config"));

        // ---- (3) the header ---------------------------------------------------------------------------------------
        var h = LocalAgentPanel.shownHeader(st(), store());
        String header = LocalAgentPanel.headerLine(h, st());
        String posture = LocalAgentPanel.postureLine(h, st());
        ev("HEADER \"" + header.replace("\n", "\\n") + "\"");
        ev("POSTURE \"" + posture.replace("\n", "\\n") + "\"");
        ev("HEADER no e-mail=" + !header.contains("@") + " full=" + header.contains("full Claude Code (default)"));

        // ---- (5) the verbs, a hidden destructive verb, graph {delete} refused ---------------------------------------
        send("Call the analyser_context tool, then analyser_read for the first 2 records. Then answer in one sentence: how "
                + "many records does the open log have?");
        waitFor("turn verbs", 150_000, M74P1bSmoke::idle);
        send("Use the analyser_open tool to open /tmp/other.yml. If you cannot, reply with one sentence saying exactly why.");
        waitFor("turn destructive", 150_000, M74P1bSmoke::idle);
        send("Call analyser_graph with {\"delete\": \"anything\"} and reply with the tool's exact result text, nothing else.");
        waitFor("turn graph delete", 150_000, M74P1bSmoke::idle);
        // ---- (4) Bash in default: denied without blocking, the tab says approvals are not available yet -----------
        long b0 = System.currentTimeMillis();
        send("Use the Bash tool to run exactly: echo SMOKE-BASH-DEFAULT. Report its output or the exact error text. Do not "
                + "retry and do not use any other tool.");
        waitFor("turn bash default", 150_000, M74P1bSmoke::idle);
        ev("BASH default ended in ms=" + (System.currentTimeMillis() - b0));
        dumpEntries("default mode");

        // ---- (7) the mode changes with a resumable conversation: Start offers New chat, never --resume ------------
        for (String mode : List.of("acceptEdits", "plan", "auto")) {
            click("inapp");
            waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
            Thread.sleep(2500);
            config().claudeCodeFullMode = mode;
            if ("auto".equals(mode)) config().claudeCodeModel = "haiku";
            click("claude");
            startAnswering(null);
            Thread.sleep(800);
            ev("MODE " + mode + " start with a resumable conversation: verdict=" + st().verdict() + " newChatOffered="
                    + st().newChatOffered() + " " + ("POSTURE_CHANGED".equals(st().verdict()) ? "PASS" : "FAIL"));
            click("newchat");
            startAnswering(null);
            waitFor("ready " + mode, 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
            ev("MODE " + mode + " after New chat: phase=" + st().phase() + " verdict=" + st().verdict() + " reason=\""
                    + st().reason() + "\" " + sessionArg());
            if ("auto".equals(mode) && !"READY".equals(st().phase())) {
                // PB-3: haiku cannot run auto; the analyser stopped it and said so. Now a model that can.
                config().claudeCodeModel = "sonnet";
                click("newchat");
                startAnswering(null);
                waitFor("ready auto sonnet", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
                ev("MODE auto on sonnet: phase=" + st().phase() + " verdict=" + st().verdict());
            }
            if (!"READY".equals(st().phase())) continue;
            long t = System.currentTimeMillis();
            send("Use the Bash tool to run exactly: echo SMOKE-BASH-" + mode.toUpperCase() + ". Report its output or the "
                    + "exact error text. Do not retry and do not use any other tool.");
            waitFor("bash " + mode, 150_000, M74P1bSmoke::idle);
            ev("BASH " + mode + " ended in ms=" + (System.currentTimeMillis() - t));
            dumpEntries(mode + " mode");
        }

        // ---- (6) a changed hook file: the next Start asks again ------------------------------------------------------
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        config().claudeCodeFullMode = "default";
        config().claudeCodeModel = "haiku";
        Path settingsFile = proj.resolve(".claude/settings.json");
        Files.writeString(settingsFile, Files.readString(settingsFile).replace("\"SessionStart\"",
                "\"UserPromptSubmit\": [{\"hooks\": [{\"type\": \"command\", \"command\": \"true\"}]}], \"SessionStart\""));
        click("claude");
        click("newchat");
        String again = startAnswering("Trust for full Claude Code");
        ev("CHANGED HOOK FILE asked again=" + (again != null) + " names the new hook=" + (again != null
                && again.contains("UserPromptSubmit (1)")) + " " + (again != null ? "PASS" : "FAIL"));
        waitFor("READY after re-trust", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        click("claude");
        String unchanged = startAnswering(null);
        waitFor("READY unchanged", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev("UNCHANGED not asked=" + (unchanged == null) + " phase=" + st().phase());

        // ---- (9) Stop mid-turn, a kill and resume, and the tree empty afterwards --------------------------------------
        send("Count from 1 to 300, one number per line. Do not stop early.");
        waitFor("busy", 30_000, LocalAgentState::busy);
        Thread.sleep(2500);
        long stopAt = System.currentTimeMillis();
        click("stop");
        waitFor("stopped", 30_000, x -> !x.busy());
        ev("STOP ended in ms=" + (System.currentTimeMillis() - stopAt) + " phase=" + st().phase());
        String before = sessionArg();
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(2500);
        click("claude");
        startAnswering(null);
        waitFor("READY resume", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        String resumed = sessionArg();
        ev("RESUME " + resumed + " (was " + before + ") " + (resumed.startsWith("--resume")
                && resumed.substring(9).equals(before.substring(before.indexOf(' ') + 1)) ? "PASS" : "FAIL"));
        send("Reply with exactly: after-resume");
        waitFor("turn after resume", 150_000, M74P1bSmoke::idle);
        dumpEntries("after resume");

        // ---- (8) warm start: CUT by the owner on 2026-10-17 (OD-74.25), after this smoke ran. The steps that set the
        // removed claudeCodeWarmStart switch and clicked the tab were trimmed (M74 UX step, 2026-10-17) so this harness
        // still compiles against the current analyser; its recorded results are in SMOKE-report.md.
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(4000);
        dumpTree("after final stop");
        ev("TREE " + (tree().isEmpty() ? "PASS" : "FAIL"));
        ev("SPENT micros=" + st().spentMicros() + " under-reported=" + st().costUnderReported());
        edt(() -> frame.dispose());
        log.close();
        System.exit(0);
    }

    static AppConfig c() {
        try {
            return config();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The person's direct click on the Assistant tab: a real MOUSE_PRESSED posted through the event queue on the side
     * tabs' Assistant tab, after selecting another tab, so WarmStart sees it as the event being dispatched.
     */
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
        Thread.sleep(300);
        int[] at = new int[2];
        edt(() -> {
            int i = tabs.indexOfComponent(dock);
            java.awt.Rectangle r = tabs.getBoundsAt(i);
            at[0] = r.x + r.width / 2;
            at[1] = r.y + r.height / 2;
        });
        long gen = st().generation();
        var q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        long now = System.currentTimeMillis();
        q.postEvent(new java.awt.event.MouseEvent(tabs, java.awt.event.MouseEvent.MOUSE_PRESSED, now,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, at[0], at[1], 1, false, java.awt.event.MouseEvent.BUTTON1));
        q.postEvent(new java.awt.event.MouseEvent(tabs, java.awt.event.MouseEvent.MOUSE_RELEASED, now + 5, 0, at[0], at[1],
                1, false, java.awt.event.MouseEvent.BUTTON1));
        Thread.sleep(3000);
        ev("WARM click: assistant tab selected=" + (tabs.getSelectedComponent() == dock) + " generation " + gen + " → "
                + st().generation() + " phase=" + st().phase());
        return st().generation() != gen;
    }
}
