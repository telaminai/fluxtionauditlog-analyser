package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M43.7 — finding the skill-shaped runbooks a project already has, to OFFER them. */
class SkillDiscoveryTest {

    private static void skill(Path root, String rel, String frontmatter) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, frontmatter);
    }

    private static SkillDiscovery.Found find(Path root) {
        return SkillDiscovery.find(root, Map.of());
    }

    @Test
    void findsTheDocumentedLayoutAndNamesItFromFrontmatter(@TempDir Path root) throws Exception {
        skill(root, ".claude/skills/restart/SKILL.md",
                "---\nname: restart-quote-service\ndescription: Restart it; what to check first.\n---\n1. …");

        var found = find(root);
        assertEquals(1, found.candidates().size());
        var c = found.candidates().get(0);
        assertEquals(".claude/skills/restart/SKILL.md", c.path(), "stored form: relative, forward slashes");
        assertEquals("restart-quote-service", c.name());
        assertEquals("Restart it; what to check first.", c.description());
        assertFalse(c.declared());
    }

    @Test
    void withNoFrontmatterTheDIRECTORYnamesTheSkill() throws Exception {
        // the convention puts the name in the path; a file without frontmatter is still a usable offer
        Path root = Files.createTempDirectory("skills");
        skill(root, ".claude/skills/deploy-service/SKILL.md", "1. just steps, no header\n");

        var c = find(root).candidates().get(0);
        assertEquals("deploy-service", c.name());
        assertNull(c.description(), "nothing declared it, so nothing is suggested");
    }

    @Test
    void oneAlreadyDeclaredIsMARKED_notHidden(@TempDir Path root) throws Exception {
        skill(root, ".claude/skills/restart/SKILL.md", "---\nname: restart\n---\n");
        skill(root, ".claude/skills/deploy/SKILL.md", "---\nname: deploy\n---\n");

        var found = SkillDiscovery.find(root, Map.of("restart",
                Runbooks.Pointer.of(".claude/skills/restart/SKILL.md")));
        assertEquals(2, found.candidates().size(), "a known file must still be listed");
        assertTrue(found.candidates().stream().filter(SkillDiscovery.Candidate::declared)
                .allMatch(c -> c.path().contains("restart")));
        // hiding it would leave someone hunting for a file they can see on disk and cannot see here
    }

    @Test
    void onlyOffersWhatCouldActuallyBeSTORED(@TempDir Path root) throws Exception {
        // a path the gate would refuse must never appear as an offer: the user would pick it and be told
        // no, for something they did not type
        skill(root, "ops/wei rd$dir/SKILL.md", "---\nname: odd\n---\n");
        skill(root, "ops/fine/SKILL.md", "---\nname: fine\n---\n");

        var found = find(root);
        for (var c : found.candidates()) {
            assertTrue(Runbooks.refusePointer("skill", c.path()).isEmpty(),
                    "offered a path the gate refuses: " + c.path());
        }
        assertTrue(found.candidates().stream().anyMatch(c -> c.name().equals("fine")));
    }

    @Test
    void skipsTheDirectoriesThatMakeATreeBigWithoutMakingItInteresting(@TempDir Path root) throws Exception {
        skill(root, "target/classes/skills/x/SKILL.md", "---\nname: built\n---\n");
        skill(root, "node_modules/pkg/SKILL.md", "---\nname: vendored\n---\n");
        skill(root, ".git/hooks/SKILL.md", "---\nname: git\n---\n");
        skill(root, "ops/real/SKILL.md", "---\nname: real\n---\n");

        var names = find(root).candidates().stream().map(SkillDiscovery.Candidate::name).toList();
        assertEquals(List.of("real"), names, "build output and vendored trees are not the project's runbooks");
    }

    @Test
    void caseInsensitiveOnTheFileName(@TempDir Path root) throws Exception {
        skill(root, "ops/a/Skill.md", "---\nname: mixed\n---\n");
        assertEquals(1, find(root).candidates().size());
    }

    @Test
    void aPlainMarkdownFileIsNotASkill(@TempDir Path root) throws Exception {
        skill(root, "docs/notes.md", "---\nname: notes\n---\n");
        assertTrue(find(root).candidates().isEmpty(), "only the convention's file name is a skill");
    }

    @Test
    void resultsAreOrderedSoTwoRunsAgree(@TempDir Path root) throws Exception {
        skill(root, "z/SKILL.md", "---\nname: zed\n---\n");
        skill(root, "a/SKILL.md", "---\nname: ay\n---\n");
        skill(root, "m/SKILL.md", "---\nname: em\n---\n");

        var first = find(root).candidates().stream().map(SkillDiscovery.Candidate::path).toList();
        assertEquals(List.of("a/SKILL.md", "m/SKILL.md", "z/SKILL.md"), first);
        assertEquals(first, find(root).candidates().stream().map(SkillDiscovery.Candidate::path).toList());
    }

    @Test
    void theScanIsBOUNDEDandSaysWhenItTruncated(@TempDir Path root) throws Exception {
        for (int i = 0; i < SkillDiscovery.MAX_RESULTS + 10; i++) {
            skill(root, "ops/s" + i + "/SKILL.md", "---\nname: s" + i + "\n---\n");
        }
        var found = find(root);
        assertTrue(found.candidates().size() <= SkillDiscovery.MAX_RESULTS);
        assertTrue(found.truncated(), "a silent cap reads as 'that is all there is' — say it stopped");
    }

    @Test
    void nothingBelowTheDepthLimitIsWalkedForever(@TempDir Path root) throws Exception {
        String deep = String.join("/", java.util.Collections.nCopies(SkillDiscovery.MAX_DEPTH + 4, "d"));
        skill(root, deep + "/SKILL.md", "---\nname: buried\n---\n");
        assertTrue(find(root).candidates().isEmpty(), "a monorepo must not hang the dialog");
    }

    @Test
    void aMissingOrFileRootIsNotAnError(@TempDir Path root) throws Exception {
        assertTrue(SkillDiscovery.find(null, Map.of()).candidates().isEmpty());
        assertTrue(SkillDiscovery.find(root.resolve("nope"), Map.of()).candidates().isEmpty());
        Path file = root.resolve("f.txt");
        Files.writeString(file, "x");
        assertTrue(SkillDiscovery.find(file, Map.of()).candidates().isEmpty());
    }

    @Test
    void NOvendorDirectoryIsPrivileged(@TempDir Path root) throws Exception {
        // owner, 2026-08-30: "let's not only use Claude". A skill is recognised by its FILE NAME, wherever
        // it lives, so a project that uses another harness's layout — or nobody's — is served identically.
        // The analyser then tells the agent which runbooks to load via context.runbooks[], which is what
        // makes this work for a harness nobody here has heard of.
        skill(root, ".claude/skills/a/SKILL.md", "---\nname: a\n---\n");
        skill(root, ".agents/skills/b/SKILL.md", "---\nname: b\n---\n");
        skill(root, ".config/agent/c/SKILL.md", "---\nname: c\n---\n");
        skill(root, "docs/runbooks/d/SKILL.md", "---\nname: d\n---\n");
        skill(root, "e/SKILL.md", "---\nname: e\n---\n");

        var names = find(root).candidates().stream().map(SkillDiscovery.Candidate::name).sorted().toList();
        assertEquals(List.of("a", "b", "c", "d", "e"), names,
                "one of these was ranked or excluded by its path — vendor paths must not be encoded here");
    }

    @Test
    void aSymlinkedDirectoryIsNotFollowedOutOfTheProject(@TempDir Path root) throws Exception {
        Path outside = Files.createTempDirectory("outside");
        skill(outside, "secret/SKILL.md", "---\nname: escaped\n---\n");
        try {
            Files.createSymbolicLink(root.resolve("link"), outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return;                       // no symlink support here; the guard is still in the code
        }
        assertTrue(find(root).candidates().isEmpty(),
                "a pointer must stay inside the project, so discovery must not leave it either");
    }

    @Test
    void aSymlinkedSKILLfileIsNotFollowedEither(@TempDir Path root) throws Exception {
        // review F1: the directory case was guarded and the FILE case was not, so my own symlink test
        // passed while the asymmetric hole stayed open. One rule, both shapes.
        Path outside = Files.createTempDirectory("outside");
        Path real = outside.resolve("SKILL.md");
        Files.writeString(real, "---\nname: escaped\ndescription: from outside the project\n---\n");
        Files.createDirectories(root.resolve("ops"));
        try {
            Files.createSymbolicLink(root.resolve("ops/SKILL.md"), real);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return;                       // no symlink support here; the guard is still in the code
        }
        assertTrue(find(root).candidates().isEmpty(),
                "a linked file must not have its frontmatter read from outside the project");
    }
}
