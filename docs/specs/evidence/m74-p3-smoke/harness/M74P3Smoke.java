package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Window;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static telamin.fluxtion.audit.analyser.analyser.ui.M74P1bSmoke.*;

/**
 * M74 P3 live smoke (spec §8.3): an ISOLATED analyser's own controls — the permission dialog the session asks for, its
 * buttons, the frame's performer, the adapter, the real hosted bridge and the real action server — against the
 * person's installed, logged-in {@code claude}, on the person's Claude plan (owner: no budget cap; every wait here is
 * bounded and a watchdog ends the JVM after 25 minutes). FlatLaf is applied first, as {@code Main} does. {@code PATH}
 * starts with the smoke's {@code bin/}, whose {@code claude} wrapper appends {@code --strict-mcp-config} when the line
 * lacks it (OD-74.18: the harness's addition, never the analyser's).
 */
public final class M74P3Smoke {

    static Path smoke;
    static Path exchange;

    static SessionDriver driver() throws Exception {
        return (SessionDriver) field("session");
    }

    /** The permission dialog now showing, within {@code ms}. */
    static LocalAgentPermissionDialog awaitPermission(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof LocalAgentPermissionDialog d && d.isShowing()) return d;
            }
            Thread.sleep(40);
        }
        return null;
    }

    static String labels(LocalAgentPermissionDialog d) {
        List<String> out = new ArrayList<>();
        for (Component c : all(d.getContentPane())) if (c instanceof JLabel l) out.add(l.getText());
        return String.join(" | ", out);
    }

    static String describe(LocalAgentPermissionDialog d) throws Exception {
        String[] s = new String[1];
        edt(() -> s[0] = "DIALOG \"" + labels(d) + "\" input=\"" + d.inputArea().getText().replace("\n", "\\n")
                + "\" default=" + (d.getRootPane().getDefaultButton() == d.denyButton() ? "Deny" : "OTHER")
                + " allowEnabled=" + d.allowButton().isEnabled());
        return s[0].replace(smoke.toString(), "<SMOKE>");
    }

    static boolean allowEnabled(LocalAgentPermissionDialog d) throws Exception {
        boolean[] b = new boolean[1];
        edt(() -> b[0] = d.allowButton().isEnabled());
        return b[0];
    }

    static void waitTurnEnd(String what) throws Exception {
        waitFor(what, 150_000, x -> x.openTicket() == 0 && !"STOPPING".equals(x.phase()));
        Thread.sleep(600);
    }

    static List<String> exchangeFiles() throws Exception {
        try (Stream<Path> s = Files.list(exchange)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    static String lastAnswer() throws Exception {
        var s = st();
        for (int i = s.entries().size() - 1; i >= 0; i--) {
            var e = s.entries().get(i);
            if ("ANSWER".equals(e.kind())) return store().text(e.id()).replace("\n", "\\n");
        }
        return "";
    }

    static String notes(long ticket) throws Exception {
        List<String> out = new ArrayList<>();
        for (var e : st().entries()) {
            if ("NOTE".equals(e.kind()) && e.ticket() == ticket) out.add("[" + e.status() + "] " + e.detail());
        }
        return String.join(" ; ", out);
    }

    /** Item 2: the hosted bridge's credential, read from the session's per-instance endpoint file (mode 600). */
    static HttpResponse<String> hostedCall(String body) throws Exception {
        Path dir = Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "local-agent");
        Path file;
        try (Stream<Path> s = Files.list(dir)) {
            file = s.filter(p -> p.getFileName().toString().startsWith("endpoint-")).findFirst().orElseThrow();
        }
        Map<?, ?> ep = (Map<?, ?>) Json.parse(Files.readString(file));
        HttpRequest r = HttpRequest.newBuilder(URI.create(ep.get("url") + "/action"))
                .header("X-Analyser-Hosted", String.valueOf(ep.get("hosted")))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(r, HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {
        smoke = Path.of(args[0]);
        exchange = Files.createDirectories(smoke.resolve("home/exchange"));
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/p3-smoke-events.txt")));
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(25 * 60_000L);
                ev("WATCHDOG: 25 minutes — exiting");
                System.exit(3);
            } catch (InterruptedException ignored) {
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.assistantActionsRest = true;
        c.claudeCodeModel = "haiku";
        c.claudeCodeBudgetUsd = null;                 // owner: the person's own plan; no cap
        c.assistantExports = true;
        c.assistantExportDir = exchange.toString();
        c.theme = "Light";
        cs.save(c);
        ThemeManager.apply(c.theme);                  // FlatLaf, as Main applies it before any UI is built
        ev("user.home=<SMOKE>/home theme=" + javax.swing.UIManager.getLookAndFeel().getName() + " exchange=<SMOKE>/home/exchange");

        edt(() -> {
            frame = new MainFrame();
            frame.setSize(1300, 900);
            frame.setVisible(true);
        });
        Thread.sleep(2500);
        edt(() -> frame.openFile(smoke.resolve("sample.yml")));
        long deadline = System.currentTimeMillis() + 20_000;
        while (!driver().snapshot().logOpen() && System.currentTimeMillis() < deadline) Thread.sleep(100);
        Thread.sleep(1500);
        M74P1bFixSmoke.dismissStrayDialogs();
        ev("log open=" + driver().snapshot().logOpen());
        click("claude");
        click("start");
        waitFor("READY", 90_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()) && !x.verdict().isBlank());
        ev("READY phase=" + st().phase() + " verdict=" + st().verdict() + " posture=" + st().posture()
                + " line has --permission-prompt-tool=" + lineHas("--permission-prompt-tool") + " stdio=" + lineHas("stdio")
                + " --disallowedTools=" + lineHas("--disallowedTools") + " dontAsk=" + lineHas("dontAsk"));

        // ---- item 1: a report to the exchange directory — Deny, then Allow once ---------------------------------------
        send("Call analyser_flag with recordIndex 3 and note \"P3 smoke finding\". Then call analyser_report with exactly "
                + "this input: {\"path\": \"p3-smoke.pdf\", \"recordIndex\": 3}. Reply in one line with what happened.");
        LocalAgentPermissionDialog d = awaitPermission(120_000);
        ev(d == null ? "NO DIALOG" : describe(d));
        ev("status=\"" + statusLine() + "\"");
        if (d != null) edt(() -> d.denyButton().doClick());
        waitTurnEnd("turn 1");
        ev("after DENY: exchange=" + exchangeFiles() + " notes=" + notes(1) + " answer=\"" + trim(lastAnswer()) + "\"");

        send("Call analyser_report again with exactly this input: {\"path\": \"p3-smoke.pdf\", \"recordIndex\": 3}. "
                + "Reply in one line with what happened.");
        LocalAgentPermissionDialog d2 = awaitPermission(120_000);
        if (d2 != null) {
            boolean early = allowEnabled(d2);
            long t0 = System.currentTimeMillis();
            while (!allowEnabled(d2) && System.currentTimeMillis() - t0 < 3_000) Thread.sleep(20);
            ev("ALLOW enabled at once=" + early + ", enabled after " + (System.currentTimeMillis() - t0) + " ms");
            edt(() -> d2.allowButton().doClick());
        }
        waitTurnEnd("turn 2");
        ev("after ALLOW: exchange=" + exchangeFiles() + " notes=" + notes(2) + " answer=\"" + trim(lastAnswer()) + "\"");

        // ---- item 2: the same call again, without a new approval — refused by the server (the grant was used) -----------
        HttpResponse<String> again = hostedCall("{\"action\":\"report\",\"params\":{\"path\":\"p3-smoke.pdf\",\"recordIndex\":3}}");
        ev("REPEAT the approved call over the hosted bridge: " + again.statusCode() + " " + again.body());
        HttpResponse<String> other = hostedCall("{\"action\":\"screenshot\",\"params\":{\"path\":\"p3-sneak.png\"}}");
        ev("UNAPPROVED screenshot over the hosted bridge: " + other.statusCode() + " " + other.body() + " exchange="
                + exchangeFiles());

        // ---- item 3: Escape and Enter deny; 120 s unanswered → denied ---------------------------------------------------
        send("Call analyser_screenshot with exactly this input: {\"path\": \"p3-escape.png\"}. Reply in one line.");
        LocalAgentPermissionDialog d3 = awaitPermission(120_000);
        if (d3 != null) {
            ev("ESCAPE: allowEnabled at once=" + allowEnabled(d3));
            edt(() -> d3.getRootPane().getActionMap().get("deny").actionPerformed(new java.awt.event.ActionEvent(d3, 0, "deny")));
        }
        waitTurnEnd("turn 3");
        ev("after ESCAPE: exchange=" + exchangeFiles() + " notes=" + notes(3));
        send("Call analyser_screenshot with exactly this input: {\"path\": \"p3-enter.png\"}. Reply in one line.");
        LocalAgentPermissionDialog d4 = awaitPermission(120_000);
        if (d4 != null) edt(() -> d4.getRootPane().getDefaultButton().doClick());
        waitTurnEnd("turn 4");
        ev("after ENTER (the default button): exchange=" + exchangeFiles() + " notes=" + notes(4));
        send("Call analyser_screenshot with exactly this input: {\"path\": \"p3-timeout.png\"}. Reply in one line.");
        LocalAgentPermissionDialog d5 = awaitPermission(120_000);
        long t5 = System.currentTimeMillis();
        if (d5 != null) {
            ev("COUNTDOWN at once: \"" + countdown(d5) + "\"");
            Thread.sleep(5_000);
            ev("COUNTDOWN after 5 s: \"" + countdown(d5) + "\"");
        }
        waitFor("the deadline", 140_000, x -> x.openTicket() == 0);
        ev("TIMEOUT after " + (System.currentTimeMillis() - t5) / 1000 + " s: dialog showing=" + (d5 != null && d5.isShowing())
                + " notes=" + notes(5));
        waitTurnEnd("turn 5");

        // ---- item 4: Stop while a dialog is open ----------------------------------------------------------------------
        send("Call analyser_screenshot with exactly this input: {\"path\": \"p3-stop.png\"}. Reply in one line.");
        LocalAgentPermissionDialog d6 = awaitPermission(120_000);
        if (d6 != null) edt(() -> d6.stopButton().doClick());
        waitTurnEnd("turn 6");
        ev("after STOP in the dialog: dialog showing=" + (d6 != null && d6.isShowing()) + " notes=" + notes(6)
                + " last answer status=" + lastStatus());

        // ---- item 5: a bidirectional override in the input ---------------------------------------------------------
        send("Call analyser_report with exactly this input, writing the character as the JSON escape \\u202e: "
                + "{\"path\": \"evil\\u202etxt.pdf\", \"recordIndex\": 3}. Reply in one line.");
        LocalAgentPermissionDialog d7 = awaitPermission(120_000);
        ev(d7 == null ? "NO DIALOG (bidi)" : describe(d7));
        if (d7 != null) edt(() -> d7.denyButton().doClick());
        waitTurnEnd("turn 7");

        // ---- item 8: the session record holds ids and decisions, never the input's words -----------------------------
        String record = String.join("\n", driver().auditSink().records());
        ev("RECORD: permission facts=" + count(record, "LocalAgentPermissionRequested") + " answers="
                + count(record, "LocalAgentPermissionAnswered") + " grants=" + count(record, "localAgentGrant ")
                + " hostedCalls=" + count(record, "HostedCallPresented") + " | holds 'p3-smoke.pdf'="
                + record.contains("p3-smoke.pdf") + " 'p3-escape'=" + record.contains("p3-escape") + " 'evil'="
                + record.contains("evil"));
        Files.writeString(smoke.resolve("raw/session-record.txt"), record);

        // ---- item 6: full posture, default: Bash asked and run once; Write asked ------------------------------------
        click("newchat");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        c().claudeCodeFull = true;
        c().claudeCodeFullMode = "default";
        saveAndApply();
        click("start");
        waitFor("READY (full)", 90_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()) && !x.verdict().isBlank());
        ev("FULL READY phase=" + st().phase() + " verdict=" + st().verdict() + " posture=" + st().posture()
                + " header posture=\"" + LocalAgentPanel.postureLine(null, st()).replace("\n", "\\n") + "\"");
        send("Run this exact Bash command once and reply with its output: echo P3-SMOKE-BASH > p3-bash.txt && cat p3-bash.txt");
        LocalAgentPermissionDialog d8 = awaitPermission(120_000);
        ev(d8 == null ? "NO DIALOG (Bash)" : describe(d8));
        if (d8 != null) {
            long t0 = System.currentTimeMillis();
            while (!allowEnabled(d8) && System.currentTimeMillis() - t0 < 3_000) Thread.sleep(20);
            edt(() -> d8.allowButton().doClick());
        }
        waitTurnEnd("full turn 1");
        ev("after Bash ALLOW: answer=\"" + trim(lastAnswer()) + "\" notes=" + notes(st().entries().stream()
                .mapToLong(e -> e.ticket()).max().orElse(0)) + " admitted=" + st().admittedGeneration());
        send("Use the Write tool to create a file named p3-write.txt containing the word hello. Reply in one line.");
        LocalAgentPermissionDialog d9 = awaitPermission(120_000);
        ev(d9 == null ? "NO DIALOG (Write)" : describe(d9));
        if (d9 != null) edt(() -> d9.denyButton().doClick());
        waitTurnEnd("full turn 2");
        ev("after Write DENY: answer=\"" + trim(lastAnswer()) + "\"");

        // ---- item 7: full posture, auto (sonnet): no hosted destructive verb visible ---------------------------------
        click("newchat");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        c().claudeCodeFullMode = "auto";
        c().claudeCodeModel = "sonnet";
        saveAndApply();
        click("start");
        waitFor("READY (auto)", 90_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()) && !x.verdict().isBlank());
        send("Reply with the single word READY.");
        waitTurnEnd("auto turn");
        ev("AUTO posture=" + st().posture() + " admitted=" + st().admittedGeneration() + " verdict=" + st().verdict()
                + " (the first turn's init is checked against the auto set: the destructive verbs hidden)"
                + " line hides report=" + lineHides("analyser_report"));

        click("newchat");
        waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
        dumpTree("end");
        ev("DONE");
        log.close();
        edt(() -> frame.dispose());
        System.exit(0);
    }

    static String countdown(LocalAgentPermissionDialog d) throws Exception {
        String[] s = new String[1];
        edt(() -> s[0] = d.countdownLabel().getText());
        return s[0];
    }

    static String statusLine() throws Exception {
        String[] s = new String[1];
        edt(() -> {
            try {
                var f = LocalAgentPanel.class.getDeclaredField("status");
                f.setAccessible(true);
                s[0] = ((javax.swing.JTextArea) f.get(panel())).getText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return s[0];
    }

    static String lastStatus() throws Exception {
        var s = st();
        for (int i = s.entries().size() - 1; i >= 0; i--) {
            if ("ANSWER".equals(s.entries().get(i).kind())) return s.entries().get(i).status();
        }
        return "";
    }

    static boolean lineHides(String tool) {
        for (var h : tree()) {
            List<String> a = List.of(h.info().arguments().orElse(new String[0]));
            int k = a.indexOf("--disallowedTools");
            if (a.contains("-p") && k >= 0) return a.get(k + 1).contains(tool);
        }
        return false;
    }

    static void saveAndApply() throws Exception {
        new ConfigStore().save(c());
        edt(() -> {
            try {
                Method m = MainFrame.class.getDeclaredMethod("onConfigChanged");
                m.setAccessible(true);
                m.invoke(frame);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ev("settings applied: full=" + c().claudeCodeFull + " mode=" + c().claudeCodeFullMode + " model=" + c().claudeCodeModel);
    }

    static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    static String trim(String s) {
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }
}
