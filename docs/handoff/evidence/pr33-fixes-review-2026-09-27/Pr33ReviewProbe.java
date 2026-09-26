package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.llm.*;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Review-only probe. Synthetic fixtures and an isolated user.home; no keys or external services. */
public final class Pr33ReviewProbe {
    static Path tmp;
    static <T> T edt(Callable<T> f) throws Exception {
        var task = new java.util.concurrent.FutureTask<>(f);
        SwingUtilities.invokeAndWait(task); return task.get();
    }
    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
    static ReportSpec report(String name) {
        return new ReportSpec(name, "DEMO " + name, "2026-09-27T00:00:00Z", "DEMO notes", null, null,
                List.of(ReportSpec.SectionSpec.narrative("DEMO finding")));
    }
    static AppConfig config(Path root) throws Exception {
        Files.createDirectories(root.resolve(".analyser"));
        AppConfig c = new AppConfig();
        c.activeProjectPath = root.resolve(".analyser/project.fluxtion-settings").toString();
        c.assistantExports = true; c.projectExchangeDir = "exchange";
        c.assistantExportDir = Files.createDirectories(tmp.resolve("machine")).toString();
        return c;
    }
    static void headless() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("project"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        AppConfig c = config(root);
        Files.createSymbolicLink(root.resolve("exchange"), outside);
        check(!ExchangeDir.of(c).fromProject(), "direct outside link refused");
        Files.delete(root.resolve("exchange"));
        Files.createSymbolicLink(root.resolve("chain"), outside);
        Files.createSymbolicLink(root.resolve("exchange"), root.resolve("chain"));
        check(!ExchangeDir.of(c).fromProject(), "chain of outside links refused");
        Files.delete(root.resolve("exchange"));
        Files.createDirectories(root.resolve("inside"));
        Files.createSymbolicLink(root.resolve("exchange"), root.resolve("inside"));
        check(ExchangeDir.of(c).fromProject(), "internal link accepted");
        Path alias = Files.createSymbolicLink(tmp.resolve("alias"), root);
        AppConfig aliased = config(alias);
        check(ExchangeDir.of(aliased).fromProject(), "project root reached through link accepted");
        Files.createSymbolicLink(root.resolve("inside/nested"), outside);
        var effective = ExchangeDir.of(c);
        var write = ExportGuard.resolve("nested/probe.txt", true, effective.dir());
        check(effective.fromProject() && write.ok(), "COUNTEREXAMPLE nested outside link accepted by write guard");
        Files.writeString(write.path(), "DEMO");
        check(Files.exists(outside.resolve("probe.txt")), "COUNTEREXAMPLE accepted path writes outside project");
        check(ExportGuard.resolveRead("nested/probe.txt", true, effective.dir(), Set.of()).ok(),
                "COUNTEREXAMPLE nested outside link also accepted by read guard");
        var beforeSwap = ExchangeDir.of(c);
        Files.delete(root.resolve("exchange"));
        Files.createSymbolicLink(root.resolve("exchange"), outside);
        check(!ExchangeDir.of(c).fromProject(), "a later request rechecks a swapped directory");
        var queued = ExportGuard.resolve("after-swap.txt", true, beforeSwap.dir());
        Files.writeString(queued.path(), "DEMO");
        check(Files.exists(outside.resolve("after-swap.txt")), "COUNTEREXAMPLE already-resolved lexical path follows later swap");

        Path mixed = Files.createDirectories(tmp.resolve("MixedCase"));
        if (Files.exists(tmp.resolve("mixedcase"))) {
            AppConfig lower = config(tmp.resolve("mixedcase"));
            Files.createDirectories(mixed.resolve("exchange"));
            check(ExchangeDir.of(lower).fromProject(), "case-insensitive alias accepted on this filesystem");
        } else System.out.println("NOT RUN case-insensitive alias: filesystem is case sensitive");

        AppConfig state = config(root);
        state.reports.add(report("deleted"));
        var tier = ProjectProfile.snapshot(new AppConfig());
        ReportBin.delete(state, "deleted", "now");
        ConfigStore store = new ConfigStore(tmp.resolve("machine-settings"));
        store.save(state, tier);
        AppConfig reloaded = store.load(); reloaded.activeProjectPath = state.activeProjectPath;
        check(ReportBin.restorable(reloaded).equals(List.of("deleted")), "global-tier save preserves machine bin");
        String shared = new SettingsShare().export(state, EnumSet.allOf(SettingsShare.Category.class));
        check(!shared.contains("deletedReport") && !shared.contains("DEMO finding"), "all-category export excludes deleted report");
        Path profile = Path.of(state.activeProjectPath);
        ProjectProfile.save(profile, state, new SettingsShare());
        check(!Files.readString(profile).contains("deletedReport"), "profile save excludes bin");
        SettingsShare share = new SettingsShare();
        AppConfig imported = new AppConfig();
        share.apply(share.preview(shared + "\ndeletedReport.count=1\ndeletedReport.0.name=injected\n", imported),
                EnumSet.allOf(SettingsShare.Category.class), imported);
        check(imported.deletedReports.isEmpty(), "import ignores deletedReport family");
        reloaded.deletedReports.clear(); store.save(reloaded, tier);
        check(store.load().deletedReports.isEmpty(), "empty bin save clears old keys");
        state.activeProjectPath = root.resolve(".analyser/second.fluxtion-settings").toString();
        check(ReportBin.restorable(state).isEmpty(), "two profiles in same root have separate bins");
        state.activeProjectPath = "";
        state.reports.add(report("global")); ReportBin.delete(state, "global", "now");
        check(ReportBin.restorable(state).equals(List.of("global")), "no-project empty key has its own bin");
    }
    static List<Component> descendants(Container parent) {
        var result = new ArrayList<Component>();
        for (var c : parent.getComponents()) { result.add(c); if (c instanceof Container p) result.addAll(descendants(p)); }
        return result;
    }
    static void await(Callable<Boolean> condition, String label) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < end) {
            if (edt(condition)) return;
            Thread.sleep(25); // bounded condition polling, not a substitute for the condition
        }
        throw new AssertionError(label);
    }
    static JDialog dialog(String title) throws Exception {
        await(() -> Arrays.stream(Window.getWindows()).anyMatch(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().equals(title)), "dialog " + title);
        return edt(() -> (JDialog) Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().equals(title)).findFirst().orElseThrow());
    }
    static void click(Robot robot, Container parent, String text) throws Exception {
        JButton button = edt(() -> (JButton) descendants(parent).stream().filter(c -> c instanceof JButton b && b.isShowing() && text.equals(b.getText())).findFirst().orElseThrow());
        check(edt(button::isEnabled), "visible button enabled: " + text);
        SwingUtilities.invokeLater(button::doClick); // actual widget action, including real modal dialog

    }
    static void display() throws Exception {
        com.formdev.flatlaf.FlatLightLaf.setup();
        Path log = Files.writeString(tmp.resolve("demo.yml"), "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            f.dialogs.stop();
            edt(() -> { f.frame.setSize(1200,800); f.frame.setVisible(true); f.frame.toFront(); return null; });
            check(f.ex.render("open", Map.of("log",log.toString())).ok(), "fixture log opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            AppConfig c = (AppConfig) edt(() -> AsyncOpenInterleavingFrameTest.field(f.frame, "config"));
            var dispatcher = new ActionDispatcher(false, null, () -> null, null, f.ex);
            var sections = List.of(Map.of("kind", "narrative", "text", "DEMO finding"));
            check(f.ex.render("report", Map.of("name","DEMO report","sections",sections)).ok(), "fixture report created");
            edt(() -> {
                for (var item : descendants(f.frame)) if (item instanceof JTabbedPane tabs) {
                    for (int i=0;i<tabs.getTabCount();i++) if (tabs.getTitleAt(i).equals("Reports")) tabs.setSelectedIndex(i);
                }
                return null;
            });
            Robot robot = new Robot(); robot.setAutoDelay(80);
            click(robot, f.frame, "Delete…");
            JDialog delete = dialog("Delete report");
            click(robot, delete, "OK");
            await(() -> c.reports.isEmpty() && c.deletedReports.size()==1, "delete completed");
            check(true, "real Delete button and confirmation move report into bin");
            click(robot, f.frame, "Restore deleted…");
            JDialog restore = dialog("Restore deleted report");
            check(edt(() -> descendants(restore).stream().anyMatch(x -> x instanceof JComboBox<?> b && "DEMO report".equals(b.getSelectedItem()))), "restore dialog offers deleted report");
            click(robot, restore, "OK");
            await(() -> c.reports.size()==1 && c.deletedReports.isEmpty(), "restore completed");
            check(true, "real restore dialog restores report");
            click(robot, f.frame, "Restore deleted…");
            JDialog empty = dialog("Restore deleted report");
            check(edt(() -> descendants(empty).stream().anyMatch(x -> x instanceof JLabel l && l.getText()!=null && l.getText().contains("No deleted reports to restore"))), "empty-bin message visible");
            javax.imageio.ImageIO.write(robot.createScreenCapture(edt(() -> f.frame.getBounds())), "png", tmp.resolve("empty-bin.png").toFile());
            click(robot, empty, "OK");

            check(f.ex.render("report",Map.of("name","true","sections",sections)).ok(), "report named true created");
            check(f.ex.render("report",Map.of("name","true","delete",true)).ok(), "report named true deleted");
            var result = dispatcher.dispatch(Map.of("action","report","params",Map.of("restore","true")));
            System.out.println("restore-name-true=" + result.toMap());
            check(result.ok() && edt(() -> c.reports.stream().noneMatch(r -> r.name().equals("true"))), "COUNTEREXAMPLE restoring name true lists instead of restoring");
            check(f.ex.render("report",Map.of("name","combined","sections",sections)).ok(), "combined fixture created");
            check(f.ex.render("report",Map.of("name","combined","delete",true)).ok(), "combined fixture deleted");
            var combined = dispatcher.dispatch(Map.of("action","report","params",Map.of("restore","combined","sections", List.of(Map.of("kind","narrative","text","REPLACEMENT")))));
            System.out.println("restore-with-sections=" + combined.toMap());
            check(combined.ok() && edt(() -> c.reports.stream().filter(r -> r.name().equals("combined")).findFirst().orElseThrow().sections().equals(c.reports.get(0).sections())), "COUNTEREXAMPLE combined restore silently ignores sections");
            Path root = Files.createDirectories(tmp.resolve("export-project"));
            Path exchange = Files.createDirectories(root.resolve("exchange"));
            Path outside = Files.createDirectories(tmp.resolve("export-outside"));
            Files.createDirectories(root.resolve(".analyser"));
            Files.createSymbolicLink(exchange.resolve("nested"), outside);
            edt(() -> { c.activeProjectPath=root.resolve(".analyser/project.fluxtion-settings").toString(); c.projectExchangeDir="exchange"; c.assistantExports=true; return null; });
            var shot = dispatcher.dispatch(Map.of("action","screenshot","params",Map.of("path","nested/out.png")));
            System.out.println("screenshot-nested-link-ok=" + shot.ok());
            check(shot.ok() && Files.exists(outside.resolve("out.png")), "COUNTEREXAMPLE real screenshot verb writes outside project through nested link");
        }
    }
    public static void main(String[] args) throws Exception {
        tmp=Path.of(args[1]); Files.createDirectories(tmp);
        try { if (args[0].equals("headless")) headless(); else display(); }
        catch(Throwable t) { t.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
}
