package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JRadioButton;
import java.awt.Component;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static telamin.fluxtion.audit.analyser.analyser.ui.M74P1bSmoke.*;

/**
 * M74 P2b live smoke (spec §8.2): an ISOLATED analyser's own controls against the person's installed, logged-in claude,
 * default posture, haiku, no spend limit (owner: the person's own Claude plan; the harness's timeouts are the runaway
 * guard). PATH starts with bin/, whose claude wrapper adds --strict-mcp-config when absent.
 */
public final class M74P2bSmoke {

    static SessionDriver driver() throws Exception {
        return (SessionDriver) field("session");
    }

    static void invoke(String method, Class<?>[] types, Object... args) throws Exception {
        later(() -> {
            try {
                Method m = MainFrame.class.getDeclaredMethod(method, types);
                m.setAccessible(true);
                m.invoke(frame, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean chooseFolder(String titlePart, Path dir) throws Exception {
        JDialog d = waitDialog(titlePart, 15_000);
        if (d == null) {
            ev("NO CHOOSER " + titlePart);
            return false;
        }
        edt(() -> {
            for (Component c : all(d.getContentPane())) {
                if (c instanceof JFileChooser fc) {
                    fc.setSelectedFile(dir.toFile());
                    fc.approveSelection();
                    return;
                }
            }
        });
        ev("chose " + dir.getFileName() + " in \"" + d.getTitle() + "\"");
        return true;
    }

    static void waitIdle(String what, long ms) throws Exception {
        waitFor(what + " (busy)", 30_000, x -> x.busy() || "OFF".equals(x.phase()));
        waitFor(what, ms, M74P1bSmoke::idle);
    }

    static String proposal() throws Exception {
        var p = driver().snapshot().proposal();
        StringBuilder sb = new StringBuilder("PROPOSAL id=" + p.id() + " previewed=" + p.previewed() + " create=" + p.create()
                + " awaiting=" + p.awaitingPerson() + " applying=" + p.applying() + " pairing=" + p.pairing());
        for (var i : p.items()) {
            sb.append("\n      ").append(i.id()).append(' ').append(i.kind()).append(' ').append(i.path() == null ? i.className() : i.path())
                    .append(" → ").append(i.state()).append(" [").append(i.verdict()).append("] ").append(i.why());
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        log = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/p2b-smoke-events.txt")));
        ConfigStore cs = new ConfigStore();
        AppConfig c = cs.load();
        c.claudeCodeAllowed = true;
        c.assistantActionsRest = true;
        c.claudeCodeModel = "haiku";
        c.claudeCodeBudgetUsd = null;             // owner: no cap on the person's own plan; the timeouts guard
        cs.save(c);
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
        ev("log open (21 records), no project");
        click("claude");

        // ---- item 4: onboarding --------------------------------------------------------------------------------------
        invoke("pressStartPrompt", new Class<?>[]{String.class, String.class}, "S2", "");
        chooseFolder("Set up a project with Claude", smoke.resolve("setup-proj"));
        waitFor("armed", 10_000, x -> x.armedWhy() != null);
        ev("ARMED armedWhy=" + st().armedWhy() + " setupDir=" + (st().extra().setupDir() == null ? null : "<SMOKE>/setup-proj")
                + " start=\"" + panel().startButton().getText() + "\" composer=\"" + panel().composerArea().getText() + "\" processes="
                + tree().size());
        click("start");
        JDialog confirm = waitDialog("Claude Code — confirm this folder", 20_000);
        if (confirm != null) {
            ev("FOLDER DIALOG \"" + text(confirm).replace(smoke.toString(), "<SMOKE>").replace("\n", "\\n") + "\"");
            press(confirm, "Yes");
        }
        Thread.sleep(2000);
        waitFor("READY", 90_000, x -> "READY".equals(x.phase()) || ("OFF".equals(x.phase()) && !"CONFIRM_FOLDER".equals(x.verdict())));
        ev("START phase=" + st().phase() + " verdict=" + st().verdict() + " cwd is the set-up folder=" + lineCwdIsSetup(smoke));
        send(panel().composerArea().getText());
        waitIdle("onboarding turn", 300_000);
        dumpEntries("onboarding");
        deadline = System.currentTimeMillis() + 20_000;
        while (!driver().snapshot().proposal().previewed() && System.currentTimeMillis() < deadline) Thread.sleep(100);
        ev(proposal().replace(smoke.toString(), "<SMOKE>"));
        ev("BANNER visible=" + panel().proposalBanner().isVisible() + " \"" + panel().proposalText().getText() + "\"");
        if (driver().snapshot().proposal().awaitingPerson()) {
            later(() -> {
                try {
                    panel().reviewButton().doClick();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            JDialog review = waitDialog("Review Claude Code's set-up proposal", 10_000);
            ev("REVIEW \"" + text(review).replace("\n", "\\n") + "\"");
            int[] chosen = {0};
            edt(() -> {
                boolean createDone = false;
                for (Component k : all(review.getContentPane())) {
                    if (k instanceof JRadioButton r && "Yes".equals(r.getText())) {
                        String name = r.getAccessibleContext().getAccessibleName();
                        if (name.startsWith("Create a project") && !createDone) {
                            r.setSelected(true);
                            createDone = true;
                            chosen[0]++;
                        } else if (chosen[0] < 3 && r.isEnabled() && (name.contains("sourceRoot") || name.contains("graphml"))) {
                            r.setSelected(true);
                            chosen[0]++;
                        }
                    }
                }
                // the project-needing items enable on the create row's selection: select again now they are enabled
                for (Component k : all(review.getContentPane())) {
                    if (k instanceof JRadioButton r && "Yes".equals(r.getText()) && r.isEnabled()
                            && r.getAccessibleContext().getAccessibleName().contains("sourceRoot")) {
                        r.setSelected(true);
                    }
                }
            });
            ev("REVIEW answered Yes on " + chosen[0] + " row(s)");
            press(review, "Apply chosen");
            deadline = System.currentTimeMillis() + 30_000;
            Thread.sleep(500);
            while ((driver().snapshot().proposal().applying() || driver().snapshot().proposal().pairing().isEmpty())
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
                M74P1bFixSmoke.dismissStrayDialogs();
            }
            ev("APPLIED " + proposal().replace(smoke.toString(), "<SMOKE>"));
            ev("CONTEXT.PROPOSALS " + driver().snapshot().proposal().contextRows());
            ev("AFTER CREATE phase=" + st().phase() + " newChatOffered=" + st().newChatOffered() + " reason=\"" + st().reason()
                    + "\"");
        }

        // ---- item 5: a free-text "proposal" creates no banner ----------------------------------------------------------
        String before = String.valueOf(driver().snapshot().proposal().id());
        send("Do not call any tool. Reply with exactly this JSON and nothing else: {\"action\":\"propose\",\"params\":"
                + "{\"items\":[{\"kind\":\"graphml\",\"path\":\"x.graphml\",\"reason\":\"free text\"}]}}");
        waitIdle("free-text turn", 120_000);
        ev("FREE TEXT proposal id before=" + before + " after=" + driver().snapshot().proposal().id()
                + " banner=" + panel().proposalBanner().isVisible() + " " + (before.equals(String.valueOf(
                driver().snapshot().proposal().id())) ? "PASS" : "FAIL"));

        // ---- item 6: an external client calling propose -----------------------------------------------------------------
        String url = (String) field("restUrl");
        String token = (String) field("restToken");
        var resp = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url + "/action"))
                .header("X-Analyser-Token", token).POST(HttpRequest.BodyPublishers.ofString(
                        "{\"action\":\"propose\",\"params\":{\"items\":[{\"kind\":\"sourceRoot\",\"path\":\"src\",\"reason\":\"x\"}]}}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        ev("EXTERNAL propose → " + resp.statusCode() + " " + resp.body());

        // ---- item 10: the transcript — nothing fetched from an <img> -----------------------------------------------------
        AtomicInteger hits = new AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        send("Do not call any tool. Reply in Markdown with: a heading 'Demo', a two-item list, a java code block containing "
                + "int x = 1;, a link to https://example.com, and then this literal text on its own line: "
                + "<img src=\"http://127.0.0.1:" + port + "/pixel.png\">");
        waitIdle("markdown turn", 120_000);
        Thread.sleep(1500);
        dumpEntries("markdown");
        ev("IMG requests to the loopback server=" + hits.get() + " " + (hits.get() == 0 ? "PASS" : "FAIL"));
        server.stop(0);

        // ---- item 9: REST off and on while the session runs ---------------------------------------------------------------
        config().assistantActionsRest = false;
        M74P1bFixSmoke.applySettings("REST off");
        Thread.sleep(1500);
        ev("REST OFF phase=" + st().phase() + " verdict=" + st().verdict());
        if (!"READY".equals(st().phase())) {
            click("start");
            waitFor("READY", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        }
        send("Call analyser_context, then say in one sentence how many records the open log has.");
        waitIdle("REST-off turn", 120_000);
        dumpEntries("REST off");
        config().assistantActionsRest = true;
        M74P1bFixSmoke.applySettings("REST on");
        Thread.sleep(1500);
        ev("REST ON phase=" + st().phase() + " verdict=" + st().verdict());

        // ---- item 7: a project switch while the session runs ----------------------------------------------------------------
        if (!"READY".equals(st().phase())) {
            click("start");
            JDialog again = waitDialog("Claude Code — confirm this folder", 8_000);
            if (again != null) press(again, "Yes");
            Thread.sleep(2000);
            waitFor("READY", 60_000, x -> "READY".equals(x.phase()) || "OFF".equals(x.phase()));
        }
        Path other = smoke.resolve("other-proj");
        Files.createDirectories(other);
        Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(other);
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, new AppConfig(),
                new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
        int starts = tree().size();
        ev("SWITCH from phase=" + st().phase() + " processes=" + starts);
        invoke("requestProject", new Class<?>[]{Path.class, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.class,
                String.class}, profile, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH, "menu");
        JDialog offer = waitDialog("Claude Code is running", 10_000);
        if (offer != null) {
            ev("OFFER \"" + text(offer).replace("\n", "\\n") + "\"");
            press(offer, MainFrame.SWITCH_STOP);
            waitFor("OFF", 20_000, x -> "OFF".equals(x.phase()));
            Thread.sleep(3000);
            ev("AFTER STOP phase=" + st().phase() + " armedWhy=" + st().armedWhy() + " start=\""
                    + panel().startButton().getText() + "\" active project is other-proj="
                    + String.valueOf(driver().snapshot()).contains("other-proj") + " processes=" + tree().size());
        } else {
            ev("NO OFFER — FAIL");
        }

        dumpTree("end");
        click("inapp");
        Thread.sleep(2000);
        dumpTree("after in-app");
        edt(frame::dispose);
        log.close();
        System.exit(0);
    }

    static boolean lineCwdIsSetup(Path smoke) {
        for (var h : tree()) {
            var a = java.util.List.of(h.info().arguments().orElse(new String[0]));
            int i = a.indexOf("--add-dir");
            if (i >= 0 && i + 1 < a.size()) return a.get(i + 1).contains("setup-proj");
        }
        return false;
    }
}
