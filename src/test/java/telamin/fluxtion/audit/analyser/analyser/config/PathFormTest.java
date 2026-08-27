package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.6 (spec-portable-context D-C9) — the anchor is declared, not chosen per path. A sibling checkout under the
 * declared workspace anchor is written relative to the project with {@code ..} steps and resolves on any machine;
 * without the anchor it falls to {@code ~}, portable for one person only. Pointers are untouched by the anchor.
 */
class PathFormTest {

    @Test
    void theAnchorIsARunOfUpSteps_andNothingElse() {
        for (String ok : List.of(".", "..", "../..", "../../../..")) assertTrue(PathForm.refuseWorkspaceRoot(ok).isEmpty(), ok);
        for (String bad : List.of("/", "/work", "~", "../shared", "..\\..", "../../../../../../..", "x")) {
            assertTrue(PathForm.refuseWorkspaceRoot(bad).isPresent(), "must refuse: " + bad);
        }
        assertTrue(PathForm.refuseWorkspaceRoot("").isEmpty(), "absent is fine");
        assertEquals(Path.of("/work"), PathForm.workspaceDir(Path.of("/work/proj"), ".."));
        assertNull(PathForm.workspaceDir(Path.of("/work/proj"), "/etc"), "a refused anchor anchors nothing");
        assertNull(PathForm.workspaceDir(null, ".."));
    }

    @Test
    void theFormIsTheMostSpecificMatch() {
        Path proj = Path.of("/home/someone/work/proj");
        String home = "/home/someone";
        assertEquals(PathForm.Form.PROJECT, PathForm.of("/home/someone/work/proj/src/main/java", proj, "..", home));
        assertEquals(PathForm.Form.WORKSPACE, PathForm.of("/home/someone/work/shared-lib/src", proj, "..", home));
        assertEquals(PathForm.Form.HOME, PathForm.of("/home/someone/work/shared-lib/src", proj, "", home), "no anchor: the sibling falls to ~");
        assertEquals(PathForm.Form.HOME, PathForm.of("/home/someone/other/x", proj, "..", home), "outside the anchor: ~");
        assertEquals(PathForm.Form.ABSOLUTE, PathForm.of("/opt/lib/src", proj, "../..", home));
        assertEquals(PathForm.Form.RELATIVE, PathForm.of("src/main/java", proj, "..", home));
        assertEquals(PathForm.Form.HOME, PathForm.of("/home/someone/x", null, "..", home), "no project: no project or workspace form");
    }

    @Test
    void aSiblingCheckoutTravelsOnlyWithTheAnchor_andPointersStayProjectRelative(@TempDir Path work) throws Exception {
        Path proj = work.resolve("proj"), sibling = work.resolve("shared-lib/src/main/java");
        Files.createDirectories(proj.resolve(".analyser")); Files.createDirectories(sibling);
        SettingsShare share = new SettingsShare(work.getParent().toString());   // "home" = the temp parent, so ~ is reachable
        AppConfig c = new AppConfig();
        c.sourceRoots.add(sibling.toString());

        String withoutAnchor = share.export(c, Set.of(SettingsShare.Category.SOURCE_ROOTS), proj);
        assertTrue(withoutAnchor.contains("sourceRoot.0=~/"), "no anchor: ~-relative, portable for one person:\n" + withoutAnchor);

        c.workspaceRoot = "..";
        String withAnchor = share.export(c, Set.of(SettingsShare.Category.SOURCE_ROOTS), proj);
        assertTrue(withAnchor.contains("sourceRoot.0=../shared-lib/src/main/java"), withAnchor);
        assertTrue(withAnchor.contains("workspaceRoot=.."), "the anchor rides with the roots it makes portable");

        // a colleague's checkout at another location: the profile resolves against ITS directory
        Path theirWork = Files.createDirectories(work.resolve("elsewhere/team"));
        Path theirProj = theirWork.resolve("proj");
        Files.createDirectories(theirProj.resolve(".analyser"));
        AppConfig theirs = new AppConfig();
        var plan = share.preview(withAnchor, theirs, theirProj);
        share.apply(plan, Set.of(SettingsShare.Category.SOURCE_ROOTS), theirs);
        assertEquals(List.of(theirWork.resolve("shared-lib/src/main/java").toString()), theirs.sourceRoots);
        assertEquals("..", theirs.workspaceRoot);
        assertTrue(plan.summary().get(SettingsShare.Category.SOURCE_ROOTS).contains("workspace anchor .."));

        // a refused anchor is named, and the roots still arrive
        var bad = share.preview(withAnchor.replace("workspaceRoot=..", "workspaceRoot=/etc"), new AppConfig(), theirProj);
        assertTrue(bad.summary().get(SettingsShare.Category.SOURCE_ROOTS).contains("REFUSED"));

        // D-C2 untouched: a runbook or glossary pointer may not use the anchor's '..'
        assertTrue(Runbooks.refusePointer("runbook 'deploy'", "../shared-lib/ops/deploy.md").isPresent());
        assertTrue(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.SOURCE_ROOTS), "the anchor rides an existing project category");
        // profile round trip + clear
        Path file = proj.resolve(ProjectProfile.CANONICAL_RELATIVE);
        assertTrue(ProjectProfile.save(file, c, share));
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded());
        assertEquals("..", back.workspaceRoot);
        assertEquals(List.of(sibling.toString()), back.sourceRoots);
        ProjectProfile.clearProjectScoped(back);
        assertEquals("", back.workspaceRoot);
    }
}
