package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JTabbedPane;
import java.awt.Component;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static telamin.fluxtion.audit.analyser.analyser.ui.M74P1bSmoke.*;

/**
 * M74 P1b §8.1 item 8, live: warm start with the full switch on starts nothing on the person's direct click on the
 * Assistant tab; the control — the same click in the default posture — starts Claude Code. No turn is sent (no spend).
 */
public final class M74P1bWarmSmoke {

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/warm-smoke-events.txt")));
        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.claudeCodeFull = true;
        c.claudeCodeFullMode = "default";
        c.claudeCodeWarmStart = true;
        c.assistantActionsRest = true;
        c.assistantPoppedOut = false;
        c.claudeCodeModel = "haiku";
        cs.save(c);
        ThemeManager.apply(args.length > 1 ? args[1] : "Light");   // as Main does, before any UI is built
        edt(() -> {
            frame = new MainFrame();
            frame.setSize(1600, 1000);
            frame.setVisible(true);
            frame.openFile(smoke.resolve("sample.yml"));
        });
        long deadline = System.currentTimeMillis() + 20_000;
        while (!((SessionDriver) field("session")).snapshot().logOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Thread.sleep(2000);
        M74P1bFixSmoke.dismissStrayDialogs();
        click("claude");
        Thread.sleep(500);
        JTabbedPane tabs = (JTabbedPane) field("sideTabs");
        Component dock = (Component) field("assistantDock");
        edt(() -> {
            int i = tabs.indexOfComponent(dock);
            ev("TABS showing=" + tabs.isShowing() + " size=" + tabs.getSize() + " count=" + tabs.getTabCount()
                    + " assistant index=" + i + " bounds=" + (i < 0 ? null : tabs.getBoundsAt(i)) + " frame=" + frame.getSize());
        });
        tabs.addChangeListener(e -> {
            java.awt.AWTEvent cur = java.awt.EventQueue.getCurrentEvent();
            ev("tab change while dispatching: " + (cur == null ? "null" : cur.getClass().getSimpleName() + " id=" + cur.getID()
                    + " source=" + (cur.getSource() == null ? "null" : cur.getSource().getClass().getSimpleName()))
                    + " direct(P1a predicate, current event only)=" + WarmStart.isDirectSelection(cur, tabs));
            StringBuilder st = new StringBuilder();
            for (StackTraceElement f : new Throwable().getStackTrace()) {
                String cn = f.getClassName();
                if (cn.startsWith("java.awt.EventDispatch")) break;
                if (cn.contains("Tabbed") || cn.contains("Mouse") || cn.contains("EventQueue") || cn.contains("MainFrame")
                        || cn.contains("Hierarchy") || cn.contains("Component")) st.append(cn.substring(cn.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(" < ");
            }
            ev("   by: " + st);
        });
        boolean full = M74P1bFixSmoke.warmClick();
        ev("WARM full posture: started=" + full + " verdict=" + st().verdict() + " " + (!full ? "PASS" : "FAIL"));
        config().claudeCodeFull = false;
        M74P1bFixSmoke.applySettings("full switch off (control)");
        M74P1bFixSmoke.dismissStrayDialogs();
        boolean def = M74P1bFixSmoke.warmClick();
        waitFor("control", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        ev("WARM control, default posture: started=" + def + " phase=" + st().phase() + " " + sessionArg() + " "
                + (def ? "PASS (the click is the person's direct selection)" : "FAIL (the click did not count)"));
        click("inapp");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        Thread.sleep(3000);
        ev("TREE " + (tree().isEmpty() ? "PASS" : "FAIL"));
        edt(() -> frame.dispose());
        log.close();
        System.exit(0);
    }
}
